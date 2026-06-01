package com.ptylr.librearm.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ptylr.librearm.R

/**
 * Compose port of the iOS HypertensionGraphView. Draws the five color-coded zones
 * (Low, Normal, Prehypertension, Stage 1, Stage 2) with axis labels and plots the
 * supplied reading. The view reserves ~60dp on the right and bottom for axis labels.
 */
@Composable
fun HypertensionGraphView(
    systolic: Double,
    diastolic: Double,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val zoneStage2 = stringResource(R.string.graph_zone_stage2)
    val zoneStage1 = stringResource(R.string.graph_zone_stage1)
    val zonePrehypertension = stringResource(R.string.graph_zone_prehypertension)
    val zoneNormal = stringResource(R.string.graph_zone_normal)
    val zoneLow = stringResource(R.string.graph_zone_low)
    val xAxisTitle = stringResource(R.string.graph_diastolic)
    val yAxisTitle = stringResource(R.string.graph_systolic)
    val zoneTextPx = with(density) { 13.sp.toPx() }
    val axisTextPx = with(density) { 12.sp.toPx() }
    val labelLeftPadPx = with(density) { 12.dp.toPx() }
    val gutterRightPx = with(density) { 60.dp.toPx() }
    val gutterBottomPx = with(density) { 60.dp.toPx() }
    val xLabelGapPx = with(density) { 16.dp.toPx() }
    val xTitleGapPx = with(density) { 40.dp.toPx() }
    val yLabelGapPx = with(density) { 8.dp.toPx() }
    val yTitleGapPx = with(density) { 48.dp.toPx() }
    val outerRPx = with(density) { 11.dp.toPx() }
    val innerRPx = with(density) { 9.dp.toPx() }
    val shadowOffsetPx = with(density) { 2.dp.toPx() }
    val shadowGrowPx = with(density) { 1.dp.toPx() }
    val zoneLabelPaint = remember(zoneTextPx) {
        Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            textSize = zoneTextPx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val axisPaint = remember(axisTextPx) {
        Paint().apply {
            color = android.graphics.Color.DKGRAY
            isAntiAlias = true
            textSize = axisTextPx
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width - gutterRightPx
        val h = size.height - gutterBottomPx
        if (w <= 0f || h <= 0f) return@Canvas

        val diaMin = 40.0
        val diaMax = 120.0
        val sysMin = 40.0
        val sysMax = 180.0
        fun mapX(d: Double): Float = (((d - diaMin) / (diaMax - diaMin)).toFloat()) * w
        fun mapY(s: Double): Float = h - (((s - sysMin) / (sysMax - sysMin)).toFloat()) * h

        // Stage 2 (red): sys >= 160 OR dia >= 100
        drawPath(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, h)
                lineTo(mapX(100.0), h)
                lineTo(mapX(100.0), mapY(160.0))
                lineTo(0f, mapY(160.0))
                close()
            },
            color = Color(0xFFFA5959)
        )
        drawPath(
            Path().apply {
                moveTo(0f, mapY(160.0))
                lineTo(mapX(100.0), mapY(160.0))
                lineTo(mapX(100.0), h)
            },
            color = Color.Black,
            style = Stroke(width = 2f)
        )

        // Stage 1 (pink)
        drawPath(
            Path().apply {
                moveTo(0f, mapY(160.0))
                lineTo(mapX(100.0), mapY(160.0))
                lineTo(mapX(100.0), h)
                lineTo(mapX(90.0), h)
                lineTo(mapX(90.0), mapY(140.0))
                lineTo(0f, mapY(140.0))
                close()
            },
            color = Color(0xFFFA80A6)
        )
        drawPath(
            Path().apply {
                moveTo(0f, mapY(140.0))
                lineTo(mapX(90.0), mapY(140.0))
                lineTo(mapX(90.0), h)
            },
            color = Color.Black,
            style = Stroke(width = 2f)
        )

        // Prehypertension (orange)
        drawPath(
            Path().apply {
                moveTo(0f, mapY(140.0))
                lineTo(mapX(90.0), mapY(140.0))
                lineTo(mapX(90.0), h)
                lineTo(mapX(80.0), h)
                lineTo(mapX(80.0), mapY(120.0))
                lineTo(0f, mapY(120.0))
                close()
            },
            color = Color(0xFFF2A659)
        )
        drawPath(
            Path().apply {
                moveTo(0f, mapY(120.0))
                lineTo(mapX(80.0), mapY(120.0))
                lineTo(mapX(80.0), h)
            },
            color = Color.Black,
            style = Stroke(width = 2f)
        )

        // Normal (green) — fills the remaining lower-left block
        drawPath(
            Path().apply {
                moveTo(0f, mapY(120.0))
                lineTo(mapX(80.0), mapY(120.0))
                lineTo(mapX(80.0), h)
                lineTo(0f, h)
                close()
            },
            color = Color(0xFF73D973)
        )

        // Border between Low and Normal zones
        drawPath(
            Path().apply {
                moveTo(0f, mapY(90.0))
                lineTo(mapX(60.0), mapY(90.0))
                lineTo(mapX(60.0), h)
            },
            color = Color.Black,
            style = Stroke(width = 2f)
        )

        // Low (cyan) overlay
        drawPath(
            Path().apply {
                moveTo(0f, mapY(90.0))
                lineTo(mapX(60.0), mapY(90.0))
                lineTo(mapX(60.0), h)
                lineTo(0f, h)
                close()
            },
            color = Color(0xFF66D9D9)
        )

        val canvas = drawContext.canvas.nativeCanvas

        fun drawZoneLabel(text: String, atSys: Double) {
            val baseline = mapY(atSys) + zoneLabelPaint.textSize / 3f
            canvas.drawText(text, labelLeftPadPx, baseline, zoneLabelPaint)
        }

        drawZoneLabel(zoneStage2, 170.0)
        drawZoneLabel(zoneStage1, 150.0)
        drawZoneLabel(zonePrehypertension, 130.0)
        drawZoneLabel(zoneNormal, 105.0)
        drawZoneLabel(zoneLow, 65.0)

        // Diastolic axis labels (bottom)
        listOf(40, 60, 80, 90, 100, 120).forEach { dia ->
            val label = dia.toString()
            val textWidth = axisPaint.measureText(label)
            canvas.drawText(
                label,
                mapX(dia.toDouble()) - textWidth / 2f,
                h + xLabelGapPx,
                axisPaint
            )
        }
        canvas.drawText(
            xAxisTitle,
            (w - axisPaint.measureText(xAxisTitle)) / 2f,
            h + xTitleGapPx,
            axisPaint
        )

        // Systolic axis labels (right)
        listOf(40, 60, 80, 90, 100, 120, 140, 160, 180).forEach { sys ->
            val label = sys.toString()
            canvas.drawText(
                label,
                w + yLabelGapPx,
                mapY(sys.toDouble()) + axisPaint.textSize / 3f,
                axisPaint
            )
        }

        // Rotated systolic axis title
        canvas.save()
        val yTitleWidth = axisPaint.measureText(yAxisTitle)
        canvas.rotate(-90f, w + yTitleGapPx, h / 2f)
        canvas.drawText(
            yAxisTitle,
            w + yTitleGapPx - yTitleWidth / 2f,
            h / 2f + axisPaint.textSize / 3f,
            axisPaint
        )
        canvas.restore()

        // Plot point
        val clampedDia = diastolic.coerceIn(diaMin, diaMax)
        val clampedSys = systolic.coerceIn(sysMin, sysMax)
        val plotX = mapX(clampedDia)
        val plotY = mapY(clampedSys)

        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = outerRPx + shadowGrowPx,
            center = Offset(plotX, plotY + shadowOffsetPx)
        )
        drawCircle(color = Color.White, radius = outerRPx, center = Offset(plotX, plotY))
        drawCircle(color = Color.Black, radius = innerRPx, center = Offset(plotX, plotY))
    }
}
