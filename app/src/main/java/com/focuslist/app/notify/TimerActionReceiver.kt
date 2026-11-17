package com.focuslist.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.focuslist.app.timer.FocusTimerService
import com.focuslist.app.ui.common.AlertPlayer

/**
 * 番茄完成通知上的按钮：
 * - 开始休息：直接启动前台服务进入短休息；
 * - 再来一个：直接开始下一个番茄；
 * - 稍后 5 分钟：停止响铃与震动，5 分钟后再提醒一次。
 *
 * 处理完都会取消当前完成通知，避免用户看到「已经点过了」的旧卡片。
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notifier = Notifier(context)
        when (action) {
            TimerActions.ACTION_START_BREAK -> {
                AlertPlayer.stop()
                NotificationManagerHolder.cancelTimerFinished(context)
                startTimerService(context, action, intent)
            }

            TimerActions.ACTION_START_NEXT -> {
                AlertPlayer.stop()
                NotificationManagerHolder.cancelTimerFinished(context)
                startTimerService(context, action, intent)
            }

            TimerActions.ACTION_SNOOZE -> {
                AlertPlayer.stop()
                NotificationManagerHolder.cancelTimerFinished(context)
                scheduleSnooze(context, intent)
            }
        }
        // notifier 变量仅用于确保渠道存在（部分 ROM 首次点按钮时渠道尚未创建）
        notifier.ensureChannels()
    }

    /**
     * 启动番茄前台服务并透传任务上下文。
     *
     * @param context 上下文
     * @param action 服务动作
     * @param source 原始通知意图
     */
    private fun startTimerService(context: Context, action: String, source: Intent) {
        val serviceIntent = Intent(context, FocusTimerService::class.java).apply {
            this.action = action
            putExtra(TimerActions.EXTRA_TASK_ID, source.getLongExtra(TimerActions.EXTRA_TASK_ID, 0L))
            putExtra(TimerActions.EXTRA_TASK_TITLE, source.getStringExtra(TimerActions.EXTRA_TASK_TITLE).orEmpty())
            putExtra(TimerActions.EXTRA_DURATION, source.getIntExtra(TimerActions.EXTRA_DURATION, 0))
            putExtra(TimerActions.EXTRA_PLAN_ID, source.getLongExtra(TimerActions.EXTRA_PLAN_ID, 0L))
            putExtra(TimerActions.EXTRA_CYCLE, source.getIntExtra(TimerActions.EXTRA_CYCLE, 0))
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
            try {
                context.startService(serviceIntent)
            } catch (_: Exception) {
                // 系统限制严格时忽略，用户仍可打开应用手动继续
            }
        }
    }

    /**
     * 安排 5 分钟后的再次提醒。
     *
     * @param context 上下文
     * @param source 原始通知意图（携带标题、正文与主按钮类型）
     */
    private fun scheduleSnooze(context: Context, source: Intent) {
        val fire = Intent(context, TimerSnoozeReceiver::class.java).apply {
            action = TimerActions.ACTION_SNOOZE_FIRE
            putExtra(TimerActions.EXTRA_TITLE, source.getStringExtra(TimerActions.EXTRA_TITLE).orEmpty())
            putExtra(TimerActions.EXTRA_TEXT, source.getStringExtra(TimerActions.EXTRA_TEXT).orEmpty())
            putExtra(TimerActions.EXTRA_PRIMARY, source.getStringExtra(TimerActions.EXTRA_PRIMARY).orEmpty())
            putExtra(TimerActions.EXTRA_TASK_ID, source.getLongExtra(TimerActions.EXTRA_TASK_ID, 0L))
            putExtra(TimerActions.EXTRA_TASK_TITLE, source.getStringExtra(TimerActions.EXTRA_TASK_TITLE).orEmpty())
            putExtra(TimerActions.EXTRA_DURATION, source.getIntExtra(TimerActions.EXTRA_DURATION, 0))
            putExtra(TimerActions.EXTRA_PLAN_ID, source.getLongExtra(TimerActions.EXTRA_PLAN_ID, 0L))
            putExtra(TimerActions.EXTRA_CYCLE, source.getIntExtra(TimerActions.EXTRA_CYCLE, 0))
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_SNOOZE,
            fire,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + SNOOZE_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private companion object {
        /** 稍后提醒间隔 */
        const val SNOOZE_MS = 5 * 60 * 1000L

        /** 闹钟请求码 */
        const val REQUEST_SNOOZE = 9101
    }
}

/**
 * 单独暴露取消入口，避免通知 ID 常量散落在各处。
 */
private object NotificationManagerHolder {

    /**
     * 取消番茄完成通知。
     *
     * @param context 上下文
     */
    fun cancelTimerFinished(context: Context) {
        androidx.core.app.NotificationManagerCompat.from(context)
            .cancel(Notifier.NOTIFY_TIMER_FINISH)
    }
}
