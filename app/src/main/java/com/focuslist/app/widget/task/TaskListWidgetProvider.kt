package com.focuslist.app.widget.task

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
 * 今日任务列表桌面小组件：展示未完成任务，支持点击圆圈直接完成、右上角打开 App 添加。
 */
class TaskListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_TASK) {
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
            if (taskId > 0) {
                val pendingResult = goAsync()
                val app = context.applicationContext as FocusListApp
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        // 切换任务完成状态并刷新所有小组件
                        val task = app.container.database.taskDao().getById(taskId)
                        if (task != null) {
                            app.container.taskRepository.setDone(task, !task.isDone)
                        }
                        WidgetRefresher.refreshAll(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        /** 小组件条目点击：切换任务完成状态 */
        const val ACTION_TOGGLE_TASK = "com.focuslist.app.action.WIDGET_TOGGLE_TASK"

        /** 附加字段：任务 ID */
        const val EXTRA_TASK_ID = "extra_task_id"

        /**
         * 渲染并更新所有实例。
         *
         * @param context 上下文
         * @param manager 小组件管理器
         * @param ids 小组件实例 ID 数组
         */
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_task_list)

                // 列表数据源绑定到 RemoteViewsService
                val serviceIntent = Intent(context, TaskListWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                views.setRemoteAdapter(R.id.widget_task_list, serviceIntent)
                views.setEmptyView(R.id.widget_task_list, R.id.widget_task_empty)

                // 条目点击模板：由 Factory 填充具体任务 ID
                val toggleIntent = Intent(context, TaskListWidgetProvider::class.java).setAction(ACTION_TOGGLE_TASK)
                val togglePending = PendingIntent.getBroadcast(
                    context, 0, toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_task_list, togglePending)

                // 右上角加号：打开 App 首页添加任务
                val addIntent = PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_task_add, addIntent)

                manager.updateAppWidget(id, views)
            }
        }
    }
}