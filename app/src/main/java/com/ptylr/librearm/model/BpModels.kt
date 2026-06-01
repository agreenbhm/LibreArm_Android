package com.ptylr.librearm.model

import java.time.Instant

const val DEFAULT_READINGS_COUNT = 1
const val DEFAULT_DELAY_SECONDS = 30
const val MIN_READINGS_COUNT = 1
const val MAX_READINGS_COUNT = 3

enum class ThemeMode { Auto, Light, Dark }

data class BpReading(
    val sys: Double,
    val dia: Double,
    val map: Double? = null,
    val hr: Double? = null
)

data class HistoricalReading(
    val time: Instant,
    val sys: Double,
    val dia: Double,
    val hr: Double? = null
)

/**
 * Structured status of the BLE/measurement state. UI maps each case to a localized string
 * (see [com.ptylr.librearm.ui.text] in Status.kt) so the BLE layer stays free of resource lookups.
 */
sealed class BpStatus {
    data object Searching : BpStatus()
    data object Connecting : BpStatus()
    data object Discovering : BpStatus()
    data object Ready : BpStatus()
    data object Disconnected : BpStatus()
    data object BluetoothUnavailable : BpStatus()
    data object BluetoothPermissionRequired : BpStatus()
    data object NotConnectedTimeout : BpStatus()
    data object BloodPressureServiceNotFound : BpStatus()
    data object Measuring : BpStatus()
    data class MeasuringRun(val current: Int, val total: Int) : BpStatus()
    data class Countdown(val secondsRemaining: Int, val justCompletedRun: Int, val total: Int) : BpStatus()
    data class BatteryCriticalBlocked(val level: Int) : BpStatus()
    data object AverageReadingInvalid : BpStatus()
    /** A reading failed validation; the session is paused awaiting Retry or Cancel from the user. */
    data class RetryPrompt(val failedRun: Int, val totalRuns: Int) : BpStatus()
    /** Same run failed [MAX_RETRIES] times in a row; the session was auto-cancelled. */
    data object RetryLimitExceeded : BpStatus()
    data class NotifyError(val gattStatus: Int) : BpStatus()
}

sealed class BatteryStatus {
    data object Unavailable : BatteryStatus()
    data class Normal(val level: Int) : BatteryStatus()
    data class Low(val level: Int) : BatteryStatus()
    data class Critical(val level: Int) : BatteryStatus()
}

val BatteryStatus.levelOrNull: Int?
    get() = when (this) {
        BatteryStatus.Unavailable -> null
        is BatteryStatus.Normal -> level
        is BatteryStatus.Low -> level
        is BatteryStatus.Critical -> level
    }

data class BpState(
    val status: BpStatus = BpStatus.Searching,
    val lastReading: BpReading? = null,
    val isConnected: Boolean = false,
    val canMeasure: Boolean = false,
    val isMeasuring: Boolean = false,
    val readingsCount: Int = DEFAULT_READINGS_COUNT,
    val delayBetweenRunsSeconds: Int = DEFAULT_DELAY_SECONDS,
    val battery: BatteryStatus = BatteryStatus.Unavailable
)
