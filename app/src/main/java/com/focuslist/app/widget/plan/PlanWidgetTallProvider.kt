package com.focuslist.app.widget.plan

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 今日计划 · 纵向 1×2 小组件：窄卡片里显示计划图标、标题与今日完成度。
 */
class PlanWidgetTallProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val cards = PlanWidgetRender.load(context)
                appWidgetIds.forEach { id ->
                    PlanWidgetRender.updateTall(context, appWidgetManager, id, cards)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
