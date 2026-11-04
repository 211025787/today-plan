package com.focuslist.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.data.repo.NoteRepository
import com.focuslist.app.data.repo.PlanRepository
import com.focuslist.app.data.repo.TripRepository
import com.focuslist.app.ui.plan.PlanTimerEntry
import com.focuslist.app.ui.plan.PlanTimerHolder
import com.focuslist.app.ui.plan.PlanTimerService
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.Search
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页（日历主页）UI 状态。
 *
 * @property shownMonth 当前展示月份 1 号 0 点时间戳
 * @property monthOffset 当前月份相对本月的偏移（0 为当月），供翻页器反向同步
 * @property plans 全部未归档计划（前端按天过滤生效范围）
 * @property trips 全部旅游行程（startDate>0 且落在该天的才显示到日历）
 * @property monthLogs 当前网格区间内的累计记录
 * @property selectedDay 选中日期（0 点）；null 表示日历展开状态
 * @property timers 并行秒表 Map：planId -> 条目（运行中或已暂停）
 */
data class HomeUiState(
    val shownMonth: Long = 0L,
    val monthOffset: Int = 0,
    val plans: List<PlanEntity> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val monthLogs: List<PlanLogEntity> = emptyList(),
    val selectedDay: Long? = null,
    val timers: Map<Long, PlanTimerEntry> = emptyMap()
)

/**
 * 首页搜索命中项：计划与备注统一排序后的展示模型。
 *
 * @property score 匹配得分（越大越靠前）
 * @property title 主标题（命中的字段；计划备注命中时为备注内容摘要）
 * @property ranges 标题内的高亮区间
 * @property snippet 副标题摘要
 */
sealed class HomeSearchHit {
    abstract val score: Int
    abstract val title: String
    abstract val ranges: List<IntRange>
    abstract val snippet: String

    /** 命中的计划 */
    data class PlanHit(
        val plan: PlanEntity,
        override val score: Int,
        override val title: String,
        override val ranges: List<IntRange>,
        override val snippet: String
    ) : HomeSearchHit()

    /** 命中的备注 */
    data class NoteHit(
        val note: QuickNoteEntity,
        override val score: Int,
        override val title: String,
        override val ranges: List<IntRange>,
        override val snippet: String
    ) : HomeSearchHit()
}

/** 搜索字段权重：标题比正文重要 */
private const val WEIGHT_TITLE = 3

