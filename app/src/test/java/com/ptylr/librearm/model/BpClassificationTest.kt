package com.ptylr.librearm.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for AHA blood pressure classification.
 */
class BpClassificationTest {

    // --- Low ---

    @Test
    fun `classify low reading 85 over 55`() {
        assertEquals(BpCategory.LOW, classifyReading(85.0, 55.0))
    }

    @Test
    fun `classify low reading 80 over 50`() {
        assertEquals(BpCategory.LOW, classifyReading(80.0, 50.0))
    }

    // --- Normal ---

    @Test
    fun `classify normal 110 over 70`() {
        assertEquals(BpCategory.NORMAL, classifyReading(110.0, 70.0))
    }

    @Test
    fun `classify normal boundary 90 over 60`() {
        assertEquals(BpCategory.NORMAL, classifyReading(90.0, 60.0))
    }

    @Test
    fun `classify normal boundary 119 over 79`() {
        assertEquals(BpCategory.NORMAL, classifyReading(119.0, 79.0))
    }

    // --- Prehypertension ---

    @Test
    fun `classify prehypertension by systolic 125 over 75`() {
        assertEquals(BpCategory.PREHYPERTENSION, classifyReading(125.0, 75.0))
    }

    @Test
    fun `classify prehypertension by diastolic 115 over 82`() {
        assertEquals(BpCategory.PREHYPERTENSION, classifyReading(115.0, 82.0))
    }

    @Test
    fun `classify prehypertension boundary 120 over 75`() {
        assertEquals(BpCategory.PREHYPERTENSION, classifyReading(120.0, 75.0))
    }

    @Test
    fun `classify prehypertension boundary 115 over 80`() {
        assertEquals(BpCategory.PREHYPERTENSION, classifyReading(115.0, 80.0))
    }

    // --- Stage 1 ---

    @Test
    fun `classify stage1 by systolic 145 over 85`() {
        assertEquals(BpCategory.STAGE1, classifyReading(145.0, 85.0))
    }

    @Test
    fun `classify stage1 by diastolic 135 over 92`() {
        assertEquals(BpCategory.STAGE1, classifyReading(135.0, 92.0))
    }

    @Test
    fun `classify stage1 boundary 140 over 85`() {
        assertEquals(BpCategory.STAGE1, classifyReading(140.0, 85.0))
    }

    @Test
    fun `classify stage1 boundary 135 over 90`() {
        assertEquals(BpCategory.STAGE1, classifyReading(135.0, 90.0))
    }

    // --- Stage 2 ---

    @Test
    fun `classify stage2 by systolic 170 over 85`() {
        assertEquals(BpCategory.STAGE2, classifyReading(170.0, 85.0))
    }

    @Test
    fun `classify stage2 by diastolic 135 over 105`() {
        assertEquals(BpCategory.STAGE2, classifyReading(135.0, 105.0))
    }

    @Test
    fun `classify stage2 boundary 160 over 85`() {
        assertEquals(BpCategory.STAGE2, classifyReading(160.0, 85.0))
    }

    @Test
    fun `classify stage2 boundary 135 over 100`() {
        assertEquals(BpCategory.STAGE2, classifyReading(135.0, 100.0))
    }

    @Test
    fun `classify stage2 both high 180 over 110`() {
        assertEquals(BpCategory.STAGE2, classifyReading(180.0, 110.0))
    }

    // --- Higher category wins ---

    @Test
    fun `systolic stage2 with normal diastolic uses stage2`() {
        assertEquals(BpCategory.STAGE2, classifyReading(165.0, 70.0))
    }

    @Test
    fun `normal systolic with stage1 diastolic uses stage1`() {
        assertEquals(BpCategory.STAGE1, classifyReading(115.0, 95.0))
    }
}
