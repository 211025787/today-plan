package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 成就体系 [Achievements] 的单元测试。
 *
 * 说明：成就「一旦解锁就永久保留」，所以解锁状态只取决于传入的解锁记录，
 * 不随进度回落而失效——这一点在本测试里有多条用例专门覆盖。
 */
class AchievementTest {

    /** 固定时区下的日期串 → 本地 0 点时间戳 */
    private fun day(day: String): Long =
        LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 一份覆盖各档进度的统计快照 */
    private val mixedStats = AchievementStats(
        totalMinutes = 300,
        totalPomos = 50,
        activeDays = 40,
        longestStreak = 2,
        bestDayMinutes = 60,
        earlyMinutes = 90,
        nightMinutes = 30,
        weekendMinutes = 150
    )

    /** 一份各项都远超阈值的统计快照 */
    private val fullStats = AchievementStats(
        totalMinutes = 99_999,
        totalPomos = 9_999,
        activeDays = 9_999,
        longestStreak = 9_999,
        bestDayMinutes = 9_999,
        earlyMinutes = 9_999,
        nightMinutes = 9_999,
        weekendMinutes = 9_999
    )

    @Test
    fun 成就定义完整且唯一() {
        assertEquals(12, Achievements.ALL.size)
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.id }.toSet().size)
        Achievements.ALL.forEach { def ->
            assertTrue("目标值必须为正：${def.id}", def.target > 0)
            assertTrue("图标不能为空：${def.id}", def.emoji.isNotBlank())
            assertTrue("名称不能为空：${def.id}", def.title.isNotBlank())
            assertTrue("描述不能为空：${def.id}", def.desc.isNotBlank())
        }
    }

    @Test
    fun 取值按指标分流() {
        val stats = AchievementStats(
            totalMinutes = 1,
            totalPomos = 2,
            activeDays = 3,
            longestStreak = 4,
            bestDayMinutes = 5,
            earlyMinutes = 6,
            nightMinutes = 7,
            weekendMinutes = 8
        )
        assertEquals(1, Achievements.valueOf(stats, AchievementMetric.TOTAL_MINUTES))
        assertEquals(2, Achievements.valueOf(stats, AchievementMetric.TOTAL_POMOS))
        assertEquals(3, Achievements.valueOf(stats, AchievementMetric.ACTIVE_DAYS))
        assertEquals(4, Achievements.valueOf(stats, AchievementMetric.LONGEST_STREAK))
        assertEquals(5, Achievements.valueOf(stats, AchievementMetric.BEST_DAY))
        assertEquals(6, Achievements.valueOf(stats, AchievementMetric.EARLY_MINUTES))
        assertEquals(7, Achievements.valueOf(stats, AchievementMetric.NIGHT_MINUTES))
        assertEquals(8, Achievements.valueOf(stats, AchievementMetric.WEEKEND_MINUTES))
    }

    @Test
    fun 未达成时按实际进度展示() {
        val def = Achievements.ALL.first { it.id == "hours_10" }
        val state = Achievements.stateOf(def, mixedStats, 0L)
        assertEquals(300, state.current)
        assertEquals(600, state.target)
        assertEquals(50, state.percent)
        assertFalse(state.unlocked)
        assertEquals(0L, state.unlockedAt)
    }

    @Test
    fun 达成但未记录时不算解锁() {
        val def = Achievements.ALL.first { it.id == "first_focus" }
        val state = Achievements.stateOf(def, mixedStats, 0L)
        assertEquals(100, state.percent)
        assertFalse("解锁只认持久化记录，不认当前进度", state.unlocked)
    }

    @Test
    fun 有解锁记录即为已解锁且进度封顶() {
        val def = Achievements.ALL.first { it.id == "first_focus" }
        val state = Achievements.stateOf(def, mixedStats, 1_700_000_000_000L)
        assertTrue(state.unlocked)
        assertEquals(1_700_000_000_000L, state.unlockedAt)
        assertEquals(100, state.percent)
        assertEquals(25, state.current)
    }

    @Test
    fun 进度回落后仍保留解锁状态() {
        val def = Achievements.ALL.first { it.id == "hours_10" }
        // 历史记录被删导致进度归零，但解锁记录还在
        val state = Achievements.stateOf(def, AchievementStats(), 1_700_000_000_000L)
        assertTrue("解锁不可逆", state.unlocked)
        assertEquals(0, state.current)
        assertEquals(0, state.percent)
    }

    @Test
    fun 异常输入被夹取到合法区间() {
        val def = Achievements.ALL.first { it.id == "first_focus" }
        val negative = AchievementStats(totalMinutes = -100)
        assertEquals(0, Achievements.stateOf(def, negative, 0L).current)
        assertEquals(0, Achievements.stateOf(def, negative, 0L).percent)
        val overflow = AchievementStats(totalMinutes = Int.MAX_VALUE)
        assertEquals(25, Achievements.stateOf(def, overflow, 0L).current)
    }

    @Test
    fun 全量快照按完成度倒序() {
        val states = Achievements.snapshot(mixedStats, emptyMap())
        val ids = states.map { it.def.id }
        assertEquals(
            listOf(
                "first_focus", "pomo_10", "streak_3", "hours_10", "deep_day",
                "pomo_100", "early_bird", "weekend_go", "century", "night_owl",
                "streak_30", "hours_100"
            ),
            ids
        )
        assertTrue("全部未解锁", states.none { it.unlocked })
    }

    @Test
    fun 已解锁排在前面且按解锁时间倒序() {
        val unlocked = mapOf("night_owl" to 3000L, "streak_3" to 1000L)
        val states = Achievements.snapshot(mixedStats, unlocked)
        assertEquals(listOf("night_owl", "streak_3"), states.take(2).map { it.def.id })
        assertEquals(listOf("night_owl", "streak_3"), states.filter { it.unlocked }.map { it.def.id })
        assertEquals(12, states.size)
    }

    @Test
    fun 解锁数量与整体完成度() {
        assertEquals(0, Achievements.unlockedCount(Achievements.snapshot(AchievementStats(), emptyMap())))
        assertEquals(0, Achievements.overallPercent(emptyList()))
        val states = Achievements.snapshot(mixedStats, emptyMap())
        assertEquals(48, Achievements.overallPercent(states))

        // 解锁状态由记录决定，进度由数据决定：全解锁但数据没到顶时进度仍按实际值算
        val allUnlocked = Achievements.ALL.associate { it.id to 1000L }
        assertEquals(12, Achievements.unlockedCount(Achievements.snapshot(mixedStats, allUnlocked)))
        assertEquals(48, Achievements.overallPercent(Achievements.snapshot(mixedStats, allUnlocked)))
        assertEquals(100, Achievements.overallPercent(Achievements.snapshot(fullStats, allUnlocked)))
    }

    @Test
    fun 最接近达成的排在最前() {
        val states = Achievements.snapshot(mixedStats, emptyMap())
        val next = Achievements.nextUp(states, 3)
        assertEquals(3, next.size)
        assertTrue("只取未解锁的", next.none { it.unlocked })
        // first_focus / pomo_10 已满进度（等下次结算落库）不进榜，
        // 排在前面的是 66% 的三日连续，再往下是同为 50% 的四条按定义顺序
        assertEquals(listOf("streak_3", "hours_10", "deep_day"), next.map { it.def.id })
        assertTrue(Achievements.nextUp(states, 0).isEmpty())
        assertEquals(10, Achievements.nextUp(states, 99).size)
    }

    @Test
    fun 全部满进度时提示即将点亮() {
        // 只解锁了初次专注，其余都早已达标、只等下次结算
        val states = Achievements.snapshot(fullStats, mapOf("first_focus" to 1000L))
        assertTrue(Achievements.nextUp(states, 3).isEmpty())
        assertEquals("继续保持，新的徽章即将点亮", Achievements.motivationText(states))
    }

    @Test
    fun 百分比边界安全() {
        assertEquals(0, Achievements.percent(0, 0))
        assertEquals(0, Achievements.percent(5, 0))
        assertEquals(0, Achievements.percent(0, 100))
        assertEquals(100, Achievements.percent(100, 100))
        assertEquals(100, Achievements.percent(999, 100))
        assertEquals(66, Achievements.percent(2, 3))
    }

    @Test
    fun 单位文案按指标区分() {
        assertEquals("分钟", Achievements.unitOf(AchievementMetric.TOTAL_MINUTES))
        assertEquals("分钟", Achievements.unitOf(AchievementMetric.BEST_DAY))
        assertEquals("分钟", Achievements.unitOf(AchievementMetric.EARLY_MINUTES))
        assertEquals("分钟", Achievements.unitOf(AchievementMetric.NIGHT_MINUTES))
        assertEquals("分钟", Achievements.unitOf(AchievementMetric.WEEKEND_MINUTES))
        assertEquals("个", Achievements.unitOf(AchievementMetric.TOTAL_POMOS))
        assertEquals("天", Achievements.unitOf(AchievementMetric.ACTIVE_DAYS))
        assertEquals("天", Achievements.unitOf(AchievementMetric.LONGEST_STREAK))
    }

    @Test
    fun 进度与差值文案() {
        val states = Achievements.snapshot(mixedStats, emptyMap())
        val hours10 = states.first { it.def.id == "hours_10" }
        assertEquals("300 / 600 分钟", Achievements.progressText(hours10))
        assertEquals("还差 300 分钟", Achievements.remainingText(hours10))

        val done = states.first { it.def.id == "first_focus" }
        assertEquals("25 / 25 分钟", Achievements.progressText(done))
        assertEquals("", Achievements.remainingText(done))

        val streak = states.first { it.def.id == "streak_3" }
        assertEquals("还差 1 天", Achievements.remainingText(streak))
    }

    @Test
    fun 总览文案() {
        assertEquals("已解锁 0 / 12", Achievements.countText(Achievements.snapshot(AchievementStats(), emptyMap())))
        val all = Achievements.ALL.associate { it.id to 1000L }
        assertEquals("已解锁 12 / 12", Achievements.countText(Achievements.snapshot(mixedStats, all)))
    }

    @Test
    fun 激励文案分三种情况() {
        val none = Achievements.snapshot(AchievementStats(), emptyMap())
        assertEquals("完成第一次专注，点亮第一枚徽章", Achievements.motivationText(none))

        val all = Achievements.ALL.associate { it.id to 1000L }
        assertEquals(
            "全部成就已解锁，你是真正的专注大师",
            Achievements.motivationText(Achievements.snapshot(mixedStats, all))
        )

        val partial = Achievements.snapshot(mixedStats, mapOf("first_focus" to 1000L, "pomo_10" to 1000L))
        assertEquals("距离「三日连续」还差 1 天", Achievements.motivationText(partial))

        assertEquals("", Achievements.motivationText(emptyList()))
    }

    @Test
    fun 稀有度文案() {
        assertEquals("铜牌", Achievements.tierText(AchievementTier.BRONZE))
        assertEquals("银牌", Achievements.tierText(AchievementTier.SILVER))
        assertEquals("金牌", Achievements.tierText(AchievementTier.GOLD))
    }

    @Test
    fun 解锁日期文案() {
        assertEquals("", Achievements.unlockDateText(0L))
        assertEquals("", Achievements.unlockDateText(-1L))
        val text = Achievements.unlockDateText(1_700_000_000_000L)
        assertTrue("形如 2023.11.14，实际为 $text", text.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}")))
    }

    @Test
    fun 时段求和越界安全() {
        val byHour = MutableList(24) { 0 }
        byHour[5] = 10
        byHour[8] = 20
        byHour[21] = 30
        byHour[23] = 40
        assertEquals(30, Achievements.earlyMinutes(byHour))
        assertEquals(70, Achievements.nightMinutes(byHour))
        assertEquals(0, Achievements.rangeMinutes(emptyList(), 0, 24))
        // 22 点为 0、23 点为 40，24/25 越界被忽略
        assertEquals(40, Achievements.rangeMinutes(byHour, 22, 26))
        // 列表不足 24 项时不越界
        assertEquals(1, Achievements.rangeMinutes(listOf(1, 2), 0, 1))
    }

    @Test
    fun 周末分钟只统计周六与周日() {
        // 2026-10-26 是周一，2026-10-31 周六、2026-11-01 周日
        val byDay = mapOf(
            day("2026-10-26") to 10, // 周一
            day("2026-10-30") to 20, // 周五
            day("2026-10-31") to 60, // 周六
            day("2026-11-01") to 40 // 周日
        )
        assertEquals(100, Achievements.weekendMinutes(byDay))
        assertEquals(0, Achievements.weekendMinutes(emptyMap()))
    }

    @Test
    fun 只挑出尚未记录的新达成() {
        val stats = AchievementStats(totalMinutes = 25)
        assertEquals(listOf("first_focus"), Achievements.freshIds(stats, emptyMap()))
        assertEquals(emptyList<String>(), Achievements.freshIds(stats, mapOf("first_focus" to 1000L)))
        assertEquals(emptyList<String>(), Achievements.freshIds(AchievementStats(), emptyMap()))
    }

    @Test
    fun 合并只补缺失不覆盖() {
        assertEquals(mapOf("a" to 100L), Achievements.merge(emptyMap(), listOf("a"), 100L))
        assertEquals(
            mapOf("a" to 1L, "b" to 100L),
            Achievements.merge(mapOf("a" to 1L), listOf("a", "b"), 100L)
        )
        val source = mapOf("a" to 1L)
        assertEquals("入参为空时原样返回", source, Achievements.merge(source, emptyList(), 100L))
    }

    @Test
    fun 编解码往返一致() {
        val source = mapOf("first_focus" to 1_700_000_000_000L, "hours_10" to 1_700_000_100_000L)
        val json = Achievements.encode(source)
        assertEquals(source, Achievements.decode(json))
        assertEquals(emptyMap<String, Long>(), Achievements.decode(Achievements.encode(emptyMap())))
    }

    @Test
    fun 脏数据一律降级为空记录() {
        assertEquals(emptyMap<String, Long>(), Achievements.decode(""))
        assertEquals(emptyMap<String, Long>(), Achievements.decode("   "))
        assertEquals(emptyMap<String, Long>(), Achievements.decode("这不是 JSON"))
        assertEquals(emptyMap<String, Long>(), Achievements.decode("[]"))
        assertEquals(emptyMap<String, Long>(), Achievements.decode("{\"first_focus\":0}"))
    }
}
