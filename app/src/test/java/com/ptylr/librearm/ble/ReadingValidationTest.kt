package com.ptylr.librearm.ble

import com.ptylr.librearm.model.BpReading
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for blood pressure reading validation.
 * Tests match the iOS BPClient.isValidReading() rules:
 * 1. dia > 0 (complete reading)
 * 2. sys and dia are finite
 * 3. sys in 60..260
 * 4. dia in 40..160
 * 5. sys > dia
 * 6. (sys - dia) <= 120 (pulse pressure)
 */
class ReadingValidationTest {

    // --- Valid readings ---

    @Test
    fun `valid normal reading 120 over 80`() {
        assertTrue(BpParser.isValidReading(BpReading(120.0, 80.0)))
    }

    @Test
    fun `valid reading with heart rate`() {
        assertTrue(BpParser.isValidReading(BpReading(138.0, 88.0, hr = 72.0)))
    }

    @Test
    fun `valid reading with MAP and heart rate`() {
        assertTrue(BpParser.isValidReading(BpReading(138.0, 88.0, map = 105.0, hr = 72.0)))
    }

    @Test
    fun `valid minimum boundary 60 over 40`() {
        assertTrue(BpParser.isValidReading(BpReading(60.0, 40.0)))
    }

    @Test
    fun `valid maximum boundary 260 over 140`() {
        assertTrue(BpParser.isValidReading(BpReading(260.0, 140.0)))
    }

    @Test
    fun `valid pulse pressure at limit 180 over 60`() {
        // Pulse pressure = 120, exactly at the limit
        assertTrue(BpParser.isValidReading(BpReading(180.0, 60.0)))
    }

    @Test
    fun `valid high reading 159 over 99`() {
        assertTrue(BpParser.isValidReading(BpReading(159.0, 99.0)))
    }

    @Test
    fun `valid low reading 90 over 60`() {
        assertTrue(BpParser.isValidReading(BpReading(90.0, 60.0)))
    }

    // --- Invalid: incomplete reading (dia = 0) ---

    @Test
    fun `reject partial reading dia equals zero`() {
        assertFalse(BpParser.isValidReading(BpReading(140.0, 0.0)))
    }

    @Test
    fun `reject negative diastolic`() {
        assertFalse(BpParser.isValidReading(BpReading(120.0, -5.0)))
    }

    // --- Invalid: non-finite values ---

    @Test
    fun `reject NaN systolic`() {
        assertFalse(BpParser.isValidReading(BpReading(Double.NaN, 80.0)))
    }

    @Test
    fun `reject NaN diastolic`() {
        assertFalse(BpParser.isValidReading(BpReading(120.0, Double.NaN)))
    }

    @Test
    fun `reject infinite systolic`() {
        assertFalse(BpParser.isValidReading(BpReading(Double.POSITIVE_INFINITY, 80.0)))
    }

    // --- Invalid: out of range ---

    @Test
    fun `reject systolic below 60`() {
        assertFalse(BpParser.isValidReading(BpReading(59.0, 40.0)))
    }

    @Test
    fun `reject systolic above 260`() {
        assertFalse(BpParser.isValidReading(BpReading(261.0, 80.0)))
    }

    @Test
    fun `reject diastolic below 40`() {
        assertFalse(BpParser.isValidReading(BpReading(120.0, 39.0)))
    }

    @Test
    fun `reject diastolic above 160`() {
        assertFalse(BpParser.isValidReading(BpReading(200.0, 161.0)))
    }

    // --- Invalid: sys <= dia ---

    @Test
    fun `reject systolic less than diastolic`() {
        assertFalse(BpParser.isValidReading(BpReading(70.0, 80.0)))
    }

    @Test
    fun `reject systolic equal to diastolic`() {
        assertFalse(BpParser.isValidReading(BpReading(80.0, 80.0)))
    }

    // --- Invalid: pulse pressure > 120 ---

    @Test
    fun `reject pulse pressure over 120`() {
        // 250 - 50 = 200 > 120
        assertFalse(BpParser.isValidReading(BpReading(250.0, 50.0)))
    }

    @Test
    fun `reject pulse pressure just over limit`() {
        // 181 - 60 = 121 > 120
        assertFalse(BpParser.isValidReading(BpReading(181.0, 60.0)))
    }
}
