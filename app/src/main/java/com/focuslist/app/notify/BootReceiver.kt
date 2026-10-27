package com.focuslist.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslist.app.FocusListApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机广播接收器：设备重启后重新注册所有未到期的任务提醒。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val app = context.applicationContext as FocusListApp
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    // 重排所有未来提醒
                    val scheduler = ReminderScheduler(context)

                    app.container.database.taskDao().getFutureReminders(System.currentTimeMillis()).forEach { task ->
                        task.reminderAt?.let { triggerAt ->
                            scheduler.scheduleTask(task.id, task.title, triggerAt)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}