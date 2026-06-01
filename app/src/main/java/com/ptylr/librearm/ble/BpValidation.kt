package com.ptylr.librearm.ble

import com.ptylr.librearm.model.BpReading

/**
 * Single source of truth for what counts as a physiologically plausible reading.
 * Mirrors the iOS v1.4.0 BPClient.isValidReading rules.
 */
object BpValidation {
    const val SYS_MIN = 60.0
    const val SYS_MAX = 260.0
    const val DIA_MIN = 40.0
    const val DIA_MAX = 160.0
    const val MAX_PULSE_PRESSURE = 120.0
    val HR_RANGE = 20.0..220.0

    fun isValid(r: BpReading): Boolean {
        if (r.dia <= 0) return false
        if (!r.sys.isFinite() || !r.dia.isFinite()) return false
        if (r.sys !in SYS_MIN..SYS_MAX) return false
        if (r.dia !in DIA_MIN..DIA_MAX) return false
        if (r.sys <= r.dia) return false
        if ((r.sys - r.dia) > MAX_PULSE_PRESSURE) return false
        return true
    }
}
