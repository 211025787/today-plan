package com.focuslist.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.prefs.SettingsStore
import com.focuslist.app.data.repo.FocusRepository
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.Goal
import com.focuslist.app.util.MonthKey
import com.focuslist.app.util.MonthReport
import com.focuslist.app.util.MonthReview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 月度回顾页状态。
 *
 * @property report 当前选中月份的回顾快照，加载完成前为 null
 * @property canPrev 是否还能往前翻（不早于可回溯范围）
 * @property canNext 是否还能往后翻（不晚于本月）
 * @property loading 是否正在读取数据
 */
data class MonthReviewUiState(
    val report: MonthReport? = null,
    val canPrev: Boolean = false,
    val canNext: Boolean = false,
    val loading: Boolean = true
)

/**
 * 月度回顾 ViewModel：一次取回最近 [MONTHS_BACK] 个月的逐日数据，翻月时不再查库。
 *
 * @property focusRepository 专注记录仓库
 * @param settingsStore 设置存储，提供每日目标
 */
class MonthReviewViewModel(
    private val focusRepository: FocusRepository,
    settingsStore: SettingsStore
) : ViewModel() {

    /** 页面打开时的「今天」，翻月过程中保持不变，避免跨零点后口径漂移 */
    private val today: LocalDate = LocalDate.now()

    /** 本月 */
    val currentMonth: MonthKey = MonthKey.of(today)

    /** 可回溯的最早月份（含本月共 [MONTHS_BACK] 个月） */
    val earliestMonth: MonthKey = currentMonth.minusMonths(MONTHS_BACK - 1)

    private val selected = MutableStateFlow(currentMonth)

    private val dayMinutes = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val loading = MutableStateFlow(true)

    private val goalMinutes: StateFlow<Int> = settingsStore.settings
        .map { it.dailyGoalMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Goal.DEFAULT_MINUTES)

    /** 页面状态流 */
    val state: StateFlow<MonthReviewUiState> = combine(
        dayMinutes, goalMinutes, selected, loading
    ) { days, goal, month, busy ->
        MonthReviewUiState(
            report = MonthReview.build(month, days, goal, today),
            canPrev = month > earliestMonth,
            canNext = month < currentMonth,
            loading = busy
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MonthReviewUiState(report = MonthReview.build(currentMonth, emptyMap(), Goal.DEFAULT_MINUTES, today))
    )

    init {
        load()
    }

    /** 往前翻一个月 */
    fun selectPrev() {
        if (selected.value > earliestMonth) selected.value = selected.value.prev()
    }

    /** 往后翻一个月 */
    fun selectNext() {
        if (selected.value < currentMonth) selected.value = selected.value.next()
    }

    /** 回到本月 */
    fun selectCurrent() {
        selected.value = currentMonth
    }

    /** 重新读取逐日数据（回到页面时调用，保证刚完成的番茄能立刻反映） */
    fun load() {
        viewModelScope.launch {
            loading.value = true
            val start = earliestMonth.firstDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val end = DateUtils.addDays(DateUtils.startOfDay(System.currentTimeMillis()), 1)
            dayMinutes.value = runCatching { focusRepository.dailyMinutes(start, end) }
                .getOrDefault(emptyMap())
            loading.value = false
        }
    }

    companion object {

        /** 可回溯的月份数量 */
        const val MONTHS_BACK: Int = 12
    }
}
