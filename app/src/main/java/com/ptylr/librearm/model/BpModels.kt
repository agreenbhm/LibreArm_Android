package com.ptylr.librearm.model

enum class MeasurementMode { SINGLE, AVERAGE3 }

data class BpReading(
    val sys: Double,
    val dia: Double,
    val map: Double? = null,
    val hr: Double? = null
)

data class BpState(
    val status: String = "Searching for device…",
    val lastReading: BpReading? = null,
    val isConnected: Boolean = false,
    val canMeasure: Boolean = false,
    val isMeasuring: Boolean = false,
    val measurementMode: MeasurementMode = MeasurementMode.SINGLE,
    val delayBetweenRunsSeconds: Int = 15,
    val batteryLevel: Int? = null,
    val batteryStatusLine: String = "Battery: unavailable"
)
