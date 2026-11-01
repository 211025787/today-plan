package com.focuslist.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focuslist.app.util.HabitReminders

/**
 * 习惯提醒调度器：每个习惯一个**每日单次**精确闹钟。
 *
 * 不用 `setRepeating` 的原因：重复闹钟在 Doze 下会被推迟，且重启后失效；
 * 这里每次触发后再排下一天，行为可预测，也和任务提醒保持同一套实现。
 *
 * Android 12+ 未授予精确闹钟权限时自动降级为非精确闹钟，避免崩溃。
 *
 * @property context 应用上下文
 */
class HabitReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 为习惯排下一次提醒。
     *
     * @param habitId 习惯 ID（用作 PendingIntent 的 requestCode，天然去重）
     * @param name 习惯名称（通知正文用）
     * @param reminderAt 提醒时间戳（只取时、分）
     * @param now 当前时刻，便于测试
     */
    fun schedule(
        habitId: Long,
        name: String,
        reminderAt: Long,
        now: Long = System.currentTimeMillis()
    ) {
        if (habitId <= 0L || reminderAt <= 0L) {
            return
        }
        val triggerAt = HabitReminders.nextTriggerAt(reminderAt, now)
        val pending = pendingIntent(habitId, name, reminderAt)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /**
     * 取消某个习惯的提醒。
     *
     * PendingIntent 的匹配只看 action / 组件 / requestCode，不看 extras，
     * 所以这里不需要知道原来的名称与时间。
     *
     * @param habitId 习惯 ID
     */
    fun cancel(habitId: Long) {
        if (habitId <= 0L) {
            return
        }
        alarmManager.cancel(pendingIntent(habitId, "", 0L))
    }

    /**
     * 构造提醒广播。
     *
     * @param habitId 习惯 ID
     * @param name 习惯名称
     * @param reminderAt 提醒时间戳
     * @return PendingIntent
     */
    private fun pendingIntent(habitId: Long, name: String, reminderAt: Long): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_HABIT_REMINDER
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_HABIT_NAME, name)
            putExtra(EXTRA_REMINDER_AT, reminderAt)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + habitId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** 习惯提醒广播动作 */
        const val ACTION_HABIT_REMINDER = "com.focuslist.app.action.HABIT_REMINDER"

        /** 附加字段：习惯 ID */
        const val EXTRA_HABIT_ID = "extra_habit_id"

        /** 附加字段：习惯名称 */
        const val EXTRA_HABIT_NAME = "extra_habit_name"

        /** 附加字段：提醒时间戳 */
        const val EXTRA_REMINDER_AT = "extra_reminder_at"

        /** requestCode 基数：与任务提醒（直接用 taskId）错开，避免撞号 */
        private const val REQUEST_BASE = 40_000
    }
}
