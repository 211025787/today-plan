package com.focuslist.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.focuslist.app.widget.habit.HabitWidgetProvider
import com.focuslist.app.widget.stats.StatsWidgetProvider
import com.focuslist.app.widget.task.TaskListWidgetProvider
import com.focuslist.app.widget.timer.TimerWidgetProvider

/**
 * 小组件统一刷新入口：数据或计时状态变化后调用，保证桌面卡片实时一致。
 */
object WidgetRefresher {

    /**
     * 刷新全部小组件（任务列表、番茄钟、统计、习惯）。
     *
     * @param context 上下文
     */
    fun refreshAll(context: Context) {
        refreshTasks(context)
        refreshTimer(context)
        refreshStats(context)
        refreshHabits(context)
    }

    /**
     * 刷新任务列表小组件。
     *
     * @param context 上下文
     */
    fun refreshTasks(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TaskListWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, com.focuslist.app.R.id.widget_task_list)
            TaskListWidgetProvider.updateAll(context, manager, ids)
        }
    }

    /**
     * 刷新番茄钟小组件。
     *
     * @param context 上下文
     */
    fun refreshTimer(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            TimerWidgetProvider.updateAll(context, manager, ids)
        }
    }

    /**
     * 刷新统计小组件。
     *
     * @param context 上下文
     */
    fun refreshStats(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            StatsWidgetProvider.updateAll(context, manager, ids)
        }
    }

    /**
     * 刷新习惯小组件。
     *
     * @param context 上下文
     */
    fun refreshHabits(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, HabitWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, com.focuslist.app.R.id.widget_habit_list)
            HabitWidgetProvider.updateAll(context, manager, ids)
        }
    }

    /**
     * 通过系统广播触发刷新（供小组件自身"刷新"按钮使用）。
     *
     * @param context 上下文
     * @param providerClass 目标 Provider
     */
    fun requestSystemUpdate(context: Context, providerClass: Class<*>) {
        val intent = Intent(context, providerClass).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        context.sendBroadcast(intent)
    }
}