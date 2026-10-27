package com.focuslist.app.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.focuslist.app.FocusListApp
import com.focuslist.app.data.local.entity.FocusRecordEntity
import com.focuslist.app.notify.Notifier
import com.focuslist.app.util.DateUtils
import com.focuslist.app.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 番茄钟前台服务：负责倒计时状态机、阶段切换、记录落库、响铃震动与小组件联动。
 *
 * 支持动作：[ACTION_START] 开始专注、[ACTION_PAUSE] 暂停、[ACTION_RESUME] 继续、
 * [ACTION_STOP] 结束当前阶段（中断）、[ACTION_SKIP] 跳过当前休息。
 */
class FocusTimerService : Service() {

    /** 服务级协程作用域 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 秒级滴答任务 */
    private var tickJob: Job? = null

    /** 当前阶段起点时间戳（用于落库） */
    private var phaseStartAt: Long = 0L

    /** 当前番茄关联的计划 ID（完成时给计划累计时长）；null 表示未关联 */
    private var currentPlanId: Long? = null

    /** 当前番茄的自定义时长（分钟）；0 表示用设置默认值 */
    private var currentDurationMin: Int = 0

    /** 暂停时刻（wall clock 换算用）；运行中为 0 */
    private var pausedAtMs: Long = 0L

    /** 已累计的暂停秒数（从总时长里扣除） */
    private var pausedAccumSec: Long = 0L

