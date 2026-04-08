package com.ptylr.librearm.model

import androidx.compose.ui.graphics.Color

/**
 * AHA blood pressure classification categories with associated display colors.
 * Matches the iOS HypertensionGraphView zone definitions.
 */
enum class BpCategory(val label: String, val color: Color) {
    LOW("Low", Color(0xFF80D9D9)),                          // Cyan
    NORMAL("Normal", Color(0xFF73D973)),                     // Green
    PREHYPERTENSION("Prehypertension", Color(0xFFF2A659)),   // Orange
    STAGE1("Stage 1 Hypertension", Color(0xFFFB80A6)),       // Pink
    STAGE2("Stage 2 Hypertension", Color(0xFFFB5959))        // Red
}

/**
 * Classify a blood pressure reading per AHA guidelines.
 * Uses the higher category when systolic and diastolic fall in different zones.
 */
fun classifyReading(systolic: Double, diastolic: Double): BpCategory = when {
    systolic >= 160 || diastolic >= 100 -> BpCategory.STAGE2
    systolic >= 140 || diastolic >= 90 -> BpCategory.STAGE1
    systolic >= 120 || diastolic >= 80 -> BpCategory.PREHYPERTENSION
    systolic >= 90 && diastolic >= 60 -> BpCategory.NORMAL
    else -> BpCategory.LOW
}
