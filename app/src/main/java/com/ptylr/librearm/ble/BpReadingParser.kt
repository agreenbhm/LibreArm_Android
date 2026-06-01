package com.ptylr.librearm.ble

import com.ptylr.librearm.model.BpReading
import kotlin.math.pow

/**
 * Pure parser for the IEEE-11073 SFLOAT-formatted Blood Pressure Measurement
 * characteristic (BLE GATT 0x2A35). Kept separate from BLE plumbing so it can
 * be unit-tested without an Android dependency.
 */
internal object BpReadingParser {

    fun parse(data: ByteArray): BpReading? {
        if (data.size < 7) return null

        val flags = data[0].toInt()
        val sys = sfloat(data[1], data[2])
        val dia = sfloat(data[3], data[4])
        val map = sfloat(data[5], data[6])

        var idx = 7
        if (flags and 0x02 != 0) idx += 7 // timestamp present

        val hr: Double? = if (flags and 0x04 != 0 && data.size >= idx + 2) {
            sfloat(data[idx], data[idx + 1])
        } else null

        return BpReading(sys = sys, dia = dia, map = map, hr = hr)
    }

    private fun sfloat(lo: Byte, hi: Byte): Double {
        val raw = ((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)
        val mantissa = raw and 0x0FFF
        val exponent = raw shr 12
        val signed = if (mantissa >= 0x0800) mantissa - 0x1000 else mantissa
        return signed * 10.0.pow(exponent.toDouble())
    }
}
