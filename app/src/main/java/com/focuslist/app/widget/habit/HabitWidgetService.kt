package com.focuslist.app.widget.habit

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.focuslist.app.FocusListApp
import com.focuslist.app.R
import com.focuslist.app.data.local.entity.HabitEntity
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.runBlocking

/**
 * 习惯小组件的远程数据服务：向系统提供 [HabitWidgetFactory]。
 */
class HabitWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return HabitWidgetFactory(applicationContext)
    }
}

/**
 * 习惯列表数据工厂：同步查询习惯与今日打卡状态，生成每行 RemoteViews。
 *
 * @property context 应用上下文
 */
class HabitWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    /** 习惯快照 */
    private var habits: List<HabitEntity> = emptyList()

    /** 今日已打卡习惯 ID 集合 */
    private var checkedIds: Set<Long> = emptySet()

    /** 习惯 ID -> 连续打卡天数 */
    private var streaks: Map<Long, Int> = emptyMap()

    override fun onCreate() {
        // 无需初始化，数据在 onDataSetChanged 加载
    }

    override fun onDataSetChanged() {
        // 运行在 Binder 线程，允许同步查询
        val app = context.applicationContext as FocusListApp
        val dao = app.container.database.habitDao()
        habits = dao.getActiveSync()
        checkedIds = dao.getCheckedHabitIdsSync(DateUtils.startOfDay(System.currentTimeMillis())).toSet()
        // 逐个计算连续天数（习惯数量有限，开销可控）
        streaks = runBlocking {
            habits.associate { habit -> habit.id to app.container.habitRepository.streakOf(habit.id) }
        }
    }

    override fun onDestroy() {
        habits = emptyList()
        checkedIds = emptySet()
        streaks = emptyMap()
    }

    override fun getCount(): Int {
        return habits.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        val habit = habits[position]
        val checked = checkedIds.contains(habit.id)
        val views = RemoteViews(context.packageName, R.layout.widget_habit_item)

        views.setTextViewText(R.id.widget_habit_name, "${habit.icon} ${habit.name}")
        views.setImageViewResource(
            R.id.widget_habit_check,
            if (checked) R.drawable.ic_widget_circle_checked else R.drawable.ic_widget_circle
        )
        // 连续天数大于 0 时展示火焰文案
        val streak = streaks[habit.id] ?: 0
        views.setTextViewText(
            R.id.widget_habit_streak,
            if (streak > 0) "连续 ${streak} 天" else ""
        )

        // 填充点击模板：携带习惯 ID 与当前打卡状态
        val fillIn = Intent()
            .putExtra(HabitWidgetProvider.EXTRA_HABIT_ID, habit.id)
            .putExtra(HabitWidgetProvider.EXTRA_CHECKED, checked)
        views.setOnClickFillInIntent(R.id.widget_habit_check, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return habits[position].id
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}