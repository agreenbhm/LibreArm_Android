package com.ptylr.librearm.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for BLE Blood Pressure Measurement characteristic (0x2A35) parsing.
 *
 * Packet format:
 *   [0]     Flags (bit 1=timestamp, bit 2=heart rate)
 *   [1:2]   Systolic (SFLOAT)
 *   [3:4]   Diastolic (SFLOAT)
 *   [5:6]   MAP (SFLOAT)
 *   [7:13]  Optional timestamp (7 bytes if flags & 0x02)
 *   [N:N+1] Optional heart rate (SFLOAT if flags & 0x04)
 */
class MeasurementParserTest {

    @Test
    fun `parse minimal packet - sys dia map only`() {
        // flags=0x00, sys=120 (0x0078), dia=80 (0x0050), map=93 (0x005D)
        val data = byteArrayOf(
            0x00,                   // flags: no timestamp, no HR
            0x78, 0x00,             // systolic: 120
            0x50, 0x00,             // diastolic: 80
            0x5D, 0x00              // MAP: 93
        )
        val reading = BpParser.parseMeasurement(data)
        assertNotNull(reading)
        assertEquals(120.0, reading!!.sys, 0.001)
        assertEquals(80.0, reading.dia, 0.001)
        assertEquals(93.0, reading.map!!, 0.001)
        assertNull(reading.hr)
    }

    @Test
    fun `parse packet with heart rate`() {
        // flags=0x04 (HR present), sys=138, dia=88, map=105, hr=72
        val data = byteArrayOf(
            0x04,                   // flags: HR present
            0x8A.toByte(), 0x00,    // systolic: 138
            0x58, 0x00,             // diastolic: 88
            0x69, 0x00,             // MAP: 105
            0x48, 0x00              // heart rate: 72
        )
        val reading = BpParser.parseMeasurement(data)
        assertNotNull(reading)
        assertEquals(138.0, reading!!.sys, 0.001)
        assertEquals(88.0, reading.dia, 0.001)
        assertEquals(105.0, reading.map!!, 0.001)
        assertEquals(72.0, reading.hr!!, 0.001)
    }

    @Test
    fun `parse packet with timestamp and heart rate`() {
        // flags=0x06 (timestamp + HR present)
        // sys=140, dia=90, map=107, then 7-byte timestamp, then hr=68
        val data = byteArrayOf(
            0x06,                   // flags: timestamp + HR
            0x8C.toByte(), 0x00,    // systolic: 140
            0x5A, 0x00,             // diastolic: 90
            0x6B, 0x00,             // MAP: 107
            // 7 bytes timestamp (dummy values)
            0xE6.toByte(), 0x07, 0x04, 0x05, 0x0A, 0x1E, 0x00,
            0x44, 0x00              // heart rate: 68
        )
        val reading = BpParser.parseMeasurement(data)
        assertNotNull(reading)
        assertEquals(140.0, reading!!.sys, 0.001)
        assertEquals(90.0, reading.dia, 0.001)
        assertEquals(107.0, reading.map!!, 0.001)
        assertEquals(68.0, reading.hr!!, 0.001)
    }

    @Test
    fun `parse partial reading - dia zero during inflation`() {
        // During cuff inflation, device sends partial readings with dia=0
        val data = byteArrayOf(
            0x00,
            0x8C.toByte(), 0x00,    // systolic: 140
            0x00, 0x00,             // diastolic: 0 (incomplete)
            0x78, 0x00              // MAP: 120
        )
        val reading = BpParser.parseMeasurement(data)
        assertNotNull(reading)
        assertEquals(140.0, reading!!.sys, 0.001)
        assertEquals(0.0, reading.dia, 0.001)
    }

    @Test
    fun `reject packet too short`() {
        val data = byteArrayOf(0x00, 0x78, 0x00, 0x50, 0x00) // only 5 bytes
        assertNull(BpParser.parseMeasurement(data))
    }

    @Test
    fun `reject empty packet`() {
        assertNull(BpParser.parseMeasurement(byteArrayOf()))
    }

    @Test
    fun `parse packet with timestamp but no HR`() {
        // flags=0x02 (timestamp present only)
        val data = byteArrayOf(
            0x02,                   // flags: timestamp only
            0x78, 0x00,             // systolic: 120
            0x50, 0x00,             // diastolic: 80
            0x5D, 0x00,             // MAP: 93
            // 7 bytes timestamp
            0xE6.toByte(), 0x07, 0x04, 0x05, 0x0A, 0x1E, 0x00
        )
        val reading = BpParser.parseMeasurement(data)
        assertNotNull(reading)
        assertEquals(120.0, reading!!.sys, 0.001)
        assertEquals(80.0, reading.dia, 0.001)
        assertNull(reading.hr)
    }
}
