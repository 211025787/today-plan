package com.focuslist.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 时间轴上的一个计划区间。
 *
 * 刻意不依赖 Room 实体：区间来自 [com.focuslist.app.data.local.entity.PlanEntity]，
 * 但布局算法只认起止日，便于在 JVM 单测里直接构造。
 *
 * @property id 计划 ID
 * @property title 计划名称
 * @property icon 图标 emoji
 * @property colorHex 主题色 "#RRGGBB"
 * @property start 生效起始日（0 点，含）
 * @property end 生效截止日（0 点，含）
 */
data class PlanSpan(
    val id: Long,
    val title: String,
    val icon: String = "🎯",
    val colorHex: String = "#4772FA",
    val start: Long,
    val end: Long
)

/**
 * 布局结果：某个计划落在网格的哪几列、第几行。
 *
 * @property plan 计划区间
 * @property startCol 起始列（0-based）
 * @property spanCols 占几列（至少 1）
 * @property row 第几行（0-based）
 */
data class TimelineBar(
    val plan: PlanSpan,
    val startCol: Int,
    val spanCols: Int,
    val row: Int
)

/**
 * 时间轴窗口：从某个周一 0 点开始、连续 [weeks] 周。
 *
 * @property start 窗口首日（周一 0 点）
 * @property weeks 周数
 */
data class TimelineWindow(val start: Long, val weeks: Int) {

    /** 窗口末日（最后一周周日 0 点，含） */
    val end: Long get() = DateUtils.addDays(start, weeks * 7 - 1)

    /**
     * 每一周的起点（周一 0 点），用于表头刻度。
     *
     * @return 长度为 [weeks] 的列表
     */
    fun columnStarts(): List<Long> = (0 until weeks).map { DateUtils.addDays(start, it * 7) }
}

/**
 * 计划时间轴：把「一段时间里有哪些计划、它们怎么重叠」画成甘特式条带。
 *
 * 这是应用里唯一的**规划视角**——其余页面都在看已经发生的事，
 * 这张图看的是接下来要不要再接一个计划。
 *
 * 设计约定：
 * - 一格 = 一周，按周取整：周三开始、周五结束的计划占满那一周，不做天级精度，
 *   否则 6 周窗口在手机上会挤成一条线；
 * - **重叠才换行**，不重叠的计划共用第一行，行数自然等于「同一时刻最多并行几个计划」；
 * - 窗口外的计划直接丢弃，只保留与窗口有交集的部分并裁剪到边缘。
 */
object PlanTimeline {

    /** 默认窗口周数：前 2 周 + 本周 + 后 3 周 */
    const val DEFAULT_WEEKS = 6

    /** 默认窗口里「今天」之前的周数 */
    const val DEFAULT_WEEKS_BACK = 2

    /** 一天的毫秒数 */
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * 构造以今天为锚点的时间轴窗口。
     *
     * @param today 今天（任意时刻）
     * @param weeks 周数，非正数按 1 处理
     * @param weeksBack 今天之前包含几周（默认 2，即今天落在第 3 列）
     * @return 窗口
     */
    fun window(today: Long, weeks: Int = DEFAULT_WEEKS, weeksBack: Int = DEFAULT_WEEKS_BACK): TimelineWindow {
        val safeWeeks = if (weeks > 0) weeks else 1
        val back = weeksBack.coerceIn(0, safeWeeks - 1)
        val start = DateUtils.addDays(DateUtils.startOfWeek(today), -back * 7)
        return TimelineWindow(start = start, weeks = safeWeeks)
    }

    /**
     * 计算布局：裁剪 + 排序 + 贪心分行。
     *
     * @param spans 全部计划区间
     * @param window 时间轴窗口
     * @return 按起始列升序排列的条带
     */
    fun layout(spans: List<PlanSpan>, window: TimelineWindow): List<TimelineBar> {
        val placed = spans
            .filter { it.end >= window.start && it.start <= window.end }
            .map { span ->
                val from = maxOf(span.start, window.start)
                val to = minOf(span.end, window.end)
                Triple(span, columnOf(window, from), columnOf(window, to))
            }
            // 起始列一致时按标题排序，保证同样数据每次画出来都一样
            .sortedWith(compareBy({ it.second }, { it.first.title }, { it.first.id }))

        // 每一行已占用的列区间
        val rows = mutableListOf<MutableList<IntRange>>()
        return placed.map { (span, startCol, endCol) ->
            val occupied = startCol..endCol
            var row = rows.indexOfFirst { ranges -> ranges.none { it.first <= endCol && it.last >= startCol } }
            if (row < 0) {
                rows.add(mutableListOf())
                row = rows.size - 1
            }
            rows[row].add(occupied)
            TimelineBar(plan = span, startCol = startCol, spanCols = endCol - startCol + 1, row = row)
        }
    }

    /**
     * 某一天落在第几列（按周取整，越界会被夹到 0..weeks-1）。
     *
     * @param window 窗口
     * @param day 任意一天
     * @return 列下标
     */
    fun columnOf(window: TimelineWindow, day: Long): Int {
        val days = ((day - window.start) / DAY_MILLIS).toInt()
        return (days / 7).coerceIn(0, window.weeks - 1)
    }

    /**
     * 条带的行数（没有条带时为 0）。
     *
     * @param bars 布局结果
     * @return 行数
     */
    fun rowCount(bars: List<TimelineBar>): Int {
        return bars.maxOfOrNull { it.row + 1 } ?: 0
    }

    /**
     * 列标签，如「10/13」。
     *
     * @param day 该周周一
     * @return 月/日
     */
    fun label(day: Long): String {
        return SimpleDateFormat("M/d", Locale.CHINA).format(Date(day))
    }

    /**
     * 窗口区间文案，如「10月13日 - 11月23日」。
     *
     * @param window 窗口
     * @return 区间文案
     */
    fun rangeText(window: TimelineWindow): String {
        val fmt = SimpleDateFormat("M月d日", Locale.CHINA)
        return "${fmt.format(Date(window.start))} - ${fmt.format(Date(window.end))}"
    }

    /**
     * 某个计划在窗口内的实际起止（用于点选后的说明文案）。
     *
     * @param window 窗口
     * @param bar 条带
     * @return 起止日文案，如「10月13日 - 11月2日」
     */
    fun barRangeText(window: TimelineWindow, bar: TimelineBar): String {
        val fmt = SimpleDateFormat("M月d日", Locale.CHINA)
        val from = maxOf(bar.plan.start, window.start)
        val to = minOf(bar.plan.end, window.end)
        return "${fmt.format(Date(from))} - ${fmt.format(Date(to))}"
    }

    /**
     * 今天是否落在窗口内。
     *
     * @param window 窗口
     * @param today 今天 0 点
     * @return 是否在窗口内
     */
    fun contains(window: TimelineWindow, today: Long): Boolean {
        return today in window.start..window.end
    }

    /**
     * 今天是第几列（不在窗口内时返回 -1）。
     *
     * @param window 窗口
     * @param today 今天 0 点
     * @return 列下标或 -1
     */
    fun todayColumn(window: TimelineWindow, today: Long): Int {
        return if (contains(window, today)) columnOf(window, today) else -1
    }

    /**
     * 某天所在周的周一（供点击时对齐）。
     *
     * @param day 任意一天
     * @return 周一 0 点
     */
    fun weekStartOf(day: Long): Long {
        return DateUtils.startOfWeek(day)
    }

    /**
     * 数字化的月份，便于表头判断跨月。
     *
     * @param day 任意一天
     * @return 月份 1-12
     */
    fun monthOf(day: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = day
        return cal.get(Calendar.MONTH) + 1
    }
}
