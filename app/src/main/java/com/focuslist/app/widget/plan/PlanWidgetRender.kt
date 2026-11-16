package com.focuslist.app.widget.plan

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.focuslist.app.FocusListApp
import com.focuslist.app.MainActivity
import com.focuslist.app.R
import com.focuslist.app.ui.plan.PlanTimerHolder
import kotlinx.coroutines.flow.first

/**
 * 今日计划小组件的数据与渲染。
 *
 * 两个规格都展示今日全部计划（最多各显示 3 条，超出部分以「…」提示）：
 * - 2×1 横向：每条计划一行，emoji + 标题 + 完成度；
 * - 1×2 纵向：每条计划一个小块，emoji + 完成度 + 标题。
 *
 * 秒数优先取当天已落库的累计值，再补上当前正在计时的会话秒数，
 * 这样倒计时期间桌面卡片也不会停在旧进度。
 */
internal object PlanWidgetRender {

    /** 每个卡片最多展示几条计划 */
    private const val MAX_ROWS = 3

    /** 横向卡片的行容器 ID */
    private val wideRows = intArrayOf(
        R.id.widget_plan_wide_row_1,
        R.id.widget_plan_wide_row_2,
        R.id.widget_plan_wide_row_3
    )

    /** 横向卡片的 emoji ID */
    private val wideIcons = intArrayOf(
        R.id.widget_plan_wide_icon_1,
        R.id.widget_plan_wide_icon_2,
        R.id.widget_plan_wide_icon_3
    )

    /** 横向卡片的标题 ID */
    private val wideTitles = intArrayOf(
        R.id.widget_plan_wide_title_1,
        R.id.widget_plan_wide_title_2,
        R.id.widget_plan_wide_title_3
    )

    /** 横向卡片的百分比 ID */
    private val widePercents = intArrayOf(
        R.id.widget_plan_wide_percent_1,
        R.id.widget_plan_wide_percent_2,
        R.id.widget_plan_wide_percent_3
    )

    /** 纵向卡片的行容器 ID */
    private val tallRows = intArrayOf(
        R.id.widget_plan_tall_row_1,
        R.id.widget_plan_tall_row_2,
        R.id.widget_plan_tall_row_3
    )

    /** 纵向卡片的 emoji ID */
    private val tallIcons = intArrayOf(
        R.id.widget_plan_tall_icon_1,
        R.id.widget_plan_tall_icon_2,
        R.id.widget_plan_tall_icon_3
    )

    /** 纵向卡片的标题 ID */
    private val tallTitles = intArrayOf(
        R.id.widget_plan_tall_title_1,
        R.id.widget_plan_tall_title_2,
        R.id.widget_plan_tall_title_3
    )

    /** 纵向卡片的百分比 ID */
    private val tallPercents = intArrayOf(
        R.id.widget_plan_tall_percent_1,
        R.id.widget_plan_tall_percent_2,
        R.id.widget_plan_tall_percent_3
    )

    /**
     * 渲染一条计划所需的展示数据。
     *
     * @property icon 计划 emoji
     * @property title 计划标题
     * @property percent 今日完成度（0-100）
     * @property minutes 今日已投入分钟
     * @property goalMinutes 每日目标分钟
     */
    data class Card(
        val icon: String,
        val title: String,
        val percent: Int,
        val minutes: Int,
        val goalMinutes: Int
    )

    /**
     * 读取今天生效的计划与完成度。
     *
     * @param context 上下文
     * @return 计划卡片数据（按排序）
     */
    suspend fun load(context: Context): List<Card> {
        val container = (context.applicationContext as FocusListApp).container
        val plans = container.planRepository.observeActiveToday().first()
        val logs = container.planRepository.observeTodayLogs().first()
            .associateBy { it.planId }
        val timers = PlanTimerHolder.timers.value
        return plans.map { plan ->
            val saved = logs[plan.id]?.accumulatedSec ?: 0L
            val live = timers[plan.id]?.takeIf { it.running }?.sessionSec ?: 0L
            val seconds = saved + live
            val goalSec = (plan.dailyGoalMinutes * 60).coerceAtLeast(60)
            Card(
                icon = plan.icon.ifBlank { "🎯" },
                title = plan.title,
                percent = ((seconds * 100) / goalSec).toInt().coerceIn(0, 100),
                minutes = (seconds / 60).toInt(),
                goalMinutes = plan.dailyGoalMinutes
            )
        }
    }

    /**
     * 刷新所有已放置的今日计划小组件（横向 + 纵向）。
     *
     * @param context 上下文
     * @param cards 预先读取的数据
     */
    fun updateAll(context: Context, cards: List<Card>) {
        val manager = AppWidgetManager.getInstance(context)
        val wideIds = manager.getAppWidgetIds(
            ComponentName(context, PlanWidgetWideProvider::class.java)
        )
        val tallIds = manager.getAppWidgetIds(
            ComponentName(context, PlanWidgetTallProvider::class.java)
        )
        wideIds.forEach { id -> updateWide(context, manager, id, cards) }
        tallIds.forEach { id -> updateTall(context, manager, id, cards) }
    }

    /**
     * 渲染单个横向 2×1 卡片。
     *
     * @param context 上下文
     * @param manager 小组件管理器
     * @param id 小组件实例 ID
     * @param cards 数据
     */
    fun updateWide(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        cards: List<Card>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_plan_wide)
        val shown = cards.take(MAX_ROWS)
        for (index in wideRows.indices) {
            val row = wideRows[index]
            if (index < shown.size) {
                val card = shown[index]
                views.setViewVisibility(row, View.VISIBLE)
                views.setTextViewText(wideIcons[index], card.icon)
                views.setTextViewText(wideTitles[index], card.title)
                views.setTextViewText(widePercents[index], "${card.percent}%")
            } else {
                views.setViewVisibility(row, View.GONE)
            }
        }
        views.setViewVisibility(
            R.id.widget_plan_wide_empty,
            if (shown.isEmpty()) View.VISIBLE else View.GONE
        )
        views.setOnClickPendingIntent(R.id.widget_plan_wide_root, launchIntent(context))
        manager.updateAppWidget(id, views)
    }

    /**
     * 渲染单个纵向 1×2 卡片。
     *
     * @param context 上下文
     * @param manager 小组件管理器
     * @param id 小组件实例 ID
     * @param cards 数据
     */
    fun updateTall(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        cards: List<Card>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_plan_tall)
        val shown = cards.take(MAX_ROWS)
        for (index in tallRows.indices) {
            val row = tallRows[index]
            if (index < shown.size) {
                val card = shown[index]
                views.setViewVisibility(row, View.VISIBLE)
                views.setTextViewText(tallIcons[index], card.icon)
                views.setTextViewText(tallTitles[index], card.title)
                views.setTextViewText(tallPercents[index], "${card.percent}%")
            } else {
                views.setViewVisibility(row, View.GONE)
            }
        }
        views.setViewVisibility(
            R.id.widget_plan_tall_empty,
            if (shown.isEmpty()) View.VISIBLE else View.GONE
        )
        views.setOnClickPendingIntent(R.id.widget_plan_tall_root, launchIntent(context))
        manager.updateAppWidget(id, views)
    }

    /**
     * 点卡片直接回到应用首页。
     *
     * @param context 上下文
     * @return PendingIntent
     */
    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
