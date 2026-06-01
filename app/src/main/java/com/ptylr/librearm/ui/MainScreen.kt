package com.ptylr.librearm.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ptylr.librearm.R
import com.ptylr.librearm.model.BatteryStatus
import com.ptylr.librearm.model.BpState
import com.ptylr.librearm.model.HistoricalReading
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    state: BpState,
    history: List<HistoricalReading>,
    guestMode: Boolean,
    showGuestControls: Boolean,
    onGuestModeChange: (Boolean) -> Unit,
    onStartStop: () -> Unit,
    onRetryConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val batteryCritical = state.battery is BatteryStatus.Critical
    val batteryColor = when (state.battery) {
        is BatteryStatus.Critical, is BatteryStatus.Low -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showGuestControls && guestMode) {
            GuestModeBanner()
        }
        StatusRow(state, batteryColor)
        ReadingCard(state)

        if (showGuestControls) {
            GuestModeToggle(
                checked = guestMode,
                enabled = !state.isMeasuring,
                onCheckedChange = onGuestModeChange
            )
        }

        Button(
            onClick = onStartStop,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isMeasuring || (state.canMeasure && !batteryCritical),
            colors = if (state.isMeasuring) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Text(stringResource(if (state.isMeasuring) R.string.action_stop_measurement else R.string.action_start_measurement))
        }

        if (history.isNotEmpty()) {
            RecentReadingsCard(history)
        }

        if (!state.isConnected) {
            Button(onClick = onRetryConnect, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Text(
                    stringResource(R.string.action_retry_connect),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(state: BpState, batteryColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            state.status.text(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            state.battery.text(),
            style = MaterialTheme.typography.bodySmall,
            color = batteryColor
        )
    }
}

@Composable
private fun GuestModeBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Person, contentDescription = null)
            Text(
                stringResource(R.string.guest_mode_banner),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun GuestModeToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(stringResource(R.string.guest_mode_label))
            Text(
                stringResource(R.string.guest_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun ReadingCard(state: BpState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val reading = state.lastReading
            if (reading != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reading_format, reading.sys.toInt(), reading.dia.toInt()),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    reading.map?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null)
                            Text(
                                stringResource(R.string.reading_map_format, it.toInt()),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    reading.hr?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.Red
                            )
                            Text(
                                stringResource(R.string.reading_bpm_format, it.toInt()),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
                HypertensionGraphView(
                    systolic = reading.sys,
                    diastolic = reading.dia,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            } else {
                Text(
                    stringResource(R.string.reading_none_yet),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                HypertensionGraphView(
                    systolic = 120.0,
                    diastolic = 80.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .alpha(0.3f)
                )
            }
        }
    }
}


@Composable
private fun RecentReadingsCard(history: List<HistoricalReading>) {
    val pattern = stringResource(R.string.history_entry_format)
    val formatter = remember(pattern) {
        DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault())
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.history_recent_readings), style = MaterialTheme.typography.titleSmall)
            history.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatter.format(entry.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = entry.hr?.let {
                            stringResource(
                                R.string.reading_with_hr_format,
                                entry.sys.toInt(),
                                entry.dia.toInt(),
                                it.toInt()
                            )
                        } ?: stringResource(R.string.reading_format, entry.sys.toInt(), entry.dia.toInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(key1 = enabled) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
