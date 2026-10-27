package com.focuslist.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.repo.FocusRepository
import com.focuslist.app.data.repo.StatsSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 统计页 ViewModel：聚合日/周/月/累计与连续天数。
 *
 * @property focusRepository 专注记录仓库
 */
class StatsViewModel(private val focusRepository: FocusRepository) : ViewModel() {

    private val mutableSummary = MutableStateFlow(StatsSummary())

    /** 统计汇总状态流 */
    val summary: StateFlow<StatsSummary> = mutableSummary.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新拉取统计汇总。
     */
    fun refresh() {
        viewModelScope.launch {
            mutableSummary.value = focusRepository.summary()
        }
    }
}