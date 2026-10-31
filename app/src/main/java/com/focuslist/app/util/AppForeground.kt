package com.focuslist.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用前后台状态：由 [com.focuslist.app.FocusListApp] 注册的生命周期回调维护，
 * 供悬浮计时胶囊判断是否需要在桌面上方展示。
 */
object AppForeground {

    /** 已启动的 Activity 数量（多 Activity 场景下计数） */
    private var startedCount = 0

    private val mutableForeground = MutableStateFlow(true)

    /** 前后台状态流：切换到后台时立即发射 false，便于服务第一时间显示胶囊 */
    val state: StateFlow<Boolean> = mutableForeground.asStateFlow()

    /** 应用是否处于前台 */
    val isForeground: Boolean
        get() = mutableForeground.value

    /** Activity 进入前台 */
    fun onActivityStarted() {
        startedCount++
        mutableForeground.value = true
    }

    /** Activity 退到后台 */
    fun onActivityStopped() {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        if (startedCount == 0) {
            mutableForeground.value = false
        }
    }
}
