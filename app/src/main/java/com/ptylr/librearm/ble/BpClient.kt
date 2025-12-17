package com.ptylr.librearm.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.ptylr.librearm.model.BpReading
import com.ptylr.librearm.model.BpState
import com.ptylr.librearm.model.MeasurementMode
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * BLE client for QardioArm blood pressure cuff.
 * Mirrors the iOS BPClient behaviors: connection management, session debouncing,
 * average-of-3 mode with adjustable delay, and final reading callback.
 */
class BpClient(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val _state = MutableStateFlow(BpState())
    val state: StateFlow<BpState> = _state

    var onFinalReading: ((BpReading) -> Unit)? = null

    private var gatt: BluetoothGatt? = null
    private var measurementCharacteristic: BluetoothGattCharacteristic? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null

    private var connectTimeoutJob: Job? = null
    private var finalizeJob: Job? = null
    private var countdownJob: Job? = null

    private var sessionActive = false
    private var hasFiredFinal = false
    private var remainingRuns = 0
    private val accumulatedReadings = mutableListOf<BpReading>()

    private val completionDebounceSeconds = 1.5

    // UUIDs
    private val bpsService = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    private val measurement = UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")
    private val control = UUID.fromString("583CB5B3-875D-40ED-9098-C39EB0C1983D")

    private val startCommand = byteArrayOf(0xF1.toByte(), 0x01)
    private val cancelCommand = byteArrayOf(0xF1.toByte(), 0x02)

    fun setMeasurementMode(mode: MeasurementMode) {
        _state.update { it.copy(measurementMode = mode) }
    }

    fun setDelayBetweenRuns(seconds: Int) {
        _state.update { it.copy(delayBetweenRunsSeconds = seconds) }
    }

    fun startConnect(timeoutSeconds: Long = 30) {
        if (!hasBlePermission()) {
            _state.update { it.copy(status = "Bluetooth permission required") }
            return
        }

        val btAdapter = adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            _state.update { it.copy(status = "Bluetooth unavailable") }
            return
        }

        resetSessionForScan()
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(bpsService))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        btAdapter.bluetoothLeScanner.startScan(filters, settings, scanCallback)
        _state.update { it.copy(status = "Searching for device…") }

        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(TimeUnit.SECONDS.toMillis(timeoutSeconds))
            val current = _state.value
            if (!current.isConnected) {
                stopScan()
                _state.update { it.copy(status = "Not connected (timeout). Check power & Bluetooth.") }
            }
        }
    }

    fun startMeasurement() {
        if (!_state.value.canMeasure || _state.value.isMeasuring) return

        sessionActive = true
        hasFiredFinal = false
        accumulatedReadings.clear()
        finalizeJob?.cancel()
        countdownJob?.cancel()

        if (_state.value.measurementMode == MeasurementMode.AVERAGE3) {
            remainingRuns = 3
            _state.update { it.copy(status = "Measuring (run 1 of 3)…", isMeasuring = true) }
        } else {
            remainingRuns = 0
            _state.update { it.copy(status = "Measuring…", isMeasuring = true) }
        }

        performSingleRunStart()
    }

    fun cancelMeasurement() {
        writeControl(cancelCommand)
        remainingRuns = 0
        accumulatedReadings.clear()
        sessionActive = false
        hasFiredFinal = true
        finalizeJob?.cancel()
        countdownJob?.cancel()
        _state.update { it.copy(status = "Connected — ready", isMeasuring = false) }
    }

    fun cleanup() {
        stopScan()
        finalizeJob?.cancel()
        countdownJob?.cancel()
        connectTimeoutJob?.cancel()
        gatt?.close()
        gatt = null
    }

    private fun hasBlePermission(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun resetSessionForScan() {
        stopScan()
        hasFiredFinal = false
        sessionActive = false
        remainingRuns = 0
        accumulatedReadings.clear()
        finalizeJob?.cancel()
        countdownJob?.cancel()
        connectTimeoutJob?.cancel()
        _state.update {
            it.copy(
                isConnected = false,
                canMeasure = false,
                isMeasuring = false,
                lastReading = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun performSingleRunStart() {
        writeControl(startCommand)
    }

    @SuppressLint("MissingPermission")
    private fun writeControl(command: ByteArray) {
        val char = controlCharacteristic ?: return
        gatt?.writeCharacteristic(
            char,
            command,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            stopScan()
            connectTimeoutJob?.cancel()
            _state.update { it.copy(status = "Connecting…") }
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                this@BpClient.gatt = gatt
                _state.update { it.copy(isConnected = true, status = "Connected — discovering…") }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                measurementCharacteristic = null
                controlCharacteristic = null
                _state.update {
                    it.copy(
                        isConnected = false,
                        canMeasure = false,
                        isMeasuring = false,
                        status = "Disconnected"
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(bpsService)
            if (service != null) {
                setupCharacteristics(gatt, service)
            } else {
                _state.update { it.copy(status = "Blood Pressure service not found") }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == measurement) {
                val data = characteristic.value ?: return
                parseMeasurement(data)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(status = "Notify error: $status") }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupCharacteristics(gatt: BluetoothGatt, service: BluetoothGattService) {
        measurementCharacteristic = service.getCharacteristic(measurement)
        controlCharacteristic = service.getCharacteristic(control)

        val notifyChar = measurementCharacteristic
        if (notifyChar != null) {
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(UUID.fromString(CLIENT_CONFIG_UUID))
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }

        val ready = measurementCharacteristic != null && controlCharacteristic != null
        _state.update { it.copy(canMeasure = ready, status = if (ready) "Connected — ready" else "Discovering…") }
    }

    private fun parseMeasurement(data: ByteArray) {
        if (data.size < 7) return

        fun sfloat(lo: Byte, hi: Byte): Double {
            val raw = (hi.toInt() and 0xFF shl 8) or (lo.toInt() and 0xFF)
            val mantissa = raw and 0x0FFF
            val exponent = raw shr 12
            val m = if (mantissa >= 0x0800) mantissa - 0x1000 else mantissa
            return m * 10.0.pow(exponent.toDouble())
        }

        val flags = data[0].toInt()
        val sys = sfloat(data[1], data[2])
        val dia = sfloat(data[3], data[4])
        val map = sfloat(data[5], data[6])

        var idx = 7
        if (flags and 0x02 != 0) idx += 7 // timestamp present

        var hr: Double? = null
        if (flags and 0x04 != 0 && data.size >= idx + 2) {
            hr = sfloat(data[idx], data[idx + 1])
        }

        val reading = BpReading(sys = sys, dia = dia, map = map, hr = hr)
        _state.update { it.copy(lastReading = reading) }
        scheduleFinalize()
    }

    private fun scheduleFinalize() {
        finalizeJob?.cancel()
        finalizeJob = scope.launch {
            delay((completionDebounceSeconds * 1000).toLong())
            finalizeIfNeeded()
        }
    }

    private fun finalizeIfNeeded() {
        val reading = _state.value.lastReading ?: return
        if (!sessionActive || hasFiredFinal || reading.dia <= 0) return

        if (_state.value.measurementMode == MeasurementMode.AVERAGE3) {
            if (isPlausible(reading)) accumulatedReadings.add(reading)

            if (remainingRuns > 1) {
                remainingRuns -= 1
                launchCountdownAndNextRun()
                return
            }

            val avg = average(accumulatedReadings)
            sessionActive = false
            hasFiredFinal = true
            _state.update { it.copy(status = "Connected — ready", isMeasuring = false) }
            onFinalReading?.invoke(avg)
            accumulatedReadings.clear()
            remainingRuns = 0
            return
        }

        sessionActive = false
        hasFiredFinal = true
        _state.update { it.copy(status = "Connected — ready", isMeasuring = false) }
        onFinalReading?.invoke(reading)
    }

    private fun launchCountdownAndNextRun() {
        countdownJob?.cancel()
        val delaySeconds = _state.value.delayBetweenRunsSeconds
        var countdown = delaySeconds
        _state.update {
            it.copy(
                status = "Measured run ${3 - remainingRuns} of 3 — next in ${countdown}s…",
                isMeasuring = true
            )
        }

        countdownJob = scope.launch {
            while (countdown > 0) {
                delay(1000)
                countdown -= 1
                _state.update {
                    it.copy(
                        status = "Measured run ${3 - remainingRuns} of 3 — next in ${countdown}s…",
                        isMeasuring = true
                    )
                }
            }
            _state.update { it.copy(status = "Measuring (run ${4 - remainingRuns} of 3)…", isMeasuring = true) }
            performSingleRunStart()
        }
    }

    private fun average(readings: List<BpReading>): BpReading {
        val valid = readings.filter { isPlausible(it) }
        if (valid.isEmpty()) {
            val last = _state.value.lastReading
            if (last != null && isPlausible(last)) return last
            return BpReading(0.0, 0.0, null, null)
        }

        val n = valid.size.toDouble()
        val sysAvg = valid.sumOf { it.sys } / n
        val diaAvg = valid.sumOf { it.dia } / n

        val mapVals = valid.mapNotNull { it.map }.filter { it.isFinite() }
        val mapAvg = mapVals.takeIf { it.isNotEmpty() }?.average()

        val hrVals = valid.mapNotNull { it.hr }.filter { it.isFinite() && it in 20.0..220.0 }
        val hrAvg = hrVals.takeIf { it.isNotEmpty() }?.average()

        return BpReading(sys = sysAvg, dia = diaAvg, map = mapAvg, hr = hrAvg)
    }

    private fun isPlausible(reading: BpReading): Boolean {
        if (!reading.sys.isFinite() || !reading.dia.isFinite()) return false
        return reading.sys in 60.0..260.0 && reading.dia in 40.0..160.0
    }

    companion object {
        private const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }
}
