package com.ptylr.librearm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ptylr.librearm.R
import com.ptylr.librearm.model.BatteryStatus
import com.ptylr.librearm.model.BpStatus

@Composable
fun BpStatus.text(): String = when (this) {
    BpStatus.Searching -> stringResource(R.string.status_searching)
    BpStatus.Connecting -> stringResource(R.string.status_connecting)
    BpStatus.Discovering -> stringResource(R.string.status_discovering)
    BpStatus.Ready -> stringResource(R.string.status_ready)
    BpStatus.Disconnected -> stringResource(R.string.status_disconnected)
    BpStatus.BluetoothUnavailable -> stringResource(R.string.status_bluetooth_unavailable)
    BpStatus.BluetoothPermissionRequired -> stringResource(R.string.status_bluetooth_permission_required)
    BpStatus.NotConnectedTimeout -> stringResource(R.string.status_not_connected_timeout)
    BpStatus.BloodPressureServiceNotFound -> stringResource(R.string.status_bp_service_not_found)
    BpStatus.Measuring -> stringResource(R.string.status_measuring)
    is BpStatus.MeasuringRun -> stringResource(R.string.status_measuring_run, current, total)
    is BpStatus.Countdown -> stringResource(R.string.status_countdown, justCompletedRun, total, secondsRemaining)
    is BpStatus.BatteryCriticalBlocked -> stringResource(R.string.status_battery_critical_blocked, level)
    BpStatus.AverageReadingInvalid -> stringResource(R.string.status_average_reading_invalid)
    is BpStatus.RetryPrompt -> if (totalRuns > 1) {
        stringResource(R.string.status_retry_prompt_multi, failedRun, totalRuns)
    } else {
        stringResource(R.string.status_retry_prompt_single)
    }
    BpStatus.RetryLimitExceeded -> stringResource(R.string.status_retry_limit_exceeded)
    is BpStatus.NotifyError -> stringResource(R.string.status_notify_error, gattStatus)
}

@Composable
fun BatteryStatus.text(): String = when (this) {
    BatteryStatus.Unavailable -> stringResource(R.string.battery_unavailable)
    is BatteryStatus.Normal -> stringResource(R.string.battery_normal, level)
    is BatteryStatus.Low -> stringResource(R.string.battery_low, level)
    is BatteryStatus.Critical -> stringResource(R.string.battery_critical, level)
}
