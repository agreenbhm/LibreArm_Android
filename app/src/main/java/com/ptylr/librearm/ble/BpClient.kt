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
import com.ptylr.librearm.model.BatteryStatus
import com.ptylr.librearm.model.BpReading
import com.ptylr.librearm.model.BpState
import com.ptylr.librearm.model.BpStatus
import com.ptylr.librearm.model.levelOrNull
import com.ptylr.librearm.notifications.BatteryNotifier
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * BLE client for QardioArm blood pressure cuff. Connection management,
 * session debouncing, configurable readings count (1, 2, or 3) with
 * adjustable inter-run delay, retry-or-cancel prompt for invalid readings,
 * battery monitoring with low/critical thresholds, strict reading
 * validation, and final reading callback.
 */
class BpClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val batteryNotifier: BatteryNotifier? = null
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val _state = MutableStateFlow(BpState())
    val state: StateFlow<BpState> = _state

    var onFinalReading: ((BpReading) -> Unit)? = null

    private val gattQueue = GattOperationQueue()

    private var gatt: BluetoothGatt? = null
    private var measurementCharacteristic: BluetoothGattCharacteristic? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var batteryCharacteristic: BluetoothGattCharacteristic? = null

    private var connectTimeoutJob: Job? = null
    private var finalizeJob: Job? = null
    private var countdownJob: Job? = null

    private var sessionActive = false
    private var hasFiredFinal = false
    /** 1-based index of the current run within the session (e.g. 2 of 3). */
    private var currentRunIndex = 0
    /** Number of consecutive failures on [currentRunIndex]. */
    private var retriesForCurrentRun = 0
    private val accumulatedReadings = mutableListOf<BpReading>()

    private var lastBatteryStage: BatteryStage = BatteryStage.UNKNOWN

    private val completionDebounceSeconds = 1.5

    // UUIDs
    private val bpsService = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    private val measurement = UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")
    private val control = UUID.fromString("583CB5B3-875D-40ED-9098-C39EB0C1983D")
    private val batteryService = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val batteryLevel = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private val startCommand = byteArrayOf(0xF1.toByte(), 0x01)
    private val cancelCommand = byteArrayOf(0xF1.toByte(), 0x02)

    fun setReadingsCount(count: Int) {
        _state.update { it.copy(readingsCount = count.coerceIn(1, 3)) }
    }

    fun setDelayBetweenRuns(seconds: Int) {
        _state.update { it.copy(delayBetweenRunsSeconds = seconds) }
    }

    @SuppressLint("MissingPermission")
    fun startConnect(timeoutSeconds: Long = 30) {
        if (!hasBlePermission()) {
            _state.update { it.copy(status = BpStatus.BluetoothPermissionRequired) }
            return
        }

        val btAdapter = adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            _state.update { it.copy(status = BpStatus.BluetoothUnavailable) }
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
        _state.update { it.copy(status = BpStatus.Searching) }

        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(TimeUnit.SECONDS.toMillis(timeoutSeconds))
            if (!_state.value.isConnected) {
                stopScan()
                _state.update { it.copy(status = BpStatus.NotConnectedTimeout) }
            }
        }
    }

    fun startMeasurement() {
        if (!_state.value.canMeasure || _state.value.isMeasuring) return

        val batteryPct = _state.value.battery.levelOrNull
        if (batteryPct != null && batteryPct <= CRITICAL_BATTERY) {
            _state.update { it.copy(status = BpStatus.BatteryCriticalBlocked(batteryPct)) }
            return
        }

        sessionActive = true
        hasFiredFinal = false
        accumulatedReadings.clear()
        finalizeJob?.cancel()
        countdownJob?.cancel()
        currentRunIndex = 1
        retriesForCurrentRun = 0

        val total = _state.value.readingsCount
        _state.update {
            it.copy(
                status = if (total > 1) {
                    BpStatus.MeasuringRun(current = 1, total = total)
                } else {
                    BpStatus.Measuring
                },
                isMeasuring = true
            )
        }

        scope.launch {
            // Each op suspends on the queue, so battery read + start write are serialized
            // and the start-command write is never silently dropped.
            readBatteryLevelQueued()
            performSingleRunStart()
        }
    }

    /**
     * Called when the user taps **Retry** on the failed-reading prompt. Waits
     * [RETRY_DELAY_SECONDS] (gives the arm time to recover) and then re-runs
     * the same run index. Successful retries reset the per-run failure counter.
     */
    fun retryFailedReading() {
        if (!sessionActive) return
        if (_state.value.status !is BpStatus.RetryPrompt) return

        val total = _state.value.readingsCount
        finalizeJob?.cancel()
        countdownJob?.cancel()

        var countdown = RETRY_DELAY_SECONDS
        fun postCountdown() {
            _state.update {
                it.copy(
                    status = BpStatus.Countdown(
                        secondsRemaining = countdown,
                        justCompletedRun = (currentRunIndex - 1).coerceAtLeast(0),
                        total = total
                    ),
                    isMeasuring = true
                )
            }
        }

        postCountdown()
        countdownJob = scope.launch {
            while (countdown > 0) {
                delay(1000)
                countdown -= 1
                postCountdown()
            }
            _state.update {
                it.copy(
                    status = if (total > 1) {
                        BpStatus.MeasuringRun(current = currentRunIndex, total = total)
                    } else {
                        BpStatus.Measuring
                    },
                    isMeasuring = true
                )
            }
            performSingleRunStart()
        }
    }

    fun cancelMeasurement() {
        scope.launch {
            controlCharacteristic?.let { queueWriteCharacteristic(it, cancelCommand) }
        }
        resetSession()
        _state.update { it.copy(status = BpStatus.Ready, isMeasuring = false) }
    }

    @SuppressLint("MissingPermission")
    fun cleanup() {
        stopScan()
        finalizeJob?.cancel()
        countdownJob?.cancel()
        connectTimeoutJob?.cancel()
        gattQueue.reset()
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

    private fun resetSession() {
        sessionActive = false
        hasFiredFinal = true
        currentRunIndex = 0
        retriesForCurrentRun = 0
        accumulatedReadings.clear()
        finalizeJob?.cancel()
        countdownJob?.cancel()
    }

    private fun resetSessionForScan() {
        stopScan()
        resetSession()
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
    private suspend fun performSingleRunStart() {
        val char = controlCharacteristic ?: return
        queueWriteCharacteristic(char, startCommand)
    }

    @SuppressLint("MissingPermission")
    private suspend fun readBatteryLevelQueued() {
        val char = batteryCharacteristic ?: return
        queueReadCharacteristic(char)
    }

    @SuppressLint("MissingPermission")
    private suspend fun queueWriteCharacteristic(
        char: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        val g = gatt ?: return false
        return gattQueue.submit {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = value
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(char)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun queueReadCharacteristic(char: BluetoothGattCharacteristic): Boolean {
        val g = gatt ?: return false
        return gattQueue.submit { g.readCharacteristic(char) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun queueEnableNotifications(char: BluetoothGattCharacteristic): Boolean {
        val g = gatt ?: return false
        if (!g.setCharacteristicNotification(char, true)) return false
        val descriptor = char.getDescriptor(UUID.fromString(CLIENT_CONFIG_UUID)) ?: return false
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gattQueue.submit {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, value) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }
    }

    private fun updateBatteryStatus(level: Int?) {
        if (level == null) {
            _state.update { it.copy(battery = BatteryStatus.Unavailable) }
            return
        }

        val newStatus: BatteryStatus = when {
            level <= CRITICAL_BATTERY -> BatteryStatus.Critical(level)
            level <= LOW_BATTERY -> BatteryStatus.Low(level)
            else -> BatteryStatus.Normal(level)
        }
        _state.update { it.copy(battery = newStatus) }

        val newStage = when (newStatus) {
            is BatteryStatus.Critical -> BatteryStage.CRITICAL
            is BatteryStatus.Low -> BatteryStage.LOW
            else -> BatteryStage.NORMAL
        }

        if (newStage != lastBatteryStage) {
            when (newStage) {
                BatteryStage.CRITICAL -> if (lastBatteryStage != BatteryStage.CRITICAL) {
                    batteryNotifier?.notifyBattery(level, isCritical = true)
                }
                BatteryStage.LOW -> if (lastBatteryStage == BatteryStage.NORMAL || lastBatteryStage == BatteryStage.UNKNOWN) {
                    batteryNotifier?.notifyBattery(level, isCritical = false)
                }
                else -> Unit
            }
            lastBatteryStage = newStage
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            stopScan()
            connectTimeoutJob?.cancel()
            _state.update { it.copy(status = BpStatus.Connecting) }
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                this@BpClient.gatt = gatt
                _state.update { it.copy(isConnected = true, status = BpStatus.Discovering) }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gattQueue.reset()
                measurementCharacteristic = null
                controlCharacteristic = null
                batteryCharacteristic = null
                lastBatteryStage = BatteryStage.UNKNOWN
                // Close the platform GATT client so its internal resources are released.
                // Without this, every power-cycle / out-of-range leaks a GATT object.
                gatt.close()
                this@BpClient.gatt = null
                _state.update {
                    it.copy(
                        isConnected = false,
                        canMeasure = false,
                        isMeasuring = false,
                        status = BpStatus.Disconnected,
                        battery = BatteryStatus.Unavailable
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val bp = gatt.getService(bpsService)
            if (bp == null) {
                _state.update { it.copy(status = BpStatus.BloodPressureServiceNotFound) }
                return
            }
            val battery = gatt.getService(batteryService)
            scope.launch {
                setupBpCharacteristics(gatt, bp)
                if (battery != null) {
                    setupBatteryCharacteristic(gatt, battery)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // Unsolicited notification — do not touch the op queue.
            dispatchCharacteristicChanged(characteristic, value)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Required for API < 33; new overload is preferred.")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val value = characteristic.value ?: return
                dispatchCharacteristicChanged(characteristic, value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (success && characteristic.uuid == batteryLevel) {
                parseBatteryLevel(value)
            }
            gattQueue.completePending(success)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Required for API < 33; new overload is preferred.")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val success = status == BluetoothGatt.GATT_SUCCESS
                if (success && characteristic.uuid == batteryLevel) {
                    characteristic.value?.let { parseBatteryLevel(it) }
                }
                gattQueue.completePending(success)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            gattQueue.completePending(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (!success) {
                _state.update { it.copy(status = BpStatus.NotifyError(status)) }
            }
            gattQueue.completePending(success)
        }
    }

    private fun dispatchCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        when (characteristic.uuid) {
            measurement -> parseMeasurement(value)
            batteryLevel -> parseBatteryLevel(value)
        }
    }

    private suspend fun setupBpCharacteristics(gatt: BluetoothGatt, service: BluetoothGattService) {
        measurementCharacteristic = service.getCharacteristic(measurement)
        controlCharacteristic = service.getCharacteristic(control)

        measurementCharacteristic?.let { queueEnableNotifications(it) }

        val ready = measurementCharacteristic != null && controlCharacteristic != null
        _state.update {
            it.copy(
                canMeasure = ready,
                status = if (ready) BpStatus.Ready else BpStatus.Discovering
            )
        }
    }

    private suspend fun setupBatteryCharacteristic(gatt: BluetoothGatt, service: BluetoothGattService) {
        val char = service.getCharacteristic(batteryLevel) ?: return
        batteryCharacteristic = char
        queueReadCharacteristic(char)
        if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            queueEnableNotifications(char)
        }
    }

    private fun parseBatteryLevel(data: ByteArray) {
        if (data.isEmpty()) return
        val level = data[0].toInt() and 0xFF
        if (level in 0..100) {
            updateBatteryStatus(level)
        }
    }

    private fun parseMeasurement(data: ByteArray) {
        val reading = BpReadingParser.parse(data) ?: return
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

        val total = _state.value.readingsCount

        if (!BpValidation.isValid(reading)) {
            retriesForCurrentRun += 1
            if (retriesForCurrentRun >= MAX_RETRIES) {
                failSession(BpStatus.RetryLimitExceeded)
            } else {
                // Halt session loop and wait for user to tap Retry or Cancel on the dialog.
                _state.update {
                    it.copy(
                        lastReading = null,
                        isMeasuring = false,
                        status = BpStatus.RetryPrompt(
                            failedRun = currentRunIndex,
                            totalRuns = total
                        )
                    )
                }
            }
            return
        }

        // Reading is valid — accept it, reset the per-run retry counter, advance.
        retriesForCurrentRun = 0
        accumulatedReadings.add(reading)

        if (currentRunIndex < total) {
            currentRunIndex += 1
            launchCountdownAndNextRun(justCompleted = currentRunIndex - 1, nextRun = currentRunIndex)
            return
        }

        // All runs collected — average if needed, save, done.
        val finalReading = if (accumulatedReadings.size > 1) {
            val avg = average(accumulatedReadings)
            if (!BpValidation.isValid(avg)) {
                failSession(BpStatus.AverageReadingInvalid)
                return
            }
            avg
        } else {
            reading
        }

        completeSession(finalReading)
    }

    private fun failSession(status: BpStatus) {
        resetSession()
        _state.update {
            it.copy(lastReading = null, isMeasuring = false, status = status)
        }
        scope.launch { readBatteryLevelQueued() }
    }

    private fun completeSession(reading: BpReading) {
        resetSession()
        _state.update {
            it.copy(lastReading = reading, status = BpStatus.Ready, isMeasuring = false)
        }
        onFinalReading?.invoke(reading)
        scope.launch { readBatteryLevelQueued() }
    }

    private fun launchCountdownAndNextRun(justCompleted: Int, nextRun: Int) {
        countdownJob?.cancel()
        var countdown = _state.value.delayBetweenRunsSeconds
        val total = _state.value.readingsCount

        fun postCountdown() {
            _state.update {
                it.copy(
                    status = BpStatus.Countdown(
                        secondsRemaining = countdown,
                        justCompletedRun = justCompleted,
                        total = total
                    ),
                    isMeasuring = true
                )
            }
        }

        postCountdown()
        countdownJob = scope.launch {
            while (countdown > 0) {
                delay(1000)
                countdown -= 1
                postCountdown()
            }
            _state.update {
                it.copy(
                    status = BpStatus.MeasuringRun(current = nextRun, total = total),
                    isMeasuring = true
                )
            }
            performSingleRunStart()
        }
    }

    private fun average(readings: List<BpReading>): BpReading {
        val valid = readings.filter { BpValidation.isValid(it) }
        if (valid.isEmpty()) return BpReading(0.0, 0.0, null, null)

        val n = valid.size.toDouble()
        val sysAvg = valid.sumOf { it.sys } / n
        val diaAvg = valid.sumOf { it.dia } / n

        val mapVals = valid.mapNotNull { it.map }.filter { it.isFinite() }
        val mapAvg = mapVals.takeIf { it.isNotEmpty() }?.average()

        val hrVals = valid.mapNotNull { it.hr }.filter { it.isFinite() && it in BpValidation.HR_RANGE }
        val hrAvg = hrVals.takeIf { it.isNotEmpty() }?.average()

        return BpReading(sys = sysAvg, dia = diaAvg, map = mapAvg, hr = hrAvg)
    }

    private enum class BatteryStage { UNKNOWN, NORMAL, LOW, CRITICAL }

    companion object {
        private const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"
        private const val LOW_BATTERY = 20
        private const val CRITICAL_BATTERY = 10
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_SECONDS = 10
    }
}
