package com.ptylr.librearm.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ptylr.librearm.data.ReadingEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    readings: List<ReadingEntity>,
    onDelete: (ReadingEntity) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (readings.isNotEmpty()) {
                        IconButton(onClick = onExport) {
                            Icon(Icons.Default.Share, "Export CSV")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (readings.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No readings yet.\nTake a measurement to see it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                val grouped = readings.groupBy { reading ->
                    val cal = Calendar.getInstance().apply { timeInMillis = reading.timestamp }
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(reading.timestamp))
                }
                grouped.forEach { (dateStr, dayReadings) ->
                    item {
                        Text(
                            text = formatDateHeader(dateStr),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(dayReadings, key = { it.id }) { reading ->
                        ReadingHistoryItem(reading, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingHistoryItem(
    reading: ReadingEntity,
    onDelete: (ReadingEntity) -> Unit
) {
    val categoryColor = classifyColor(reading.systolic, reading.diastolic)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(categoryColor, CircleShape)
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "${reading.systolic.toInt()}/${reading.diastolic.toInt()} mmHg",
                    style = MaterialTheme.typography.bodyLarge
                )
                Row {
                    reading.heartRate?.let { hr ->
                        Text(
                            "HR: ${hr.toInt()} bpm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        SimpleDateFormat("HH:mm", Locale.US).format(Date(reading.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (reading.mode == "average3") {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "(avg)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            IconButton(onClick = { onDelete(reading) }) {
                Icon(
                    Icons.Default.Delete,
                    "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatDateHeader(dateStr: String): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
        Date(System.currentTimeMillis() - 86400000)
    )
    return when (dateStr) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            parsed?.let { SimpleDateFormat("MMMM d, yyyy", Locale.US).format(it) } ?: dateStr
        }
    }
}

private fun classifyColor(systolic: Double, diastolic: Double): Color = when {
    systolic >= 160 || diastolic >= 100 -> Color(0xFFFB5959)   // Stage 2 Red
    systolic >= 140 || diastolic >= 90 -> Color(0xFFFB80A6)    // Stage 1 Pink
    systolic >= 120 || diastolic >= 80 -> Color(0xFFF2A659)    // Prehypertension Orange
    systolic >= 90 && diastolic >= 60 -> Color(0xFF73D973)     // Normal Green
    else -> Color(0xFF80D9D9)                                   // Low Cyan
}
