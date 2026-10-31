package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [QuickPlanParser] 纯逻辑单元测试：时长、日期/相对日、跨度、标题与图标清理。
 *
 * 固定"今天"为 2026-11-01（周日），保证相对日期断言在任何时区下都成立。
 */
class QuickPlanParserTest {

    /** 固定今天：2026-11-01 本地 0 点 */
    private fun today(): Long {
        return LocalDate.of(2026, 11, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 结果里的日期转回 yyyy-MM-dd，便于断言 */
    private fun day(timeMillis: Long): String = DayKeys.format(timeMillis)

    private fun draft(input: String) = QuickPlanParser.parse(input, today())

    @Test
    fun blankInput_returnsEmptyTitleWithDefaults() {
        val d = draft("")
        assertEquals("", d.title)
        assertFalse(d.hasTitle())
        assertEquals(QuickPlanParser.DEFAULT_GOAL_MINUTES, d.dailyGoalMinutes)
        assertEquals("2026-11-01", day(d.startDate))
        // 默认跨度 30 天（含首尾）
        assertEquals("2026-11-30", day(d.endDate))
        assertEquals(30, d.days)
        assertEquals(QuickPlanParser.FALLBACK_ICON, d.icon)
    }

    @Test
    fun plainTitle_keepsTextAndDefaults() {
        val d = draft("写日记")
        assertEquals("写日记", d.title)
        assertEquals(30, d.dailyGoalMinutes)
        assertEquals("2026-11-01", day(d.startDate))
    }

    @Test
    fun minuteToken_becomesDailyGoal() {
        assertEquals(10, draft("冥想 10 分钟").dailyGoalMinutes)
        assertEquals(45, draft("健身 45min").dailyGoalMinutes)
        assertEquals(25, draft("专注 25 分").dailyGoalMinutes)
    }

    @Test
    fun hourAndMinuteTokens_areSummed() {
        assertEquals(90, draft("阅读 1小时30分").dailyGoalMinutes)
        assertEquals(60, draft("写作 1 小时").dailyGoalMinutes)
        assertEquals(120, draft("学习 2个小时").dailyGoalMinutes)
        assertEquals(30, draft("复盘 半小时").dailyGoalMinutes)
    }

    @Test
    fun goalIsClampedToReasonableRange() {
        assertEquals(QuickPlanParser.MAX_GOAL_MINUTES, draft("学习 1000 分钟").dailyGoalMinutes)
        assertEquals(QuickPlanParser.MIN_GOAL_MINUTES, draft("学习 1 分钟").dailyGoalMinutes)
    }

    @Test
    fun emojiIsTakenAsIconAndRemovedFromTitle() {
        val d = draft("读 \uD83D\uDCDA 书")
        assertEquals("\uD83D\uDCDA", d.icon)
        assertEquals("读书", d.title)
    }

    @Test
    fun iconFallsBackToKeywordGuess() {
        assertEquals("\uD83E\uDDD8", draft("冥想 10 分钟").icon)
        assertEquals("\uD83D\uDCAA", draft("健身打卡").icon)
        assertEquals("\uD83C\uDFC3", draft("跑步").icon)
        assertEquals(QuickPlanParser.FALLBACK_ICON, draft("逛超市").icon)
    }

    @Test
    fun suggestIcon_isStable() {
        assertEquals(QuickPlanParser.suggestIcon("背英语单词"), QuickPlanParser.suggestIcon("背英语单词"))
        assertTrue(QuickPlanParser.suggestIcon("写周报").isNotEmpty())
    }

    @Test
    fun relativeDayTokens_resolve() {
        assertEquals("2026-11-01", day(draft("今天起复盘").startDate))
        assertEquals("2026-11-02", day(draft("明天交周报").startDate))
        assertEquals("2026-11-03", day(draft("后天体检").startDate))
        assertEquals("2026-11-04", day(draft("大后天出差").startDate))
    }

    @Test
    fun weekdayTokens_resolveToComingDay() {
        // 2026-11-01 是周日：本周内的周一已过 → 顺延一周
        assertEquals("2026-11-02", day(draft("周一开始瑜伽").startDate))
        assertEquals("2026-11-04", day(draft("周三复盘").startDate))
        assertEquals("2026-11-07", day(draft("周末大扫除").startDate))
        // 今天就是周日 → 不跨周
        assertEquals("2026-11-01", day(draft("周日休息").startDate))
    }

    @Test
    fun nextWeekToken_addsOneWeek() {
        // 今天（11-01 周日）所在周为 10-26~11-01，"下周"即 11-02 起的那一周
        assertEquals("2026-11-02", day(draft("下周一开会").startDate))
        assertEquals("2026-11-07", day(draft("下周六爬山").startDate))
    }

    @Test
    fun monthDayToken_usesCurrentYear() {
        assertEquals("2026-11-05", day(draft("11月5日述职").startDate))
        assertEquals("2026-11-05", day(draft("11.5 述职").startDate))
        assertEquals("2026-11-05", day(draft("11/5 述职").startDate))
    }

    @Test
    fun passedMonthDay_rollsToNextYear() {
        assertEquals("2027-01-05", day(draft("1月5日健身").startDate))
    }

    @Test
    fun fullDateToken_isKeptAsIs() {
        assertEquals("2026-12-01", day(draft("2026-12-01 起学 Java").startDate))
        assertEquals("2027-03-09", day(draft("2027年3月9日答辩").startDate))
    }

    @Test
    fun explicitRange_isRespected() {
        val d = draft("11月1日-11月30日 每天健身 45 分钟")
        assertEquals("2026-11-01", day(d.startDate))
        assertEquals("2026-11-30", day(d.endDate))
        assertEquals(30, d.days)
        assertEquals(45, d.dailyGoalMinutes)
    }

    @Test
    fun toDateToken_onlySetsEnd() {
        val d = draft("每天读书 30 分钟 到年底")
        assertEquals("2026-11-01", day(d.startDate))
        assertEquals("2026-12-31", day(d.endDate))
        assertEquals(61, d.days)
        assertEquals("读书", d.title)
    }

    @Test
    fun monthEndAndYearEndTokens_resolve() {
        assertEquals("2026-11-30", day(draft("到月底整理照片").endDate))
        assertEquals("2026-11-30", day(draft("月末总结").startDate))
        assertEquals("2026-12-31", day(draft("到年末整理笔记").endDate))
    }

    @Test
    fun spanToken_shortensRange() {
        val d = draft("冥想 10 分钟 坚持 21 天")
        assertEquals("2026-11-01", day(d.startDate))
        assertEquals("2026-11-21", day(d.endDate))
        assertEquals(21, d.days)
    }

    @Test
    fun chineseNumberSpan_isSupported() {
        assertEquals("2026-11-10", day(draft("拉伸 两周打卡".replace("两周", "十天")).endDate))
        assertEquals(14, draft("拉伸 坚持 两周").days)
    }

    @Test
    fun titleStripsLeadingDailyWordsAndPunctuation() {
        assertEquals("读书", draft("每天读书 30 分钟").title)
        assertEquals("复盘", draft("每日复盘，").title)
        assertEquals("晨跑", draft("今天起 晨跑").title)
    }

    @Test
    fun titleIsCappedAtMaxLength() {
        val long = "背单词".repeat(30)
        val d = draft(long)
        assertTrue(d.title.length <= QuickPlanParser.MAX_TITLE_LENGTH)
    }

    @Test
    fun datesAreNeverReversed() {
        val d = draft("12月31日到11月1日 健身")
        assertTrue(d.startDate <= d.endDate)
        assertTrue(d.days >= 1)
    }

    @Test
    fun colorIndex_isStableAndInRange() {
        val size = 8
        repeat(5) {
            assertEquals(
                QuickPlanParser.colorIndex("健身", size),
                QuickPlanParser.colorIndex("健身", size)
            )
        }
        for (seed in listOf("", "读书", "冥想", "写日记")) {
            val idx = QuickPlanParser.colorIndex(seed, size)
            assertTrue(idx in 0 until size)
        }
        assertEquals(0, QuickPlanParser.colorIndex("任意", 0))
    }
}
