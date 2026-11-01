package com.focuslist.app.data.repo

import com.focuslist.app.util.AchievementStats
import com.focuslist.app.util.Rhythm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [achievementStatsOf] 的单元测试：验证「统计汇总 → 成就指标」的每个字段都接对了。
 *
 * 这里只用到纯数据的 [StatsSummary]，不碰 Room，因此能直接在 JVM 上跑。
 */
class AchievementStatsTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(day: String): Long =
        LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 构造一份只填成就相关字段的统计汇总 */
    private fun summary(
        totalMinutes: Int = 0,
        totalPomos: Int = 0,
        activeDays: Int = 0,
        longestStreak: Int = 0,
        bestDay: Int = 0,
        byHour: List<Int> = List(Rhythm.HOURS) { 0 },
        byDay: Map<Long, Int> = emptyMap()
    ): StatsSummary = StatsSummary(
        totalMinutes = totalMinutes,
        totalPomos = totalPomos,
        allTimeActiveDays = activeDays,
        allTimeLongestStreak = longestStreak,
        bestDayMinutes = bestDay,
        rhythmByHour = byHour,
        heatByDay = byDay
    )

    @Test
    fun 全量字段逐一对应() {
        val stats = achievementStatsOf(
            summary(totalMinutes = 120, totalPomos = 8, activeDays = 30, longestStreak = 5, bestDay = 60)
        )
        assertEquals(
            AchievementStats(
                totalMinutes = 120,
                totalPomos = 8,
                activeDays = 30,
                longestStreak = 5,
                bestDayMinutes = 60
            ),
            stats
        )
    }

    @Test
    fun 清晨与深夜取自节律分布() {
        val byHour = MutableList(Rhythm.HOURS) { 0 }
        byHour[5] = 60
        byHour[8] = 30
        byHour[21] = 45
        byHour[23] = 15
        val stats = achievementStatsOf(summary(byHour = byHour))
        assertEquals(90, stats.earlyMinutes)
        assertEquals(60, stats.nightMinutes)
    }

    @Test
    fun 周末取自热力图按天数据() {
        // 2026-10-30 周五、10-31 周六、11-01 周日
        val byDay = mapOf(
            day("2026-10-30") to 25,
            day("2026-10-31") to 60,
            day("2026-11-01") to 40
        )
        val stats = achievementStatsOf(summary(byDay = byDay))
        assertEquals(100, stats.weekendMinutes)
    }

    @Test
    fun 空汇总得到全零快照() {
        assertEquals(AchievementStats(), achievementStatsOf(StatsSummary()))
    }
}
