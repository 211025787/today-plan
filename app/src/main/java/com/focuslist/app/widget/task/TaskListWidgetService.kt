package com.focuslist.app.widget.task

import android.content.Context
import android.content.Intent

import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.focuslist.app.FocusListApp
import com.focuslist.app.R
import com.focuslist.app.data.local.entity.TaskEntity

/**
 * 任务列表小组件的远程数据服务：向系统提供 [TaskWidgetFactory]。
 */
class TaskListWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TaskWidgetFactory(applicationContext)
    }
}

/**
 * 任务列表数据工厂：在 Binder 线程同步查询数据库，生成每行 RemoteViews。
 *
 * @property context 应用上下文
 */
class TaskWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    /** 当前快照数据 */
    private var tasks: List<TaskEntity> = emptyList()

    override fun onCreate() {
        // 无需初始化，数据在 onDataSetChanged 加载
    }

    override fun onDataSetChanged() {
        // 运行在 Binder 线程，允许同步查询
        val app = context.applicationContext as FocusListApp
        tasks = app.container.database.taskDao().getPendingSync()
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int {
        return tasks.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_list_item)

        views.setTextViewText(R.id.widget_item_title, task.title)
        // 番茄进度文案，例如 "2/4"
        views.setTextViewText(R.id.widget_item_pomo, "${task.completedPomos}/${task.estimatedPomos}")
        views.setImageViewResource(R.id.widget_item_check, R.drawable.ic_widget_circle)

        // 优先级色条颜色：3 红、2 橙、1 蓝、0 灰
        val color = when (task.priority) {
            3 -> 0xFFE94B3C.toInt()
            2 -> 0xFFFFB300.toInt()
            1 -> 0xFF1E88E5.toInt()
            else -> 0xFFBDBDBD.toInt()
        }
        views.setInt(R.id.widget_item_priority, "setColorFilter", color)

        // 填充点击模板：带上任务 ID，由 Provider 广播处理
        val fillIn = Intent().putExtra(TaskListWidgetProvider.EXTRA_TASK_ID, task.id)
        views.setOnClickFillInIntent(R.id.widget_item_check, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return tasks[position].id
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}