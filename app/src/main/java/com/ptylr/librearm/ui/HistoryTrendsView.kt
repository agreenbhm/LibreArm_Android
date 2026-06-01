package com.ptylr.librearm.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ptylr.librearm.R
import com.ptylr.librearm.model.HistoricalReading
import com.ptylr.librearm.ui.theme.LocalChartColors
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class DailyAverage(val sys: Double, val dia: Double, val hr: Double?)

@Composable
internal fun TrendsView(
    month: YearMonth,
    readings: List<HistoricalReading>,
    zone: ZoneId
) {
    val chartColors = LocalChartColors.current
    val dailyAverages: Map<Int, DailyAverage> = remember(readings, month, zone) {
        readings
            .filter { YearMonth.from(it.time.atZone(zone).toLocalDate()) == month }
            .groupBy { it.time.atZone(zone).dayOfMonth }
            .mapValues { (_, list) ->
                val hrValues = list.mapNotNull { it.hr }
                DailyAverage(
                    sys = list.map { it.sys }.average(),
                    dia = list.map { it.dia }.average(),
                    hr = if (hrValues.isNotEmpty()) hrValues.average() else null
                )
            }
            .toSortedMap()
    }

    if (dailyAverages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.history_no_chart),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TrendChart(
            month = month,
            dailyAverages = dailyAverages,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        )

        val hrValues = dailyAverages.values.mapNotNull { it.hr }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            LegendSwatch(color = chartColors.systolic, label = stringResource(R.string.history_legend_systolic))
            Spacer(modifier = Modifier.width(24.dp))
            LegendSwatch(color = chartColors.diastolic, label = stringResource(R.string.history_legend_diastolic))
            if (hrValues.isNotEmpty()) {
                Spacer(modifier = Modifier.width(24.dp))
                LegendSwatch(color = chartColors.heartRate, label = stringResource(R.string.history_legend_heart_rate))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val sysAvg = dailyAverages.values.map { it.sys }.average()
                val diaAvg = dailyAverages.values.map { it.dia }.average()
                val sysMax = dailyAverages.values.maxOf { it.sys }
                val sysMin = dailyAverages.values.minOf { it.sys }
                val diaMax = dailyAverages.values.maxOf { it.dia }
                val diaMin = dailyAverages.values.minOf { it.dia }

                Text(stringResource(R.string.history_summary_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.history_summary_sys, sysAvg.toInt(), sysMin.toInt(), sysMax.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    stringResource(R.string.history_summary_dia, diaAvg.toInt(), diaMin.toInt(), diaMax.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (hrValues.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.history_summary_hr,
                            hrValues.average().toInt(),
                            hrValues.min().toInt(),
                            hrValues.max().toInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    stringResource(R.string.history_summary_days, dailyAverages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 4.dp)
                .background(color)
        )
        Text(
            text = " $label",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun TrendChart(
    month: YearMonth,
    dailyAverages: Map<Int, DailyAverage>,
    modifier: Modifier = Modifier
) {
    val chartColors = LocalChartColors.current
    val density = LocalDensity.current
    val monthTitlePattern = stringResource(R.string.history_short_format)
    val monthTitleFormatter = remember(monthTitlePattern) { DateTimeFormatter.ofPattern(monthTitlePattern) }
    val leftGutter = with(density) { 36.dp.toPx() }
    val bottomGutter = with(density) { 28.dp.toPx() }
    val topPad = with(density) { 8.dp.toPx() }
    val rightPad = with(density) { 8.dp.toPx() }
    val axisTextSize = with(density) { 11.sp.toPx() }
    val dotRadius = with(density) { 3.dp.toPx() }
    val lineWidth = with(density) { 2.dp.toPx() }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisPaint = remember(axisTextSize, labelColor) {
        Paint().apply {
            color = labelColor.toArgb()
            isAntiAlias = true
            textSize = axisTextSize
        }
    }
    val xPaddingPx = with(density) { 4.dp.toPx() }
    val tickGapPx = with(density) { 14.dp.toPx() }
    val xTitleBottomPx = with(density) { 2.dp.toPx() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val plotW = w - leftGutter - rightPad
        val plotH = h - bottomGutter - topPad
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val daysInMonth = month.lengthOfMonth()
        val yMin = 40.0
        val yMax = 200.0
        val xSpan = (daysInMonth - 1).coerceAtLeast(1).toFloat()

        fun mapX(day: Int): Float = leftGutter + (day - 1) / xSpan * plotW
        fun mapY(v: Double): Float = topPad + (((yMax - v) / (yMax - yMin)).toFloat()) * plotH

        val canvas = drawContext.canvas.nativeCanvas

        listOf(60, 80, 100, 120, 140, 160, 180).forEach { v ->
            val y = mapY(v.toDouble())
            drawLine(
                color = gridColor,
                start = Offset(leftGutter, y),
                end = Offset(w - rightPad, y),
                strokeWidth = 1f
            )
            canvas.drawText(
                v.toString(),
                leftGutter - axisPaint.measureText(v.toString()) - xPaddingPx,
                y + axisPaint.textSize / 3f,
                axisPaint
            )
        }

        val tickEvery = when {
            daysInMonth <= 7 -> 1
            daysInMonth <= 15 -> 2
            else -> 5
        }
        var d = 1
        while (d <= daysInMonth) {
            val x = mapX(d)
            canvas.drawText(
                d.toString(),
                x - axisPaint.measureText(d.toString()) / 2f,
                h - bottomGutter + tickGapPx,
                axisPaint
            )
            d += tickEvery
        }
        val monthTitle = month.format(monthTitleFormatter)
        canvas.drawText(
            monthTitle,
            leftGutter + plotW / 2f - axisPaint.measureText(monthTitle) / 2f,
            h - xTitleBottomPx,
            axisPaint
        )

        fun drawSeries(seriesColor: Color, points: List<Pair<Int, Double>>) {
            if (points.isEmpty()) return
            val path = Path()
            points.forEachIndexed { idx, (day, v) ->
                val x = mapX(day)
                val y = mapY(v)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = seriesColor, style = Stroke(width = lineWidth))
            points.forEach { (day, v) ->
                drawCircle(seriesColor, radius = dotRadius, center = Offset(mapX(day), mapY(v)))
            }
        }

        drawSeries(chartColors.systolic, dailyAverages.map { it.key to it.value.sys })
        drawSeries(chartColors.diastolic, dailyAverages.map { it.key to it.value.dia })
        val hrPoints = dailyAverages.mapNotNull { (day, value) -> value.hr?.let { day to it } }
        if (hrPoints.isNotEmpty()) {
            drawSeries(chartColors.heartRate, hrPoints)
        }
    }
}
