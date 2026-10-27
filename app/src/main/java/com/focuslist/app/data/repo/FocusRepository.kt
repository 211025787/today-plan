package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.FocusRecordDao
import com.focuslist.app.data.local.entity.FocusRecordEntity
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 单日统计快照。
 *
 * @property minutes 完整番茄累计分钟数
 * @property pomos 完整番茄个数
 */
data class DayStat(val minutes: Int, val pomos: Int)

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
 */
data class StatsSummary(
    val todayMinutes: Int = 0,
    val todayPomos: Int = 0,
    val weekMinutes: Int = 0,
    val monthMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val streakDays: Int = 0,
    val weekDaily: List<Int> = List(7) { 0 }
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
            val dayStart = weekStart + offset * DateUtils.DAY_MILLIS
            val dayEnd = dayStart + DateUtils.DAY_MILLIS
            ((recordDao.sumWorkSeconds(dayStart, dayEnd) ?: 0L) / 60L).toInt()
        }

        return StatsSummary(
            todayMinutes = (todaySec / 60L).toInt(),
            todayPomos = todayPomos,
            weekMinutes = (weekSec / 60L).toInt(),
            monthMinutes = (monthSec / 60L).toInt(),
            totalMinutes = (totalSec / 60L).toInt(),
            streakDays = calcStreak(recordDao.getActiveDays()),
            weekDaily = weekDaily
        )
    }

    /**
     * 计算连续专注天数：从今天（或昨天，若今天还没专注）向前逐日回溯。
     *
     * @param activeDays 全部活跃日的 0 点时间戳（倒序）
     * @return 连续天数
     */
    private fun calcStreak(activeDays: List<Long>): Int {
        if (activeDays.isEmpty()) {
            return 0
        }
        val todayStart = DateUtils.startOfDay(System.currentTimeMillis())
        val daySet = activeDays.toSet()
        // 今天没打卡则从昨天开始算
        var cursor = if (daySet.contains(todayStart)) {
            todayStart
        } else {
            todayStart - DateUtils.DAY_MILLIS
        }
        var streak = 0
        while (daySet.contains(cursor)) {
            streak++
            cursor -= DateUtils.DAY_MILLIS
        }
        return streak
    }
}