    private val notifier by lazy { Notifier(this) }
    private val container by lazy { (application as FocusListApp).container }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Android 12+ 要求 startForegroundService 后 5 秒内调用 startForeground，
                // 必须先同步挂一个占位前台通知，再去协程里读设置初始化状态；
                // ColorOS 等定制 ROM 对 specialUse 前台服务可能抛 SecurityException，降级后继续计时
                try {
                    startForeground(Notifier.NOTIFY_TIMER_RUNNING, notifier.buildTimerNotification("准备专注", "--:--"))
                } catch (e: Exception) {
                    // 降级：无前台通知继续运行（前台页面内计时可用）
                }
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L).takeIf { it > 0 }
                val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: ""
                // 自定义时长（分钟）：>0 时覆盖设置里的默认番茄时长
                val durationMin = intent.getIntExtra(EXTRA_DURATION_MINUTES, 0)
                // 关联计划 ID：番茄完成时给计划当日累计加时长
                val planId = intent.getLongExtra(EXTRA_PLAN_ID, 0L).takeIf { it > 0 }
                startWork(taskId, title, durationMin, planId)
            }
            ACTION_PAUSE -> pauseTick()
            ACTION_RESUME -> resumeTick()
            ACTION_STOP -> stopPhase(interrupted = true)
            ACTION_SKIP -> skipBreak()
        }
        return START_STICKY
    }

    /**
     * 进入专注阶段并开始倒计时。
     *
     * @param taskId 关联任务 ID；null 表示自由专注
     * @param title 任务/计划标题
     * @param durationMin 自定义时长（分钟）；<=0 时使用设置里的番茄时长
     * @param planId 关联计划 ID；番茄完成时给该计划当日累计加时长
     */
    private fun startWork(taskId: Long?, title: String, durationMin: Int = 0, planId: Long? = null) {
        // 记录本次关联计划与自定义时长，供完成时累计与后续番茄沿用
        if (planId != null) {
            currentPlanId = planId
        }
        if (durationMin > 0) {
            currentDurationMin = durationMin
        }
        serviceScope.launch {
            val settings = container.settingsStore.settings.first()
            val totalSec = (if (currentDurationMin > 0) currentDurationMin else settings.focusMinutes) * 60L
            phaseStartAt = System.currentTimeMillis()
            pausedAtMs = 0L
            pausedAccumSec = 0L
            TimerStateHolder.set(
                TimerUiState(
                    phase = TimerPhase.WORK,
                    remainSec = totalSec,
                    totalSec = totalSec,
                    running = true,
                    taskId = taskId,
                    taskTitle = title,
                    cyclePomos = TimerStateHolder.current().cyclePomos
                )
            )
            // 占位通知更新为真实倒计时
            notifier.updateTimerNotification("专注中", DateUtils.formatCountdown(totalSec))
            startTick()
        }
    }

    /**
     * 计算当前阶段剩余秒数（wall clock 基准，息屏休眠不影响准确性）。
     *
     * @param current 当前计时状态
     * @return 剩余秒数
     */
    private fun remainSecOf(current: TimerUiState): Long {
        val elapsedWallSec = (System.currentTimeMillis() - phaseStartAt) / 1000L
        val pausedSec = pausedAccumSec + (if (pausedAtMs > 0) (System.currentTimeMillis() - pausedAtMs) / 1000L else 0L)
        return current.totalSec - (elapsedWallSec - pausedSec)
    }

    /**
     * 启动秒级滴答循环：按 wall clock 刷新剩余时间（息屏亮屏后自动追平）、通知与小组件。
     */
    private fun startTick() {
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                val current = TimerStateHolder.current()
                if (!current.running) {
                    continue
                }
                val remain = remainSecOf(current)
                if (remain <= 0) {
                    TimerStateHolder.update { it.copy(remainSec = 0) }
                    onPhaseFinished()
                    break
                }
                TimerStateHolder.update { it.copy(remainSec = remain) }
                notifyTick()
            }
        }
    }

    /**
     * 暂停倒计时（wall clock 记账：记录暂停时刻）。
     */
    private fun pauseTick() {
        if (pausedAtMs == 0L) {
            pausedAtMs = System.currentTimeMillis()
        }
        TimerStateHolder.update { it.copy(running = false) }
        notifyTick()
    }

    /**
     * 继续倒计时（wall clock 记账：把暂停段累加进扣除项）。
     */
    private fun resumeTick() {
        val current = TimerStateHolder.current()
        if (current.phase == TimerPhase.IDLE) {
            return
        }
        // 把刚结束的暂停段计入累计
        if (pausedAtMs > 0) {
            pausedAccumSec += (System.currentTimeMillis() - pausedAtMs) / 1000L
            pausedAtMs = 0L
        }
        TimerStateHolder.update { it.copy(running = true) }
        if (tickJob?.isActive != true) {
            startTick()
        }
        notifyTick()
    }

    /**
     * 当前阶段被手动结束：专注中断会落库一条未完成记录，随后回到空闲。
     *
     * @param interrupted 是否为用户主动中断
     */
    private fun stopPhase(interrupted: Boolean) {
        val current = TimerStateHolder.current()
        if (interrupted && current.phase == TimerPhase.WORK) {
            val elapsed = (current.totalSec - current.remainSec).toInt()
            if (elapsed > 0) {
                serviceScope.launch {
                    container.focusRepository.addRecord(
                        FocusRecordEntity(
                            taskId = current.taskId,
                            startAt = phaseStartAt,
                            endAt = System.currentTimeMillis(),
                            durationSec = elapsed,
                            phase = "WORK",
                            completed = false
                        )
                    )
                }
            }
        }
        tickJob?.cancel()
        TimerStateHolder.set(TimerUiState(cyclePomos = current.cyclePomos))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        WidgetRefresher.refreshTimer(this)
    }

    /**
     * 跳过当前休息，直接回到空闲等待下一次专注。
     */
    private fun skipBreak() {
        val current = TimerStateHolder.current()
        if (current.phase == TimerPhase.SHORT_BREAK || current.phase == TimerPhase.LONG_BREAK) {
            tickJob?.cancel()
            TimerStateHolder.set(TimerUiState(cyclePomos = current.cyclePomos))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            WidgetRefresher.refreshTimer(this)
        }
    }

    /**
     * 倒计时自然走完：落库、提醒、按设置衔接下一阶段。
     */
    private fun onPhaseFinished() {
        val finished = TimerStateHolder.current()
        val now = System.currentTimeMillis()
        serviceScope.launch {
            val settings = container.settingsStore.settings.first()

            if (finished.phase == TimerPhase.WORK) {
                // 完整番茄落库并累计到任务
                container.focusRepository.addRecord(
                    FocusRecordEntity(
                        taskId = finished.taskId,
                        startAt = phaseStartAt,
                        endAt = now,
                        durationSec = finished.totalSec.toInt(),
                        phase = "WORK",
                        completed = true
                    )
                )
                finished.taskId?.let { container.taskRepository.incPomo(it) }
                // 番茄绑定计划时：完成的番茄时长累计进计划当日完成度
                currentPlanId?.let { planId ->
                    container.planRepository.addSessionSeconds(planId, finished.totalSec)
                }
            }

            playAlarm(settings.soundEnabled, settings.vibrateEnabled)

            val newCycle = if (finished.phase == TimerPhase.WORK) {
                finished.cyclePomos + 1
            } else {
                finished.cyclePomos
            }

            when (finished.phase) {
                TimerPhase.WORK -> {
                    val isLongBreak = newCycle % settings.longBreakInterval == 0
                    val nextPhase = if (isLongBreak) TimerPhase.LONG_BREAK else TimerPhase.SHORT_BREAK
                    notifier.showTimerFinished("番茄完成", "休息一下，喝口水吧")
                    if (settings.autoStartBreak) {
                        startBreak(nextPhase, settings.shortBreakMinutes, settings.longBreakMinutes, newCycle, finished)
                    } else {
                        idleWithCycle(newCycle)
                    }
                }
                TimerPhase.SHORT_BREAK, TimerPhase.LONG_BREAK -> {
                    notifier.showTimerFinished("休息结束", "开始下一个番茄吧")
                    if (settings.autoStartNextPomo) {
                        startWork(finished.taskId, finished.taskTitle, currentDurationMin, currentPlanId)
                    } else {
                        idleWithCycle(newCycle)
                    }
                }
                TimerPhase.IDLE -> idleWithCycle(newCycle)
            }
            WidgetRefresher.refreshAll(this@FocusTimerService)
        }
    }

    /**
     * 进入休息阶段。
     *
     * @param phase 休息类型
     * @param shortMin 短休息分钟数
     * @param longMin 长休息分钟数
     * @param cyclePomos 连续番茄数
     * @param prev 上一阶段状态（保留任务信息）
     */
    private fun startBreak(phase: TimerPhase, shortMin: Int, longMin: Int, cyclePomos: Int, prev: TimerUiState) {
        val totalSec = (if (phase == TimerPhase.LONG_BREAK) longMin else shortMin) * 60L
        phaseStartAt = System.currentTimeMillis()
        pausedAtMs = 0L
        pausedAccumSec = 0L
        TimerStateHolder.set(
            TimerUiState(
                phase = phase,
                remainSec = totalSec,
                totalSec = totalSec,
                running = true,
                taskId = prev.taskId,
                taskTitle = prev.taskTitle,
                cyclePomos = cyclePomos
            )
        )
        try {
            startForeground(Notifier.NOTIFY_TIMER_RUNNING, notifier.buildTimerNotification("休息中", DateUtils.formatCountdown(totalSec)))
        } catch (e: Exception) {
            // ColorOS 兼容降级
        }
        startTick()
    }

    /**
     * 回到空闲态并保留连续番茄计数。
     *
     * @param cyclePomos 连续番茄数
     */
    private fun idleWithCycle(cyclePomos: Int) {
        tickJob?.cancel()
        TimerStateHolder.set(TimerUiState(cyclePomos = cyclePomos))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 每秒刷新常驻通知与计时小组件。
     */
    private fun notifyTick() {
        val state = TimerStateHolder.current()
        val phaseText = when (state.phase) {
            TimerPhase.WORK -> if (state.running) "专注中" else "已暂停"
            TimerPhase.SHORT_BREAK, TimerPhase.LONG_BREAK -> if (state.running) "休息中" else "已暂停"
            TimerPhase.IDLE -> "准备专注"
        }
        notifier.updateTimerNotification(phaseText, DateUtils.formatCountdown(state.remainSec))
        WidgetRefresher.refreshTimer(this)
    }

    /**
     * 阶段结束后的响铃与震动提醒。
     *
     * @param sound 是否响铃
     * @param vibrate 是否震动
     */
    private fun playAlarm(sound: Boolean, vibrate: Boolean) {
        if (sound) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(this, uri)?.play()
            } catch (ignored: SecurityException) {
                // 铃声不可用时静默降级
            }
        }
        if (vibrate) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500L)
            }
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        super.onDestroy()
    }

    companion object {
        /** 开始专注动作 */
        const val ACTION_START = "com.focuslist.app.action.TIMER_START"

        /** 暂停动作 */
        const val ACTION_PAUSE = "com.focuslist.app.action.TIMER_PAUSE"

        /** 继续动作 */
        const val ACTION_RESUME = "com.focuslist.app.action.TIMER_RESUME"

        /** 停止（中断）动作 */
        const val ACTION_STOP = "com.focuslist.app.action.TIMER_STOP"

        /** 跳过休息动作 */
        const val ACTION_SKIP = "com.focuslist.app.action.TIMER_SKIP"

        /** 附加字段：任务 ID */
        const val EXTRA_TASK_ID = "extra_task_id"

        /** 附加字段：任务标题 */
        const val EXTRA_TASK_TITLE = "extra_task_title"

        /** 附加字段：自定义时长（分钟），0 表示用设置默认值 */
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"

        /** 附加字段：关联计划 ID（番茄完成时累计到计划） */
        const val EXTRA_PLAN_ID = "extra_plan_id"

        /**
         * 便捷启动：以服务方式开始一个番茄。
         * ColorOS 等定制 ROM 对前台服务有额外限制，失败时降级普通服务（前台页面内计时不受影响）。
         *
         * @param context 上下文
         * @param taskId 关联任务 ID；null 表示自由专注
         * @param title 任务标题
         * @param durationMin 自定义时长（分钟）；0 表示用设置里的番茄时长
         * @param planId 关联计划 ID；番茄完成时给计划当日累计加时长
         */
        fun startPomodoro(context: Context, taskId: Long?, title: String, durationMin: Int = 0, planId: Long? = null) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId ?: 0L)
                putExtra(EXTRA_TASK_TITLE, title)
                putExtra(EXTRA_DURATION_MINUTES, durationMin)
                putExtra(EXTRA_PLAN_ID, planId ?: 0L)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // 降级：普通服务启动（App 在前台时可用）
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    // 双降级也失败时静默，UI 保持空闲态
                }
            }
        }

        /**
         * 便捷控制：向服务发送控制动作。
         *
         * @param context 上下文
         * @param action 控制动作
         */
        fun sendAction(context: Context, action: String) {
            try {
                context.startService(Intent(context, FocusTimerService::class.java).setAction(action))
            } catch (e: Exception) {
                // 服务未运行时忽略
            }
        }
    }
}