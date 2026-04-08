package com.ptylr.librearm.ble

import com.ptylr.librearm.model.BpReading
import kotlin.math.pow

/**
 * Standalone blood pressure measurement parsing utilities.
 * Extracted from BpClient for testability.
 */
object BpParser {

    /**
     * Decode an IEEE 11073 SFLOAT (16-bit) value from two bytes.
     *
     * SFLOAT format: [EEEE MMMM MMMM MMMM]
     *   E = 4-bit signed exponent (-8 to +7)
     *   M = 12-bit signed mantissa (-2048 to +2047)
     *   value = mantissa × 10^exponent
     *
     * Special reserved mantissa values per IEEE 11073-20601:
     *   0x07FF = NaN
     *   0x0800 = NRes (Not at this Resolution)
     *   0x07FE = +INFINITY
     *   0x0802 = -INFINITY
     *
     * @param lo Low byte (first byte on wire, little-endian)
     * @param hi High byte (second byte on wire)
     * @return Decoded double value, or Double.NaN/Infinity for special SFLOAT values
     */
    fun decodeSfloat(lo: Byte, hi: Byte): Double {
        val raw = ((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)
        val unsignedMantissa = raw and 0x0FFF

        // Check for reserved special values (exact matches only)
        when (unsignedMantissa) {
            0x07FF -> return Double.NaN
            0x0800 -> return Double.NaN              // NRes
            0x07FE -> return Double.POSITIVE_INFINITY
            0x0802 -> return Double.NEGATIVE_INFINITY
        }

        // Extract and sign-extend the 4-bit exponent (top nibble of the 16-bit value)
        val unsignedExponent = (raw shr 12) and 0xF
        val exponent = if (unsignedExponent >= 8) unsignedExponent - 16 else unsignedExponent

        // Sign-extend the 12-bit mantissa
        val signedMantissa =
            if (unsignedMantissa >= 0x0800) unsignedMantissa - 0x1000 else unsignedMantissa

        return signedMantissa * 10.0.pow(exponent.toDouble())
    }

    /**
     * Parse a BLE Blood Pressure Measurement characteristic value (0x2A35).
     *
     * @param data Raw bytes from BLE notification
     * @return Parsed reading, or null if data is too short
     */
    fun parseMeasurement(data: ByteArray): BpReading? {
        if (data.size < 7) return null

        val flags = data[0].toInt()
        val sys = decodeSfloat(data[1], data[2])
        val dia = decodeSfloat(data[3], data[4])
        val map = decodeSfloat(data[5], data[6])

        var idx = 7
        if (flags and 0x02 != 0) idx += 7 // timestamp present

        var hr: Double? = null
        if (flags and 0x04 != 0 && data.size >= idx + 2) {
            hr = decodeSfloat(data[idx], data[idx + 1])
        }

        return BpReading(sys = sys, dia = dia, map = map, hr = hr)
    }

    /**
     * Validates a blood pressure reading for physiological plausibility.
     * Mirrors the iOS BPClient.isValidReading() validation rules.
     *
     * @return true if the reading is physiologically valid and complete
     */
    fun isValidReading(reading: BpReading): Boolean {
        // Diastolic > 0 indicates a complete reading (not partial)
        if (reading.dia <= 0) return false

        // Values must be finite (filters SFLOAT NaN and Infinity)
        if (!reading.sys.isFinite() || !reading.dia.isFinite()) return false

        // Physiologically plausible ranges
        if (reading.sys !in 60.0..260.0) return false
        if (reading.dia !in 40.0..160.0) return false

        // Systolic must exceed diastolic
        if (reading.sys <= reading.dia) return false

        // Pulse pressure must be reasonable
        if ((reading.sys - reading.dia) > 120) return false

        return true
    }

    /**
     * Compute average of multiple readings, filtering for validity.
     *
     * @return Averaged reading, or the last valid reading if no accumulated readings are valid
     */
    fun average(readings: List<BpReading>, fallback: BpReading? = null): BpReading {
        val valid = readings.filter { isValidReading(it) }
        if (valid.isEmpty()) {
            if (fallback != null && isValidReading(fallback)) return fallback
            return BpReading(0.0, 0.0, null, null)
        }

        val n = valid.size.toDouble()
        val sysAvg = valid.sumOf { it.sys } / n
        val diaAvg = valid.sumOf { it.dia } / n

        val mapVals = valid.mapNotNull { it.map }.filter { it.isFinite() }
        val mapAvg = mapVals.takeIf { it.isNotEmpty() }?.average()

        val hrVals = valid.mapNotNull { it.hr }.filter { it.isFinite() && it in 20.0..220.0 }
        val hrAvg = hrVals.takeIf { it.isNotEmpty() }?.average()

        return BpReading(sys = sysAvg, dia = diaAvg, map = mapAvg, hr = hrAvg)
    }
}
