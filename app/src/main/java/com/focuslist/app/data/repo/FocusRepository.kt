package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.FocusRecordDao
import com.focuslist.app.data.local.entity.FocusRecordEntity
import com.focuslist.app.util.AchievementStats
import com.focuslist.app.util.Achievements
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.DayKeys
import com.focuslist.app.util.Heatmap
import com.focuslist.app.util.Rhythm
import com.focuslist.app.util.YearOverview
import kotlinx.coroutines.flow.Flow

/**
 * 单日统计快照。
 *
 * @property minutes 完整番茄累计分钟数
 * @property pomos 完整番茄个数
 */
data class DayStat(val minutes: Int, val pomos: Int)

/** 年度热力图统计区间天数（滚动一年，含今天） */
const val HEATMAP_DAYS = 365

/** 专注节律的统计区间天数：与热力图保持一致（滚动一年，含今天） */
const val RHYTHM_DAYS = HEATMAP_DAYS

/** 每日目标建议的采样天数（不含今天） */
const val GOAL_SAMPLE_DAYS = 14

/**
 * 统计页汇总数据。
 *
 * @property todayMinutes 今日专注分钟数
 * @property todayPomos 今日完成番茄数
 * @property weekMinutes 本周专注分钟数
 * @property monthMinutes 本月专注分钟数
 * @property totalMinutes 累计专注分钟数
 * @property totalPomos 全部时间累计完成的番茄个数
 * @property allTimeActiveDays 全部时间里出现过完整番茄的自然日天数
 * @property allTimeLongestStreak 全部时间的最长连续专注天数
 * @property bestDayMinutes 历史最佳单日的专注分钟数
 * @property streakDays 连续专注天数（今天或昨天有番茄即连续）
 * @property weekDaily 本周 7 天每日分钟数（从周一开始）
 * @property lastWeekDaily 上周 7 天每日分钟数（从周一开始），用于周回顾同期对比
 * @property heatRangeStart 热力图区间首日（本地 0 点）
 * @property heatRangeEnd 热力图区间末日（本地 0 点，即今天）
 * @property heatByDay 热力图数据：本地 0 点时间戳 -> 当天专注分钟数（仅含有效日）
 * @property year 年度总览指标
 * @property rhythmByHour 专注节律：24 个小时各自的累计专注分钟数（本地时区）
 */
data class StatsSummary(
    val todayMinutes: Int = 0,
    val todayPomos: Int = 0,
    val weekMinutes: Int = 0,
    val monthMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val totalPomos: Int = 0,
    val allTimeActiveDays: Int = 0,
    val allTimeLongestStreak: Int = 0,
    val bestDayMinutes: Int = 0,
    val streakDays: Int = 0,
    val weekDaily: List<Int> = List(7) { 0 },
    val lastWeekDaily: List<Int> = List(7) { 0 },
    val heatRangeStart: Long = 0L,
    val heatRangeEnd: Long = 0L,
    val heatByDay: Map<Long, Int> = emptyMap(),
    val year: YearOverview = YearOverview(),
    val rhythmByHour: List<Int> = List(Rhythm.HOURS) { 0 }
)

/**
 * 专注记录仓库：写入记录并聚合统计结果。
 *
 * @property recordDao 专注记录 DAO
 */
class FocusRepository(private val recordDao: FocusRecordDao) {

    /**
     * 观察时间段内的专注记录。
     *
     * @param start 段起点（含）
     * @param end 段终点（不含）
     * @return 记录流
     */
    fun observeBetween(start: Long, end: Long): Flow<List<FocusRecordEntity>> {
        return recordDao.observeBetween(start, end)
    }

    /**
     * 写入一条专注记录。
     *
     * @param record 记录实体
     */
    suspend fun addRecord(record: FocusRecordEntity) {
        recordDao.insert(record)
    }

