package com.ptylr.librearm.health

import androidx.activity.result.contract.ActivityResultContract
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Pressure
import com.ptylr.librearm.model.BpReading
import com.ptylr.librearm.model.HistoricalReading
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

class HealthConnectManager(private val context: Context) {
    private val client: HealthConnectClient = HealthConnectClient.getOrCreate(context)

    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class)
    )

    private val readBpPermission: String =
        HealthPermission.getReadPermission(BloodPressureRecord::class)
    private val readHrPermission: String =
        HealthPermission.getReadPermission(HeartRateRecord::class)

    /** Combined bundle requested via the permission launcher. The user can grant or deny each individually. */
    val permissions: Set<String> =
        writePermissions + setOf(readBpPermission, readHrPermission)

    /**
     * Snapshot of the four permission flags Health Connect exposes for this
     * app. Callers that need more than one flag should prefer
     * [currentPermissionState] — each `has*` call below makes a separate
     * cross-process round-trip, so batching matters.
     *
     * - [canWrite]: write access to BP **and** HR records. Bundled because
     *   `saveReading` writes both together.
     * - [canReadBloodPressure]: gates the History feature entirely.
     * - [canReadHeartRate]: optional; when denied, History still works for
     *   BP and the screen surfaces a banner inviting the user to grant it.
     */
    data class PermissionState(
        val canWrite: Boolean,
        val canReadBloodPressure: Boolean,
        val canReadHeartRate: Boolean
    )

    /** One round-trip to Health Connect; preferred over the single-flag helpers when multiple flags are needed. */
    suspend fun currentPermissionState(): PermissionState {
        val granted = client.permissionController.getGrantedPermissions()
        return PermissionState(
            canWrite = granted.containsAll(writePermissions),
            canReadBloodPressure = readBpPermission in granted,
            canReadHeartRate = readHrPermission in granted
        )
    }

    suspend fun hasWritePermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(writePermissions)
    }

    suspend fun readRecent(daysBack: Long = 30, limit: Int = 10): List<HistoricalReading> {
        val end = Instant.now()
        val start = end.minus(daysBack, ChronoUnit.DAYS)
        return readRange(start, end, limit)
    }

    suspend fun readRange(start: Instant, end: Instant, pageSize: Int = 1000): List<HistoricalReading> {
        val perms = currentPermissionState()
        if (!perms.canReadBloodPressure) return emptyList()
        return runCatching {
            val bpRequest = ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = pageSize
            )
            val bpRecords = client.readRecords(bpRequest).records

            // Heart rate is recorded alongside BP with the same Instant in saveReading.
            // We query HR per BP record with a 1ms window, rather than a single
            // range query, because users with wearables can have thousands of
            // continuous HR samples per day — a single ranged query would blow
            // past pageSize on unrelated samples and miss the BP-paired ones.
            // HR read perm is optional; if denied, surface BP without HR.
            val hrByTime: Map<Instant, Double> = if (perms.canReadHeartRate) {
                bpRecords.mapNotNull { bp ->
                    runCatching {
                        val hrRequest = ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(
                                bp.time, bp.time.plusMillis(1)
                            ),
                            pageSize = 50
                        )
                        client.readRecords(hrRequest).records
                            .flatMap { it.samples }
                            .firstOrNull { it.time == bp.time }
                            ?.let { bp.time to it.beatsPerMinute.toDouble() }
                    }.getOrNull()
                }.toMap()
            } else {
                emptyMap()
            }

            bpRecords.map { record ->
                HistoricalReading(
                    time = record.time,
                    sys = record.systolic.inMillimetersOfMercury,
                    dia = record.diastolic.inMillimetersOfMercury,
                    hr = hrByTime[record.time]
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun saveReading(reading: BpReading, timestampMillis: Long): SaveResult {
        if (!hasWritePermissions()) return SaveResult.MissingPermissions
        // Upstream validation in BpValidation.isValid ensures finite, plausible values.
        return runCatching {
            val instant = Instant.ofEpochMilli(timestampMillis)
            val zoneOffset: ZoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant)
            val metadata = Metadata.autoRecorded(QARDIO_DEVICE)

            val bpRecord = BloodPressureRecord(
                time = instant,
                zoneOffset = zoneOffset,
                systolic = Pressure.millimetersOfMercury(reading.sys),
                diastolic = Pressure.millimetersOfMercury(reading.dia),
                metadata = metadata
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
                    ),
                    metadata = metadata
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
            data = "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE".toUri()
            setPackage("com.android.vending")
        }
    }

    /**
     * Intent that opens the Health Connect surface where the user can grant or
     * revoke permissions for LibreArm manually. Health Connect won't re-prompt
     * via the permission launcher after a user-initiated denial; this is the
     * recovery path.
     *
     * On Android 14+, Health Connect lives in system Settings and is reachable
     * via the `HEALTH_HOME_SETTINGS` action. On older Android it's a standalone
     * app with a launcher activity. We try the system action first, then fall
     * back to the standalone app's launcher. Returns null if neither resolves.
     *
     * Note: there's a more specific `MANAGE_HEALTH_PERMISSIONS` action that
     * would deep-link to LibreArm's app-specific permission page, but it's
     * restricted to system-signed Health Connect role holders — a third-party
     * app's `startActivity` call is silently rejected with a SecurityException.
     */
    fun openHealthConnectIntent(): Intent? {
        val pm = context.packageManager
        val systemIntent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        if (pm.queryIntentActivities(systemIntent, 0).isNotEmpty()) {
            return systemIntent
        }
        return pm.getLaunchIntentForPackage(HEALTH_CONNECT_PACKAGE)
    }

    enum class Availability { Available, NotInstalled, NeedsUpdate, Unknown }

    sealed interface SaveResult {
        data object Saved : SaveResult
        data object MissingPermissions : SaveResult
        data class InvalidData(val reason: String) : SaveResult
    }

    companion object {
        private val QARDIO_DEVICE = Device(
            manufacturer = "Qardio",
            model = "QardioArm",
            type = Device.TYPE_UNKNOWN
        )

        fun createRequestPermissionActivityContract(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
    }
}
