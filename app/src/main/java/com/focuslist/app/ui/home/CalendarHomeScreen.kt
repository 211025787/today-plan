package com.focuslist.app.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.ui.common.AntiBurnInGlow
import com.focuslist.app.ui.common.DayDetailState
import com.focuslist.app.ui.common.ExpandableFab
import com.focuslist.app.ui.common.TravelOverlayState
import com.focuslist.app.ui.common.FabMenuItem
import com.focuslist.app.ui.common.antiBurnInDrift
import com.focuslist.app.ui.note.DayNotePreviewCard
import com.focuslist.app.ui.plan.AddPlanSheet
import com.focuslist.app.ui.plan.PlanInsightSheet
import com.focuslist.app.ui.plan.QuickPlanSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.DayWrapScript
import com.focuslist.app.util.DayWraps
import com.focuslist.app.util.Ics
import com.focuslist.app.util.IcsPlan
import com.focuslist.app.util.NowAdvice
import com.focuslist.app.util.NowKind
import com.focuslist.app.util.NowPlan
import com.focuslist.app.util.PlanTemplate
import com.focuslist.app.util.PlanPaces
import com.focuslist.app.util.PlanTemplates
import com.focuslist.app.util.WeekStrip
import com.focuslist.app.util.WrapPlan
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 月份标题格式 */
private val monthTitleFormat = SimpleDateFormat("yyyy 年 M 月", Locale.CHINA)

/** 选中日期标题格式 */
private val dayTitleFormat = SimpleDateFormat("M 月 d 日", Locale.CHINA)

/** 周一到周日表头 */
private val weekHeaders = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 翻页器可翻范围：前后各 1200 个月（100 年）。
 * 不能用 Int.MAX_VALUE 充当无限页数——惯性滑动的目标页计算会溢出，轻轻一划就飞出几十万页。
 */
private const val PAGER_MONTH_RANGE = 1200

/** 翻页器起始页：范围中点即当月 */
private const val PAGER_INITIAL_PAGE = PAGER_MONTH_RANGE

/** 翻页器总页数 */
private const val PAGER_PAGE_COUNT = PAGER_MONTH_RANGE * 2 + 1

/**
 * 返回手势进度 → 动画比例的系数。
 * 1f 表示手指滑多少动画就走多少（完全 1:1 跟手）；0.5f 表示滑到系统允许的最大距离时，
 * 动画只走一半，松手再补完剩下的。想调手感改这里一个数就行。
 */
private const val BACK_FOLLOW = 0.5f

/**
 * 上半窗口：孩子始终按「整屏高」测量，自己只报 `visible × 整屏高` 的高度，多出来的裁掉。
 * 好处是收起过程中内容不重新排版——日历不会被压扁，就是单纯被推走。
 * [visible] 在测量阶段才读取，返回手势每帧推进时只重跑布局，不触发重组。
 */
private fun Modifier.topPane(visible: () -> Float): Modifier = this
    .layout { measurable, constraints ->
        val width = constraints.maxWidth
        val full = constraints.maxHeight
        val placeable = measurable.measure(Constraints.fixed(width, full))
        val height = (full * visible().coerceIn(0f, 1f)).roundToInt().coerceIn(0, full)
        layout(width, height) { placeable.place(0, 0) }
    }
    .clipToBounds()

/**
 * 相对当月偏移 [offset] 个月后的 1 号 0 点时间戳。
 *
 * @param offset 月份偏移（0 当月，正数未来，负数过去）
 * @return 目标月 1 号 0 点
 */
private fun monthStartForOffset(offset: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = DateUtils.startOfDay(System.currentTimeMillis())
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.add(Calendar.MONTH, offset)
    return cal.timeInMillis
}


