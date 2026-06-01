package com.ptylr.librearm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ptylr.librearm.R
import com.ptylr.librearm.health.HealthConnectManager
import com.ptylr.librearm.model.ThemeMode

private val READINGS_OPTIONS = listOf(1, 2, 3)
private val DELAY_OPTIONS = listOf(15, 30, 45, 60)
private val THEME_OPTIONS = listOf(ThemeMode.Auto, ThemeMode.Light, ThemeMode.Dark)

@Composable
fun SettingsScreen(
    readingsCount: Int,
    delaySeconds: Int,
    isMeasuring: Boolean,
    autoSaveToHealth: Boolean,
    healthAuthorized: Boolean,
    healthAvailable: HealthConnectManager.Availability,
    healthRequestInFlight: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReadingsCountChange: (Int) -> Unit,
    onDelayChange: (Int) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Section(title = stringResource(R.string.settings_section_measurement)) {
            ReadingsCountSelector(
                readingsCount = readingsCount,
                enabled = !isMeasuring,
                onReadingsCountChange = onReadingsCountChange
            )

            if (readingsCount > 1) {
                DelaySelector(
                    delaySeconds = delaySeconds,
                    enabled = !isMeasuring,
                    onDelayChange = onDelayChange
                )
            }
        }

        Section(title = stringResource(R.string.settings_section_appearance)) {
            ThemeSelector(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }

        Section(title = stringResource(R.string.settings_section_storage)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(R.string.setting_save_to_health))
                    if (!healthAuthorized) {
                        Text(
                            when (healthAvailable) {
                                HealthConnectManager.Availability.Available -> stringResource(R.string.hc_toggle_to_request)
                                HealthConnectManager.Availability.NotInstalled -> stringResource(R.string.hc_install)
                                HealthConnectManager.Availability.NeedsUpdate -> stringResource(R.string.hc_update)
                                else -> stringResource(R.string.hc_unavailable)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(
                    checked = autoSaveToHealth,
                    onCheckedChange = onAutoSaveChange,
                    enabled = !isMeasuring && !healthRequestInFlight
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingsCountSelector(
    readingsCount: Int,
    enabled: Boolean,
    onReadingsCountChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.setting_readings_per_measurement))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            READINGS_OPTIONS.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = readingsCount == value,
                    onClick = { onReadingsCountChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = READINGS_OPTIONS.size
                    ),
                    enabled = enabled,
                    icon = {}
                ) {
                    Text(stringResource(R.string.count_format, value))
                }
            }
        }
        Text(
            text = stringResource(R.string.setting_readings_per_measurement_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelaySelector(
    delaySeconds: Int,
    enabled: Boolean,
    onDelayChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.setting_delay_between_readings))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DELAY_OPTIONS.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = delaySeconds == value,
                    onClick = { onDelayChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DELAY_OPTIONS.size
                    ),
                    enabled = enabled,
                    icon = {}
                ) {
                    Text(stringResource(R.string.delay_format, value))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.setting_theme))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            THEME_OPTIONS.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = THEME_OPTIONS.size
                    ),
                    icon = {}
                ) {
                    Text(
                        stringResource(
                            when (mode) {
                                ThemeMode.Auto -> R.string.theme_auto
                                ThemeMode.Light -> R.string.theme_light
                                ThemeMode.Dark -> R.string.theme_dark
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}
