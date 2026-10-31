package com.focuslist.app.util

import com.focuslist.app.data.repo.HEATMAP_DAYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * [Heatmap] 纯逻辑单元测试：等级划分、网格排布、月份标签、总览与连续天数。
 */
class HeatmapTest {

    /** 取"今天"本地 0 点时间戳 */
    private fun todayStart(): Long = DateUtils.startOfDay(System.currentTimeMillis())

    /** 取本周（或之前最近一个）周一的 0 点时间戳 */
    private fun aMonday(): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = todayStart()
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }

    @Test
    fun levelOf_zeroOrNoData_returnsLevel0() {
        assertEquals(0, Heatmap.levelOf(0, 100))
        assertEquals(0, Heatmap.levelOf(50, 0))
        assertEquals(0, Heatmap.levelOf(-10, 100))
        assertEquals(0, Heatmap.levelOf(30, -5))
    }

    @Test
    fun levelOf_splitsIntoFourBuckets() {
        // 四档边界：ratio ∈ (0,1] -> 1，(1,2] -> 2，(2,3] -> 3，(3,4] -> 4
        assertEquals(1, Heatmap.levelOf(1, 100))
        assertEquals(1, Heatmap.levelOf(25, 100))
        assertEquals(2, Heatmap.levelOf(26, 100))
        assertEquals(2, Heatmap.levelOf(50, 100))
        assertEquals(3, Heatmap.levelOf(51, 100))
        assertEquals(3, Heatmap.levelOf(75, 100))
        assertEquals(4, Heatmap.levelOf(76, 100))
        assertEquals(4, Heatmap.levelOf(100, 100))
    }

    @Test
    fun rowOf_mondayIsZeroAndSundayIsSix() {
        val monday = aMonday()
        for (offset in 0..6) {
            assertEquals(offset, Heatmap.rowOf(DateUtils.addDays(monday, offset)))
        }
    }

    @Test
    fun buildGrid_emptyRange_returnsEmptyGrid() {
        val today = todayStart()
        val grid = Heatmap.buildGrid(DateUtils.addDays(today, 1), today, emptyMap())
        assertTrue(grid.columns.isEmpty())
        assertTrue(grid.monthLabels.isEmpty())
        assertEquals(0, grid.maxMinutes)
    }

    @Test
    fun buildGrid_twoFullWeeks_yieldsTwoColumnsWithoutPadding() {
        val monday = aMonday()
        val end = DateUtils.addDays(monday, 13)
        val data = mapOf(monday to 30, DateUtils.addDays(monday, 1) to 60)
        val grid = Heatmap.buildGrid(monday, end, data)

        assertEquals(2, grid.columns.size)
        assertEquals(7, grid.columns[0].size)
        assertEquals(7, grid.columns[1].size)
        assertTrue(grid.columns.flatten().all { it != null })
        assertEquals(monday, grid.columns[0][0]!!.dayStart)
        assertEquals(60, grid.maxMinutes)
        // 30/60 → 第二档，60/60 → 第四档
        assertEquals(2, grid.columns[0][0]!!.level)
        assertEquals(4, grid.columns[0][1]!!.level)
        assertEquals(0, grid.columns[0][2]!!.level)
    }

    @Test
    fun buildGrid_midWeekStart_padsLeadingCells() {
        val monday = aMonday()
        val wednesday = DateUtils.addDays(monday, 2)
        val end = DateUtils.addDays(wednesday, 9) // 10 天
        val grid = Heatmap.buildGrid(wednesday, end, emptyMap())

        // 前置 2 个占位（周一、周二）后共 12 格 → 2 列（7 + 5）
        assertEquals(2, grid.columns.size)
        assertEquals(2, grid.columns[0].count { it == null })
        assertEquals(wednesday, grid.columns[0][2]!!.dayStart)
        assertEquals(5, grid.columns[1].count { it != null })
        assertEquals(end, grid.columns[1][4]!!.dayStart)
    }

    @Test
    fun buildGrid_eachCellRowMatchesItsWeekday() {
        val start = DateUtils.addDays(todayStart(), -40)
        val end = todayStart()
        val grid = Heatmap.buildGrid(start, end, emptyMap())
        grid.columns.forEach { week ->
            week.forEach { cell ->
                if (cell != null) {
                    assertEquals(Heatmap.rowOf(cell.dayStart), week.indexOf(cell))
                }
            }
        }
    }

    @Test
    fun monthLabelsOf_firstColumnAlwaysLabeledWithStartMonth() {
        val start = DateUtils.startOfMonth(System.currentTimeMillis())
        val end = DateUtils.addDays(start, 120)
        val grid = Heatmap.buildGrid(start, end, emptyMap())

        assertTrue(grid.monthLabels.isNotEmpty())
        val (index, label) = grid.monthLabels.first()
        assertEquals(0, index)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals("${cal.get(Calendar.MONTH) + 1}月", label)
    }

    @Test
    fun monthLabelsOf_keepsAtLeastThreeColumnsBetweenLabels() {
        val start = DateUtils.addDays(todayStart(), -330)
        val end = todayStart()
        val grid = Heatmap.buildGrid(start, end, emptyMap())
        val indices = grid.monthLabels.map { it.first }
        indices.forEachIndexed { i, idx ->
            if (i > 0) {
                assertTrue(idx - indices[i - 1] >= 3)
            }
        }
    }

    @Test
    fun longestStreak_handlesGapsAndSingles() {
        val base = DateUtils.addDays(todayStart(), -100)
        assertEquals(0, Heatmap.longestStreak(emptySet()))
        assertEquals(1, Heatmap.longestStreak(setOf(base)))
        assertEquals(3, Heatmap.longestStreak(setOf(base, DateUtils.addDays(base, 1), DateUtils.addDays(base, 2))))
        assertEquals(
            2,
            Heatmap.longestStreak(
                setOf(base, DateUtils.addDays(base, 1), DateUtils.addDays(base, 5), DateUtils.addDays(base, 6))
            )
        )
    }

    @Test
    fun currentStreak_countsBackFromTodayOrYesterday() {
        val today = todayStart()
        val yesterday = DateUtils.addDays(today, -1)
        val before = DateUtils.addDays(today, -2)

        assertEquals(0, Heatmap.currentStreak(emptySet(), today))
        assertEquals(1, Heatmap.currentStreak(setOf(today), today))
        assertEquals(2, Heatmap.currentStreak(setOf(today, yesterday), today))
        // 今天还没专注，从昨天起算
        assertEquals(2, Heatmap.currentStreak(setOf(yesterday, before), today))
        // 断档
        assertEquals(0, Heatmap.currentStreak(setOf(before), today))
    }

    @Test
    fun overview_aggregatesYearMetrics() {
        val base = DateUtils.addDays(todayStart(), -300)
        val data = mapOf(
            base to 30,
            DateUtils.addDays(base, 1) to 60,
            DateUtils.addDays(base, 2) to 10,
            DateUtils.addDays(base, 10) to 0
        )
        val overview = Heatmap.overview(data, DateUtils.addDays(base, 2))

        assertEquals(100, overview.totalMinutes)
        assertEquals(3, overview.activeDays) // 0 分钟不计入活跃
        assertEquals(60, overview.bestDayMinutes)
        assertEquals(3, overview.longestStreak)
        assertEquals(3, overview.currentStreak)
        assertEquals(33, overview.avgMinutesPerActiveDay)
    }

    @Test
    fun overview_emptyData_returnsZeroes() {
        val overview = Heatmap.overview(emptyMap(), todayStart())
        assertEquals(0, overview.totalMinutes)
        assertEquals(0, overview.activeDays)
        assertEquals(0, overview.bestDayMinutes)
        assertEquals(0, overview.longestStreak)
        assertEquals(0, overview.currentStreak)
        assertEquals(0, overview.avgMinutesPerActiveDay)
    }

    @Test
    fun buildGrid_fullYearWindowProducesAbout53Columns() {
        val start = DateUtils.addDays(todayStart(), -(HEATMAP_DAYS - 1))
        val grid = Heatmap.buildGrid(start, todayStart(), emptyMap())
        assertTrue("列数应在 53~54 之间，实际 ${grid.columns.size}", grid.columns.size in 53..54)
        assertEquals(HEATMAP_DAYS, grid.columns.flatten().count { it != null })
    }
}
