package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.FocusRecordDao
import com.focuslist.app.data.local.entity.FocusRecordEntity
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.DayKeys
import com.focuslist.app.util.Heatmap
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

/**
 * 统计页汇总数据。
 *
 * @property todayMinutes 今日专注分钟数
 * @property todayPomos 今日完成番茄数
 * @property weekMinutes 本周专注分钟数
 * @property monthMinutes 本月专注分钟数
 * @property totalMinutes 累计专注分钟数
 * @property streakDays 连续专注天数（今天或昨天有番茄即连续）
 * @property weekDaily 本周 7 天每日分钟数（从周一开始）
 * @property heatRangeStart 热力图区间首日（本地 0 点）
 * @property heatRangeEnd 热力图区间末日（本地 0 点，即今天）
 * @property heatByDay 热力图数据：本地 0 点时间戳 -> 当天专注分钟数（仅含有效日）
 * @property year 年度总览指标
 */
data class StatsSummary(
    val todayMinutes: Int = 0,
    val todayPomos: Int = 0,
    val weekMinutes: Int = 0,
    val monthMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val streakDays: Int = 0,
    val weekDaily: List<Int> = List(7) { 0 },
    val heatRangeStart: Long = 0L,
    val heatRangeEnd: Long = 0L,
    val heatByDay: Map<Long, Int> = emptyMap(),
    val year: YearOverview = YearOverview()
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

        // 逐天聚合本周 7 天数据
        val weekDaily = (0 until 7).map { offset ->
            val dayStart = DateUtils.addDays(weekStart, offset)
            val dayEnd = DateUtils.addDays(dayStart, 1)
            ((recordDao.sumWorkSeconds(dayStart, dayEnd) ?: 0L) / 60L).toInt()
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
            // 连续天数基于全量活跃日，不受热力图一年窗口截断
            streakDays = Heatmap.currentStreak(
                recordDao.getActiveDayKeys().map { DayKeys.parse(it) }.toSet(),
                todayStart
            ),
            weekDaily = weekDaily,
            heatRangeStart = heatStart,
            heatRangeEnd = todayStart,
            heatByDay = heatByDay,
            year = Heatmap.overview(heatByDay, todayStart)
        )
    }
}