package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 铃声片段范围与文案的边界测试。
 */
class RingtoneClipTest {

    @Test
    fun `normalize keeps range inside duration`() {
        val range = RingtoneClip.normalize(10_000L, 20_000L, 60_000L)
        assertEquals(10_000L, range.first)
        assertEquals(20_000L, range.last)
    }

    @Test
    fun `end zero means full length`() {
        val range = RingtoneClip.normalize(5_000L, 0L, 30_000L)
        assertEquals(5_000L, range.first)
        assertEquals(30_000L, range.last)
    }

    @Test
    fun `end beyond duration is clamped`() {
        val range = RingtoneClip.normalize(-100L, 99_000L, 30_000L)
        assertEquals(0L, range.first)
        assertEquals(30_000L, range.last)
    }

    @Test
    fun `too short range is expanded`() {
        val range = RingtoneClip.normalize(10_000L, 10_200L, 30_000L)
        assertEquals(1_000L, range.last - range.first)
        assertEquals(10_000L, range.first)
    }

    @Test
    fun `short range at the end moves start back`() {
        val range = RingtoneClip.normalize(29_900L, 30_000L, 30_000L)
        assertEquals(29_000L, range.first)
        assertEquals(30_000L, range.last)
    }

    @Test
    fun `unknown duration falls back to minimum gap`() {
        val range = RingtoneClip.normalize(0L, 0L, 0L)
        assertEquals(0L, range.first)
        assertEquals(RingtoneClip.MIN_GAP_MS, range.last)
    }

    @Test
    fun `effective end uses full length when unset`() {
        assertEquals(30_000L, RingtoneClip.effectiveEnd(0L, 30_000L))
        assertEquals(12_000L, RingtoneClip.effectiveEnd(12_000L, 30_000L))
    }

    @Test
    fun `duration clamped to range`() {
        assertEquals(0, RingtoneClip.clampDurationSec(-5))
        assertEquals(90, RingtoneClip.clampDurationSec(90))
        assertEquals(1_800, RingtoneClip.clampDurationSec(5_000))
    }

    @Test
    fun `time format`() {
        assertEquals("0:00", RingtoneClip.formatTime(0L))
        assertEquals("0:59", RingtoneClip.formatTime(59_900L))
        assertEquals("1:05", RingtoneClip.formatTime(65_000L))
        assertEquals("10:00", RingtoneClip.formatTime(600_000L))
    }

    @Test
    fun `duration text`() {
        assertEquals("0 秒", RingtoneClip.durationText(0, 0))
        assertEquals("45 秒", RingtoneClip.durationText(0, 45))
        assertEquals("2 分", RingtoneClip.durationText(2, 0))
        assertEquals("1 分 30 秒", RingtoneClip.durationText(1, 30))
    }
}
