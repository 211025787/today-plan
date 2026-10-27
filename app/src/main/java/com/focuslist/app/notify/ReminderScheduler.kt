package com.focuslist.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 提醒调度器：基于 AlarmManager 的精确闹钟注册与取消，全离线实现。
 *
 * Android 12+ 若用户未授予"精确闹钟"权限，自动降级为非精确闹钟，避免 SecurityException 崩溃。
 *
 * @property context 应用上下文
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 为任务注册到期提醒。
     *
     * @param taskId 任务 ID
     * @param title 任务标题
     * @param triggerAt 触发时间戳（毫秒）
     */
    fun scheduleTask(taskId: Long, title: String, triggerAt: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }
        val pending = PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Android 12+ 精确闹钟需授权，未授权降级为非精确闹钟
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /**
     * 取消任务提醒。
     *
     * @param taskId 任务 ID
     */
    fun cancelTask(taskId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
        }
        val pending = PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    companion object {
        /** 任务提醒广播动作 */
        const val ACTION_TASK_REMINDER = "com.focuslist.app.action.TASK_REMINDER"

        /** 附加字段：任务 ID */
        const val EXTRA_TASK_ID = "extra_task_id"

        /** 附加字段：任务标题 */
        const val EXTRA_TITLE = "extra_title"
    }
}