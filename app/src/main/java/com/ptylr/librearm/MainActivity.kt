package com.ptylr.librearm

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.ptylr.librearm.health.HealthConnectManager
import com.ptylr.librearm.model.ThemeMode
import com.ptylr.librearm.prefs.Preferences
import com.ptylr.librearm.ui.LibreArmApp
import com.ptylr.librearm.ui.theme.LibreArmTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BpViewModel by viewModels()
    private lateinit var healthManager: HealthConnectManager
    private lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthManager = HealthConnectManager(this)
        preferences = Preferences(this)

        setContent {
            var themeMode by remember { mutableStateOf(preferences.themeMode) }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.Auto -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Re-apply edge-to-edge whenever the app's resolved theme changes so the
            // status / nav bar icons match — auto() alone watches the system, not our theme.
            DisposableEffect(darkTheme) {
                val statusBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                val navBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = statusBarStyle, navigationBarStyle = navBarStyle)
                onDispose {}
            }
            LibreArmTheme(themeMode = themeMode) {
                LibreArmApp(
                    viewModel = viewModel,
                    healthManager = healthManager,
                    preferences = preferences,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        preferences.themeMode = mode
                    },
                    onOpenUrl = ::openUrl,
                    onLaunchInstallIntent = {
                        runCatching { startActivity(healthManager.installIntent()) }
                    }
                )
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }
}
