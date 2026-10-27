package com.focuslist.app.widget.habit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.focuslist.app.FocusListApp
import com.focuslist.app.MainActivity
import com.focuslist.app.R
import com.focuslist.app.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 习惯打卡桌面小组件：展示今日习惯列表，点击圆圈即完成打卡。
 */
class HabitWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHECK_HABIT) {
            val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
            val checked = intent.getBooleanExtra(EXTRA_CHECKED, false)
            if (habitId > 0) {
                val pendingResult = goAsync()
                val app = context.applicationContext as FocusListApp
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        // 小组件点击是"取反"语义：当前已打卡则取消，否则打卡
                        app.container.habitRepository.toggleToday(habitId, !checked)
                        WidgetRefresher.refreshHabits(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        /** 小组件条目点击：切换习惯打卡 */
        const val ACTION_CHECK_HABIT = "com.focuslist.app.action.WIDGET_CHECK_HABIT"

        /** 附加字段：习惯 ID */
        const val EXTRA_HABIT_ID = "extra_habit_id"

        /** 附加字段：当前是否已打卡 */
        const val EXTRA_CHECKED = "extra_checked"

        /**
         * 渲染并更新所有实例。
         *
         * @param context 上下文
         * @param manager 小组件管理器
         * @param ids 小组件实例 ID 数组
         */
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_habit)

                // 列表数据源绑定到 RemoteViewsService
                val serviceIntent = Intent(context, HabitWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                views.setRemoteAdapter(R.id.widget_habit_list, serviceIntent)
                views.setEmptyView(R.id.widget_habit_list, R.id.widget_habit_empty)

                // 条目点击模板
                val checkIntent = Intent(context, HabitWidgetProvider::class.java).setAction(ACTION_CHECK_HABIT)
                val checkPending = PendingIntent.getBroadcast(
                    context, 0, checkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_habit_list, checkPending)

                // 点击标题打开 App
                val openIntent = PendingIntent.getActivity(
                    context, 3, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_habit_title, openIntent)

                manager.updateAppWidget(id, views)
            }
        }
    }
}