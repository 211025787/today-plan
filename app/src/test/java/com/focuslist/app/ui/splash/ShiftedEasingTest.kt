package com.focuslist.app.ui.splash

import androidx.compose.animation.core.Easing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 开屏续播缓动的纯 JVM 测试。
 *
 * 这一层是「文字提前 0.3 秒」的数学基础：把曲线整体后移 [offset]，但每秒的
 * alpha 变化量必须和原动画一模一样，否则就成了「加快播放」。
 */
class ShiftedEasingTest {

    /** 一条好算的曲线：y = x² */
    private val quad = Easing { it * it }

    /** 复现 Animatable 的插值方式：value = start + (target - start) * eased */
    private fun alphaAt(easing: ShiftedEasing, offset: Float, fraction: Float): Float {
        val start = quad.transform(offset)
        return start + (1f - start) * easing.transform(fraction)
    }

    @Test
    fun `没有偏移时与原曲线完全一致`() {
        val shifted = ShiftedEasing(quad, 0f)
        for (i in 0..20) {
            val f = i / 20f
            assertEquals(quad.transform(f), shifted.transform(f), 1e-6f)
        }
    }

    @Test
    fun `偏移后与曲线原本的绝对进度一一对应`() {
        val offset = 0.25f
        val shifting = ShiftedEasing(quad, offset)
        for (i in 0..20) {
            val f = i / 20f
            // 续播到 f 时的 alpha，应该正好等于原曲线在 0.25 + 0.75f 处的 alpha
            assertEquals(quad.transform(offset + f * (1f - offset)), alphaAt(shifting, offset, f), 1e-6f)
        }
    }

    @Test
    fun `两端仍落在 0 和 1`() {
        val shifting = ShiftedEasing(quad, 0.25f)
        assertEquals(0f, shifting.transform(0f), 1e-6f)
        assertEquals(1f, shifting.transform(1f), 1e-6f)
    }

    @Test
    fun `单调递增且不越界`() {
        val shifting = ShiftedEasing(quad, 0.25f)
        var last = -1f
        for (i in 0..100) {
            val v = shifting.transform(i / 100f)
            assertEquals(true, v >= last)
            assertEquals(true, v in 0f..1f)
            last = v
        }
    }

    @Test
    fun `每毫秒的变化量与原动画相同`() {
        // 原动画 1200ms；从 300ms 处续播，剩 900ms
        val offset = 300f / 1200f
        val remain = 1200f * (1f - offset)
        val shifting = ShiftedEasing(quad, offset)
        for (ms in 0 until remain.toInt()) {
            val here = alphaAt(shifting, offset, ms / remain)
            val next = alphaAt(shifting, offset, (ms + 1) / remain)
            val absHere = quad.transform((300f + ms) / 1200f)
            val absNext = quad.transform((300f + ms + 1) / 1200f)
            assertEquals(absNext - absHere, next - here, 1e-6f)
        }
    }
}
