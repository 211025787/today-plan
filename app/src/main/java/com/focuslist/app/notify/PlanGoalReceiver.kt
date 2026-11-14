package com.focuslist.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focuslist.app.FocusListApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 计划提醒广播接收器：闹钟到点时播放提示音/震动并发送通知。
 *
 * 由 [PlanGoalAlarms] 在「提前 N 分钟」或「到达每日目标」时刻触发，
 * 进程未运行时也会被系统唤起执行。正常前台计时时由 [PlanAlert] 的服务侧路径先触发，
 * 这里再触发会被去重表挡掉，不会响两遍。
 */
class PlanGoalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlanGoalAlarms.ACTION) {
            return
        }
        val title = intent.getStringExtra(PlanGoalAlarms.EXTRA_TITLE) ?: "计划"
        val planId = intent.getLongExtra(PlanGoalAlarms.EXTRA_PLAN_ID, 0L)
        val kind = intent.getStringExtra(PlanGoalAlarms.EXTRA_KIND) ?: PlanGoalAlarms.KIND_GOAL
        val leadMinutes = intent.getIntExtra(PlanGoalAlarms.EXTRA_LEAD_MINUTES, 0)
        Log.d("FocusAlarm", "fire kind=$kind plan=$planId lead=$leadMinutes")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as FocusListApp
                val settings = app.container.settingsStore.settings.first()
                PlanAlert.fire(context, settings, planId, title, kind, leadMinutes)
            } catch (_: Exception) {
                // 任何异常都不能让广播崩溃
            } finally {
                pending.finish()
            }
        }
    }
}