/**
 * 首页 ViewModel：月历网格数据（事项标签 + 每日完成度）与选中日卡片数据，多计划并行计时，
 * 另提供跨「计划 + 备注」的全局搜索。
 *
 * @property planRepository 计划仓库
 * @property noteRepository 备注仓库（搜索备注用）
 * @property tripRepository 行程仓库（日历显示旅游规划用）
 * @property appContext 应用上下文（启动前台计时服务用）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val planRepository: PlanRepository,
    private val noteRepository: NoteRepository,
    private val tripRepository: TripRepository,
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

    /** 搜索关键词（空串表示未进入搜索态） */
    private val query = MutableStateFlow("")

    /** 搜索关键词只读暴露 */
    val queryText: StateFlow<String> = query.asStateFlow()

    /**
     * 全局搜索结果：计划（标题 + 备注）与备注（标题 + 正文）混合排序。
     * 关键词为空白时返回空列表——UI 据此回到日历而不是展示全量列表。
     */
    val searchResults: StateFlow<List<HomeSearchHit>> = combine(
        planRepository.observeAllPlans(),
        noteRepository.observeAll(),
        query
    ) { plans, notes, text ->
        val tokens = Search.tokenize(text)
        if (Search.isBlank(text) || tokens.isEmpty()) {
            return@combine emptyList<HomeSearchHit>()
        }
        val planHits = Search.rank(plans, tokens) { plan ->
            listOf(
                Search.Field(plan.title, WEIGHT_TITLE),
                Search.Field(plan.note, 1)
            )
        }.map { m ->
            val plan = m.item
            if (m.fieldIndex == 0) {
                HomeSearchHit.PlanHit(
                    plan = plan,
                    score = m.score,
                    title = plan.title,
                    ranges = m.ranges,
                    snippet = plan.note.ifBlank { dateRangeText(plan) }
                )
            } else {
                HomeSearchHit.PlanHit(
                    plan = plan,
                    score = m.score,
                    title = Search.snippet(plan.note, m.ranges),
                    ranges = emptyList(),
                    snippet = plan.title
                )
            }
        }
        val noteHits = Search.rank(notes, tokens) { note ->
            listOf(
                Search.Field(note.title, WEIGHT_TITLE),
                Search.Field(note.content, 1)
            )
        }.map { m ->
            val note = m.item
            val heading = note.title.ifBlank { "无标题备注" }
            if (m.fieldIndex == 0) {
                HomeSearchHit.NoteHit(
                    note = note,
                    score = m.score,
                    title = heading,
                    ranges = m.ranges,
                    snippet = Search.snippet(note.content, emptyList())
                )
            } else {
                HomeSearchHit.NoteHit(
                    note = note,
                    score = m.score,
                    title = heading,
                    ranges = emptyList(),
                    snippet = Search.snippet(note.content, m.ranges)
                )
            }
        }
        (planHits + noteHits).sortedByDescending { it.score }.take(Search.DEFAULT_LIMIT)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            // 提前预取前一个月和后一个月：区间向前后各扩 31 天，滑动到邻月时计划与完成度均已就绪
            val rangeStart = gridStart - 31 * DateUtils.DAY_MILLIS
            val rangeEnd = gridStart + 42 * DateUtils.DAY_MILLIS - 1 + 31 * DateUtils.DAY_MILLIS
            combine(
                planRepository.observeLogsBetween(rangeStart, rangeEnd),
                planRepository.observePlansInRange(rangeStart, rangeEnd)
            ) { logs, plans -> Triple(offset, logs, plans) }
        },
        PlanTimerHolder.timers
    ) { data, timers ->
        val (offset, logs, plans) = data
        HomeUiState(
            shownMonth = monthStartOf(offset),
            monthOffset = offset,
            plans = plans,
            monthLogs = logs,
            timers = timers
        )
    }.combine(tripRepository.observeTrips()) { state, trips ->
        state.copy(trips = trips)
    }.combine(selectedDay) { state, day ->
        state.copy(selectedDay = day)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /**
     * 选中日所在周的累计记录。
     *
     * 日卡片顶部的周条要显示整周 7 天，而 [uiState] 里的 monthLogs 只覆盖当前月份网格，
     * 跨月的那一周会拿到 0；这里单独按周观察一次，保证数字与月历一致。
     */
    val weekLogs: StateFlow<List<PlanLogEntity>> = selectedDay.flatMapLatest { day ->
        if (day == null) {
            flowOf(emptyList())
        } else {
            val start = DateUtils.startOfWeek(day)
            planRepository.observeLogsBetween(start, DateUtils.addDays(start, 6))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
     * 直接设置月份偏移（翻页器吸附到位后同步，与当前值相同则忽略）。
     *
     * @param offset 相对当月的偏移月数
     */
    fun setMonthOffset(offset: Int) {
        if (monthOffset.value == offset) {
            return
        }
        monthOffset.value = offset
        selectedDay.value = null
    }

    /** 「2026/11/1 - 2026/11/30」形式的计划生效区间文案 */
    private fun dateRangeText(plan: PlanEntity): String {
        val fmt = SimpleDateFormat("yyyy/M/d", Locale.CHINA)
        return "${fmt.format(Date(plan.startDate))} - ${fmt.format(Date(plan.endDate))}"
    }

    /**
     * 目标日期所在月相对当月的偏移月数。
     *
     * @param dayStart 目标日期 0 点时间戳
     * @return 偏移月数（0 为当月）
     */
    private fun monthDeltaOf(dayStart: Long): Int {
        val base = Calendar.getInstance().apply { timeInMillis = monthStartOf(0) }
        val target = Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.get(Calendar.YEAR) - base.get(Calendar.YEAR)) * 12 +
            (target.get(Calendar.MONTH) - base.get(Calendar.MONTH))
    }

    /**
     * 更新搜索关键词；空白输入即视为退出搜索态。
     *
     * @param text 用户输入
     */
    fun setQuery(text: String) {
        query.value = text
    }

    /**
     * 清空搜索关键词（退出搜索态）。
     */
    fun clearQuery() {
        query.value = ""
    }

    /**
     * 搜索结果跳计划：把日历翻到该计划生效期内离今天最近的一天并展开日卡片。
     *
     * @param plan 命中的计划
     */
    fun focusPlan(plan: PlanEntity) {
        val today = DateUtils.startOfDay(System.currentTimeMillis())
        val day = today.coerceIn(plan.startDate, plan.endDate)
        monthOffset.value = monthDeltaOf(day)
        selectedDay.value = day
        query.value = ""
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
     * 选中任意一天并同步月份：日卡片顶部周条左右翻周时用，跨月也不会错位。
     *
     * @param dayStart 目标日期（任意时刻，内部会归一到 0 点）
     */
    fun selectDayAnywhere(dayStart: Long) {
        val target = DateUtils.startOfDay(dayStart)
        val delta = monthDeltaOf(target)
        if (monthOffset.value != delta) {
            monthOffset.value = delta
        }
        selectedDay.value = target
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

    /** 每个计划从开始到今天的累计投入秒数（不含未落库的运行段） */
    private val mutablePlanTotals = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val planTotals: StateFlow<Map<Long, Long>> = mutablePlanTotals.asStateFlow()

    /**
     * 重新汇总各计划的累计投入，用于日卡片上的「落后 N 天」角标。
     *
     * 一次分组查询即可，计划再多也只有一次读库；
     * 今天的运行中秒数由界面用 [PlanTimerHolder] 的实时会话补上，这里只负责已落库部分。
     */
    fun refreshPlanTotals() {
        viewModelScope.launch {
            val today = DateUtils.startOfDay(System.currentTimeMillis())
            mutablePlanTotals.value = planRepository.totalSecondsByPlan(0L, today)
        }
    }

    /**
     * 暂停所有正在计时的计划（收工用）。
     *
     * 并行计时没有「全部暂停」的入口时，忘记停表会把第二天的时间一起算进去，
     * 收工小结里给一个一键收尾的动作，避免数据被算虚。
     */
    fun pauseAllTimers() {
        val running = PlanTimerHolder.timers.value.values.filter { it.running }
        if (running.isEmpty()) {
            return
        }
        running.forEach { PlanTimerHolder.pause(it.planId) }
        PlanTimerHolder.persist(appContext)
        PlanTimerService.requestUpdate(appContext)
    }

    /**
     * 计算某天生效的计划列表。
     *
     * @param state 当前 UI 状态
     * @param dayStart 当天 0 点时间戳
     * @return 生效计划
     */
    fun plansOfDay(state: HomeUiState, dayStart: Long): List<PlanEntity> {
        // 保持原始顺序（按创建时间），不按卡片尺寸重排，避免调整大小后位置跳变
        return state.plans.filter { it.startDate <= dayStart && it.endDate >= dayStart }
    }

    /**
     * 计算某天生效的旅游行程列表（startDate>0 且落在该天）。
     *
     * @param state 当前 UI 状态
     * @param dayStart 当天 0 点时间戳
     * @return 生效行程
     */
    fun tripsOfDay(state: HomeUiState, dayStart: Long): List<TripEntity> {
        // 保持原始顺序，不按卡片尺寸重排
        return state.trips.filter { it.startDate > 0L && it.startDate <= dayStart && it.endDate >= dayStart }
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
     * 更新计划卡片尺寸（日历网格中占用列数与行数）。
     *
     * @param planId 计划 ID
     * @param cardSize 卡片尺寸编码：0=1×1, 1=1×2, 2=2×1, 3=2×2
     */
    fun updateCardSize(planId: Long, cardSize: Int) {
        viewModelScope.launch {
            planRepository.updateCardSize(planId, cardSize)
        }
    }

    /**
     * 更新行程卡片尺寸（日历网格中占用列数与行数）。
     *
     * @param tripId 行程 ID
     * @param cardSize 卡片尺寸编码：0=1×1, 1=1×2, 2=2×1, 3=2×2
     */
    fun updateTripCardSize(tripId: Long, cardSize: Int) {
        viewModelScope.launch {
            tripRepository.updateCardSize(tripId, cardSize)
        }
    }

    /**
     * 保存计划卡片的自由位置与大小。
     *
     * @param planId 计划 ID
     * @param cols 占列数（1-4）
     * @param rows 占行数（1-4）
     * @param x 左上角列（0 起）
     * @param y 左上角行（0 起）
     * @param z 叠放时间戳；越大越靠上
     */
    fun updateCardLayout(planId: Long, cols: Int, rows: Int, x: Int, y: Int, z: Long) {
        viewModelScope.launch {
            planRepository.updateCardLayout(planId, cols, rows, x, y, z)
        }
    }

    /**
     * 保存行程卡片的自由位置与大小。
     *
     * @param tripId 行程 ID
     * @param cols 占列数（1-4）
     * @param rows 占行数（1-4）
     * @param x 左上角列（0 起）
     * @param y 左上角行（0 起）
     * @param z 叠放时间戳；越大越靠上
     */
    fun updateTripCardLayout(tripId: Long, cols: Int, rows: Int, x: Int, y: Int, z: Long) {
        viewModelScope.launch {
            tripRepository.updateCardLayout(tripId, cols, rows, x, y, z)
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

    /**
     * 按日期范围批量移除计划：只移除落在 [rangeStart, rangeEnd] 内的部分，
     * 范围外日期保留（计划被截短或拆分）；范围完全覆盖计划时才整体删除。
     *
     * @param planIds 计划 ID 集合
     * @param rangeStart 删除区间起点 0 点（含）
     * @param rangeEnd 删除区间终点 0 点（含）
     */
    fun deletePlansInRange(planIds: Set<Long>, rangeStart: Long, rangeEnd: Long) {
        if (planIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val today = DateUtils.startOfDay(System.currentTimeMillis())
            planIds.forEach { id ->
                planRepository.deleteInRange(id, rangeStart, rangeEnd)
                // 计划已整体删除，或今天已不在剩余生效期内时，停掉对应计时
                val plan = planRepository.getPlan(id)
                if (plan == null || today < plan.startDate || today > plan.endDate) {
                    PlanTimerHolder.remove(id)
                }
            }
            PlanTimerHolder.persist(appContext)
        }
    }

}