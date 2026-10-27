package com.focuslist.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.data.repo.PlanRepository
import com.focuslist.app.ui.plan.PlanTimerEntry
import com.focuslist.app.ui.plan.PlanTimerHolder
import com.focuslist.app.ui.plan.PlanTimerService
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 首页（日历主页）UI 状态。
 *
 * @property shownMonth 当前展示月份 1 号 0 点时间戳
 * @property plans 全部未归档计划（前端按天过滤生效范围）
 * @property monthLogs 当前网格区间内的累计记录
 * @property selectedDay 选中日期（0 点）；null 表示日历展开状态
 * @property timers 并行秒表 Map：planId -> 条目（运行中或已暂停）
 */
data class HomeUiState(
    val shownMonth: Long = 0L,
    val plans: List<PlanEntity> = emptyList(),
    val monthLogs: List<PlanLogEntity> = emptyList(),
    val selectedDay: Long? = null,
    val timers: Map<Long, PlanTimerEntry> = emptyMap()
)

/**
 * 首页 ViewModel：月历网格数据（事项标签 + 每日完成度）与选中日卡片数据，多计划并行计时。
 *
 * @property planRepository 计划仓库
 * @property appContext 应用上下文（启动前台计时服务用）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val planRepository: PlanRepository,
    private val appContext: Context
) : ViewModel() {

    /** 月份偏移：0 表示当月 */
    private val monthOffset = MutableStateFlow(0)

    /** 选中日期（0 点时间戳）；null 表示未选中（日历展开） */
    private val selectedDay = MutableStateFlow<Long?>(null)

    /** 多选模式下选中的计划 ID 集合；空集表示非多选模式 */
    private val selectedPlanIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 多选模式选中集合（只读暴露） */
    val selectMode: StateFlow<Set<Long>> = selectedPlanIds.asStateFlow()

    init {
        // 跨天自动落库回调：秒表跨过 0 点时把各计划已跑段写入当天
        PlanTimerHolder.flushListener = { planId, sec ->
            viewModelScope.launch {
                planRepository.addSessionSeconds(planId, sec)
            }
        }
        // 冷启动恢复（进程重启场景）
        PlanTimerHolder.restore(appContext)
    }

    /** 月偏移对应的 1 号 0 点 */
    private fun monthStartOf(offset: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = DateUtils.startOfDay(System.currentTimeMillis())
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, offset)
        return cal.timeInMillis
    }

    /** 网格覆盖区间（含前后月补位）起点 */
    private fun gridStartOf(monthStart: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
        val lead = if (firstDow == Calendar.SUNDAY) 6 else firstDow - Calendar.MONDAY
        return monthStart - lead * DateUtils.DAY_MILLIS
    }

    /** 聚合 UI 状态流 */
    val uiState: StateFlow<HomeUiState> = combine(
        monthOffset.flatMapLatest { offset ->
            val monthStart = monthStartOf(offset)
            val gridStart = gridStartOf(monthStart)
            // 网格固定 6 行 7 列 = 42 天
            planRepository.observeLogsBetween(gridStart, gridStart + 42 * DateUtils.DAY_MILLIS - 1)
        },
        planRepository.observeAllPlans(),
        PlanTimerHolder.timers
    ) { logs, plans, timers ->
        Triple(logs, plans, timers)
    }.combine(monthOffset) { (logs, plans, timers), offset ->
        HomeUiState(
            shownMonth = monthStartOf(offset),
            plans = plans,
            monthLogs = logs,
            timers = timers
        )
    }.combine(selectedDay) { state, day ->
        state.copy(selectedDay = day)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /**
     * 切换月份。
     *
     * @param delta 偏移量（+1 下一月，-1 上一月）
     */
    fun shiftMonth(delta: Int) {
        monthOffset.value += delta
        // 翻月时收起卡片回到日历
        selectedDay.value = null
    }

    /**
     * 选中/取消选中某天：点击已选中的同一天则收起回日历。
     *
     * @param dayStart 当天 0 点时间戳
     */
    fun selectDay(dayStart: Long) {
        selectedDay.value = if (selectedDay.value == dayStart) null else dayStart
    }

    /**
     * 收起卡片回到日历。
     */
    fun clearSelection() {
        selectedDay.value = null
    }

    /**
     * 卡片开始/暂停计时（多计划并行，互不干扰）：
     * - 运行中点击 → 冻结不清零，聚合通知刷新；
     * - 暂停/空闲点击 → 开始或继续叠加，前台服务保活。
     *
     * @param planId 计划 ID
     */
    fun toggleTimer(planId: Long) {
        val running = PlanTimerHolder.toggle(planId)
        if (running) {
            PlanTimerService.ensureStarted(appContext)
        } else {
            // 暂停后若没有任何运行中的计时，通知可保持（显示已暂停）但无需重启服务
            PlanTimerService.requestUpdate(appContext)
        }
        PlanTimerHolder.persist(appContext)
    }

    /**
     * 计算某天生效的计划列表。
     *
     * @param state 当前 UI 状态
     * @param dayStart 当天 0 点时间戳
     * @return 生效计划
     */
    fun plansOfDay(state: HomeUiState, dayStart: Long): List<PlanEntity> {
        return state.plans.filter { it.startDate <= dayStart && it.endDate >= dayStart }
    }

    /**
     * 计划当日的展示秒数：库中累计 + 未落库段（运行或暂停冻结都算，仅当天）。
     *
     * @param state 当前 UI 状态
     * @param planId 计划 ID
     * @param dayStart 当天 0 点时间戳
     * @return 当日总秒数
     */
    fun daySecondsOf(state: HomeUiState, planId: Long, dayStart: Long): Long {
        val saved = state.monthLogs.firstOrNull { it.planId == planId && it.date == dayStart }?.accumulatedSec ?: 0L
        val isToday = dayStart == DateUtils.startOfDay(System.currentTimeMillis())
        val extra = if (isToday) state.timers[planId]?.sessionSec ?: 0L else 0L
        return saved + extra
    }

    /**
     * 计算某天完成度百分比：各生效计划"累计/目标"（截断 100%）的均值。
     *
     * @param state 当前 UI 状态
     * @param dayStart 当天 0 点时间戳
     * @return 0-100 的整数百分比；当天无生效计划返回 -1（不显示）
     */
    fun percentOfDay(state: HomeUiState, dayStart: Long): Int {
        val plans = plansOfDay(state, dayStart)
        if (plans.isEmpty()) {
            return -1
        }
        val sum = plans.sumOf { plan ->
            val goal = plan.dailyGoalMinutes * 60L
            if (goal <= 0) {
                0.0
            } else {
                (daySecondsOf(state, plan.id, dayStart).toDouble() / goal).coerceAtMost(1.0)
            }
        }
        return (sum / plans.size * 100).toInt()
    }

    /**
     * 手动设置计划当天的已完成分钟数（补录场景：覆盖原值）。
     *
     * @param planId 计划 ID
     * @param minutes 分钟数；小于 0 时按 0 处理
     */
    fun setTodayMinutes(planId: Long, minutes: Int) {
        viewModelScope.launch {
            planRepository.setTodaySeconds(planId, minutes.toLong() * 60L)
        }
    }

    /**
     * 切换某计划的多选勾选状态；首次勾选时进入多选模式。
     *
     * @param planId 计划 ID
     */
    fun toggleSelectPlan(planId: Long) {
        val current = selectedPlanIds.value
        selectedPlanIds.value = if (planId in current) {
            current - planId
        } else {
            current + planId
        }
    }

    /**
     * 退出多选模式并清空选中集合。
     */
    fun exitSelectMode() {
        selectedPlanIds.value = emptySet()
    }

    /**
     * 删除单个计划：先停计时再删库，并从多选集合中移除。
     *
     * @param planId 计划 ID
     */
    fun deletePlan(planId: Long) {
        PlanTimerHolder.remove(planId)
        PlanTimerHolder.persist(appContext)
        selectedPlanIds.value = selectedPlanIds.value - planId
        viewModelScope.launch {
            planRepository.delete(planId)
        }
    }

    /**
     * 批量删除选中的计划：逐个停计时与删库，完成后退出多选模式。
     */
    fun deleteSelected() {
        val ids = selectedPlanIds.value
        if (ids.isEmpty()) {
            return
        }
        ids.forEach { PlanTimerHolder.remove(it) }
        PlanTimerHolder.persist(appContext)
        selectedPlanIds.value = emptySet()
        viewModelScope.launch {
            ids.forEach { planRepository.delete(it) }
        }
    }
}