package com.ptylr.librearm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = OnBluePrimary,
    secondary = SecondaryGray,
    background = BackgroundLight,
    surface = SurfaceLight,
    onSurface = Color(0xFF0F172A),
    error = ErrorRed
)

@Composable
fun LibreArmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
