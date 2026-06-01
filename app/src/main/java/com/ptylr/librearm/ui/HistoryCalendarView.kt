package com.ptylr.librearm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ptylr.librearm.R
import com.ptylr.librearm.model.HistoricalReading
import com.ptylr.librearm.ui.theme.LocalChartColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
internal fun CalendarView(
    month: YearMonth,
    readings: List<HistoricalReading>,
    zone: ZoneId
) {
    val chartColors = LocalChartColors.current
    val daysWithReadings: Set<Int> = remember(readings, month, zone) {
        readings
            .map { it.time.atZone(zone).toLocalDate() }
            .filter { YearMonth.from(it) == month }
            .map { it.dayOfMonth }
            .toSet()
    }

    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val firstDay = month.atDay(1)
    val startOffset = ((firstDay.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
    val daysInMonth = month.lengthOfMonth()
    // Always render 6 rows of 7 so swiping between months doesn't change the grid height.
    val totalCells = 42
    val today = LocalDate.now()

    var selectedDay by remember(month) { mutableStateOf<Int?>(null) }
    // Auto-select today on first load if this is the current month and today has readings.
    LaunchedEffect(daysWithReadings) {
        val current = selectedDay
        if (current != null && current !in daysWithReadings) {
            selectedDay = null
        }
        if (selectedDay == null &&
            YearMonth.from(today) == month &&
            today.dayOfMonth in daysWithReadings
        ) {
            selectedDay = today.dayOfMonth
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                val day = firstDayOfWeek.plus(i.toLong())
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val cells = (0 until totalCells).map { idx ->
            val dayNum = idx - startOffset + 1
            if (dayNum in 1..daysInMonth) dayNum else null
        }
        cells.chunked(7).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                row.forEach { dayNum ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (dayNum != null) {
                            val date = month.atDay(dayNum)
                            val isToday = date == today
                            val hasReadings = dayNum in daysWithReadings
                            val isSelected = selectedDay == dayNum

                            val background = when {
                                isSelected && hasReadings -> chartColors.diastolic
                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                isSelected && hasReadings -> Color.White
                                hasReadings -> chartColors.diastolic
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(background)
                                    .clickable(enabled = hasReadings) {
                                        selectedDay = dayNum
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isToday || hasReadings || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            daysWithReadings.isEmpty() -> {
                Text(
                    text = stringResource(R.string.history_no_readings_month),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            selectedDay == null -> {
                Text(
                    text = stringResource(R.string.history_tap_day_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                val day = selectedDay!!
                DayDetailCard(
                    date = month.atDay(day),
                    readings = readings.filter {
                        val ld = it.time.atZone(zone).toLocalDate()
                        ld.year == month.year && ld.monthValue == month.monthValue && ld.dayOfMonth == day
                    }
                )
            }
        }
    }
}

@Composable
private fun DayDetailCard(date: LocalDate, readings: List<HistoricalReading>) {
    val titlePattern = stringResource(R.string.history_day_detail_format)
    val titleFormatter = remember(titlePattern) { DateTimeFormatter.ofPattern(titlePattern) }
    val timePattern = stringResource(R.string.history_time_only_format)
    val timeFormatter = remember(timePattern) {
        DateTimeFormatter.ofPattern(timePattern).withZone(ZoneId.systemDefault())
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
            Text(date.format(titleFormatter), style = MaterialTheme.typography.titleSmall)
            readings.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormatter.format(entry.time),
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