/**
 * 日历主页（打开即展示）：月历格子内展示当天事项标签与完成度百分比；
 * 点击某天收起日历，切换为该日计划的渐变方形卡片（右上角开始/暂停计时）。
 *
 * @param container 依赖容器
 * @param onOpenNote 打开备注全屏页回调（备注 ID，<=0 表示新建；关联日期 0 点时间戳）
 * @param onOpenTravel 打开旅游规划页回调（行程 ID；新建后传入）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarHomeScreen(
    container: AppContainer,
    onOpenNote: (Long, Long) -> Unit = { _, _ -> },
    onOpenTravel: (Long) -> Unit = {},
    onOpenPlanStats: () -> Unit = {}
) {
    // LocalContext 需在 Composable 作用域先取出，再传入 ViewModel 工厂
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(container.planRepository, container.noteRepository, container.tripRepository, appContext)
    }
    val state by viewModel.uiState.collectAsState()
    val weekLogs by viewModel.weekLogs.collectAsState()
    val planTotals by viewModel.planTotals.collectAsState()
    // 搜索态：顶栏变搜索框，内容区换成结果列表
    var searchActive by remember { mutableStateOf(false) }
    val query by viewModel.queryText.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    // 日历 ⇄ 日卡片页的收展进度：0 = 日历满屏，1 = 卡片满屏。
    // 平时用弹簧动画跑（进入 / 点「返回日历」）；全面屏手势返回时由手指直接驱动，
    // 见下面的 PredictiveBackHandler——滑多少，动画就走多少。
    val cardFraction = remember { Animatable(if (state.selectedDay != null) 1f else 0f) }
    val cardSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            // 值域只有 0..1，默认按像素设的阈值放在这里会让动画提前「认为到位」
            visibilityThreshold = 0.001f
        )
    }
    // 两块内容在动画跑完之前都得留着画，跑完再从组合里摘掉（省得日历在卡片页白跑动画）
    var calendarMounted by remember { mutableStateOf(state.selectedDay == null) }
    var cardMounted by remember { mutableStateOf(state.selectedDay != null) }
    // 退出动画期间 selectedDay 已是 null，还要用最后这一天把卡片画完
    var lastSelectedDay by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.selectedDay) {
        val day = state.selectedDay
        if (day != null) {
            lastSelectedDay = day
            cardMounted = true
            cardFraction.animateTo(1f, cardSpring)
            calendarMounted = false
        } else {
            calendarMounted = true
            cardFraction.animateTo(0f, cardSpring)
            cardMounted = false
        }
    }

    // 是否有计划正在计时：用于防烧屏（页面持续微动 + 隐藏 Dock）
    val anyPlanRunning = state.timers.values.any { it.running }

    // 「现在」建议：每分钟刷新一次时钟，让「夜深了」这类建议能自然切换
    var nowMinute by remember { mutableStateOf(DateUtils.minuteOfDay(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMinute = DateUtils.minuteOfDay(System.currentTimeMillis())
        }
    }
    val todayStart = remember { DateUtils.startOfDay(System.currentTimeMillis()) }
    val nowTip = remember(state.plans, state.monthLogs, state.timers, nowMinute) {
        NowAdvice.of(
            plans = viewModel.plansOfDay(state, todayStart).map { plan ->
                NowPlan(
                    id = plan.id,
                    title = plan.title,
                    icon = plan.icon,
                    todayMinutes = (viewModel.daySecondsOf(state, plan.id, todayStart) / 60L).toInt(),
                    goalMinutes = plan.dailyGoalMinutes,
                    running = state.timers[plan.id]?.running == true
                )
            },
            nowMinutes = nowMinute
        )
    }

    // 同步「当日卡片页」展开状态：主界面据此判断是否隐藏 Dock（月视图不隐藏）
    LaunchedEffect(state.selectedDay) {
        DayDetailState.set(state.selectedDay != null)
    }
    DisposableEffect(Unit) {
        onDispose { DayDetailState.set(false) }
    }

    // 卡片页（已选中某天）时接管系统返回，并接上 Android 的「预测式返回」：
    // 从屏幕边缘往里滑，progress 0→1 直接映射成收展比例，日历和卡片跟着手指实时走；
    // 松手完成手势 = 真的按了返回，中途放弃则弹回卡片页。
    val backScope = androidx.compose.runtime.rememberCoroutineScope()
    // 旅游覆盖层打开时让出返回手势，否则两层会同时响应
    val travelTripId by TravelOverlayState.tripId.collectAsState()
    PredictiveBackHandler(enabled = state.selectedDay != null && travelTripId == null) { progress ->
        try {
            progress.collect { event ->
                // 手势一动就把日历装回来，否则它会被当成「已卸载」而看不到跟手效果
                if (!calendarMounted) calendarMounted = true
                cardFraction.snapTo((1f - event.progress * BACK_FOLLOW).coerceIn(0f, 1f))
            }
            // 手势走完：执行返回
            viewModel.clearSelection()
        } catch (e: CancellationException) {
            // 手势取消：卡片页弹回原位。
            // 此刻本协程已被取消，animateTo 会立刻抛 Cancellation，得另起一个作用域来弹回。
            backScope.launch {
                cardFraction.animateTo(1f, cardSpring)
                calendarMounted = false
            }
            throw e
        }
    }

    // 搜索态拦截返回键：先退出搜索
    androidx.activity.compose.BackHandler(enabled = searchActive && travelTripId == null) {
        searchActive = false
        viewModel.clearQuery()
    }

    // 添加计划/备注弹层
    var showAddPlan by remember { mutableStateOf(false) }
    var showQuickPlan by remember { mutableStateOf(false) }
    // 新建旅游规划弹层
    var showNewTrip by remember { mutableStateOf(false) }
    // 收工小结弹层
    var showWrap by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 通用提示弹窗（存为模板、导出到日历等）
    var hintDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 导出到日历：先记下要导出的计划，再走系统文件选择器
    var icsPlan by remember { mutableStateOf<PlanEntity?>(null) }
    val icsOf: (PlanEntity) -> IcsPlan = { plan ->
        IcsPlan(
            id = plan.id,
            title = plan.title,
            note = plan.note,
            start = plan.startDate,
            end = plan.endDate,
            dailyGoalMinutes = plan.dailyGoalMinutes
        )
    }
    val icsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val plan = icsPlan
        icsPlan = null
        if (uri != null && plan != null) {
            val payload = icsOf(plan)
            scope.launch {
                val ok = runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Ics.forPlan(payload).toByteArray())
                    } ?: error("无法写入")
                }.isSuccess
                hintDialog = "导出到日历" to if (ok) {
                    "已导出「${Ics.fileNameOf(payload)}」。用系统日历或 Google 日历打开它，" +
                        "这个计划的每一天都会以整天事件出现。"
                } else {
                    "导出失败，请换一个保存位置再试。"
                }
            }
        }
    }

    // 多选管理模式（纯 UI 状态）：选中日卡片页点击"管理"进入
    var manageMode by remember { mutableStateOf(false) }
    val selectedIds by viewModel.selectMode.collectAsState()
    // 长按单卡片弹出的操作菜单：查看统计 / 编辑时长 / 删除
    var menuPlan by remember { mutableStateOf<PlanEntity?>(null) }

    // 计划投入统计弹层：点卡片打开
    var insightPlan by remember { mutableStateOf<PlanEntity?>(null) }
    // 编辑今日时长对话框的目标计划
    var editPlan by remember { mutableStateOf<PlanEntity?>(null) }
    // 删除确认对话框：单删时为该计划，批量删时为 null（用 selectedIds 判断）
    var deleteConfirmPlan by remember { mutableStateOf<PlanEntity?>(null) }
    var deleteConfirmBatch by remember { mutableStateOf(false) }
    // 按日期范围删除计划对话框
    var showRangeDelete by remember { mutableStateOf(false) }
    // 计划模板：添加计划与一句话建计划共用，点一下即可复用
    val templates by container.settingsStore.planTemplates.collectAsState(initial = emptyList())
    /** 记住一条模板 */
    val rememberTemplate: (PlanTemplate) -> Unit = { template ->
        scope.launch { container.settingsStore.rememberPlanTemplate(template) }
    }
    /** 删除一条模板 */
    val forgetTemplate: (PlanTemplate) -> Unit = { template ->
        scope.launch { container.settingsStore.removePlanTemplate(template.id) }
    }

    // 月历翻页器：跟手拖动 + 惯性吸附，页号即月份偏移
    val pagerState = rememberPagerState(initialPage = PAGER_INITIAL_PAGE) { PAGER_PAGE_COUNT }
    // 吸附完成后把目标月同步给 ViewModel（触发当月计时记录查询）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.setMonthOffset(page - PAGER_INITIAL_PAGE)
        }
    }

    // 反向同步：搜索跳转或周条跨月翻周改了月份时，把翻页器一并挪到对应页
    LaunchedEffect(state.monthOffset) {
        val target = PAGER_INITIAL_PAGE + state.monthOffset
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        HomeSearchField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            onClose = { viewModel.clearQuery() }
                        )
                    } else {
                        Text(
                            // 日历展开时标题跟随翻页器目标页，拖动过半即预告新月
                            text = state.selectedDay?.let { dayTitleFormat.format(Date(it)) }
                                ?: monthTitleFormat.format(Date(monthStartForOffset(pagerState.targetPage - PAGER_INITIAL_PAGE))),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                },
                actions = {
                    if (searchActive) {
                        // 退出搜索：清空关键词回到日历
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.clearQuery()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "退出搜索")
                        }
                    } else if (state.selectedDay == null) {
                        // 计划统计入口：日历里的计划投入总览（数据源是 plan_logs，不是番茄记录）
                        IconButton(onClick = onOpenPlanStats) {
                            Icon(Icons.Default.Insights, contentDescription = "计划统计")
                        }

                        // 搜索入口（仅日历展开时提供）
                        IconButton(onClick = {
                            manageMode = false
                            viewModel.exitSelectMode()
                            searchActive = true
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }

                        // 功能菜单：添加计划 / 备注 / 旅游规划 / 删除计划，点击向下展开
                        ExpandableFab(
                            items = listOf(
                                FabMenuItem(icon = "⚡", label = "一句话建计划", onClick = { showQuickPlan = true }),
                                FabMenuItem(icon = "📋", label = "添加计划", onClick = { showAddPlan = true }),
                                FabMenuItem(
                                    icon = "📝",
                                    label = "备注",
                                    onClick = {
                                        // 新备注默认关联当前查看的日期（未选中日则为今天）
                                        onOpenNote(
                                            -1L,
                                            state.selectedDay ?: DateUtils.startOfDay(System.currentTimeMillis())
                                        )
                                    }
                                ),
                                FabMenuItem(icon = "🧳", label = "添加旅游规划", onClick = { showNewTrip = true }),
                                FabMenuItem(icon = "🗑️", label = "删除计划", onClick = { showRangeDelete = true })
                            )
                        )
                    } else {
                        // 选中日卡片页：管理 / 取消多选
                        TextButton(onClick = {
                            if (manageMode) {
                                manageMode = false
                                viewModel.exitSelectMode()
                            } else {
                                manageMode = true
                            }
                        }) {
                            Text(if (manageMode) "取消" else "管理")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        // 搜索 ⇄ 日历用淡入淡出过渡，退出搜索不再「唰」地一下
        Crossfade(
            targetState = searchActive,
            animationSpec = tween(durationMillis = 220),
            label = "searchTransition"
        ) { searching ->
        if (searching) {
            SearchResultList(
                results = results,
                query = query,
                onOpenPlan = { plan ->
                    searchActive = false
                    viewModel.focusPlan(plan)
                },
                onOpenNote = { noteId, day ->
                    searchActive = false
                    viewModel.clearQuery()
                    onOpenNote(noteId, day)
                },
                onOpenTrip = { trip ->
                    searchActive = false
                    viewModel.clearQuery()
                    onOpenTravel(trip.id)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 月历区：始终按整屏高测量，只露出 (1 - 进度) 的高度，其余裁掉。
                // 内容全程不重新排版，所以收起时不会把日历「压扁」，就是单纯被推上去。
                if (calendarMounted) {
                    // 填满剩余屏幕高度：星期表头固定，月份网格按行均分其余空间；
                    // 底部留出系统导航条 + 悬浮 Dock 栏（62+10*2=82dp，再加 6dp 间隙）的空间
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .topPane { 1f - cardFraction.value }
                            .graphicsLayer { alpha = (1f - cardFraction.value).coerceIn(0f, 1f) }
                            .navigationBarsPadding()
                            .padding(bottom = 88.dp)
                            // 计划计时中缓慢漂移，防止长期停留烧屏
                            .antiBurnInDrift(anyPlanRunning)
                    ) {
                        // 「现在」建议条：一句话提示此刻最值得做的事，点一下就能开始。
                        // shownMonth 为 0 表示首帧还没取到数据，此时先不渲染，避免闪一下「今天还没有计划」
                        if (state.shownMonth > 0L) {
                            NowStrip(
                                tip = nowTip,
                                onClick = {
                                    when {
                                        nowTip.kind == NowKind.NO_PLAN -> showQuickPlan = true
                                        nowTip.planId != null && nowTip.actionText.isNotEmpty() ->
                                            nowTip.planId?.let { viewModel.toggleTimer(it) }
                                        else -> viewModel.selectDay(todayStart)
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp)
                            )
                        }

                        // 星期表头（固定不动，月份网格在其下方滑动）
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            weekHeaders.forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        // 月份网格翻页器：左右滑动跟手移动，松手后惯性吸附到相邻月（一次至多翻一页）
                        HorizontalPager(
                            state = pagerState,
                            flingBehavior = PagerDefaults.flingBehavior(
                                state = pagerState,
                                pagerSnapDistance = PagerSnapDistance.atMost(1)
                            ),
                            beyondBoundsPageCount = 1,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) { page ->
                            HomeMonthGrid(
                                state = state,
                                viewModel = viewModel,
                                monthStart = monthStartForOffset(page - PAGER_INITIAL_PAGE),
                                onSelectDay = { day ->
                                    if (!pagerState.isScrollInProgress) viewModel.selectDay(day)
                                }
                            )
                        }
                    }
                }

                // 选中日后：渐变方形计划卡片。
                // 它拿的是日历让出来的剩余高度（weight 自动跟随），所以进入时「日历走多少它长多少」，
                // 退出时「日历回来多少它被挤掉多少」——进出是同一条曲线，手势中途也严格跟手。
                val cardDay = if (cardMounted) (state.selectedDay ?: lastSelectedDay) else null
                cardDay?.let { day ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                    ) {
                    // 收工入口只在「今天」出现：有计时在跑，或到了晚上
                    val wrap = remember(day, state.plans, state.monthLogs, state.timers, nowMinute, todayStart) {
                        if (day != todayStart) {
                            null
                        } else {
                            DayWraps.of(
                                plans = viewModel.plansOfDay(state, day).map { plan ->
                                    WrapPlan(
                                        id = plan.id,
                                        title = plan.title,
                                        icon = plan.icon,
                                        minutes = (viewModel.daySecondsOf(state, plan.id, day) / 60L).toInt(),
                                        goalMinutes = plan.dailyGoalMinutes,
                                        running = state.timers[plan.id]?.running == true
                                    )
                                },
                                nowMinutes = nowMinute
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "‹ 返回日历",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                if (manageMode) {
                                    manageMode = false
                                    viewModel.exitSelectMode()
                                }
                                viewModel.clearSelection()
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // 白天不打扰：只有还有计时在跑、或到了晚上才给「收工」
                        if (wrap?.suggest == true) {
                            Text(
                                text = "收工",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showWrap = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 当日备注：按关联日期查询（收工小结要判断今天是否已存过）
                    val dayNotes by container.noteRepository.observeByDate(day).collectAsState(initial = emptyList())
                    val wrapNote = dayNotes.firstOrNull { it.title == DayWrapScript.NOTE_TITLE }

                    if (showWrap && wrap != null) {
                        DayWrapSheet(
                            wrap = wrap,
                            dateLabel = dayTitleFormat.format(Date(day)),
                            noteSaved = wrapNote != null,
                            // 暂停后弹层不关：让用户看到「计时中」消失、达标数更新
                            onPauseAll = { viewModel.pauseAllTimers() },
                            onSaveNote = {
                                scope.launch {
                                    container.noteRepository.add(
                                        title = DayWrapScript.NOTE_TITLE,
                                        content = DayWrapScript.content(wrap, dayTitleFormat.format(Date(day))),
                                        date = day
                                    )
                                }
                            },
                            onWriteNote = {
                                showWrap = false
                                onOpenNote(-1L, todayStart)
                            },
                            onDismiss = { showWrap = false }
                        )
                    }
                    // 周条：整周 7 天一眼看全，点一下直接切到那天（跨月自动翻月）
                    val weekDays = remember(day, weekLogs, state.plans, state.timers, todayStart) {
                        WeekStrip.of(
                            anchor = day,
                            today = todayStart,
                            minutesOf = { target ->
                                viewModel.plansOfDay(state, target).sumOf { plan ->
                                    val saved = weekLogs
                                        .firstOrNull { it.planId == plan.id && it.date == target }
                                        ?.accumulatedSec ?: 0L
                                    val live = if (target == todayStart) {
                                        state.timers[plan.id]?.sessionSec ?: 0L
                                    } else {
                                        0L
                                    }
                                    ((saved + live) / 60L).toInt()
                                }
                            },
                            goalOf = { target ->
                                viewModel.plansOfDay(state, target).sumOf { it.dailyGoalMinutes }
                            }
                        )
                    }
                    WeekStripBar(
                        days = weekDays,
                        selected = day,
                        summary = WeekStrip.summary(weekDays),
                        onSelect = { target -> viewModel.selectDayAnywhere(target) },
                        onShiftWeek = { weeks -> viewModel.selectDayAnywhere(WeekStrip.shift(day, weeks)) },
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                    )

                    val dayPlans = viewModel.plansOfDay(state, day)
                    // 累计投入随落库变化：每次进入日卡片或当月记录更新时重算一次
                    LaunchedEffect(day, state.monthLogs) {
                        viewModel.refreshPlanTotals()
                    }
                    // 备注默认最多展示 3 条，更多可展开（随日期重置）
                    var notesExpanded by remember(day) { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxSize().antiBurnInDrift(anyPlanRunning)) {
                        // 计时中叠加呼吸光晕，画面持续流动
                        if (anyPlanRunning) {
                            AntiBurnInGlow()
                        }
                        // 单格宽度 = (屏宽 - 左右内边距 - 3个列间距) / 4 列
                        val cellWidth = (LocalConfiguration.current.screenWidthDp.dp - 12.dp * 2 - 10.dp * 3) / 4
                        val gridSpacing = 10.dp

                        val dayTrips = viewModel.tripsOfDay(state, day)
                        val planCards = dayPlans.map { plan ->
                            val totalSec = viewModel.daySecondsOf(state, plan.id, day)
                            val paceText = if (day == todayStart) {
                                val allSec = (planTotals[plan.id] ?: 0L) +
                                    (state.timers[plan.id]?.sessionSec ?: 0L)
                                PlanPaces.chipText(
                                    PlanPaces.of(
                                        planStart = plan.startDate,
                                        planEnd = plan.endDate,
                                        goalMinutes = plan.dailyGoalMinutes,
                                        totalSeconds = allSec,
                                        today = todayStart
                                    )
                                )
                            } else {
                                null
                            }
                            DayPlanCardData(
                                plan = plan,
                                todaySec = totalSec,
                                running = state.timers[plan.id]?.running == true,
                                paceText = paceText
                            )
                        }
                        val tripCards = dayTrips.map { DayTripCardData(it) }

                        // 自由画布：卡片位置由数据库决定，不再跟着网格自动重排。
                        // 长按拖动换位置，右下角手柄直接拖大小；点卡片仍打开投入统计。
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp)
                                .padding(bottom = if (manageMode) 200.dp else 170.dp)
                        ) {
                            if (planCards.isEmpty() && tripCards.isEmpty()) {
                                Text(
                                    text = "这一天没有生效中的计划，点右下角圆点添加",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                                )
                            }
                            DayCardBoard(
                                plans = planCards,
                                trips = tripCards,
                                cellWidth = cellWidth,
                                spacing = gridSpacing,
                                manageMode = manageMode,
                                selectedPlanIds = selectedIds,
                                onPlanClick = { plan ->
                                    if (manageMode) {
                                        viewModel.toggleSelectPlan(plan.id)
                                    } else {
                                        insightPlan = plan
                                    }
                                },
                                onPlanLongClick = { plan ->
                                    if (manageMode) {
                                        viewModel.toggleSelectPlan(plan.id)
                                    } else {
                                        menuPlan = plan
                                    }
                                },
                                onTripClick = { trip -> onOpenTravel(trip.id) },
                                onToggleTimer = { planId -> viewModel.toggleTimer(planId) },
                                onToggleSelect = { planId -> viewModel.toggleSelectPlan(planId) },
                                onPlanLayout = { planId, cols, rows, x, y, z ->
                                    viewModel.updateCardLayout(planId, cols, rows, x, y, z)
                                },
                                onTripLayout = { tripId, cols, rows, x, y, z ->
                                    viewModel.updateTripCardLayout(tripId, cols, rows, x, y, z)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 当日备注区：标题 + 最多 3 条预览 + 展开全部
                            if (dayNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "📝 当日备注 · ${dayNotes.size} 条",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val shownNotes = if (notesExpanded) dayNotes else dayNotes.take(3)
                                shownNotes.forEach { note ->
                                    DayNotePreviewCard(note = note, onClick = { onOpenNote(note.id, 0L) })
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (dayNotes.size > 3) {
                                    TextButton(
                                        onClick = { notesExpanded = !notesExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (notesExpanded) {
                                                "收起备注"
                                            } else {
                                                "展开全部 ${dayNotes.size} 条备注"
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                            // 多选模式底部：删除所选按钮
                            if (manageMode && selectedIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 100.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.error)
                                        .clickable { deleteConfirmBatch = true }
                                        .padding(horizontal = 28.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "删除所选 (${selectedIds.size})",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                    }
                    }
                }
            }
        }
        }
    }

    // 首帧数据还没到达：用骨架屏盖住空白日历，加载完淡出
    AnimatedVisibility(
        visible = !state.loaded,
        exit = fadeOut(animationSpec = tween(durationMillis = 300))
    ) {
        CalendarSkeleton()
    }

    // 添加计划弹层
    if (showAddPlan) {
        AddPlanSheet(
            templates = templates,
            onDismiss = { showAddPlan = false },
            onRemember = rememberTemplate,
            onDeleteTemplate = forgetTemplate,
            onSave = { plan ->
                scope.launch { container.planRepository.save(plan) }
                showAddPlan = false
            }
        )
    }

    // 一句话建计划弹层：解析结果直接落库
    if (showQuickPlan) {
        QuickPlanSheet(
            templates = templates,
            onDismiss = { showQuickPlan = false },
            onRemember = rememberTemplate,
            onDeleteTemplate = forgetTemplate,
            onSave = { plan ->
                scope.launch { container.planRepository.save(plan) }
                showQuickPlan = false
            }
        )
    }

    // 新建旅游规划弹层：填标题 + 起止日期，创建空行程后跳旅游规划页
    if (showNewTrip) {
        NewTripDialog(
            onDismiss = { showNewTrip = false },
            onCreate = { title, start, end ->
                showNewTrip = false
                scope.launch {
                    val tripId = container.tripRepository.createTrip(
                        title = title,
                        startDate = start,
                        endDate = end
                    )
                    onOpenTravel(tripId)
                }
            }
        )
    }

    // 长按单卡片操作菜单：编辑今日时长 / 删除该计划
    menuPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { menuPlan = null },
            title = { Text(plan.title) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "长按卡片拖动换位置，右下角拖动改大小",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                insightPlan = plan
                                menuPlan = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("查看投入统计", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editPlan = plan
                                menuPlan = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("编辑今日已完成时长", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val template = PlanTemplates.fromPlan(
                                    title = plan.title,
                                    icon = plan.icon,
                                    dailyMinutes = plan.dailyGoalMinutes,
                                    startAt = plan.startDate,
                                    endAt = plan.endDate,
                                    colorHex = plan.colorHex
                                )
                                menuPlan = null
                                if (template == null) {
                                    hintDialog = "计划模板" to "该计划没有名称，无法存为模板"
                                } else {
                                    rememberTemplate(template)
                                    hintDialog = "计划模板" to
                                        "已把「${template.title}」存为模板，下次添加时点一下就能复用"
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("存为模板（一键复用）", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                icsPlan = plan
                                menuPlan = null
                                icsLauncher.launch(Ics.fileNameOf(icsOf(plan)))
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("导出到日历（.ics）", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {

                                deleteConfirmPlan = plan
                                menuPlan = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("删除该计划", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuPlan = null }) { Text("取消") } }
        )
    }

    // 计划投入统计弹层：点卡片或长按菜单打开
    insightPlan?.let { plan ->
        PlanInsightSheet(
            plan = plan,
            todaySeconds = viewModel.daySecondsOf(
                state = state,
                planId = plan.id,
                dayStart = DateUtils.startOfDay(System.currentTimeMillis())
            ),
            repository = container.planRepository,
            onDismiss = { insightPlan = null }
        )
    }

    // 编辑今日时长对话框：手动补录已完成分钟数
    editPlan?.let { plan ->
        val todayStart = DateUtils.startOfDay(System.currentTimeMillis())
        var minutesText by remember(plan.id) {
            mutableStateOf((viewModel.daySecondsOf(state, plan.id, todayStart) / 60L).toString())
        }
        AlertDialog(
            onDismissRequest = { editPlan = null },
            title = { Text("编辑今日时长") },
            text = {
                Column {
                    Text(
                        text = "为「${plan.title}」设置今天已完成的分钟数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                        label = { Text("分钟") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = minutesText.toIntOrNull() ?: 0
                    viewModel.setTodayMinutes(plan.id, minutes)
                    editPlan = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editPlan = null }) { Text("取消") } }
        )
    }

    // 单个计划删除确认
    deleteConfirmPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleteConfirmPlan = null },
            title = { Text("删除计划") },
            text = { Text("确定删除「${plan.title}」？该计划的所有计时记录将被清除，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlan(plan.id)
                    deleteConfirmPlan = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmPlan = null }) { Text("取消") } }
        )
    }

    // 批量删除确认
    if (deleteConfirmBatch) {
        AlertDialog(
            onDismissRequest = { deleteConfirmBatch = false },
            title = { Text("批量删除") },
            text = { Text("确定删除选中的 ${selectedIds.size} 个计划？所有计时记录将被清除，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    deleteConfirmBatch = false
                    manageMode = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmBatch = false }) { Text("取消") } }
        )
    }

    // 通用提示弹窗
    hintDialog?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { hintDialog = null },
            title = { Text(title) },
            text = { Text(message, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { hintDialog = null }) { Text("好") } },
            dismissButton = null
        )
    }

    // 按日期范围删除计划对话框
    if (showRangeDelete) {
        RangeDeleteDialog(
            plans = state.plans,
            onDismiss = { showRangeDelete = false },
            onDelete = { ids, start, end ->
                // 只移除所选日期范围内的计划部分，范围外日期保留
                viewModel.deletePlansInRange(ids, start, end)
                showRangeDelete = false
            }
        )
    }
}

/**
 * 首页月历网格：单元格内展示日期、至多 2 个事项标签、当日完成度百分比。
 *
 * @param state 首页 UI 状态
 * @param viewModel 首页 ViewModel（取按天数据）
 * @param monthStart 本页展示月份的 1 号 0 点时间戳（翻页器各页面不同）
 * @param onSelectDay 选中日期回调
 */
@Composable
private fun HomeMonthGrid(state: HomeUiState, viewModel: HomeViewModel, monthStart: Long, onSelectDay: (Long) -> Unit) {
    // 网格起点：当月 1 号所在周的周一
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    val leadOffset = if (firstDow == Calendar.SUNDAY) 6 else firstDow - Calendar.MONDAY
    val gridStart = monthStart - leadOffset * DateUtils.DAY_MILLIS
    val today = DateUtils.startOfDay(System.currentTimeMillis())
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        repeat(6) { row ->
            // 6 行按权重均分翻页器高度，屏幕越高格子越高
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                repeat(7) { col ->
                    val dayStart = gridStart + (row * 7 + col) * DateUtils.DAY_MILLIS
                    val inMonth = dayStart >= monthStart && dayStart < monthStart + daysInMonth * DateUtils.DAY_MILLIS
                    DayCellHome(
                        dayStart = dayStart,
                        inMonth = inMonth,
                        isToday = dayStart == today,
                        plans = if (inMonth) viewModel.plansOfDay(state, dayStart) else emptyList(),
                        trips = if (inMonth) viewModel.tripsOfDay(state, dayStart) else emptyList(),
                        state = state,
                        viewModel = viewModel,
                        onClick = { onSelectDay(dayStart) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * 首页日历单元格：日期数字 + 事项标签行（每行即迷你进度条，按当日完成比例加深填充，行尾小字数值）。
 *
 * @param dayStart 当天 0 点时间戳
 * @param inMonth 是否属于当前展示月
 * @param isToday 是否今天
 * @param plans 当天生效计划（标签来源）
 * @param trips 当天生效旅游行程（标签来源，与计划并列）
 * @param state 首页 UI 状态（算当日完成度）
 * @param viewModel 首页 ViewModel（算当日完成度）
 * @param onClick 点击回调
 * @param modifier 布局修饰符
 */
@Composable
private fun DayCellHome(
    dayStart: Long,
    inMonth: Boolean,
    isToday: Boolean,
    plans: List<PlanEntity>,
    trips: List<TripEntity>,
    state: HomeUiState,
    viewModel: HomeViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayNum = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_MONTH)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Column {
            // 日期数字：今天用主色圆底
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
            ) {
                Text(
                    text = dayNum.toString(),
                    fontSize = 11.sp,
                    color = when {
                        isToday -> Color.White
                        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            // 事项标签（计划 + 行程，至多 3 个，超出显示 +N）：计划标签带迷你进度条，行程标签纯色条
            val planLabels = plans.take(3)
            val tripLabels = trips.take((3 - planLabels.size).coerceAtLeast(0))
            planLabels.forEach { plan ->
                val goalSec = plan.dailyGoalMinutes * 60L
                val progress = if (goalSec > 0) {
                    (viewModel.daySecondsOf(state, plan.id, dayStart) * 100 / goalSec).toInt()
                } else {
                    0
                }
                val fraction = progress.coerceIn(0, 100) / 100f
                val baseColor = parseThemeColor(plan.colorHex)
                // 文字统一深色：把填充调淡（0.28），保证深色文字在任何计划色下都读得清
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .drawBehind {
                            // 整行淡色底，再按完成比例叠加深色填充
                            drawRect(baseColor.copy(alpha = 0.18f))
                            if (fraction > 0f) {
                                drawRect(
                                    color = baseColor.copy(alpha = 0.4f),
                                    size = Size(size.width * fraction, size.height)
                                )
                            }
                        }
                        .padding(horizontal = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(baseColor)
                    )
                    Text(
                        text = plan.title,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 2.dp)
                    )
                    Text(
                        text = progress.toString(),
                        fontSize = 7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 行程标签：emoji + 名称，无进度条
            tripLabels.forEach { trip ->
                val baseColor = parseThemeColor(trip.colorHex)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(baseColor.copy(alpha = 0.18f))
                        .padding(horizontal = 3.dp)
                ) {
                    Text(trip.emoji, fontSize = 8.sp)
                    Text(
                        text = trip.title,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 2.dp)
                    )
                }
            }
            if (plans.size + trips.size > 3) {
                Text(
                    text = "+${plans.size + trips.size - 3}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }
        }
    }
}

/**
 * 选中日的渐变方形计划卡片：
 * - 非管理模式：右上角开始/暂停计时，长按弹操作菜单；
 * - 管理模式：左上角勾选圈，点击/长按切换勾选。
 *
 * @param plan 计划实体
 * @param todaySec 当日累计秒数
 * @param running 是否正在计时
 * @param manageMode 是否处于多选管理模式
 * @param selected 是否被勾选
 * @param onToggleTimer 开始/暂停回调
 * @param onClick 点击回调（管理模式下切换勾选）
 * @param onLongClick 长按回调（非管理模式弹菜单 / 管理模式切换勾选）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GradientPlanCard(
    plan: PlanEntity,
    todaySec: Long,
    running: Boolean,
    manageMode: Boolean,
    selected: Boolean,
    paceText: String?,
    cardSize: PlanCardSize,
    cellWidth: Dp,
    gridSpacing: Dp,
    onToggleTimer: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEnterResize: () -> Unit
) {
    val base = parseThemeColor(plan.colorHex)
    // 由计划色派生的深色渐变
    val deep = Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f)
    val goalSec = plan.dailyGoalMinutes * 60L
    val percent = if (goalSec > 0) {
        (todaySec.toFloat() / goalSec.toFloat() * 100f).toInt()
    } else {
        0
    }
    // 倒计时剩余秒数（可为负，超出目标后继续计时）
    val remainSec = goalSec - todaySec
    // 尺寸只由持久化档位决定：调整在屏幕级弹层里预览，卡片本身不参与实时重排
    val effectiveSize = cardSize
    val targetHeight = cellWidth * cardSize.rows + gridSpacing * (cardSize.rows - 1)
    val animatedHeight by animateDpAsState(targetValue = targetHeight, label = "cardHeight")

    // 外层占满 grid slot 宽度（slot 有固定宽度约束，无法直接缩小 item 本身）
    Box(modifier = Modifier.fillMaxWidth().height(animatedHeight)) {
        // 卡片本体：玻璃拟态——半透明渐变 + 白色细边框 + 顶部高光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(base, deep)))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            // 玻璃拟态顶部高光：白色渐变从上到下渐隐，增加深度感
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)))
            )
        if (!manageMode) {
            // 非管理模式：右上角开始/暂停圆钮（小卡缩小避免遮挡 emoji）
            val timerSize = if (effectiveSize == PlanCardSize.SMALL) 28.dp else 36.dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(timerSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
                    .clickable(onClick = onToggleTimer)
            ) {
                if (running) {
                    Text(text = "⏸", color = Color.White, fontSize = if (timerSize == 28.dp) 11.sp else 14.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "开始计时", tint = Color.White, modifier = Modifier.size(if (timerSize == 28.dp) 16.dp else 20.dp))
                }
            }
        } else {
            // 管理模式：左上角勾选圈
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f))
            ) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 响应式内容：按当前尺寸切换布局，保证各档位都清晰易读
        val countdownText = "${DateUtils.formatCountdown(remainSec)} / ${plan.dailyGoalMinutes} 分钟" +
            if (running) " · 计时中" else ""
        when (effectiveSize) {
            PlanCardSize.SMALL -> {
                // 1×1：emoji + 完成率，极简玻璃拟态
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = plan.icon, fontSize = 22.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            PlanCardSize.TALL -> {
                // 1×2：两格高，保留标题/完成率/倒计时/进度条，去掉重复的目标行
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(text = plan.icon, fontSize = 22.sp)
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (paceText != null) {
                        Text(
                            text = paceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "$percent%", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    }
                }
            }
            PlanCardSize.WIDE -> {
                // 2×1：只有一格高，压紧内边距与字号，去掉次要信息，保证不裁字
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = plan.icon, fontSize = 18.sp)
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (paceText != null) {
                            Text(
                                text = paceText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
            PlanCardSize.LARGE -> {
                // 2×2：完整内容 + 进度条，信息最丰富
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(text = plan.icon, fontSize = 32.sp)
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (paceText != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = paceText, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "$percent%", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = countdownText, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    Spacer(modifier = Modifier.height(6.dp))
                    // 进度条：完成率可视化
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "今日 ${todaySec / 60}分 · 目标 ${plan.dailyGoalMinutes}分" + if (running) " · 计时中" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            PlanCardSize.TALL3 -> {
                // 1×3：超高卡——完整内容 + 进度条 + 每日目标，纵向信息最丰富
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(text = plan.icon, fontSize = 36.sp)
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (paceText != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = paceText, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 玻璃拟态统计容器：半透明白底展示完成率与倒计时
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(text = "$percent%", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = countdownText, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                            Text(
                                text = "今日 ${todaySec / 60} 分钟",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // 进度条：完成率可视化
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "每日目标 ${plan.dailyGoalMinutes} 分钟" + if (running) " · 计时中" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            PlanCardSize.WIDE3 -> {
                // 3×1：一格高、三列宽——只保留最关键的三个信息，纵向压紧
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1.3f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = plan.icon, fontSize = 20.sp)
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                            )
                        }
                        Text(
                            text = " ${plan.dailyGoalMinutes}分",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // 右下角调整手柄：点开屏幕级调整弹层（卡片本身不参与拖动重排）
        if (!manageMode) {
            CardResizeHandle(
                onClick = onEnterResize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
            )
        }
        }
    }
}
/** 范围删除日期显示格式 */
private val rangeDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

/**
 * 计划卡片尺寸：日历网格中占用的列数与行数。
 *
 * @property code 持久化编码
 * @property cols 占用列数（1~3）
 * @property rows 占用行数（1~3）
 * @property label 展示标签
 */
enum class PlanCardSize(val code: Int, val cols: Int, val rows: Int, val label: String) {
    /** 1×1 小卡 */
    SMALL(0, 1, 1, "1×1"),

    /** 1×2 高卡 */
    TALL(1, 1, 2, "1×2"),

    /** 2×1 宽卡 */
    WIDE(2, 2, 1, "2×1"),

    /** 2×2 大卡 */
    LARGE(3, 2, 2, "2×2"),

    /** 1×3 超高卡 */
    TALL3(4, 1, 3, "1×3"),

    /** 3×1 超宽卡 */
    WIDE3(5, 3, 1, "3×1");

    companion object {
        /**
         * 按持久化编码取尺寸；非法值回退为 1×1。
         *
         * @param code 编码
         * @return 对应尺寸
         */
        fun of(code: Int): PlanCardSize = entries.firstOrNull { it.code == code } ?: SMALL

        /**
         * 按列数与行数组合尺寸。
         *
         * @param cols 列数
         * @param rows 行数
         * @return 对应尺寸
         */
        fun fromColsRows(cols: Int, rows: Int): PlanCardSize = when {
            cols >= 3 -> WIDE3
            rows >= 3 -> TALL3
            cols >= 2 && rows >= 2 -> LARGE
            cols >= 2 -> WIDE
            rows >= 2 -> TALL
            else -> SMALL
        }
    }
}

/**
 * 按日期范围删除计划对话框：选起止日期，列出范围内生效的计划，勾选删除计划实体及其全部计时记录。
 *
 * @param plans 全部未归档计划
 * @param onDismiss 关闭回调
 * @param onDelete 删除回调（选中的计划 ID 集合, 起始日期, 结束日期）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDeleteDialog(
    plans: List<PlanEntity>,
    onDismiss: () -> Unit,
    onDelete: (Set<Long>, Long, Long) -> Unit
) {
    /** 当前显示月的 1 号 0 点 */
    var displayMonth by remember {
        mutableStateOf(Calendar.getInstance().apply {
            timeInMillis = DateUtils.startOfDay(System.currentTimeMillis())
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis)
    }
    /** 起始日期 0 点时间戳 */
    var rangeStart by remember { mutableStateOf<Long?>(null) }
    /** 结束日期 0 点时间戳 */
    var rangeEnd by remember { mutableStateOf<Long?>(null) }
    /** 已勾选的计划 ID */
    val selectedPlans = remember { mutableStateListOf<Long>() }

    // 范围内生效的计划：计划生效区间与选择区间有交集
    val filteredPlans = if (rangeStart != null && rangeEnd != null) {
        plans.filter { it.startDate <= rangeEnd!! && it.endDate >= rangeStart!! }
    } else {
        emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按日期范围删除") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 已选范围提示
                val rangeHint = when {
                    rangeStart != null && rangeEnd != null -> "${rangeDateFormat.format(Date(rangeStart!!))} ~ ${rangeDateFormat.format(Date(rangeEnd!!))}"
                    rangeStart != null -> "已选起始 ${rangeDateFormat.format(Date(rangeStart!!))}，请选结束日期"
                    else -> "点击日期选择起始，再选结束"
                }
                Text(rangeHint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                // 月份翻页
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = {
                        displayMonth = Calendar.getInstance().apply {
                            timeInMillis = displayMonth
                            add(Calendar.MONTH, -1)
                        }.timeInMillis
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    Text(monthTitleFormat.format(Date(displayMonth)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = {
                        displayMonth = Calendar.getInstance().apply {
                            timeInMillis = displayMonth
                            add(Calendar.MONTH, 1)
                        }.timeInMillis
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
                // 星期表头
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekHeaders.forEach { h ->
                        Text(h, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                // 日期网格
                RangeMonthGrid(
                    displayMonth = displayMonth,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    onDateSelected = { clicked ->
                        when {
                            rangeStart == null -> rangeStart = clicked
                            rangeEnd == null -> {
                                if (clicked < rangeStart!!) {
                                    rangeStart = clicked
                                    rangeEnd = null
                                } else if (clicked == rangeStart) {
                                    rangeEnd = clicked
                                } else {
                                    rangeEnd = clicked
                                }
                            }
                            else -> {
                                rangeStart = clicked
                                rangeEnd = null
                            }
                        }
                        // 范围变化后，清理不再属于新范围的勾选，避免误删
                        val s = rangeStart
                        val e = rangeEnd
                        if (s != null && e != null) {
                            val validIds = plans.filter { it.startDate <= e && it.endDate >= s }.map { it.id }
                            selectedPlans.retainAll(validIds)
                        } else {
                            selectedPlans.clear()
                        }
                    }
                )
                // 范围内计划列表
                if (filteredPlans.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("范围内生效 ${filteredPlans.size} 个计划，勾选要删除的", style = MaterialTheme.typography.labelMedium)
                    filteredPlans.forEach { plan ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (plan.id in selectedPlans) {
                                        selectedPlans.remove(plan.id)
                                    } else {
                                        selectedPlans.add(plan.id)
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = plan.id in selectedPlans,
                                onCheckedChange = {
                                    if (it) {
                                        selectedPlans.add(plan.id)
                                    } else {
                                        selectedPlans.remove(plan.id)
                                    }
                                }
                            )
                            Text("${plan.icon} ${plan.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                text = "${plan.dailyGoalMinutes} 分钟/天",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (rangeStart != null && rangeEnd != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("该日期范围内没有生效中的计划", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (rangeStart != null && rangeEnd != null) {
                        onDelete(selectedPlans.toSet(), rangeStart!!, rangeEnd!!)
                    }
                },
                enabled = selectedPlans.isNotEmpty() && rangeStart != null && rangeEnd != null && rangeStart!! <= rangeEnd!!
            ) {
                Text(
                    text = "删除 (${selectedPlans.size})",
                    color = if (selectedPlans.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

}
/**
 * 范围删除对话框内嵌的月历日期网格。
 *
 * 以周一为首列，展示 [displayMonth] 所在月的每一天；点击某天触发 [onDateSelected]。
 * 落在 [rangeStart]..[rangeEnd] 区间内的日期高亮背景，起止两端用主色圆形底。
 *
 * @param displayMonth 当前显示月的 1 号 0 点时间戳
 * @param rangeStart 已选起始日期 0 点时间戳，null 表示未选
 * @param rangeEnd 已选结束日期 0 点时间戳，null 表示未选
 * @param onDateSelected 日期点击回调（0 点时间戳）
 */
@Composable
private fun RangeMonthGrid(
    displayMonth: Long,
    rangeStart: Long?,
    rangeEnd: Long?,
    onDateSelected: (Long) -> Unit
) {
    val cal = remember(displayMonth) {
        Calendar.getInstance().apply { timeInMillis = displayMonth }
    }
    val daysInMonth = remember(displayMonth) {
        Calendar.getInstance().apply {
            timeInMillis = displayMonth
            getActualMaximum(Calendar.DAY_OF_MONTH)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    // 周一为首列：Calendar.DAY_OF_WEEK 中周一=2，故偏移 1
    val firstDayOfWeek = remember(displayMonth) {
        Calendar.getInstance().apply {
            timeInMillis = displayMonth
            set(Calendar.DAY_OF_MONTH, 1)
        }.get(Calendar.DAY_OF_WEEK)
    }
    val leadingBlanks = remember(firstDayOfWeek) {
        (firstDayOfWeek - Calendar.MONDAY + 7) % 7
    }
    val todayStart = remember { DateUtils.startOfDay(System.currentTimeMillis()) }
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    // 逐行渲染，最多 6 行
    val totalCells = leadingBlanks + daysInMonth
    val rowCount = (totalCells + 6) / 7
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        for (row in 0 until rowCount) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - leadingBlanks + 1
                    if (dayNum < 1 || dayNum > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val dayStart = remember(displayMonth, dayNum) {
                            Calendar.getInstance().apply {
                                timeInMillis = displayMonth
                                set(Calendar.DAY_OF_MONTH, dayNum)
                            }.timeInMillis
                        }
                        val isStart = rangeStart != null && dayStart == rangeStart
                        val isEnd = rangeEnd != null && dayStart == rangeEnd
                        val inRange = rangeStart != null && rangeEnd != null && dayStart >= rangeStart!! && dayStart <= rangeEnd!!
                        val isToday = dayStart == todayStart
                        val bg = when {
                            isStart || isEnd -> primary
                            inRange -> primaryContainer
                            else -> Color.Transparent
                        }
                        val fg = when {
                            isStart || isEnd -> Color.White
                            inRange -> onSurface
                            else -> onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable { onDateSelected(dayStart) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNum.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = fg,
                                fontWeight = if (isStart || isEnd || isToday) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * 选中日的旅游行程卡片：行程色渐变背景 + emoji + 标题 + 日期范围，点击跳旅游规划页。
 * 支持与计划卡片一致的调整大小能力（右下角可见手柄 + 4 档快捷）。
 *
 * @param trip 行程实体
 * @param cardSize 当前尺寸
 * @param cellWidth 单格宽度
 * @param gridSpacing 网格间距
 * @param resizing 是否处于调整模式
 * @param onClick 点击回调（跳旅游规划页）
 * @param onResizeEnd 松手落库回调
 * @param onEnterResize 进入调整模式
 * @param onExitResize 退出调整模式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GradientTripCard(
    trip: TripEntity,
    cardSize: PlanCardSize,
    cellWidth: Dp,
    gridSpacing: Dp,
    onClick: () -> Unit,
    onEnterResize: () -> Unit
) {
    val base = parseThemeColor(trip.colorHex)
    // 由行程色派生的深色渐变
    val deep = Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f)
    val dateFmt = remember { SimpleDateFormat("M/d", Locale.CHINA) }
    val dateText = if (trip.startDate > 0L && trip.endDate > 0L) {
        "${dateFmt.format(Date(trip.startDate))} - ${dateFmt.format(Date(trip.endDate))}"
    } else {
        ""
    }
    // 尺寸只由持久化档位决定：调整在屏幕级弹层里预览
    val effectiveSize = cardSize
    val targetHeight = cellWidth * cardSize.rows + gridSpacing * (cardSize.rows - 1)
    val animatedHeight by animateDpAsState(targetValue = targetHeight, label = "tripCardHeight")

    // 外层占满 grid slot 宽度（slot 有固定宽度约束，无法直接缩小 item 本身）
    Box(modifier = Modifier.fillMaxWidth().height(animatedHeight)) {
        // 卡片本体：玻璃拟态——半透明渐变 + 白色细边框 + 顶部高光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(base, deep)))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                .clickable(onClick = onClick)
        ) {
            // 玻璃拟态顶部高光：白色渐变从上到下渐隐
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)))
            )
        // 响应式内容：按尺寸切换布局，与计划卡片视觉风格一致
        when (effectiveSize) {
            PlanCardSize.SMALL -> {
                // 1×1：emoji + 标题缩写，极简
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(trip.emoji, fontSize = 24.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = trip.title.take(4),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PlanCardSize.TALL -> {
                // 1×2：垂直完整
                Column(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(trip.emoji, fontSize = 28.sp)
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dateText.isNotEmpty()) {
                        Text(text = dateText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            PlanCardSize.WIDE -> {
                // 2×1：横向——左 emoji+标题，右 日期
                Row(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trip.emoji, fontSize = 28.sp)
                        Text(
                            text = trip.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (dateText.isNotEmpty()) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            PlanCardSize.LARGE -> {
                // 2×2：完整 + 备注
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(trip.emoji, fontSize = 36.sp)
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dateText.isNotEmpty()) {
                        Text(text = dateText, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (trip.note.isNotBlank()) {
                        Text(
                            text = trip.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            PlanCardSize.TALL3 -> {
                // 1×3：超高卡——完整信息 + 日期胶囊 + 备注容器，纵向最丰富
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(trip.emoji, fontSize = 40.sp)
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dateText.isNotEmpty()) {
                        // 玻璃拟态日期胶囊
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(text = dateText, style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (trip.note.isNotBlank()) {
                        // 玻璃拟态备注容器
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = trip.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            PlanCardSize.WIDE3 -> {
                // 3×1：超宽卡——三栏：左 emoji+标题，中 日期胶囊，右 备注
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左栏：标识
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(trip.emoji, fontSize = 36.sp)
                        Text(
                            text = trip.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 中栏：日期胶囊
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (dateText.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(text = dateText, style = MaterialTheme.typography.labelLarge, color = Color.White)
                            }
                        }
                    }
                    // 右栏：备注
                    if (trip.note.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = trip.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        // 调整大小覆盖层（与计划卡片共用同一组件）
        CardResizeHandle(
            onClick = onEnterResize,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
        )
        }
    }
}

/**
 * 新建旅游规划对话框：填标题 + 选起止日期，创建后跳旅游规划页。
 *
 * @param onDismiss 关闭回调
 * @param onCreate 创建回调（标题，起始日 0 点，结束日 0 点）
 */
@Composable
private fun NewTripDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, startDate: Long, endDate: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(0L) }
    var endDate by remember { mutableStateOf(0L) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("yyyy/M/d", Locale.CHINA) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加旅游规划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("行程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 起始日期行：点选弹内嵌月历
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { pickingStart = true }.padding(vertical = 4.dp)
                ) {
                    Text("起始日期", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (startDate > 0) dateFmt.format(Date(startDate)) else "未选择",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 结束日期行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { pickingEnd = true }.padding(vertical = 4.dp)
                ) {
                    Text("结束日期", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (endDate > 0) dateFmt.format(Date(endDate)) else "未选择",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim(), startDate, endDate) },
                enabled = title.isNotBlank() && startDate > 0 && endDate >= startDate
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (pickingStart) {
        SimpleMonthPicker(
            onDismiss = { pickingStart = false },
            onPick = {
                startDate = it
                pickingStart = false
            }
        )
    }
    if (pickingEnd) {
        SimpleMonthPicker(
            onDismiss = { pickingEnd = false },
            onPick = {
                endDate = it
                pickingEnd = false
            }
        )
    }
}

/**
 * 简单月份选择器：内嵌 RangeMonthGrid 点选单日，带翻月按钮。
 *
 * @param onDismiss 关闭回调
 * @param onPick 选中某天 0 点时间戳回调
 */
@Composable
private fun SimpleMonthPicker(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    var monthStart by remember { mutableStateOf(monthStartForOffset(0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.MONTH, -1) }
                    monthStart = cal.timeInMillis
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上月")
                }
                Text(
                    text = monthTitleFormat.format(Date(monthStart)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.MONTH, 1) }
                    monthStart = cal.timeInMillis
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下月")
                }
            }
        },
        text = {
            Column {
                // 星期表头
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    weekHeaders.forEach { h ->
                        Text(
                            text = h,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                RangeMonthGrid(
                    displayMonth = monthStart,
                    rangeStart = null,
                    rangeEnd = null,
                    onDateSelected = {
                        onDismiss()
                        onPick(it)
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 日历加载骨架屏：与真实布局同构（标题 / 建议条 / 星期表头 / 6 行格子），
 * 用缓慢呼吸的半透明色块占位，避免刚启动时空荡荡的日历。
 */
@Composable
private fun CalendarSkeleton() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "skeleton")
    val phase by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonPhase"
    )
    val placeholder = scheme.onSurfaceVariant.copy(alpha = phase * 0.16f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 96.dp, bottom = 104.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(placeholder)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(placeholder)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(placeholder)
                    )
                }
            }
            repeat(6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(7) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(placeholder)
                        )
                    }
                }
            }
            Text(
                text = "正在整理你的日历…",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
