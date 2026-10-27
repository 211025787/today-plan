package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.HabitDao
import com.focuslist.app.data.local.entity.HabitCheckEntity
import com.focuslist.app.data.local.entity.HabitEntity
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 习惯仓库：习惯定义管理与打卡、连续天数计算。
 *
 * @property habitDao 习惯 DAO
 */
class HabitRepository(private val habitDao: HabitDao) {

    /**
     * 观察未归档习惯。
     *
     * @return 习惯流
     */
    fun observeActive(): Flow<List<HabitEntity>> {
        return habitDao.observeActive()
    }

    /**
     * 观察今天的打卡记录。
     *
     * @return 今日打卡记录流
     */
    fun observeTodayChecks(): Flow<List<HabitCheckEntity>> {
        return habitDao.observeChecksOfDay(DateUtils.startOfDay(System.currentTimeMillis()))
    }

    /**
     * 新增或更新习惯。
     *
     * @param habit 习惯实体；id 为 0 时插入
     * @return 习惯 ID
     */
    suspend fun save(habit: HabitEntity): Long {
        return if (habit.id == 0L) {
            habitDao.insert(habit)
        } else {
            habitDao.update(habit)
            habit.id
        }
    }

    /**
     * 删除习惯及其打卡记录。
     *
     * @param id 习惯 ID
     */
    suspend fun delete(id: Long) {
        habitDao.deleteChecksOf(id)
        habitDao.deleteById(id)
    }

    /**
     * 切换今天的打卡状态（已打卡则取消，未打卡则补上）。
     *
     * @param habitId 习惯 ID
     * @param checked 目标状态：true 打卡，false 取消
     */
    suspend fun toggleToday(habitId: Long, checked: Boolean) {
        val today = DateUtils.startOfDay(System.currentTimeMillis())
        if (checked) {
            habitDao.insertCheck(HabitCheckEntity(habitId = habitId, date = today))
        } else {
            habitDao.deleteCheck(habitId, today)
        }
    }

    /**
     * 计算习惯连续打卡天数：今天没打则从昨天起算。
     *
     * @param habitId 习惯 ID
     * @return 连续天数
     */
    suspend fun streakOf(habitId: Long): Int {
        val dates = habitDao.getCheckDates(habitId).toSet()
        if (dates.isEmpty()) {
            return 0
        }
        val today = DateUtils.startOfDay(System.currentTimeMillis())
        var cursor = if (dates.contains(today)) {
            today
        } else {
            today - DateUtils.DAY_MILLIS
        }
        var streak = 0
        while (dates.contains(cursor)) {
            streak++
            cursor -= DateUtils.DAY_MILLIS
        }
        return streak
    }
}