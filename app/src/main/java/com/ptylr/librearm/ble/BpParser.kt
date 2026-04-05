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
     * @param lo Low byte (first byte on wire, little-endian)
     * @param hi High byte (second byte on wire)
     * @return Decoded double value, or Double.NaN for special SFLOAT values
     */
    fun decodeSfloat(lo: Byte, hi: Byte): Double {
        val raw = (hi.toInt() and 0xFF shl 8) or (lo.toInt() and 0xFF)
        val mantissa = raw and 0x0FFF

        // Special values: NaN (0x07FF), NRes (0x0800), +INF (0x07FE), -INF (0x0802)
        if (mantissa >= 0x07FE) return Double.NaN

        val exponent = raw shr 12
        val signedMantissa = if (mantissa >= 0x0800) mantissa - 0x1000 else mantissa
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
