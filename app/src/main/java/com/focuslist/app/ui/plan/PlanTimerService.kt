package com.focuslist.app.ui.plan

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.focuslist.app.FocusListApp
import com.focuslist.app.notify.Notifier
import com.focuslist.app.notify.PlanGoalAlarms
import com.focuslist.app.util.AppForeground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 计划秒表前台服务：多计划并行计时的保活进程 + 聚合计时通知 + 悬浮胶囊。
 *
 * 服务本身不做计时计算——秒数由 [PlanTimerHolder] 基于系统时钟换算；
 * 服务职责：
 * 1. 前台保活（息屏/后台不被杀）+ 监听计时 Map 更新聚合通知；
 * 2. 为每个运行中的计划安排「到达每日目标」的精确闹钟（后台也能准点提醒）；
 * 3. 应用退到后台时展示悬浮玻璃胶囊（需悬浮窗权限）。
 */
class PlanTimerService : Service() {

    /** 服务协程域 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 是否正在监听 */
    private var observing = false

    /** 已安排的闹钟触发时刻：planId -> 时间戳 */
    private val scheduledGoalAt = mutableMapOf<Long, Long>()

    private val notifier by lazy { Notifier(this) }

    /** 悬浮计时胶囊 */
    private val capsule by lazy { TimerCapsule(this) }

    /** 依赖容器：读取计划目标与设置开关 */
    private val container by lazy { (application as FocusListApp).container }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // 胶囊拖动松手后持久化位置，下次展示沿用
        capsule.onPositionChanged = { x, y ->
            serviceScope.launch {
                container.settingsStore.updateCapsulePosition(x, y)
            }
        }
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
     * 监听秒表 Map：更新聚合通知、同步目标闹钟与悬浮胶囊；全部清空时关闭服务。
     */
    private fun observeHolder() {
        if (observing) {
            return
        }
        observing = true
        // 前后台切换时立刻刷新胶囊，不必等下一次秒表刷新
        serviceScope.launch {
            AppForeground.state.collect {
                try {
                    updateCapsule(PlanTimerHolder.timers.value)
                } catch (_: Exception) {
                    // 单次刷新失败不影响后续
                }
            }
        }
        serviceScope.launch {
            // 用 collect 而非 collectLatest：每秒的状态更新都必须完整走完闹钟/胶囊同步，
            // 避免上一次同步被取消导致漏排闹钟
            PlanTimerHolder.timers.collect { map ->
                try {
                    if (map.isEmpty()) {
                        capsule.hide()
                        scheduledGoalAt.keys.toList().forEach { PlanGoalAlarms.cancel(this@PlanTimerService, it) }
                        scheduledGoalAt.clear()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val entry = currentEntry()
                        notifier.updateStopwatchNotification(entry.runningCount, entry.pausedCount, entry.baseSec, entry.anyRunning)
                        PlanTimerHolder.persist(this@PlanTimerService)
                        syncGoalAlarms(map)
                        updateCapsule(map)
                    }
                } catch (_: Exception) {
                    // 数据库或系统限制导致单次刷新失败时保持监听
                }
            }
        }
    }

    /**
     * 同步「到达每日目标」闹钟：运行中的计划按剩余秒数安排精确闹钟，暂停/超出的取消。
     *
     * 只在触发时刻变化超过 5 秒时才重新安排，避免每秒重复设置闹钟。
     *
     * @param map 当前秒表 Map
     */
    private suspend fun syncGoalAlarms(map: Map<Long, PlanTimerEntry>) {
        map.forEach { (planId, entry) ->
            if (!entry.running) {
                if (scheduledGoalAt.remove(planId) != null) {
                    PlanGoalAlarms.cancel(this, planId)
                }
                return@forEach
            }
            val plan = container.planRepository.getPlan(planId) ?: return@forEach
            val goalSec = plan.dailyGoalMinutes * 60L
            if (goalSec <= 0) {
                return@forEach
            }
            val savedSec = container.planRepository.getTodaySeconds(planId)
            val remainSec = goalSec - (savedSec + entry.sessionSec)
            if (remainSec <= 0) {
                // 已达成：不需要再提醒
                if (scheduledGoalAt.remove(planId) != null) {
                    PlanGoalAlarms.cancel(this, planId)
                }
                return@forEach
            }
            val triggerAt = System.currentTimeMillis() + remainSec * 1000L
            val previous = scheduledGoalAt[planId]
            if (previous == null || abs(previous - triggerAt) > 5000L) {
                PlanGoalAlarms.schedule(this, planId, plan.title, triggerAt)
                scheduledGoalAt[planId] = triggerAt
            }
        }
        // 已不存在的计划取消闹钟
        (scheduledGoalAt.keys - map.keys).toList().forEach { planId ->
            PlanGoalAlarms.cancel(this, planId)
            scheduledGoalAt.remove(planId)
        }
    }

    /**
     * 更新悬浮胶囊：仅在已开启、有运行中的计时、应用在后台且已授予悬浮窗权限时展示。
     * 多计划并行时逐行展示（至多 3 行，超出折叠为提示行）。
     *
     * @param map 当前秒表 Map
     */
    private suspend fun updateCapsule(map: Map<Long, PlanTimerEntry>) {
        val settings = container.settingsStore.settings.first()
        val running = map.values.filter { it.running }
        if (!settings.capsuleEnabled || running.isEmpty() || AppForeground.isForeground || !Settings.canDrawOverlays(this)) {
            capsule.hide()
            return
        }
        val shown = running.take(3)
        val entries = shown.mapNotNull { entry ->
            val plan = container.planRepository.getPlan(entry.planId) ?: return@mapNotNull null
            val savedSec = container.planRepository.getTodaySeconds(entry.planId)
            TimerCapsule.Entry(
                title = plan.title,
                sessionSec = savedSec + entry.sessionSec,
                goalSec = plan.dailyGoalMinutes * 60L
            )
        }.toMutableList()
        if (running.size > shown.size) {
            entries.add(TimerCapsule.Entry("… 还有 ${running.size - shown.size} 个", 0L, 0L))
        }
        if (entries.isEmpty()) {
            capsule.hide()
        } else {
            capsule.show(
                entries = entries,
                config = TimerCapsule.Config(
                    bgColorHex = settings.capsuleColorHex,
                    alphaPercent = settings.capsuleAlpha,
                    widthDp = settings.capsuleWidthDp,
                    rowHeightDp = settings.capsuleRowHeightDp,
                    textColorHex = settings.capsuleTextColorHex,
                    textAlphaPercent = settings.capsuleTextAlpha,
                    progressColorHex = settings.capsuleProgressColorHex,
                    progressAlphaPercent = settings.capsuleProgressAlpha,
                    xPx = settings.capsuleX,
                    yPx = settings.capsuleY
                )
            )
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
        capsule.hide()
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
