package com.focuslist.app.ui.plan

import android.content.Context
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 单个计划的秒表条目：正计时，暂停冻结不清零，再开始继续叠加。
 *
 * @property planId 计划 ID
 * @property running 是否正在走动
 * @property frozenSec 已冻结段秒数（暂停保留）
 * @property startedAtMs 运行中本次起算的 epoch 毫秒；暂停时为 0
 * @property sessionSec 未落库累计秒数（冻结段 + 实时段，展示用）
 */
data class PlanTimerEntry(
    val planId: Long,
    val running: Boolean,
    val frozenSec: Long,
    val startedAtMs: Long,
    val sessionSec: Long
)

/**
 * 计划秒表全局持有者：支持**多个计划并行计时**（各自独立开始/暂停/累加）。
 *
 * - 计时一律基于 wall clock 换算，息屏/后台不影响真实秒数；
 * - 暂停只冻结不清零，再点开始从冻结值继续；
 * - 状态实时写入 SharedPreferences（行格式：planId,running,frozenSec,startedAtMs），防进程被杀；
 * - 落库时机：手动 flush、跨天自动、退后台兜底（[flushListener] 回调）。
 */
object PlanTimerHolder {

    /** SP 文件名 */
    private const val PREFS = "plan_timer_state"

    /** SP 键：序列化的条目列表 */
    private const val KEY_ENTRIES = "entries"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    /** 上次 tick 所在日期（0 点），用于跨天检测 */
    private var lastDay: Long = 0L

    /** 落库回调：参数为 (planId, 待落库秒数)，由 HomeViewModel / MainActivity 注册 */
    @Volatile
    var flushListener: ((Long, Long) -> Unit)? = null

    private val mutableTimers = MutableStateFlow<Map<Long, PlanTimerEntry>>(emptyMap())

    /** 并行秒表状态流：planId -> 条目 */
    val timers: StateFlow<Map<Long, PlanTimerEntry>> = mutableTimers.asStateFlow()

    /**
     * 开始或恢复某计划计时（与其他计划并行，互不干扰）。
     *
     * @param planId 计划 ID
     */
    @Synchronized
    fun start(planId: Long) {
        val current = mutableTimers.value.toMutableMap()
        val entry = current[planId]
        if (entry == null) {
            // 新计划：从 0 起
            current[planId] = PlanTimerEntry(
                planId = planId,
                running = true,
                frozenSec = 0,
                startedAtMs = System.currentTimeMillis(),
                sessionSec = 0
            )
        } else if (!entry.running) {
            // 暂停态：从冻结值继续，不清零
            current[planId] = entry.copy(
                running = true,
                startedAtMs = System.currentTimeMillis()
            )
        } else {
            // 已在运行：幂等
            return
        }
        mutableTimers.value = current
        ensureTick()
    }

    /**
     * 暂停某计划：冻结秒数不清零。
     *
     * @param planId 计划 ID
     */
    @Synchronized
    fun pause(planId: Long) {
        val current = mutableTimers.value.toMutableMap()
        val entry = current[planId] ?: return
        if (!entry.running) {
            return
        }
        val sec = entry.frozenSec + (System.currentTimeMillis() - entry.startedAtMs) / 1000L
        current[planId] = entry.copy(running = false, frozenSec = sec, startedAtMs = 0, sessionSec = sec)
        mutableTimers.value = current
    }

    /**
     * 切换某计划的开始/暂停。
     *
     * @param planId 计划 ID
     * @return 切换后是否在运行
     */
    fun toggle(planId: Long): Boolean {
        val entry = mutableTimers.value[planId]
        return if (entry != null && entry.running) {
            pause(planId)
            false
        } else {
            start(planId)
            true
        }
    }

    /**
     * 取出某计划的未落库段并清零（运行中清零后继续跑）。
     *
     * @param planId 计划 ID
     * @return 待落库秒数；无累计返回 null
     */
    @Synchronized
    fun flush(planId: Long): Long? {
        val current = mutableTimers.value.toMutableMap()
        val entry = current[planId] ?: return null
        val sec = computeSessionSec(entry)
        if (sec <= 0) {
            return null
        }
        current[planId] = if (entry.running) {
            entry.copy(frozenSec = 0, startedAtMs = System.currentTimeMillis(), sessionSec = 0)
        } else {
            entry.copy(frozenSec = 0, sessionSec = 0)
        }
        mutableTimers.value = current
        return sec
    }

