package com.focuslist.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.prefs.SettingsStore
import com.focuslist.app.data.repo.FocusRepository
import com.focuslist.app.data.repo.StatsSummary
import com.focuslist.app.util.Goal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 统计页 ViewModel：聚合日/周/月/累计与连续天数，并叠加每日目标相关指标。
 *
 * @property focusRepository 专注记录仓库
 * @property settingsStore 设置存储，提供每日目标分钟数
 */
class StatsViewModel(
    private val focusRepository: FocusRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val mutableSummary = MutableStateFlow(StatsSummary())

    /** 统计汇总状态流 */
    val summary: StateFlow<StatsSummary> = mutableSummary.asStateFlow()

    /** 每日目标分钟数 */
    val goalMinutes: StateFlow<Int> = settingsStore.settings
        .map { it.dailyGoalMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Goal.DEFAULT_MINUTES)

    private val mutableRecentMinutes = MutableStateFlow<List<Int>>(emptyList())

    /** 按近 14 天表现推算出的建议目标（分钟） */
    private val mutableSuggestedGoal = MutableStateFlow<Int?>(null)
    val suggestedGoal: StateFlow<Int?> = mutableSuggestedGoal.asStateFlow()

    /** 连续达标天数：今天还没达标不算断（从昨天起算） */
    val goalStreak: StateFlow<Int> = combine(
        mutableRecentMinutes, mutableSummary, goalMinutes
    ) { recent, summary, goal ->
        Goal.goalStreak(recent + summary.todayMinutes, goal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        refresh()
    }

    /**
     * 重新拉取统计汇总与近期每日数据。
     */
    fun refresh() {
        viewModelScope.launch {
            mutableSummary.value = focusRepository.summary()
            val recent = focusRepository.recentDailyMinutes()
            mutableRecentMinutes.value = recent
            mutableSuggestedGoal.value = Goal.suggest(recent)
        }
    }
}
