package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收工备注 [DayWrapScript] 的单元测试。
 *
 * 关注点：Markdown 结构与字段顺序（备注页按行渲染，多一个空行都会显得乱）、
 * 行数上限、以及「已达标 / 没设目标」两种列表写法。
 */
class DayWrapScriptTest {

    /** 构造一条计划 */
    private fun plan(
        id: Long,
        title: String = "计划$id",
        minutes: Int = 0,
        goal: Int = 30
    ): WrapPlan = WrapPlan(id = id, title = title, minutes = minutes, goalMinutes = goal)

    /** 中午生成小结 */
    private fun wrap(vararg plans: WrapPlan): DayWrap = DayWraps.of(plans.toList(), 12 * 60)

    @Test
    fun titleIsStable() {
        assertEquals("收工小结", DayWrapScript.NOTE_TITLE)
    }

    @Test
    fun startsWithHeadingAndDate() {
        val text = DayWrapScript.content(wrap(plan(1, minutes = 30)), "11 月 1 日")

        assertTrue(text.startsWith("### 收工小结 · 11 月 1 日\n"))
    }

    @Test
    fun includesBoldHeadline() {
        val text = DayWrapScript.content(wrap(plan(1, minutes = 60)), "11 月 1 日")

        assertTrue(text.contains("**今天 1 小时**"))
    }

    @Test
    fun includesAchievedRatio() {
        val text = DayWrapScript.content(
            wrap(plan(1, minutes = 30), plan(2, minutes = 10)),
            "11 月 1 日"
        )

        assertTrue(text.contains("达标 1/2"))
    }

    @Test
    fun listsPlansWithGoal() {
        val text = DayWrapScript.content(wrap(plan(1, "写作", minutes = 30, goal = 30)), "11 月 1 日")

        assertTrue(text.contains("- 写作 30 / 30 分钟 ✅"))
    }

    @Test
    fun listsPlansWithoutGoal() {
        val text = DayWrapScript.content(wrap(plan(1, "写作", minutes = 12, goal = 0)), "11 月 1 日")

        assertTrue(text.contains("- 写作 12 分钟"))
        assertFalse(text.contains("✅"))
    }

    @Test
    fun unfinishedPlansHaveNoCheckMark() {
        val text = DayWrapScript.content(wrap(plan(1, "读书", minutes = 10, goal = 30)), "11 月 1 日")

        assertTrue(text.contains("- 读书 10 / 30 分钟"))
        assertFalse(text.contains("✅"))
    }

    @Test
    fun capsRowsAndMentionsRest() {
        val plans = (1..7).map { plan(it.toLong(), "计划$it", minutes = 10) }
        val text = DayWrapScript.content(wrap(*plans.toTypedArray()), "11 月 1 日")

        assertTrue(text.contains("- 另有 2 个计划未列出"))
        // 第 6、7 个计划不再逐条列出
        assertFalse(text.contains("- 计划6 10 / 30 分钟"))
    }

    @Test
    fun includesClosingAsQuote() {
        val text = DayWrapScript.content(wrap(plan(1, minutes = 30)), "11 月 1 日")

        assertTrue(text.contains("> 全部达标，可以安心收工了"))
    }

    @Test
    fun omitsQuoteWhenClosingBlank() {
        val text = DayWrapScript.content(DayWrap(headline = "今天还没有投入"), "11 月 1 日")

        assertFalse(text.contains(">"))
    }

    @Test
    fun noTrailingNewline() {
        val text = DayWrapScript.content(wrap(plan(1, minutes = 30)), "11 月 1 日")

        assertFalse(text.endsWith("\n"))
    }

    @Test
    fun emptyDayKeepsHeadingAndHeadline() {
        val text = DayWrapScript.content(DayWraps.of(emptyList(), 12 * 60), "11 月 1 日")

        assertTrue(text.startsWith("### 收工小结"))
        assertTrue(text.contains("**今天没有计划**"))
        assertFalse(text.contains("- "))
    }

    @Test
    fun keepsRowOrderFromWrap() {
        val text = DayWrapScript.content(
            wrap(plan(1, "少", minutes = 5), plan(2, "多", minutes = 45)),
            "11 月 1 日"
        )

        assertTrue(text.indexOf("多") < text.indexOf("少"))
    }

    @Test
    fun staysShortEnoughForNote() {
        val plans = (1..5).map { plan(it.toLong(), "计划$it", minutes = 30) }
        val text = DayWrapScript.content(wrap(*plans.toTypedArray()), "11 月 1 日")

        assertTrue("长度 ${text.length}", text.length < 600)
    }
}
