package com.ptylr.librearm.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ptylr.librearm.BpViewModel
import com.ptylr.librearm.R
import com.ptylr.librearm.ble.BlePermissions
import com.ptylr.librearm.health.HealthConnectManager
import com.ptylr.librearm.model.BpStatus
import com.ptylr.librearm.model.HistoricalReading
import com.ptylr.librearm.model.ThemeMode
import com.ptylr.librearm.prefs.Preferences
import java.time.Instant
import kotlinx.coroutines.launch

private enum class TopLevel(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    Home("home", R.string.nav_home, Icons.Default.MonitorHeart),
    History("history", R.string.nav_history, Icons.Default.History),
    Settings("settings", R.string.nav_settings, Icons.Default.Settings),
    About("about", R.string.nav_about, Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibreArmApp(
    viewModel: BpViewModel,
    healthManager: HealthConnectManager,
    preferences: Preferences,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenUrl: (String) -> Unit,
    onLaunchInstallIntent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val hcPermissionMissingMessage = stringResource(R.string.toast_hc_permission_missing)
    val readingInvalidMessage = stringResource(R.string.toast_reading_invalid)

    var autoSaveToHealth by rememberSaveable { mutableStateOf(preferences.autoSaveToHealth) }
    // Transient by design: a guest session shouldn't outlive the app's lifetime,
    // so it lives in saveable UI state rather than persisted preferences.
    var guestMode by rememberSaveable { mutableStateOf(false) }
    var healthWriteGranted by remember { mutableStateOf(false) }
    var healthBpReadGranted by remember { mutableStateOf(false) }
    var healthHrReadGranted by remember { mutableStateOf(false) }
    var healthBpReadDeniedAfterRequest by remember { mutableStateOf(false) }
    var healthAvailable by remember { mutableStateOf(HealthConnectManager.Availability.Unknown) }
    var healthRequestInFlight by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<HistoricalReading>>(emptyList()) }

    // Single round-trip refresh of all three HC permission flags. Local
    // because it captures the var delegates above.
    suspend fun refreshHealthPermissions(): HealthConnectManager.PermissionState {
        val perms = healthManager.currentPermissionState()
        healthWriteGranted = perms.canWrite
        healthBpReadGranted = perms.canReadBloodPressure
        healthHrReadGranted = perms.canReadHeartRate
        return perms
    }

    val blePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) viewModel.startConnect()
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = HealthConnectManager.createRequestPermissionActivityContract()
    ) { _ ->
        healthRequestInFlight = false
        scope.launch {
            val perms = refreshHealthPermissions()
            // Health Connect won't re-prompt after a user-initiated denial — track it
            // so the History screen can switch the CTA to "Open Health Connect".
            healthBpReadDeniedAfterRequest = !perms.canReadBloodPressure
            autoSaveToHealth = perms.canWrite
            preferences.autoSaveToHealth = perms.canWrite
            if (perms.canReadBloodPressure) history = healthManager.readRecent()
        }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* battery alerts are best-effort */ }

    LaunchedEffect(Unit) {
        if (!BlePermissions.areGranted(context)) {
            blePermissionsLauncher.launch(BlePermissions.required)
        } else {
            viewModel.startConnect()
        }
        val perms = refreshHealthPermissions()
        healthAvailable = healthManager.availability()
        viewModel.setReadingsCount(preferences.readingsCount)
        viewModel.setDelayBetweenRuns(preferences.delayBetweenRunsSeconds)
        if (!perms.canWrite && autoSaveToHealth) {
            autoSaveToHealth = false
            preferences.autoSaveToHealth = false
        }
        if (perms.canReadBloodPressure) {
            history = healthManager.readRecent()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Re-check Health Connect permissions whenever the activity resumes — covers
    // the case where the user goes to HC, grants/revokes a permission, then
    // returns. The permission launcher only fires for our own in-app prompt.
    LifecycleResumeEffect(Unit) {
        scope.launch {
            val perms = refreshHealthPermissions()
            if (perms.canReadBloodPressure) {
                healthBpReadDeniedAfterRequest = false
                history = healthManager.readRecent()
            }
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(autoSaveToHealth, healthWriteGranted, guestMode) {
        viewModel.setOnFinalReading { reading ->
            if (guestMode || !autoSaveToHealth || !healthWriteGranted) return@setOnFinalReading
            scope.launch {
                when (healthManager.saveReading(reading, Instant.now().toEpochMilli())) {
                    HealthConnectManager.SaveResult.Saved -> {
                        history = healthManager.readRecent()
                    }
                    HealthConnectManager.SaveResult.MissingPermissions -> {
                        Toast.makeText(context, hcPermissionMissingMessage, Toast.LENGTH_SHORT).show()
                    }
                    is HealthConnectManager.SaveResult.InvalidData -> {
                        Toast.makeText(context, readingInvalidMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    KeepScreenOn(enabled = state.isMeasuring)

    (state.status as? BpStatus.RetryPrompt)?.let { prompt ->
        AlertDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = { Text(stringResource(R.string.retry_dialog_title)) },
            text = {
                Text(
                    if (prompt.totalRuns > 1) {
                        stringResource(
                            R.string.retry_dialog_message_multi,
                            prompt.failedRun,
                            prompt.totalRuns
                        )
                    } else {
                        stringResource(R.string.retry_dialog_message_single)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.retryFailedReading() }) {
                    Text(stringResource(R.string.retry_dialog_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelMeasurement() }) {
                    Text(stringResource(R.string.retry_dialog_cancel))
                }
            }
        )
    }

    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val currentDestination = TopLevel.entries.firstOrNull { it.route == currentRoute } ?: TopLevel.Home

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(currentDestination.labelRes)) }
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevel.entries.forEach { dest ->
                    val selected = currentBackStack?.destination?.hierarchy
                        ?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = stringResource(dest.labelRes)
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevel.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevel.Home.route) {
                MainScreen(
                    state = state,
                    history = history,
                    guestMode = guestMode,
                    showGuestControls = autoSaveToHealth && healthWriteGranted,
                    onGuestModeChange = { guestMode = it },
                    onStartStop = {
                        if (state.isMeasuring) viewModel.cancelMeasurement() else viewModel.startMeasurement()
                    },
                    onRetryConnect = { viewModel.startConnect() }
                )
            }
            composable(TopLevel.History.route) {
                HistoryScreen(
                    healthManager = healthManager,
                    hasBloodPressureReadPermission = healthBpReadGranted,
                    hasHeartRateReadPermission = healthHrReadGranted,
                    healthAvailable = healthAvailable,
                    permissionPreviouslyDenied = healthBpReadDeniedAfterRequest,
                    onRequestReadPermission = {
                        healthPermissionLauncher.launch(healthManager.permissions)
                    },
                    onOpenHealthConnect = {
                        healthManager.openHealthConnectIntent()?.let {
                            runCatching { context.startActivity(it) }
                        }
                    },
                    onInstallHealthConnect = onLaunchInstallIntent
                )
            }
            composable(TopLevel.Settings.route) {
                SettingsScreen(
                    readingsCount = state.readingsCount,
                    delaySeconds = state.delayBetweenRunsSeconds,
                    isMeasuring = state.isMeasuring,
                    autoSaveToHealth = autoSaveToHealth,
                    healthAuthorized = healthWriteGranted,
                    healthAvailable = healthAvailable,
                    healthRequestInFlight = healthRequestInFlight,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onReadingsCountChange = { count ->
                        viewModel.setReadingsCount(count)
                        preferences.readingsCount = count
                    },
                    onDelayChange = { seconds ->
                        viewModel.setDelayBetweenRuns(seconds)
                        preferences.delayBetweenRunsSeconds = seconds
                    },
                    onAutoSaveChange = { enabled ->
                        if (!enabled) {
                            autoSaveToHealth = false
                            preferences.autoSaveToHealth = false
                            return@SettingsScreen
                        }
                        if (healthAvailable != HealthConnectManager.Availability.Available) {
                            autoSaveToHealth = false
                            preferences.autoSaveToHealth = false
                            onLaunchInstallIntent()
                            return@SettingsScreen
                        }
                        healthRequestInFlight = true
                        scope.launch {
                            val perms = refreshHealthPermissions()
                            if (perms.canWrite && perms.canReadBloodPressure) {
                                autoSaveToHealth = true
                                preferences.autoSaveToHealth = true
                                healthRequestInFlight = false
                            } else {
                                healthPermissionLauncher.launch(healthManager.permissions)
                            }
                        }
                    }
                )
            }
            composable(TopLevel.About.route) {
                AboutScreen(onOpenLink = onOpenUrl)
            }
        }
    }
}

