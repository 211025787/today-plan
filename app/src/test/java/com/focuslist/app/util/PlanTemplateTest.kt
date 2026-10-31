package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计划模板纯逻辑单元测试：构造与约束、日期换算、去重合并、排序与截断、JSON 往返。
 *
 * 日期相关断言一律走「日期串 → 本地 0 点 → 日期串」的往返，
 * 不依赖运行环境的时区（Windows 上无法用 tzset 切换时区）。
 */
class PlanTemplateTest {

    private fun template(
        title: String = "读书",
        icon: String = "📚",
        minutes: Int = 30,
        days: Int = 21,
        color: String = "#4772FA",
        useCount: Int = 1,
        createdAt: Long = 1_000L
    ) = PlanTemplate(
        id = PlanTemplates.signature(title, minutes, days, icon),
        title = title,
        icon = icon,
        dailyMinutes = minutes,
        durationDays = days,
        colorHex = color,
        useCount = useCount,
        createdAt = createdAt
    )

    @Test
    fun cleanTitle_trimsAndCaps() {
        assertEquals("读书", PlanTemplates.cleanTitle("  读书  "))
        assertEquals("读 书", PlanTemplates.cleanTitle("读   书"))
        assertEquals(PlanTemplates.TITLE_MAX, PlanTemplates.cleanTitle("计".repeat(80)).length)
        assertEquals("", PlanTemplates.cleanTitle("   "))
    }

    @Test
    fun coerce_clampsToBounds() {
        assertEquals(5, PlanTemplates.coerceMinutes(1))
        assertEquals(480, PlanTemplates.coerceMinutes(9999))
        assertEquals(45, PlanTemplates.coerceMinutes(45))
        assertEquals(1, PlanTemplates.coerceDays(0))
        assertEquals(365, PlanTemplates.coerceDays(10000))
        assertEquals(30, PlanTemplates.coerceDays(30))
    }

    @Test
    fun create_rejectsBlankTitle() {
        assertNull(PlanTemplates.create("", "🎯", 30, 30, "#4772FA"))
        assertNull(PlanTemplates.create("   ", "🎯", 30, 30, "#4772FA"))
    }

    @Test
    fun create_normalizesFields() {
        val t = PlanTemplates.create("  健身 计划  ", "💪", 3, 0, "#E94B3C", now = 123L)!!
        assertEquals("健身 计划", t.title)
        assertEquals(PlanTemplates.MIN_MINUTES, t.dailyMinutes)
        assertEquals(PlanTemplates.MIN_DAYS, t.durationDays)
        assertEquals("#E94B3C", t.colorHex)
        assertEquals(1, t.useCount)
        assertEquals(123L, t.createdAt)
        assertTrue(t.id.isNotBlank())
    }

