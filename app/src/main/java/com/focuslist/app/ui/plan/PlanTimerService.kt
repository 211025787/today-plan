package com.focuslist.app.ui.plan

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.focuslist.app.FocusListApp
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.notify.Notifier
import com.focuslist.app.notify.PlanAlert
import com.focuslist.app.notify.PlanGoalAlarms
import com.focuslist.app.util.AppForeground
import com.focuslist.app.util.PlanLead
import com.focuslist.app.widget.WidgetRefresher
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

    /** 已安排的「到达目标」闹钟触发时刻：planId -> 时间戳 */
    private val scheduledGoalAt = mutableMapOf<Long, Long>()

    /** 已安排的「提前 N 分钟」闹钟触发时刻：planId -> 时间戳 */
    private val scheduledLeadAt = mutableMapOf<Long, Long>()

    /** 本次运行中已经排过提前提醒的计划（避免窗口内每秒重复补排） */
    private val leadAlerted = mutableSetOf<Long>()

    /** 本次运行中已经提醒过「达标」的计划 */
    private val goalAlerted = mutableSetOf<Long>()

    /** 上一次同步时各计划的剩余秒数：用于识别「跨过阈值」的那一秒 */
    private val lastRemainSec = mutableMapOf<Long, Long>()

    /** 设置快照缓存：服务每秒要用，不能每秒读 DataStore */
    @Volatile
    private var cachedSettings: TimerSettings? = null

    /** 上一次生效的提前分钟数：设置变化时清空 [leadAlerted]，让新阈值重新生效 */
    private var lastLeadMinutes: Int = -1

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
        // 设置变更（提前分钟数 / 铃声）实时生效：下一次秒表刷新就会用新值
        serviceScope.launch {
            container.settingsStore.settings.collect { settings ->
                cachedSettings = settings
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
                        scheduledLeadAt.clear()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val entry = currentEntry()
                        notifier.updateStopwatchNotification(entry.runningCount, entry.pausedCount, entry.baseSec, entry.anyRunning)
                        PlanTimerHolder.persist(this@PlanTimerService)
                        syncGoalAlarms(map)
                        updateCapsule(map)
                        WidgetRefresher.refreshPlansThrottled(this@PlanTimerService)
                    }
                } catch (_: Exception) {
                    // 数据库或系统限制导致单次刷新失败时保持监听
                }
            }
        }
    }

    /**
     * 同步计划提醒：由前台服务按秒判断是否跨过「提前 N 分钟」与「到达目标」，
     * 跨过阈值的瞬间直接提醒；AlarmManager 只作为进程被冻结 / 被杀时的兜底。
     *
     * 为什么不能只靠闹钟：Android 13+ 默认不授予精确闹钟权限，
     * `setAndAllowWhileIdle` 会被系统批量延后（实测可达几十分钟），
     * 而前台服务每秒都在跑，用它直接判断才能做到准点。
     *
     * 闹钟仍然保留：进程被杀后由系统唤醒广播兜底，两条路径靠 [PlanAlert] 去重。
     *
     * @param map 当前秒表 Map
     */
    private suspend fun syncGoalAlarms(map: Map<Long, PlanTimerEntry>) {
        val now = System.currentTimeMillis()
        val settings = cachedSettings
            ?: container.settingsStore.settings.first().also { cachedSettings = it }
        val leadMinutes = PlanLead.coerce(settings.planReachGoalLeadMinutes)
        var leadChanged = false
        if (lastLeadMinutes < 0) {
            // 服务刚起来：只记录当前值，不把「首次读取」当成用户改设置，避免一开应用就补响
            lastLeadMinutes = leadMinutes
        } else if (leadMinutes != lastLeadMinutes) {
            // 用户改了提前分钟数：旧的「已提醒」记录作废，按新阈值重新判断
            lastLeadMinutes = leadMinutes
            leadAlerted.clear()
            leadChanged = true
        }
        val leadSec = leadMinutes * 60L
        map.forEach { (planId, entry) ->
            if (!entry.running) {
                cancelPendingAlarms(planId, now)
                // 暂停视为本次提醒作废：继续计时后按新的剩余时间重新判断
                leadAlerted.remove(planId)
                goalAlerted.remove(planId)
                lastRemainSec.remove(planId)
                return@forEach
            }
            val plan = container.planRepository.getPlan(planId) ?: run {
                cancelPendingAlarms(planId, now)
                lastRemainSec.remove(planId)
                return@forEach
            }
            val goalSec = plan.dailyGoalMinutes * 60L
            if (goalSec <= 0) {
                cancelPendingAlarms(planId, now)
                lastRemainSec.remove(planId)
                return@forEach
            }
            val savedSec = container.planRepository.getTodaySeconds(planId)
            val remainSec = goalSec - (savedSec + entry.sessionSec)
            val previousRemain = lastRemainSec[planId]
            lastRemainSec[planId] = remainSec

            if (remainSec <= 0) {
                // 跨过 0 的瞬间直接提醒；闹钟可能被系统延后，不能干等
                if (previousRemain != null && previousRemain > 0L && goalAlerted.add(planId)) {
                    PlanGoalAlarms.cancelKind(this, planId, PlanGoalAlarms.KIND_GOAL)
                    PlanAlert.fire(this, settings, planId, plan.title, PlanGoalAlarms.KIND_GOAL, 0)
                }
                cancelPendingAlarms(planId, now)
                leadAlerted.remove(planId)
                return@forEach
            }

            // 到达每日目标：闹钟兜底
            val goalAt = PlanLead.goalAt(now, remainSec)
            if (goalAt != null) {
                val previous = scheduledGoalAt[planId]
                if (previous == null || abs(previous - goalAt) > 5000L) {
                    PlanGoalAlarms.scheduleGoal(this, planId, plan.title, goalAt)
                    scheduledGoalAt[planId] = goalAt
                }
            }

            // 提前 N 分钟：闹钟兜底（到点时同样会走去重表）
            val leadAt = PlanLead.triggerAt(now, remainSec, leadMinutes)
            if (leadAt != null) {
                val previous = scheduledLeadAt[planId]
                if (previous == null || abs(previous - leadAt) > 5000L) {
                    PlanGoalAlarms.scheduleLead(this, planId, plan.title, leadMinutes, leadAt)
                    scheduledLeadAt[planId] = leadAt
                }
            }

            // 服务侧准点判断：刚开始计时就已在窗口内、这一秒刚跨进窗口，
            // 或用户刚把阈值调大到覆盖当前剩余时间
            val catchUp = previousRemain == null && PlanLead.shouldCatchUp(
                remainSec = remainSec,
                leadMinutes = leadMinutes,
                sessionSec = entry.sessionSec,
                alerted = leadAlerted.contains(planId)
            )
            val crossed = previousRemain != null &&
                leadMinutes > 0 &&
                previousRemain > leadSec &&
                remainSec <= leadSec
            val thresholdRaised = leadChanged && leadMinutes > 0 && remainSec <= leadSec
            if ((catchUp || crossed || thresholdRaised) && leadAlerted.add(planId)) {
                if (scheduledLeadAt.remove(planId) != null) {
                    PlanGoalAlarms.cancelKind(this, planId, PlanGoalAlarms.KIND_LEAD)
                }
                // 用实际剩余分钟数做文案：补提醒时可能已经不是配置的提前量
                val remainMinutes = ((remainSec + 59L) / 60L).toInt()
                PlanAlert.fire(this, settings, planId, plan.title, PlanGoalAlarms.KIND_LEAD, remainMinutes)
            }
        }
        // 已不存在的计划取消闹钟
        val removed = (scheduledGoalAt.keys + scheduledLeadAt.keys - map.keys).toList()
        removed.forEach { planId ->
            cancelPendingAlarms(planId, now)
            scheduledGoalAt.remove(planId)
            scheduledLeadAt.remove(planId)
            leadAlerted.remove(planId)
            goalAlerted.remove(planId)
            lastRemainSec.remove(planId)
        }
    }

    /**
     * 取消某计划未来尚未触发的闹钟；已到点的条目只从内存登记里移除，交给系统继续投递。
     *
     * @param planId 计划 ID
     * @param now 当前时间戳
     */
    private fun cancelPendingAlarms(planId: Long, now: Long) {
        val goalAt = scheduledGoalAt.remove(planId)
        if (goalAt != null && goalAt - now > CANCEL_GRACE_MS) {
            PlanGoalAlarms.cancelKind(this, planId, PlanGoalAlarms.KIND_GOAL)
        }
        val leadAt = scheduledLeadAt.remove(planId)
        if (leadAt != null && leadAt - now > CANCEL_GRACE_MS) {
            PlanGoalAlarms.cancelKind(this, planId, PlanGoalAlarms.KIND_LEAD)
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

        /** 取消闹钟的宽限：距触发不足 3 秒就不撤，避免和系统投递抢时序 */
        private const val CANCEL_GRACE_MS = 3_000L


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
