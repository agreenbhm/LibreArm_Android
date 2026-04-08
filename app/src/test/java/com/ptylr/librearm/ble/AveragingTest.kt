package com.ptylr.librearm.ble

import com.ptylr.librearm.model.BpReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the blood pressure reading averaging algorithm.
 */
class AveragingTest {

    @Test
    fun `average of three valid readings`() {
        val readings = listOf(
            BpReading(120.0, 80.0, map = 93.0, hr = 70.0),
            BpReading(130.0, 85.0, map = 100.0, hr = 72.0),
            BpReading(125.0, 82.0, map = 96.0, hr = 68.0)
        )
        val avg = BpParser.average(readings)
        assertEquals(125.0, avg.sys, 0.001)
        assertEquals(82.333, avg.dia, 0.01)
        assertEquals(96.333, avg.map!!, 0.01)
        assertEquals(70.0, avg.hr!!, 0.01)
    }

    @Test
    fun `average filters out invalid readings`() {
        val readings = listOf(
            BpReading(120.0, 80.0),                    // valid
            BpReading(70.0, 80.0),                     // invalid: sys <= dia
            BpReading(130.0, 85.0)                     // valid
        )
        val avg = BpParser.average(readings)
        // Only the two valid readings should be averaged
        assertEquals(125.0, avg.sys, 0.001)
        assertEquals(82.5, avg.dia, 0.001)
    }

    @Test
    fun `average of empty list returns fallback`() {
        val fallback = BpReading(120.0, 80.0)
        val avg = BpParser.average(emptyList(), fallback)
        assertEquals(120.0, avg.sys, 0.001)
        assertEquals(80.0, avg.dia, 0.001)
    }

    @Test
    fun `average of empty list without fallback returns zeros`() {
        val avg = BpParser.average(emptyList())
        assertEquals(0.0, avg.sys, 0.001)
        assertEquals(0.0, avg.dia, 0.001)
        assertNull(avg.map)
        assertNull(avg.hr)
    }

    @Test
    fun `average with all invalid readings uses fallback`() {
        val readings = listOf(
            BpReading(70.0, 80.0),     // invalid: sys <= dia
            BpReading(300.0, 80.0)     // invalid: sys > 260
        )
        val fallback = BpReading(120.0, 80.0)
        val avg = BpParser.average(readings, fallback)
        assertEquals(120.0, avg.sys, 0.001)
    }

    @Test
    fun `average excludes non-finite MAP values`() {
        val readings = listOf(
            BpReading(120.0, 80.0, map = Double.NaN, hr = 70.0),
            BpReading(130.0, 85.0, map = 100.0, hr = 72.0)
        )
        val avg = BpParser.average(readings)
        // Only the finite MAP value should be used
        assertEquals(100.0, avg.map!!, 0.001)
    }

    @Test
    fun `average excludes heart rate outside 20-220 range`() {
        val readings = listOf(
            BpReading(120.0, 80.0, hr = 5.0),      // HR too low (< 20)
            BpReading(130.0, 85.0, hr = 72.0)       // valid HR
        )
        val avg = BpParser.average(readings)
        assertEquals(72.0, avg.hr!!, 0.001)
    }

    @Test
    fun `average with no heart rate data returns null hr`() {
        val readings = listOf(
            BpReading(120.0, 80.0),
            BpReading(130.0, 85.0)
        )
        val avg = BpParser.average(readings)
        assertNull(avg.hr)
    }

    @Test
    fun `average with single reading returns that reading`() {
        val readings = listOf(BpReading(135.0, 87.0, map = 103.0, hr = 75.0))
        val avg = BpParser.average(readings)
        assertEquals(135.0, avg.sys, 0.001)
        assertEquals(87.0, avg.dia, 0.001)
        assertEquals(103.0, avg.map!!, 0.001)
        assertEquals(75.0, avg.hr!!, 0.001)
    }
}
