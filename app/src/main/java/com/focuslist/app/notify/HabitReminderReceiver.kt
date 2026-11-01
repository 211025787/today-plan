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
 * 习惯提醒广播接收器：响铃后发通知，并把下一次提醒排到明天。
 *
 * 是否继续提醒**以数据库为准**：习惯被删除或提醒被清空时，即使旧闹钟已经触发，
 * 这里也不会再通知、更不会把闹钟排回去。
 */
class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HabitReminderScheduler.ACTION_HABIT_REMINDER) {
            return
        }
        val habitId = intent.getLongExtra(HabitReminderScheduler.EXTRA_HABIT_ID, 0L)
        if (habitId <= 0L) {
            return
        }
        val pendingResult = goAsync()
        val app = context.applicationContext as FocusListApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val habit = app.container.database.habitDao()
                    .getActiveSync()
                    .firstOrNull { it.id == habitId }
                val reminderAt = habit?.reminderAt
                if (habit == null || reminderAt == null || reminderAt <= 0L) {
                    return@launch
                }
                val notifier = Notifier(context)
                notifier.ensureChannels()
                notifier.showHabitReminder(habit.id, habit.name)
                // 排下一天，同一个 requestCode 会覆盖旧的 PendingIntent
                HabitReminderScheduler(context).schedule(habit.id, habit.name, reminderAt)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
