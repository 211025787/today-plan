package com.focuslist.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 计划提醒的精确闹钟调度：支持「提前 N 分钟」与「到达每日目标」两种闹钟。
 *
 * 为什么不只靠前台服务的秒表轮询：ColorOS 等系统在后台会冻结进程，
 * 轮询可能停摆，导致"回到应用才响"。这里用 AlarmManager 在目标时刻
 * 唤醒进程投递广播，即使应用在后台也能准点提醒。
 *
 * 同一计划的两种闹钟用不同的 requestCode 区分，互不覆盖；取消时会一起取消。
 */
object PlanGoalAlarms {

    /** 闹钟动作 */
    const val ACTION = "com.focuslist.app.action.PLAN_GOAL_REACHED"

    /** 额外参数：计划 ID */
    const val EXTRA_PLAN_ID = "planId"

    /** 额外参数：计划标题 */
    const val EXTRA_TITLE = "title"

    /** 额外参数：闹钟类型 */
    const val EXTRA_KIND = "kind"

    /** 额外参数：提前分钟数（仅提前提醒） */
    const val EXTRA_LEAD_MINUTES = "leadMinutes"

    /** 类型：提前提醒 */
    const val KIND_LEAD = "LEAD"

    /** 类型：到达每日目标 */
    const val KIND_GOAL = "GOAL"

    /** 到达目标的请求码基数（叠加计划 ID 保证唯一） */
    private const val REQUEST_BASE_GOAL = 7300

    /** 提前提醒的请求码基数 */
    private const val REQUEST_BASE_LEAD = 8300

    /** 日志 TAG */
    private const val TAG = "FocusAlarm"

    /**
     * 安排「到达每日目标」提醒。
     *
     * @param context 上下文
     * @param planId 计划 ID
     * @param title 计划标题（用于通知文案）
     * @param triggerAtMs 触发时间戳（毫秒）
     */
    fun scheduleGoal(context: Context, planId: Long, title: String, triggerAtMs: Long) {
        schedule(context, planId, title, triggerAtMs, KIND_GOAL, 0)
    }

    /**
     * 安排「还差 N 分钟达标」提前提醒。
     *
     * @param context 上下文
     * @param planId 计划 ID
     * @param title 计划标题
     * @param leadMinutes 提前分钟数
     * @param triggerAtMs 触发时间戳（毫秒）
     */
    fun scheduleLead(context: Context, planId: Long, title: String, leadMinutes: Int, triggerAtMs: Long) {
        schedule(context, planId, title, triggerAtMs, KIND_LEAD, leadMinutes)
    }

    /**
     * 取消某计划的全部提醒（提前 + 达标）。
     *
     * @param context 上下文
     * @param planId 计划 ID
     */
    fun cancel(context: Context, planId: Long) {
        cancelKind(context, planId, KIND_GOAL)
        cancelKind(context, planId, KIND_LEAD)
    }

    /**
     * 取消某计划的指定类型提醒。
     *
     * @param context 上下文
     * @param planId 计划 ID
     * @param kind 类型：LEAD / GOAL
     */
    fun cancelKind(context: Context, planId: Long, kind: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, planId, "", kind, 0))
        Log.d(TAG, "cancel $kind plan=$planId")
    }

    /**
     * 在指定时刻安排一次闹钟。
     *
     * @param context 上下文
     * @param planId 计划 ID
     * @param title 计划标题
     * @param triggerAtMs 触发时间戳（毫秒）
     * @param kind 类型
     * @param leadMinutes 提前分钟数
     */
    private fun schedule(
        context: Context,
        planId: Long,
        title: String,
        triggerAtMs: Long,
        kind: String,
        leadMinutes: Int
    ) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, planId, title, kind, leadMinutes)
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
        Log.d(
            TAG,
            "schedule $kind plan=$planId in ${(triggerAtMs - System.currentTimeMillis()) / 1000}s" +
                " lead=$leadMinutes"
        )
    }

    /**
     * 构造广播 PendingIntent（同 planId + 同类型视为同一闹钟，可被覆盖）。
     *
     * @param context 上下文
     * @param planId 计划 ID
     * @param title 计划标题
     * @param kind 类型
     * @param leadMinutes 提前分钟数
     * @return PendingIntent
     */
    private fun pendingIntent(
        context: Context,
        planId: Long,
        title: String,
        kind: String,
        leadMinutes: Int
    ): PendingIntent {
        val intent = Intent(context, PlanGoalReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
        }
        val base = if (kind == KIND_LEAD) REQUEST_BASE_LEAD else REQUEST_BASE_GOAL
        return PendingIntent.getBroadcast(
            context,
            (base + planId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