    @Test
    fun signature_sameConfigSameId() {
        val a = PlanTemplates.signature("读书", 30, 21, "📚")
        val b = PlanTemplates.signature(" 读书 ", 30, 21, "📚")
        val c = PlanTemplates.signature("读书", 45, 21, "📚")
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun daysBetween_countsBothEnds() {
        val start = DayKeys.parse("2026-01-01")
        assertEquals(1, PlanTemplates.daysBetween(start, start))
        assertEquals(2, PlanTemplates.daysBetween(start, DayKeys.parse("2026-01-02")))
        assertEquals(31, PlanTemplates.daysBetween(start, DayKeys.parse("2026-01-31")))
        assertEquals(365, PlanTemplates.daysBetween(start, DayKeys.parse("2026-12-31")))
    }

    @Test
    fun daysBetween_reversedIsAtLeastOneDay() {
        val later = DayKeys.parse("2026-03-10")
        val earlier = DayKeys.parse("2026-03-01")
        assertEquals(PlanTemplates.MIN_DAYS, PlanTemplates.daysBetween(later, earlier))
    }

    @Test
    fun range_startsTodayAndSpansDays() {
        val (start, end) = PlanTemplates.range(7, DayKeys.parse("2026-11-01") + 15 * 3600_000L)
        assertEquals("2026-11-01", DayKeys.format(start))
        assertEquals("2026-11-07", DayKeys.format(end))
    }

    @Test
    fun range_crossesMonthSafely() {
        val (start, end) = PlanTemplates.range(35, DayKeys.parse("2026-01-20"))
        assertEquals("2026-01-20", DayKeys.format(start))
        assertEquals("2026-02-23", DayKeys.format(end))
    }

    @Test
    fun range_singleDayIsSameDay() {
        val (start, end) = PlanTemplates.range(1, DayKeys.parse("2026-06-06"))
        assertEquals(start, end)
        assertEquals("2026-06-06", DayKeys.format(start))
    }

    @Test
    fun fromPlan_derivesDurationDays() {
        val t = PlanTemplates.fromPlan(
            title = "冥想",
            icon = "🧘",
            dailyMinutes = 10,
            startAt = DayKeys.parse("2026-05-01"),
            endAt = DayKeys.parse("2026-05-21"),
            colorHex = "#43A047"
        )!!
        assertEquals(21, t.durationDays)
        assertEquals("冥想", t.title)
        assertEquals(10, t.dailyMinutes)
    }

    @Test
    fun merge_addsNewAndBumpsExisting() {
        val a = template("读书", useCount = 1, createdAt = 100)
        var list = PlanTemplates.merge(emptyList(), a)
        assertEquals(1, list.size)
        assertEquals(1, list[0].useCount)

        list = PlanTemplates.merge(list, template("读书", useCount = 1, createdAt = 999))
        assertEquals(1, list.size)
        assertEquals(2, list[0].useCount)
        // 复用不回退创建时间
        assertEquals(100L, list[0].createdAt)

        list = PlanTemplates.merge(list, template("健身", "💪", 45, 7))
        assertEquals(2, list.size)
    }

    @Test
    fun merge_dropsLeastUsedWhenOverCap() {
        var list = emptyList<PlanTemplate>()
        repeat(PlanTemplates.MAX_COUNT) { i ->
            list = PlanTemplates.merge(list, template("计划$i", minutes = 30, days = 7, useCount = 1, createdAt = i.toLong()))
        }
        assertEquals(PlanTemplates.MAX_COUNT, list.size)

        // 新的冷门模板进来，挤掉「用得最少且最旧」的那条
        list = PlanTemplates.merge(list, template("新计划", useCount = 1, createdAt = 99_999L))
        assertEquals(PlanTemplates.MAX_COUNT, list.size)
        assertTrue(list.any { it.title == "新计划" })
        assertEquals(1, list.count { it.title == "新计划" })
    }

    @Test
    fun sorted_ordersByUseThenRecency() {
        val list = PlanTemplates.sorted(
            listOf(
                template("旧常用", useCount = 5, createdAt = 10),
                template("最新", useCount = 5, createdAt = 20),
                template("用过一次", useCount = 1, createdAt = 30)
            )
        )
        assertEquals(listOf("最新", "旧常用", "用过一次"), list.map { it.title })
    }

    @Test
    fun remove_dropsById() {
        val a = template("读书")
        val b = template("健身", "💪", 45, 7)
        val list = PlanTemplates.remove(listOf(a, b), a.id)
        assertEquals(1, list.size)
        assertEquals("健身", list[0].title)
    }

    @Test
    fun describe_showsMinutesAndDays() {
        assertEquals("每天 30 分钟 · 共 21 天", PlanTemplates.describe(template("读书")))
    }

    @Test
    fun encodeDecode_roundTrips() {
        val list = listOf(template("读书"), template("健身", "💪", 45, 7, "#E94B3C", 3, 20L))
        val decoded = PlanTemplates.decode(PlanTemplates.encode(list))
        assertEquals(list, decoded)
    }

    @Test
    fun decode_toleratesBlankAndBrokenJson() {
        assertTrue(PlanTemplates.decode("").isEmpty())
        assertTrue(PlanTemplates.decode("   ").isEmpty())
        assertTrue(PlanTemplates.decode("{不是 JSON").isEmpty())
        assertTrue(PlanTemplates.decode("{\"a\":1}").isEmpty())
    }

    @Test
    fun decode_survivesUnknownFields() {
        val decoded = PlanTemplates.decode(
            """[{"id":"x","title":"跑步","icon":"🏃","dailyMinutes":20,"durationDays":7,
               "colorHex":"#FB8C00","useCount":2,"createdAt":5,"futureField":1}]""".trimIndent()
        )
        assertEquals(1, decoded.size)
        assertEquals("跑步", decoded[0].title)
        assertEquals(20, decoded[0].dailyMinutes)
        assertEquals(2, decoded[0].useCount)
    }

    @Test
    fun dayPresets_areWithinBounds() {
        assertTrue(PlanTemplates.DAY_PRESETS.all { it >= PlanTemplates.MIN_DAYS && it <= PlanTemplates.MAX_DAYS })
    }
}
