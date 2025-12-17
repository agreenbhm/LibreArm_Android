package com.ptylr.librearm.health

import androidx.activity.result.contract.ActivityResultContract
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.units.Pressure
import com.ptylr.librearm.model.BpReading
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

class HealthConnectManager(private val context: Context) {
    private val client: HealthConnectClient = HealthConnectClient.getOrCreate(context)

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class)
    )

    suspend fun hasPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun saveReading(reading: BpReading, timestampMillis: Long): SaveResult {
        if (!hasPermissions()) return SaveResult.MissingPermissions
        if (!isWithinSupportedRange(reading)) {
            return SaveResult.InvalidData("Reading outside supported Health Connect range")
        }

        return runCatching {
            val instant = Instant.ofEpochMilli(timestampMillis)
            val zoneOffset: ZoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)

            val bpRecord = BloodPressureRecord(
                time = instant,
                zoneOffset = zoneOffset,
                systolic = Pressure.millimetersOfMercury(reading.sys),
                diastolic = Pressure.millimetersOfMercury(reading.dia)
            )

            val records = mutableListOf<androidx.health.connect.client.records.Record>(bpRecord)
            reading.hr?.let { bpm ->
                val hrRecord = HeartRateRecord(
                    startTime = instant,
                    startZoneOffset = zoneOffset,
                    endTime = instant,
                    endZoneOffset = zoneOffset,
                    samples = listOf(
                        HeartRateRecord.Sample(
                            time = instant,
                            beatsPerMinute = bpm.roundToInt().toLong()
                        )
                    )
                )
                records.add(hrRecord)
            }
            client.insertRecords(records)
            SaveResult.Saved
        }.getOrElse { SaveResult.InvalidData(it.message ?: "Unable to save") }
    }

    fun availability(): Availability {
        val status = HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)
        return when (status) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NeedsUpdate
            HealthConnectClient.SDK_UNAVAILABLE -> Availability.NotInstalled
            else -> Availability.Unknown
        }
    }

    fun installIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
            setPackage("com.android.vending")
        }
    }

    enum class Availability { Available, NotInstalled, NeedsUpdate, Unknown }

    sealed class SaveResult {
        object Saved : SaveResult()
        object MissingPermissions : SaveResult()
        data class InvalidData(val reason: String) : SaveResult()
    }

    private fun isWithinSupportedRange(reading: BpReading): Boolean {
        // Health Connect validation is stricter than device output; keep within documented limits.
        val sysOk = reading.sys.isFinite() && reading.sys in 40.0..200.0
        val diaOk = reading.dia.isFinite() && reading.dia in 20.0..130.0
        return sysOk && diaOk
    }

    companion object {
        fun createRequestPermissionActivityContract(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
    }
}