    /**
     * 取出全部未落库段（退后台兜底）：每条清零，运行中的继续跑。
     *
     * @return （计划 ID, 秒数）列表
     */
    @Synchronized
    fun flushAll(): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        val current = mutableTimers.value.toMutableMap()
        current.keys.toList().forEach { planId ->
            val entry = current[planId] ?: return@forEach
            val sec = computeSessionSec(entry)
            if (sec > 0) {
                result.add(planId to sec)
                current[planId] = if (entry.running) {
                    entry.copy(frozenSec = 0, startedAtMs = System.currentTimeMillis(), sessionSec = 0)
                } else {
                    entry.copy(frozenSec = 0, sessionSec = 0)
                }
            }
        }
        mutableTimers.value = current
        return result
    }

    /**
     * 完全移除某计划计时（计划删除时调用）。
     *
     * @param planId 计划 ID
     */
    @Synchronized
    fun remove(planId: Long) {
        val current = mutableTimers.value.toMutableMap()
        current.remove(planId)
        mutableTimers.value = current
    }

    /**
     * 是否有任何计时占用（运行或暂停）。
     *
     * @return 是否非空
     */
    fun hasActive(): Boolean {
        return mutableTimers.value.isNotEmpty()
    }

    /**
     * 当前是否有运行中的计时。
     *
     * @return 是否有走动中的秒表
     */
    fun hasRunning(): Boolean {
        return mutableTimers.value.values.any { it.running }
    }

    /**
     * 序列化全部条目到 SharedPreferences。
     *
     * @param context 上下文
     */
    fun persist(context: Context) {
        val text = mutableTimers.value.values.joinToString(";") { entry ->
            "${entry.planId},${if (entry.running) 1 else 0},${entry.frozenSec},${entry.startedAtMs}"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, text)
            .apply()
    }

    /**
     * 从 SharedPreferences 恢复全部条目（进程重启后）。
     *
     * @param context 上下文
     */
    @Synchronized
    fun restore(context: Context) {
        if (mutableTimers.value.isNotEmpty()) {
            return
        }
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, "") ?: ""
        if (text.isBlank()) {
            return
        }
        val map = mutableMapOf<Long, PlanTimerEntry>()
        text.split(";").forEach { line ->
            val parts = line.split(",")
            if (parts.size == 4) {
                val planId = parts[0].toLongOrNull() ?: return@forEach
                val running = parts[1] == "1"
                val frozenSec = parts[2].toLongOrNull() ?: 0L
                val startedAtMs = parts[3].toLongOrNull() ?: 0L
                val entry = PlanTimerEntry(
                    planId = planId,
                    running = running,
                    frozenSec = frozenSec,
                    startedAtMs = startedAtMs,
                    sessionSec = 0
                )
                map[planId] = entry.copy(sessionSec = computeSessionSec(entry))
            }
        }
        mutableTimers.value = map
        if (map.values.any { it.running }) {
            ensureTick()
        }
    }

    /**
     * 计算条目的实时累计秒数（冻结段 + 运行段）。
     *
     * @param entry 条目
     * @return 秒数
     */
    private fun computeSessionSec(entry: PlanTimerEntry): Long {
        return if (entry.running && entry.startedAtMs > 0) {
            entry.frozenSec + (System.currentTimeMillis() - entry.startedAtMs) / 1000L
        } else {
            entry.frozenSec
        }
    }

    /**
     * 确保全局滴答任务运行：每秒刷新所有运行中条目的展示秒数 + 跨天自动落库。
     */
    private fun ensureTick() {
        if (tickJob?.isActive == true) {
            return
        }
        lastDay = DateUtils.startOfDay(System.currentTimeMillis())
        tickJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val current = mutableTimers.value
                if (current.isEmpty()) {
                    continue
                }
                // 刷新运行中条目的 sessionSec
                val updated = current.mapValues { (_, entry) ->
                    if (entry.running) {
                        entry.copy(sessionSec = computeSessionSec(entry))
                    } else {
                        entry
                    }
                }
                mutableTimers.value = updated
                // 跨天：全部段落库并清零继续
                val today = DateUtils.startOfDay(System.currentTimeMillis())
                if (today != lastDay) {
                    lastDay = today
                    flushAll().forEach { (planId, sec) ->
                        flushListener?.invoke(planId, sec)
                    }
                }
            }
        }
    }
}