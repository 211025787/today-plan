package com.focuslist.app.ui.common

import android.graphics.Bitmap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 旅游规划页的**覆盖层**状态。
 *
 * 旅游页不再是一个独立导航目的地，而是叠在 NavHost 之上的一层；
 * 这样底下的日历页（月历或某一天的卡片页）始终在组合中、状态完整保留，
 * 侧滑返回时左边露出的就是真实的上一页，不需要画静态占位图。
 *
 * null = 未打开；非 null = 打开并聚焦该行程（-1 表示不指定行程）。
 */
object TravelOverlayState {

    private val mutableTripId = MutableStateFlow<Long?>(null)

    /** 当前打开的行程 ID；null 表示覆盖层关闭 */
    val tripId: StateFlow<Long?> = mutableTripId.asStateFlow()

    /**
     * 进入旅游页**之前**那一帧的真实屏幕画面。
     *
     * 侧滑返回时当前页整体右移，左边露出的就是这张图；它不是画的占位图，
     * 而是刚才用户真正看到的那张日历页 / 卡片页（含 Dock、卡片摆放与配色）。
     * 关掉覆盖层后清引用交给 GC（不主动 recycle：可能还有一帧正在绘制它）。
     */
    var previousPage: Bitmap? = null

    /**
     * 打开旅游页覆盖层。
     *
     * @param id 要聚焦的行程 ID；<=0 表示沿用上次活跃行程
     */
    fun open(id: Long, snapshot: Bitmap?) {
        previousPage = snapshot
        mutableTripId.value = id
    }

    /** 关闭覆盖层，露出下面的真实日历页 */
    fun close() {
        mutableTripId.value = null
        previousPage = null
    }
}
