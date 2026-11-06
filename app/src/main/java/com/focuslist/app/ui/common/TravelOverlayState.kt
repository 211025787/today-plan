package com.focuslist.app.ui.common

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
     * 进入旅游页之前日历页选中的那一天（null = 停在月历）。
     * 覆盖层里的只读日历页用它恢复到卡片页，露出的才是真实的那一天。
     */
    var previousSelectedDay: Long? = null

    /**
     * 打开旅游页覆盖层。
     *
     * @param id 要聚焦的行程 ID；<=0 表示沿用上次活跃行程
     */
    fun open(id: Long) {
        mutableTripId.value = id
    }

    /** 关闭覆盖层，露出下面的真实日历页 */
    fun close() {
        mutableTripId.value = null
    }
}