    /**
     * 计算全量统计汇总（日/周/月/总计/连续天数/本周分布）。
     *
     * @return 统计汇总
     */
    suspend fun summary(): StatsSummary {
        val now = System.currentTimeMillis()
        val todayStart = DateUtils.startOfDay(now)
        val tomorrowStart = todayStart + DateUtils.DAY_MILLIS
        val weekStart = DateUtils.startOfWeek(now)
        val monthStart = DateUtils.startOfMonth(now)

        val todaySec = recordDao.sumWorkSeconds(todayStart, tomorrowStart) ?: 0L
        val todayPomos = recordDao.countCompletedPomos(todayStart, tomorrowStart) ?: 0
        val weekSec = recordDao.sumWorkSeconds(weekStart, tomorrowStart) ?: 0L
        val monthSec = recordDao.sumWorkSeconds(monthStart, tomorrowStart) ?: 0L
        val totalSec = recordDao.sumWorkSeconds(0L, tomorrowStart) ?: 0L
        // 全量活跃日：连续天数、活跃天数、最长连续都按全部记录算，不受一年窗口截断
        val allActiveDays = recordDao.getActiveDayKeys().map { DayKeys.parse(it) }.toSet()
        val totalPomos = recordDao.countCompletedPomos(0L, tomorrowStart) ?: 0

        // 逐天聚合本周与上周共 14 天：一次分组查询代替 14 次 sumWorkSeconds
        val weekRangeStart = DateUtils.addDays(weekStart, -7)
        val weekByDay = recordDao.sumByDay(weekRangeStart, tomorrowStart)
            .map { DayKeys.parse(it.day) to (it.seconds / 60L).toInt() }
            .toMap()
        val lastWeekDaily = (0 until 7).map { offset ->
            weekByDay[DateUtils.addDays(weekRangeStart, offset)] ?: 0
        }
        val weekDaily = (0 until 7).map { offset ->
            weekByDay[DateUtils.addDays(weekStart, offset)] ?: 0
        }

        // 年度热力图：滚动一年（含今天），一次按天聚合查询，避免逐日循环查库
        val heatStart = DateUtils.addDays(todayStart, -(HEATMAP_DAYS - 1))
        val heatEndExclusive = DateUtils.addDays(todayStart, 1)
        val heatByDay = recordDao.sumByDay(heatStart, heatEndExclusive)
            .map { DayKeys.parse(it.day) to (it.seconds / 60L).toInt() }
            .toMap()

        return StatsSummary(
            todayMinutes = (todaySec / 60L).toInt(),
            todayPomos = todayPomos,
            weekMinutes = (weekSec / 60L).toInt(),
            monthMinutes = (monthSec / 60L).toInt(),
            totalMinutes = (totalSec / 60L).toInt(),
            totalPomos = totalPomos,
            allTimeActiveDays = recordDao.countActiveDays() ?: 0,
            allTimeLongestStreak = Heatmap.longestStreak(allActiveDays),
            bestDayMinutes = ((recordDao.bestDay().firstOrNull()?.seconds ?: 0L) / 60L).toInt(),
            // 连续天数基于全量活跃日，不受热力图一年窗口截断
            streakDays = Heatmap.currentStreak(allActiveDays, todayStart),
            weekDaily = weekDaily,
            lastWeekDaily = lastWeekDaily,
            heatRangeStart = heatStart,
            heatRangeEnd = todayStart,
            heatByDay = heatByDay,
            year = Heatmap.overview(heatByDay, todayStart),
            rhythmByHour = rhythmByHour(heatStart, heatEndExclusive)
        )
    }

    /**
     * 统计时间段内每个小时的专注分钟数，用于「专注节律」。
     *
     * 一次按小时聚合查询即可，不必逐小时循环查库；越界的小时会被丢弃而不是崩溃。
     *
     * @param start 段起点时间戳（含）
     * @param end 段终点时间戳（不含）
     * @return 长度 24 的列表，索引即本地小时 0~23
     */
    suspend fun rhythmByHour(start: Long, end: Long): List<Int> {
        val minutes = IntArray(Rhythm.HOURS)
        recordDao.sumByHour(start, end).forEach { total ->
            val hour = total.hour
            if (hour in 0 until Rhythm.HOURS) {
                minutes[hour] += (total.seconds / 60L).toInt()
            }
        }
        return minutes.toList()
    }

    /**
     * 统计时间段内每一天的专注分钟数，按**本地日期串**索引。
     *
     * 月度回顾要浏览多个月、逐日渲染日历，一次分组查询把整段数据取回来即可，
     * 既省查询次数，也保证「本月」与「上月同期」用的是同一套口径。
     *
     * @param start 段起点时间戳（含）
     * @param end 段终点时间戳（不含）
     * @return `yyyy-MM-dd` -> 分钟数，仅含有记录的日子
     */
    suspend fun dailyMinutes(start: Long, end: Long): Map<String, Int> {
        return recordDao.sumByDay(start, end)
            .associate { it.day to (it.seconds / 60L).toInt() }
    }

    /**
     * 取最近若干天（不含今天）的每日专注分钟数，用于推算建议的每日目标。
     *
     * @param days 采样天数，默认 [GOAL_SAMPLE_DAYS]
     * @return 按日期升序排列的每日分钟数，长度为 days
     */
    suspend fun recentDailyMinutes(days: Int = GOAL_SAMPLE_DAYS): List<Int> {
        val todayStart = DateUtils.startOfDay(System.currentTimeMillis())
        val start = DateUtils.addDays(todayStart, -days)
        val byDay = recordDao.sumByDay(start, todayStart)
            .map { DayKeys.parse(it.day) to (it.seconds / 60L).toInt() }
            .toMap()
        return (0 until days).map { offset -> byDay[DateUtils.addDays(start, offset)] ?: 0 }
    }
}

/**
 * 把统计汇总换算成成就体系所需的指标快照。
 *
 * 清晨 / 深夜直接复用节律的 24 小时分布，周末复用热力图的按天数据，不额外查库。
 * 做成顶层函数（而非仓库方法）是为了能在 JVM 单测里直接调用，不必构造 DAO。
 *
 * @param summary 统计汇总
 * @return 成就指标快照
 */
fun achievementStatsOf(summary: StatsSummary): AchievementStats {
    return AchievementStats(
        totalMinutes = summary.totalMinutes,
        totalPomos = summary.totalPomos,
        activeDays = summary.allTimeActiveDays,
        longestStreak = summary.allTimeLongestStreak,
        bestDayMinutes = summary.bestDayMinutes,
        earlyMinutes = Achievements.earlyMinutes(summary.rhythmByHour),
        nightMinutes = Achievements.nightMinutes(summary.rhythmByHour),
        weekendMinutes = Achievements.weekendMinutes(summary.heatByDay)
    )
}