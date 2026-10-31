package com.focuslist.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 计划到达每日目标时的精确闹钟调度。
 *
 * 为什么不只靠前台服务的秒表轮询：ColorOS 等系统在后台会冻结进程，
 * 轮询可能停摆，导致"回到应用才响"。这里用 AlarmManager 在目标时刻
 * 唤醒进程投递广播，即使应用在后台也能准点提醒。
 */
object PlanGoalAlarms {

    /** 闹钟动作 */
    const val ACTION = "com.focuslist.app.action.PLAN_GOAL_REACHED"

    /** 请求码基数（叠加计划 ID 保证唯一） */
    private const val REQUEST_BASE = 7300

    /** 额外参数：计划 ID */
    const val EXTRA_PLAN_ID = "planId"

    /** 额外参数：计划标题 */
    const val EXTRA_TITLE = "title"

    /**
     * 在指定时刻安排一次提醒。
     *
     * @param context 上下文
     * @param planId 计划 ID
     * @param title 计划标题（用于通知文案）
     * @param triggerAtMs 触发时间戳（毫秒）
     */
    fun schedule(context: Context, planId: Long, title: String, triggerAtMs: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, planId, title)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                // 未授予精确闹钟权限时退化为非精确闹钟（仍可在息屏时唤醒）
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            }
        } catch (_: SecurityException) {
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            } catch (_: Exception) {
                // 系统限制严格时静默降级
            }
        }
    }

    /**
     * 取消某计划的提醒。
     *
     * @param context 上下文
     * @param planId 计划 ID
     */
    fun cancel(context: Context, planId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, planId, ""))
    }

    /**
     * 构造广播 PendingIntent（同 planId 视为同一闹钟，可被覆盖）。
     *
     * @param context 上下文
     * @param planId 计划 ID
     * @param title 计划标题
     * @return PendingIntent
     */
    private fun pendingIntent(context: Context, planId: Long, title: String): PendingIntent {
        val intent = Intent(context, PlanGoalReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            (REQUEST_BASE + planId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
