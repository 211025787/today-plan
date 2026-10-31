package com.focuslist.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslist.app.FocusListApp
import com.focuslist.app.ui.common.AlertPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 计划到达每日目标广播接收器：播放提示音/震动并发通知。
 *
 * 由 [PlanGoalAlarms] 在目标时刻触发，进程未运行时也会被系统唤起执行。
 */
class PlanGoalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlanGoalAlarms.ACTION) {
            return
        }
        val title = intent.getStringExtra(PlanGoalAlarms.EXTRA_TITLE) ?: "计划"
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as FocusListApp
                val settings = app.container.settingsStore.settings.first()
                if (settings.planReachGoalSound || settings.planReachGoalVibrate) {
                    AlertPlayer.play(
                        context = context,
                        chime = settings.chimeType,
                        sound = settings.planReachGoalSound,
                        vibrate = settings.planReachGoalVibrate,
                        soundSec = settings.alertSoundSec,
                        vibrateSec = settings.alertVibrateSec
                    )
                }
                Notifier(context).ensureChannels()
                Notifier(context).showPlanGoal(title)
            } catch (_: Exception) {
                // 任何异常都不能让广播崩溃
            } finally {
                pending.finish()
            }
        }
    }
}
