package com.focuslist.app.ui.plan

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.focuslist.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 计划秒表前台服务：多计划并行计时的保活进程 + 聚合计时通知。
 *
 * 服务本身不做计时计算——秒数由 [PlanTimerHolder] 基于系统时钟换算；
 * 服务职责：前台保活（息屏/后台不被杀）+ 监听计时 Map 更新聚合通知。
 */
class PlanTimerService : Service() {

    /** 服务协程域 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 是否正在监听 */
    private var observing = false

    private val notifier by lazy { Notifier(this) }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                // 5 秒时序内同步挂前台，再异步监听
                val entry = currentEntry()
                startForeground(
                    Notifier.NOTIFY_PLAN_STOPWATCH,
                    notifier.buildStopwatchNotification(entry.runningCount, entry.pausedCount, entry.baseSec, entry.anyRunning)
                )
                observeHolder()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // START_STICKY 重启恢复：从 SP 恢复全部秒表并继续前台
                PlanTimerHolder.restore(this)
                if (PlanTimerHolder.hasActive()) {
                    val entry = currentEntry()
                    startForeground(
                        Notifier.NOTIFY_PLAN_STOPWATCH,
                        notifier.buildStopwatchNotification(entry.runningCount, entry.pausedCount, entry.baseSec, entry.anyRunning)
                    )
                    observeHolder()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    /**
     * 监听秒表 Map：更新聚合通知；全部清空时关闭服务；状态变化即持久化防杀。
     */
    private fun observeHolder() {
        if (observing) {
            return
        }
        observing = true
        serviceScope.launch {
            PlanTimerHolder.timers.collectLatest { map ->
                if (map.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val entry = currentEntry()
                    notifier.updateStopwatchNotification(entry.runningCount, entry.pausedCount, entry.baseSec, entry.anyRunning)
                    PlanTimerHolder.persist(this@PlanTimerService)
                }
            }
        }
    }

    /** 聚合通知所需的瞬时数据。 */
    private data class NotificationEntry(
        /** 运行中计划数 */
        val runningCount: Int,
        /** 已暂停计划数 */
        val pausedCount: Int,
        /** 最早运行中计划的累计秒数 */
        val baseSec: Long,
        /** 是否有运行中的计时 */
        val anyRunning: Boolean
    )

    /**
     * 从 holder Map 汇总聚合通知数据。
     *
     * @return 聚合数据
     */
    private fun currentEntry(): NotificationEntry {
        val map = PlanTimerHolder.timers.value
        val running = map.values.filter { it.running }
        return NotificationEntry(
            runningCount = running.size,
            pausedCount = map.size - running.size,
            // 最早开始（startedAtMs 最小）的运行计划作为 chronometer 展示基准
            baseSec = running.minByOrNull { it.startedAtMs }?.sessionSec ?: 0L,
            anyRunning = running.isNotEmpty()
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        observing = false
        super.onDestroy()
    }

    companion object {
        /** 开始/有新计时动作 */
        const val ACTION_START = "com.focuslist.app.action.PLAN_TIMER_START"

        /** 状态更新动作（暂停等） */
        const val ACTION_UPDATE = "com.focuslist.app.action.PLAN_TIMER_UPDATE"

        /** 停止动作 */
        const val ACTION_STOP = "com.focuslist.app.action.PLAN_TIMER_STOP"

        /**
         * 确保前台服务已启动（有计时占用时调用；幂等）。
         *
         * @param context 上下文
         */
        fun ensureStarted(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlanTimerService::class.java).setAction(ACTION_START)
                )
            } catch (e: Exception) {
                // ColorOS 等定制系统对前台服务有额外限制时静默降级：计时仍由 holder 在内存中运行
            }
        }

        /**
         * 请求刷新通知。
         *
         * @param context 上下文
         */
        fun requestUpdate(context: Context) {
            try {
                context.startService(Intent(context, PlanTimerService::class.java).setAction(ACTION_UPDATE))
            } catch (e: Exception) {
                // 降级忽略
            }
        }

        /**
         * 停止并移除前台通知。
         *
         * @param context 上下文
         */
        fun stop(context: Context) {
            try {
                context.startService(Intent(context, PlanTimerService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                // 降级忽略
            }
        }
    }
}