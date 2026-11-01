package com.focuslist.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.prefs.SettingsStore
import com.focuslist.app.data.repo.FocusRepository
import com.focuslist.app.data.repo.StatsSummary
import com.focuslist.app.data.repo.achievementStatsOf
import com.focuslist.app.util.Achievements
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

    /** 成就解锁记录：`成就 id -> 解锁时刻` */
    val achievements: StateFlow<Map<String, Long>> = settingsStore.achievements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** 本次刷新中新解锁的成就 id，用于「🎉 新解锁」提示 */
    private val mutableFreshIds = MutableStateFlow<List<String>>(emptyList())
    val freshIds: StateFlow<List<String>> = mutableFreshIds.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新拉取统计汇总与近期每日数据，并顺带结算成就。
     */
    fun refresh() {
        viewModelScope.launch {
            val summary = focusRepository.summary()
            mutableSummary.value = summary
            val recent = focusRepository.recentDailyMinutes()
            mutableRecentMinutes.value = recent
            mutableSuggestedGoal.value = Goal.suggest(recent)

            // 成就结算：已达成就就永久保留（入库），只有新达成的才落库，避免刷新时间被覆盖
            val stored = settingsStore.readAchievements()
            val fresh = Achievements.freshIds(achievementStatsOf(summary), stored)
            if (fresh.isNotEmpty()) {
                settingsStore.unlockAchievements(fresh)
                mutableFreshIds.value = fresh
            }
        }
    }

    /**
     * 清掉「新解锁」提示（用户看完后调用）。
     */
    fun clearFreshIds() {
        mutableFreshIds.value = emptyList()
    }
}
