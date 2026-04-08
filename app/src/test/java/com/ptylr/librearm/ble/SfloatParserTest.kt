package com.ptylr.librearm.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for IEEE 11073 SFLOAT (16-bit) decoder.
 *
 * SFLOAT format: [EEEE MMMM MMMM MMMM]
 *   E = 4-bit signed exponent, M = 12-bit signed mantissa
 *   value = mantissa × 10^exponent
 *   Wire format: [lo_byte, hi_byte] (little-endian)
 */
class SfloatParserTest {

    @Test
    fun `decode zero`() {
        // raw = 0x0000, mantissa=0, exponent=0, value = 0 * 10^0 = 0
        assertEquals(0.0, BpParser.decodeSfloat(0x00, 0x00), 0.001)
    }

    @Test
    fun `decode systolic 120`() {
        // mantissa=120 (0x078), exponent=0, raw = 0x0078
        // lo=0x78, hi=0x00
        assertEquals(120.0, BpParser.decodeSfloat(0x78, 0x00), 0.001)
    }

    @Test
    fun `decode diastolic 80`() {
        // mantissa=80 (0x050), exponent=0, raw = 0x0050
        assertEquals(80.0, BpParser.decodeSfloat(0x50, 0x00), 0.001)
    }

    @Test
    fun `decode heart rate 72`() {
        // mantissa=72 (0x048), exponent=0, raw = 0x0048
        assertEquals(72.0, BpParser.decodeSfloat(0x48, 0x00), 0.001)
    }

    @Test
    fun `decode value with positive exponent`() {
        // mantissa=15, exponent=1, value = 15 * 10^1 = 150
        // raw = 0x100F (exponent=1 in top nibble, mantissa=0x00F)
        // lo=0x0F, hi=0x10
        assertEquals(150.0, BpParser.decodeSfloat(0x0F, 0x10), 0.001)
    }

    @Test
    fun `decode value 200`() {
        // mantissa=200 (0x0C8), exponent=0, raw = 0x00C8
        assertEquals(200.0, BpParser.decodeSfloat(0xC8.toByte(), 0x00), 0.001)
    }

    @Test
    fun `decode small value with negative exponent`() {
        // mantissa=1, exponent=15 (which is -1 in 4-bit signed), value = 1 * 10^(-1) = 0.1
        // raw = 0xF001 (exponent=0xF=-1, mantissa=0x001)
        // lo=0x01, hi=0xF0
        assertEquals(0.1, BpParser.decodeSfloat(0x01, 0xF0.toByte()), 0.001)
    }

    @Test
    fun `decode NaN returns NaN`() {
        // NaN: mantissa=0x07FF, raw = 0x07FF
        // lo=0xFF, hi=0x07
        assertTrue(BpParser.decodeSfloat(0xFF.toByte(), 0x07).isNaN())
    }

    @Test
    fun `decode NRes returns NaN`() {
        // NRes: mantissa=0x0800, raw = 0x0800
        // lo=0x00, hi=0x08
        // Actually 0x0800 mantissa >= 0x07FE so it's special
        assertTrue(BpParser.decodeSfloat(0x00, 0x08).isNaN())
    }

    @Test
    fun `decode positive infinity returns infinity`() {
        // +INFINITY: mantissa=0x07FE
        val result = BpParser.decodeSfloat(0xFE.toByte(), 0x07)
        assertTrue(result.isInfinite() && result > 0)
    }

    @Test
    fun `decode negative infinity returns negative infinity`() {
        // -INFINITY: mantissa=0x0802
        val result = BpParser.decodeSfloat(0x02, 0x08)
        assertTrue(result.isInfinite() && result < 0)
    }

    @Test
    fun `decode negative mantissa`() {
        // mantissa=0xFFF (4095 unsigned = -1 in two's complement 12-bit), exponent=0
        // value = -1 * 10^0 = -1
        // raw = 0x0FFF, lo=0xFF, hi=0x0F
        assertEquals(-1.0, BpParser.decodeSfloat(0xFF.toByte(), 0x0F), 0.001)
    }
}
