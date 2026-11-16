package com.focuslist.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.focuslist.app.ui.common.AlertPlayer

/**
 * 停止计划提醒：通知上的「停止响铃」按钮、通知被划掉时都会触发。
 *
 * 只做两件事：停掉正在循环的铃声 / 震动，并把对应通知取消。
 */
class PlanAlertStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            return
        }
        AlertPlayer.stop()
        val notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, 0)
        if (notifyId != 0) {
            NotificationManagerCompat.from(context).cancel(notifyId)
        }
    }

    companion object {
        /** 停止提醒动作 */
        const val ACTION = "com.focuslist.app.action.STOP_PLAN_ALERT"

        /** 需要取消的通知 ID */
        const val EXTRA_NOTIFY_ID = "notifyId"
    }
}
