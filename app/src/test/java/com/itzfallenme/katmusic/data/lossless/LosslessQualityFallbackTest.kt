package com.itzfallenme.katmusic.data.lossless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LosslessQualityFallbackTest {
    @Test
    fun youtubeSelectionSkipsLosslessBackends() {
        assertTrue(LosslessMusicApi.getQualityAttemptOrder(LosslessMusicApi.QUALITY_YOUTUBE).isEmpty())
    }

    @Test
    fun hiResSelectionOnlyStepsDownAndNeverAddsAtmos() {
        val order = LosslessMusicApi.getQualityAttemptOrder(LosslessMusicApi.QUALITY_MAX_HI_RES)
        assertEquals(
            listOf(
                LosslessMusicApi.QUALITY_MAX_HI_RES,
                LosslessMusicApi.QUALITY_HI_RES_96,
                LosslessMusicApi.QUALITY_CD_LOSSLESS,
                LosslessMusicApi.QUALITY_MP3_320,
            ),
            order,
        )
        assertFalse(order.contains(LosslessMusicApi.QUALITY_DOLBY_ATMOS))
        assertFalse(order.contains(LosslessMusicApi.QUALITY_DATA_SAVER))
    }

    @Test
    fun cdLosslessMayUpgradeToHiResButNeverAtmos() {
        val order = LosslessMusicApi.getQualityAttemptOrder(LosslessMusicApi.QUALITY_CD_LOSSLESS)
        assertEquals(LosslessMusicApi.QUALITY_CD_LOSSLESS, order.first())
        assertTrue(order.indexOf(LosslessMusicApi.QUALITY_HI_RES_96) > 0)
        assertTrue(order.indexOf(LosslessMusicApi.QUALITY_MAX_HI_RES) > 0)
        assertFalse(order.contains(LosslessMusicApi.QUALITY_DOLBY_ATMOS))
        assertFalse(order.contains(LosslessMusicApi.QUALITY_DATA_SAVER))
    }

    @Test
    fun dataSaverIsOnlyUsedWhenExplicitlySelected() {
        val normal = LosslessMusicApi.getQualityAttemptOrder(LosslessMusicApi.QUALITY_MP3_320)
        val saver = LosslessMusicApi.getQualityAttemptOrder(LosslessMusicApi.QUALITY_DATA_SAVER)
        assertFalse(normal.contains(LosslessMusicApi.QUALITY_DATA_SAVER))
        assertEquals(LosslessMusicApi.QUALITY_DATA_SAVER, saver.first())
    }
}
