package com.focuslist.app.widget.plan

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 今日计划 · 横向 2×1 小组件：一眼看到今天最靠前的一条计划与完成度。
 */
class PlanWidgetWideProvider : AppWidgetProvider() {

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
                    PlanWidgetRender.updateWide(context, appWidgetManager, id, cards)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
