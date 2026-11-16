package com.focuslist.app.widget

import android.content.Context
import android.os.SystemClock
import com.focuslist.app.widget.plan.PlanWidgetRender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件统一刷新入口：计划数据或计时状态变化后调用，保证桌面卡片实时一致。
 */
object WidgetRefresher {

    /** 应用级协程域：刷新是「读库 + 推 RemoteViews」，不阻塞调用方 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 上次刷新时间（elapsedRealtime），用于节流 */
    @Volatile
    private var lastRefreshAt = 0L

    /**
     * 刷新今日计划小组件（横向 + 纵向）。
     *
     * @param context 上下文
     */
    fun refreshPlans(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val cards = PlanWidgetRender.load(app)
                PlanWidgetRender.updateAll(app, cards)
                lastRefreshAt = SystemClock.elapsedRealtime()
            }
        }
    }

    /**
     * 节流版刷新：计时服务每秒都会调用，真正推送到桌面不必这么频繁。
     *
     * @param context 上下文
     * @param minIntervalMs 最小间隔（默认 20 秒）
     */
    fun refreshPlansThrottled(context: Context, minIntervalMs: Long = 20_000L) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRefreshAt < minIntervalMs) {
            return
        }
        lastRefreshAt = now
        refreshPlans(context)
    }
}
