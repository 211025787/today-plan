package com.focuslist.app.widget.stats

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.focuslist.app.FocusListApp
import com.focuslist.app.MainActivity
import com.focuslist.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 专注统计桌面小组件：展示今日分钟数、番茄数、连续天数与本周累计。
 */
class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, StatsWidgetProvider::class.java))
            updateAll(context, manager, ids)
        }
    }

    companion object {
        /** 小组件刷新动作 */
        const val ACTION_REFRESH = "com.focuslist.app.action.WIDGET_STATS_REFRESH"

        /**
         * 渲染并更新所有实例：异步聚合统计数据后回填 RemoteViews。
         *
         * @param context 上下文
         * @param manager 小组件管理器
         * @param ids 小组件实例 ID 数组
         */
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) {
                return
            }
            val app = context.applicationContext as FocusListApp
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val summary = app.container.focusRepository.summary()
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_stats)
                    views.setTextViewText(R.id.widget_stats_minutes, summary.todayMinutes.toString())
                    views.setTextViewText(R.id.widget_stats_pomos, summary.todayPomos.toString())
                    views.setTextViewText(R.id.widget_stats_streak, summary.streakDays.toString())
                    views.setTextViewText(R.id.widget_stats_week, "本周累计 ${summary.weekMinutes} 分钟")

                    // 点击卡片打开 App 统计页
                    val openIntent = PendingIntent.getActivity(
                        context, 2, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_stats_title, openIntent)
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }
}