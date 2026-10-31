package com.focuslist.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 日历「当日卡片页」的展开状态：由 [com.focuslist.app.ui.home.CalendarHomeScreen] 写入，
 * 供主界面判断是否处于"计时页面"，从而决定要不要隐藏 Dock（防烧屏）。
 */
object DayDetailState {

    private val mutableOpen = MutableStateFlow(false)

    /** 是否正在展示某一天的卡片页 */
    val open: StateFlow<Boolean> = mutableOpen.asStateFlow()

    /**
     * 更新展开状态。
     *
     * @param value true 表示正在看某一天的卡片页
     */
    fun set(value: Boolean) {
        mutableOpen.value = value
    }
}
