package com.ptylr.librearm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systolic: Double,
    val diastolic: Double,
    val meanArterialPressure: Double?,
    val heartRate: Double?,
    val timestamp: Long,        // epoch millis
    val mode: String,           // "single" or "average3"
    val savedToHealth: Boolean = false
)
