package com.focuslist.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.focuslist.app.MainActivity
import com.focuslist.app.R

/**
 * 通知中心封装：渠道创建与各类通知的构造发送。
 *
 * @property context 应用上下文
 */
class Notifier(private val context: Context) {

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * 确保所有通知渠道已创建（幂等，可重复调用）。
     *
     * 计时渠道用 IMPORTANCE_DEFAULT：保证状态栏图标可见（OPPO ColorOS 状态栏计时展示的前提）。
     */
    fun ensureChannels() {
        val reminder = NotificationChannel(CHANNEL_REMINDER, "任务提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "任务到期与习惯打卡提醒"
        }
        val timer = NotificationChannel(CHANNEL_TIMER, "计时", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "计划秒表与番茄钟运行状态常驻通知"
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(reminder, timer))
    }

    /**
     * 发送任务到期提醒。
     *
     * @param taskId 任务 ID（用作通知 ID，避免重复）
     * @param title 任务标题
     */
    fun showTaskReminder(taskId: Long, title: String) {
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_widget_circle_checked)
            .setContentTitle("任务到期")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        manager.notify((NOTIFY_TASK_BASE + taskId).toInt(), notification)
    }

    /**
     * 发送计时结束提醒（番茄完成/休息结束）。
     *
     * @param title 标题
     * @param text 正文
     */
    fun showTimerFinished(title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_widget_circle_checked)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFY_TIMER_FINISH, notification)
    }

    /**
     * 构造计时服务常驻通知。
     *
     * @param phaseText 阶段文案，如"专注中"
     * @param timeText 剩余时间文案，如"25:00"
     * @return 前台服务通知
     */
    fun buildTimerNotification(phaseText: String, timeText: String): Notification {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction("com.focuslist.app.action.OPEN_TIMER"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_widget_play)
            .setContentTitle(phaseText)
            .setContentText(timeText)
            .setOngoing(true)
            .setContentIntent(intent)
            .build()
    }

    /**
     * 更新计时常驻通知内容。
     *
     * @param phaseText 阶段文案
     * @param timeText 剩余时间文案
     */
    fun updateTimerNotification(phaseText: String, timeText: String) {
        manager.notify(NOTIFY_TIMER_RUNNING, buildTimerNotification(phaseText, timeText))
    }

    /**
     * 构造计划秒表聚合通知（系统计时器样式）：setUsesChronometer 让系统自走正计时，
     * 状态栏/控制中心可见。多计划并行时标题显示数量，chronometer 展示最早一个运行中的计时。
     *
     * 注意：OPPO ColorOS 流体云（灵动岛）目前不对第三方普通应用开放，本通知是第三方
     * 能达到的最大可见形态（状态栏计时胶囊/通知栏计时器）。
     *
     * @param runningCount 运行中的计划数
     * @param pausedCount 已暂停的计划数
     * @param baseSessionSec 最早一个运行中计划的累计秒数（chronometer 基准用）
     * @param anyRunning 是否有运行中的计时
     * @return 前台服务通知
     */
    fun buildStopwatchNotification(runningCount: Int, pausedCount: Int, baseSessionSec: Long, anyRunning: Boolean): Notification {
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val total = runningCount + pausedCount
        val title = when {
            runningCount > 0 -> "$runningCount 个计划计时中"
            else -> "$total 个计划已暂停"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_widget_play)
            .setContentTitle(title)
            .setOngoing(true)
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (anyRunning) {
            // 系统自走计时：when 设为"开始时刻"（当前时间 - 已累计秒数）
            builder.setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - baseSessionSec * 1000L)
                .setContentText(if (pausedCount > 0) "另有 $pausedCount 个已暂停" else "点击返回应用")
        } else {
            builder.setUsesChronometer(false)
                .setContentText("已全部暂停，点击返回继续")
        }
        return builder.build()
    }

    /**
     * 更新秒表聚合通知。
     *
     * @param runningCount 运行中的计划数
     * @param pausedCount 已暂停的计划数
     * @param baseSessionSec 最早运行中计划的累计秒数
     * @param anyRunning 是否有运行中的计时
     */
    fun updateStopwatchNotification(runningCount: Int, pausedCount: Int, baseSessionSec: Long, anyRunning: Boolean) {
        manager.notify(NOTIFY_PLAN_STOPWATCH, buildStopwatchNotification(runningCount, pausedCount, baseSessionSec, anyRunning))
    }


    companion object {
        /** 提醒渠道 ID */
        const val CHANNEL_REMINDER = "channel_reminder"

        /** 计时渠道 ID */
        const val CHANNEL_TIMER = "channel_timer"

        /** 计时服务前台通知 ID */
        const val NOTIFY_TIMER_RUNNING = 1001

        /** 计时结束通知 ID */
        const val NOTIFY_TIMER_FINISH = 1002

        /** 计划秒表前台通知 ID */
        const val NOTIFY_PLAN_STOPWATCH = 1003

        /** 任务提醒通知 ID 基数（叠加任务 ID） */
        const val NOTIFY_TASK_BASE = 2000L
    }
}