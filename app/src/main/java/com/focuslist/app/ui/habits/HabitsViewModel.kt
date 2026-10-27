package com.focuslist.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.local.dao.HabitDao
import com.focuslist.app.data.local.entity.HabitEntity
import com.focuslist.app.data.repo.HabitRepository
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 习惯页条目状态。
 *
 * @property habit 习惯实体
 * @property checkedToday 今天是否已打卡
 * @property streak 连续打卡天数
 * @property weekChecks 近 7 天打卡日期集合（当天 0 点时间戳）
 */
data class HabitItemState(
    val habit: HabitEntity,
    val checkedToday: Boolean,
    val streak: Int,
    val weekChecks: Set<Long> = emptySet()
)

/**
 * 习惯页 UI 状态。
 *
 * @property items 习惯条目列表
 */
data class HabitsUiState(val items: List<HabitItemState> = emptyList())

/**
 * 习惯页 ViewModel：习惯增删、今日打卡、连续天数与近 7 天格子数据聚合。
 *
 * @property habitRepository 习惯仓库
 * @property habitDao 习惯 DAO（区间查询直接用）
 */
class HabitsViewModel(
    private val habitRepository: HabitRepository,
    private val habitDao: HabitDao
) : ViewModel() {

    /** 连续天数缓存 */
    private val streaks = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** 近 7 天起点（含今天往前 6 天） */
    private val weekStart: Long = DateUtils.startOfDay(System.currentTimeMillis()) - 6 * DateUtils.DAY_MILLIS

    /** 聚合 UI 状态流 */
    val uiState: StateFlow<HabitsUiState> = combine(
        habitRepository.observeActive(),
        habitRepository.observeTodayChecks(),
        habitDao.observeChecksBetween(weekStart, DateUtils.startOfDay(System.currentTimeMillis())),
        streaks
    ) { habits, todayChecks, weekChecksAll, streakMap ->
        val checkedIds = todayChecks.map { it.habitId }.toSet()
        // 按习惯分组近 7 天打卡日期
        val weekMap = weekChecksAll.groupBy({ it.habitId }, { it.date })
        HabitsUiState(
            items = habits.map { habit ->
                HabitItemState(
                    habit = habit,
                    checkedToday = checkedIds.contains(habit.id),
                    streak = streakMap[habit.id] ?: 0,
                    weekChecks = (weekMap[habit.id] ?: emptyList()).toSet()
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitsUiState())

    init {
        refreshStreaks()
    }

    /**
     * 持续跟踪习惯列表并重算连续天数。
     */
    private fun refreshStreaks() {
        viewModelScope.launch {
            habitRepository.observeActive().collect { habits ->
                streaks.value = habits.associate { it.id to habitRepository.streakOf(it.id) }
            }
        }
    }

    /**
     * 新建习惯。
     *
     * @param name 习惯名
     * @param icon 图标（emoji）
     */
    fun addHabit(name: String, icon: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                habitRepository.save(HabitEntity(name = name.trim(), icon = icon))
            }
        }
    }

    /**
     * 删除习惯。
     *
     * @param habit 习惯实体
     */
    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch {
            habitRepository.delete(habit.id)
        }
    }

    /**
     * 切换今日打卡，并刷新连续天数与桌面小组件。
     *
     * @param item 习惯条目
     * @param refreshWidgets 刷新小组件回调（由界面注入）
     */
    fun toggle(item: HabitItemState, refreshWidgets: () -> Unit) {
        viewModelScope.launch {
            habitRepository.toggleToday(item.habit.id, !item.checkedToday)
            val current = streaks.value.toMutableMap()
            current[item.habit.id] = habitRepository.streakOf(item.habit.id)
            streaks.value = current
            refreshWidgets()
        }
    }
}