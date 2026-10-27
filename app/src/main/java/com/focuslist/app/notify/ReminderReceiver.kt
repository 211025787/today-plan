package com.focuslist.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 任务到期提醒接收器：收到闹钟广播后弹出通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderScheduler.ACTION_TASK_REMINDER) {
            val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, 0L)
            val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "你有任务到期了"
            Notifier(context).showTaskReminder(taskId, title)
        }
    }
}