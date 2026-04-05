package com.ptylr.librearm.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Blood pressure classification graph matching the iOS HypertensionGraphView.
 *
 * Displays five AHA-classified zones (Low, Normal, Prehypertension, Stage 1, Stage 2)
 * with a plot point showing the current reading. X-axis = Diastolic (40-120 mmHg),
 * Y-axis = Systolic (40-180 mmHg).
 */
@Composable
fun HypertensionGraph(
    systolic: Double?,
    diastolic: Double?,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
    ) {
        val padLeft = 40.dp.toPx()
        val padBottom = 40.dp.toPx()
        val padTop = 8.dp.toPx()
        val padRight = 8.dp.toPx()
        val plotW = size.width - padLeft - padRight
        val plotH = size.height - padTop - padBottom

        // Coordinate mapping
        val sysMin = 40.0; val sysMax = 180.0
        val diaMin = 40.0; val diaMax = 120.0

        fun mapX(dia: Double) = padLeft + ((dia - diaMin) / (diaMax - diaMin) * plotW).toFloat()
        fun mapY(sys: Double) = padTop + ((sysMax - sys) / (sysMax - sysMin) * plotH).toFloat()

        // Zone colors matching iOS HypertensionGraphView
        val stage2Color = Color(0xFFFB5959)   // Red
        val stage1Color = Color(0xFFFB80A6)   // Pink
        val preColor = Color(0xFFF2A659)      // Orange
        val normalColor = Color(0xFF73D973)   // Green
        val lowColor = Color(0xFF80D9D9)      // Cyan
        val borderColor = Color(0xFF333333)

        // Draw zones back-to-front (largest first)

        // Stage 2 (red) — full plot area background
        drawRect(
            color = stage2Color,
            topLeft = Offset(padLeft, padTop),
            size = Size(plotW, plotH)
        )

        // Stage 1 (pink) — L-shaped: below sys=160 AND left of dia=100
        val path1 = Path().apply {
            moveTo(padLeft, mapY(160.0))
            lineTo(mapX(100.0), mapY(160.0))
            lineTo(mapX(100.0), mapY(sysMin))
            lineTo(mapX(90.0), mapY(sysMin))
            lineTo(mapX(90.0), mapY(140.0))
            lineTo(padLeft, mapY(140.0))
            close()
        }
        drawPath(path1, stage1Color)

        // Prehypertension (orange) — L-shaped: below sys=140 AND left of dia=90
        val pathPre = Path().apply {
            moveTo(padLeft, mapY(140.0))
            lineTo(mapX(90.0), mapY(140.0))
            lineTo(mapX(90.0), mapY(sysMin))
            lineTo(mapX(80.0), mapY(sysMin))
            lineTo(mapX(80.0), mapY(120.0))
            lineTo(padLeft, mapY(120.0))
            close()
        }
        drawPath(pathPre, preColor)

        // Normal + Low combined (green) — below sys=120 AND left of dia=80
        drawRect(
            color = normalColor,
            topLeft = Offset(padLeft, mapY(120.0)),
            size = Size(mapX(80.0) - padLeft, mapY(sysMin) - mapY(120.0))
        )

        // Low (cyan) — below sys=90 AND left of dia=60
        drawRect(
            color = lowColor,
            topLeft = Offset(padLeft, mapY(90.0)),
            size = Size(mapX(60.0) - padLeft, mapY(sysMin) - mapY(90.0))
        )

        // Zone borders
        val borderStroke = Stroke(width = 2.dp.toPx())

        // Border at sys=160 / dia=100
        drawLine(borderColor, Offset(padLeft, mapY(160.0)), Offset(mapX(100.0), mapY(160.0)), borderStroke.width)
        drawLine(borderColor, Offset(mapX(100.0), mapY(160.0)), Offset(mapX(100.0), mapY(sysMin)), borderStroke.width)

        // Border at sys=140 / dia=90
        drawLine(borderColor, Offset(padLeft, mapY(140.0)), Offset(mapX(90.0), mapY(140.0)), borderStroke.width)
        drawLine(borderColor, Offset(mapX(90.0), mapY(140.0)), Offset(mapX(90.0), mapY(sysMin)), borderStroke.width)

        // Border at sys=120 / dia=80
        drawLine(borderColor, Offset(padLeft, mapY(120.0)), Offset(mapX(80.0), mapY(120.0)), borderStroke.width)
        drawLine(borderColor, Offset(mapX(80.0), mapY(120.0)), Offset(mapX(80.0), mapY(sysMin)), borderStroke.width)

        // Border between Low and Normal (sys=90 / dia=60)
        drawLine(borderColor, Offset(padLeft, mapY(90.0)), Offset(mapX(60.0), mapY(90.0)), borderStroke.width)
        drawLine(borderColor, Offset(mapX(60.0), mapY(90.0)), Offset(mapX(60.0), mapY(sysMin)), borderStroke.width)

        // Outer border
        drawRect(borderColor, Offset(padLeft, padTop), Size(plotW, plotH), style = borderStroke)

        // Zone labels
        drawZoneLabels(padLeft, plotW, ::mapY)

        // Axis labels
        drawAxisLabels(padLeft, padTop, plotW, plotH, ::mapX, ::mapY, sysMin, sysMax, diaMin, diaMax)

        // Plot point
        if (systolic != null && diastolic != null) {
            val clampedSys = systolic.coerceIn(sysMin, sysMax)
            val clampedDia = diastolic.coerceIn(diaMin, diaMax)
            val cx = mapX(clampedDia)
            val cy = mapY(clampedSys)

            // Shadow
            drawCircle(Color.Black.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = Offset(cx + 1, cy + 2))
            // White border
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy))
            // Black fill
            drawCircle(Color.Black, radius = 6.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

private fun DrawScope.drawZoneLabels(
    padLeft: Float,
    plotW: Float,
    mapY: (Double) -> Float
) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#333333")
        textSize = 11.sp.toPx()
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val labelX = padLeft + 8.dp.toPx()

    drawContext.canvas.nativeCanvas.apply {
        drawText("Stage 2", labelX, mapY(170.0) + 4.dp.toPx(), paint)
        drawText("Stage 1", labelX, mapY(150.0) + 4.dp.toPx(), paint)
        drawText("Prehypertension", labelX, mapY(130.0) + 4.dp.toPx(), paint)
        drawText("Normal", labelX, mapY(105.0) + 4.dp.toPx(), paint)
        drawText("Low", labelX, mapY(65.0) + 4.dp.toPx(), paint)
    }
}

private fun DrawScope.drawAxisLabels(
    padLeft: Float,
    padTop: Float,
    plotW: Float,
    plotH: Float,
    mapX: (Double) -> Float,
    mapY: (Double) -> Float,
    sysMin: Double,
    sysMax: Double,
    diaMin: Double,
    diaMax: Double
) {
    val axisPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 10.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val sysAxisPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 10.sp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    drawContext.canvas.nativeCanvas.apply {
        // Diastolic axis (bottom)
        val diaBottom = padTop + plotH + 16.dp.toPx()
        for (dia in listOf(40, 60, 80, 90, 100, 120)) {
            drawText("$dia", mapX(dia.toDouble()), diaBottom, axisPaint)
        }
        // Diastolic label
        drawText("Diastolic (mmHg)", padLeft + plotW / 2, diaBottom + 16.dp.toPx(), axisPaint)

        // Systolic axis (left)
        for (sys in listOf(40, 60, 80, 100, 120, 140, 160, 180)) {
            drawText("$sys", padLeft - 6.dp.toPx(), mapY(sys.toDouble()) + 4.dp.toPx(), sysAxisPaint)
        }
    }
}
