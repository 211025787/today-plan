package com.focuslist.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.focuslist.app.ui.common.FabMenuItem
import com.focuslist.app.ui.common.antiBurnInDrift
import com.focuslist.app.ui.note.DayNotePreviewCard
import com.focuslist.app.ui.plan.AddPlanSheet
import com.focuslist.app.ui.plan.PlanInsightSheet
import com.focuslist.app.ui.plan.QuickPlanSheet
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
    onOpenTravel: (Long) -> Unit = {}
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

    // 卡片页（已选中某天）时拦截返回键：回到日历而不是退出应用
    androidx.activity.compose.BackHandler(enabled = state.selectedDay != null) {
        viewModel.clearSelection()
    }

    // 搜索态拦截返回键：先退出搜索
    androidx.activity.compose.BackHandler(enabled = searchActive) {
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
    // 长按菜单「调整卡片大小」进入调整模式的目标计划
    var resizingPlan by remember { mutableStateOf<PlanEntity?>(null) }
    // 进入调整模式的旅游行程目标
    var resizingTrip by remember { mutableStateOf<TripEntity?>(null) }

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
                        // 搜索入口（仅日历展开时提供）
                        IconButton(onClick = {
                            manageMode = false
                            viewModel.exitSelectMode()
                            searchActive = true
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        // 日历展开时允许翻月（与手势滑动共用翻页器，带动画）
                        IconButton(onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一月")
                        }
                        IconButton(onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一月")
                        }
                        // 按日期范围删除计划入口
                        IconButton(onClick = { showRangeDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "范围删除")
                        }
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
        },
        floatingActionButton = {
            // 右下角圆点：添加计划 / 备注；搜索态下隐藏，避免遮挡结果
            if (!searchActive) {
                Box(modifier = Modifier.padding(bottom = 92.dp)) {
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
                            FabMenuItem(icon = "🧳", label = "添加旅游规划", onClick = { showNewTrip = true })
                        )
                    )
                }
            }
        }
    ) { padding ->
        if (searchActive) {
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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 月历区：未选中日期时展示；选中后动画收起
                AnimatedVisibility(
                    visible = state.selectedDay == null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    // 填满剩余屏幕高度：星期表头固定，月份网格按行均分其余空间；
                    // 底部留出系统导航条 + 悬浮 Dock 栏（62+10*2=82dp，再加 6dp 间隙）的空间
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
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
                        // 调整卡片大小期间禁用翻页，避免 pager 抢占水平拖动事件导致左右缩放不跟手
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = resizingPlan == null && resizingTrip == null,
                            flingBehavior = PagerDefaults.flingBehavior(
                                state = pagerState,
                                pagerSnapDistance = PagerSnapDistance.atMost(1)
                            ),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) { page ->
                            HomeMonthGrid(
                                state = state,
                                viewModel = viewModel,
                                monthStart = monthStartForOffset(page - PAGER_INITIAL_PAGE),
                                onSelectDay = viewModel::selectDay
                            )
                        }
                    }
                }

                // 选中日后：渐变方形计划卡片
                state.selectedDay?.let { day ->
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

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = if (manageMode) 200.dp else 170.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 当天没有计划时的提示（仍可展示备注）
                            if (dayPlans.isEmpty()) {
                                item(span = { GridItemSpan(4) }, key = "empty_hint") {
                                    Text(
                                        text = "这一天没有生效中的计划，点右下角圆点添加",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                            items(
                                dayPlans,
                                key = { it.id },
                                span = { if (resizingPlan?.id == it.id) GridItemSpan(2) else GridItemSpan(PlanCardSize.of(it.cardSize).cols) }
                            ) { plan ->
                                // 当日总秒数 = 库中累计 + 未落库段（暂停冻结也计入）
                                val totalSec = viewModel.daySecondsOf(state, plan.id, day)
                                val isSelected = plan.id in selectedIds
                                // 整体节奏角标：只在今天、且确实落后时出现
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
                                GradientPlanCard(
                                    plan = plan,
                                    todaySec = totalSec,
                                    running = state.timers[plan.id]?.running == true,
                                    manageMode = manageMode,
                                    selected = isSelected,
                                    paceText = paceText,
                                    cardSize = PlanCardSize.of(plan.cardSize),
                                    cellWidth = cellWidth,
                                    gridSpacing = gridSpacing,
                                    resizing = resizingPlan?.id == plan.id,
                                    onToggleTimer = { viewModel.toggleTimer(plan.id) },
                                    onClick = {
                                        if (resizingPlan?.id == plan.id) {
                                            resizingPlan = null
                                        } else if (manageMode) {
                                            viewModel.toggleSelectPlan(plan.id)
                                        } else {
                                            insightPlan = plan
                                        }
                                    },
                                    onLongClick = {
                                        if (manageMode) {
                                            viewModel.toggleSelectPlan(plan.id)
                                        } else {
                                            menuPlan = plan
                                        }
                                    },
                                    onResizeEnd = { newSize ->
                                        viewModel.updateCardSize(plan.id, newSize.code)
                                        resizingPlan = null
                                    },
                                    onEnterResize = { resizingPlan = plan },
                                    onExitResize = {
                                        resizingPlan = null
                                    }
                                )
                            }

                            // 当日旅游行程卡片：emoji + 标题 + 日期范围，点击跳旅游规划页
                            val dayTrips = viewModel.tripsOfDay(state, day)
                            items(
                                dayTrips,
                                key = { "trip_${it.id}" },
                                span = { if (resizingTrip?.id == it.id) GridItemSpan(2) else GridItemSpan(PlanCardSize.of(it.cardSize).cols) }
                            ) { trip ->
                                GradientTripCard(
                                    trip = trip,
                                    cardSize = PlanCardSize.of(trip.cardSize),
                                    cellWidth = cellWidth,
                                    gridSpacing = gridSpacing,
                                    resizing = resizingTrip?.id == trip.id,
                                    onClick = { onOpenTravel(trip.id) },
                                    onResizeEnd = { newSize ->
                                        viewModel.updateTripCardSize(trip.id, newSize.code)
                                        resizingTrip = null
                                    },
                                    onEnterResize = { resizingTrip = trip },
                                    onExitResize = {
                                        resizingTrip = null
                                    }
                                )
                            }

                            // 当日备注区：标题 + 最多 3 条预览 + 展开全部
                            if (dayNotes.isNotEmpty()) {
                                item(span = { GridItemSpan(4) }, key = "notes_header") {
                                    Text(
                                        text = "📝 当日备注 · ${dayNotes.size} 条",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                                    )
                                }
                                val shownNotes = if (notesExpanded) dayNotes else dayNotes.take(3)
                                items(shownNotes, key = { "note_${it.id}" }, span = { GridItemSpan(4) }) { note ->
                                    DayNotePreviewCard(note = note, onClick = { onOpenNote(note.id, 0L) })
                                }
                                if (dayNotes.size > 3) {
                                    item(span = { GridItemSpan(4) }, key = "notes_toggle") {
                                        TextButton(
                                            onClick = { notesExpanded = !notesExpanded },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (notesExpanded) "收起备注" else "展开全部 ${dayNotes.size} 条备注",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
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
                            .clickable {
                                resizingPlan = plan
                                menuPlan = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("调整卡片大小", style = MaterialTheme.typography.bodyLarge)
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
    resizing: Boolean,
    onToggleTimer: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onResizeEnd: (PlanCardSize) -> Unit,
    onEnterResize: () -> Unit,
    onExitResize: () -> Unit
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
    // 拖动中连续跟手的实时宽高（Dp）；非调整态不参与渲染
    var liveWidth by remember { mutableStateOf(cellWidth * cardSize.cols) }
    var liveHeight by remember { mutableStateOf(cellWidth * cardSize.rows) }
    LaunchedEffect(resizing, cardSize) {
        if (resizing) {
            // 进入调整：以当前档位尺寸为拖动起点
            liveWidth = cellWidth * cardSize.cols + gridSpacing * (cardSize.cols - 1)
            liveHeight = cellWidth * cardSize.rows + gridSpacing * (cardSize.rows - 1)
        }
    }
    // 内容布局尺寸：拖动中取最近档位（步进切换内容），松手后回 cardSize
    val effectiveSize = if (resizing) {
        PlanCardSize.fromColsRows(
            (liveWidth / cellWidth).roundToInt().coerceIn(1, 2),
            (liveHeight / cellWidth).roundToInt().coerceIn(1, 2)
        )
    } else {
        cardSize
    }
    // 高度：拖动中连续跟手（无动画延迟），松手后动画吸附到档位
    val targetHeight = cellWidth * cardSize.rows + gridSpacing * (cardSize.rows - 1)
    val animatedHeight by animateDpAsState(targetValue = targetHeight, label = "cardHeight")
    val displayHeight = if (resizing) liveHeight else animatedHeight

    // 外层占满 grid slot 宽度（slot 有固定宽度约束，无法直接缩小 item 本身）
    Box(modifier = Modifier.fillMaxWidth().height(displayHeight)) {
        // 卡片本体：调整态宽度跟手连续变化，非调整态填满 slot
        Box(
            modifier = Modifier
                .then(if (resizing) Modifier.width(liveWidth) else Modifier.fillMaxWidth())
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(base, deep)))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
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
                // 1×1：emoji + 完成率，极简一眼易懂
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
                // 1×2：垂直完整布局
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Text(text = plan.icon, fontSize = 26.sp)
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
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
                    Text(text = "$percent%", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(text = countdownText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            PlanCardSize.WIDE -> {
                // 2×1：横向——左 emoji+标题+角标，右 完成率+倒计时
                Row(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = plan.icon, fontSize = 26.sp)
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
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
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(text = "$percent%", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(text = countdownText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
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
                    Text(text = "$percent%", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
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
                }
            }
        }

        // 调整大小覆盖层：常驻可见手柄 + 调整模式遮罩/快捷档位/尺寸标签
        CardResizeOverlay(
            cardSize = cardSize,
            cellWidth = cellWidth,
            gridSpacing = gridSpacing,
            resizing = resizing,
            onEnterResize = onEnterResize,
            onResizeLive = { w, h ->
                liveWidth = w
                liveHeight = h
            },
            onResizeEnd = onResizeEnd,
            onExitResize = onExitResize
        )
        }
    }
}
/** 范围删除日期显示格式 */
private val rangeDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

/**
 * 计划卡片尺寸：日历网格中占用的列数与行数。
 *
 * @property code 持久化编码
 * @property cols 占用列数（1 或 2）
 * @property rows 占用行数（1 或 2）
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
    LARGE(3, 2, 2, "2×2");

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
            cols >= 2 && rows >= 2 -> LARGE
            cols >= 2 -> WIDE
            rows >= 2 -> TALL
            else -> SMALL
        }
        }
}

/**
 * 卡片调整大小覆盖层：常驻右下角可见拖动手柄 + 调整模式遮罩/快捷档位/尺寸标签。
 *
 * 借鉴 Notion / iOS 桌面小组件 / Material 调整大小模式：手柄常驻可见（不再隐藏在长按），
 * 点击进入调整模式后提供放大拖动手柄 + 整卡遮罩，拖动跟手连续调整宽高，松手吸附最近档位落库。
 * 计划卡片与旅游卡片共用此组件。
 *
 * @param cardSize 当前尺寸（非调整态）
 * @param cellWidth 单格宽度，用于换算拖动偏移与吸附档位
 * @param gridSpacing 网格间距，用于计算起点与上限的精确宽高
 * @param resizing 是否处于调整模式
 * @param onEnterResize 进入调整模式（点常驻手柄）
 * @param onResizeLive 拖动中实时宽高回调（Dp，连续跟手）
 * @param onResizeEnd 松手或点快捷档位落库回调
 * @param onExitResize 退出调整模式且不落库（点遮罩空白）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.CardResizeOverlay(
    cardSize: PlanCardSize,
    cellWidth: Dp,
    gridSpacing: Dp,
    resizing: Boolean,
    onEnterResize: () -> Unit,
    onResizeLive: (width: Dp, height: Dp) -> Unit,
    onResizeEnd: (PlanCardSize) -> Unit,
    onExitResize: () -> Unit
) {
    val density = LocalDensity.current
    // 拖动起点尺寸（当前档位的实际宽高，含间距）
    val startW = cellWidth * cardSize.cols + gridSpacing * (cardSize.cols - 1)
    val startH = cellWidth * cardSize.rows + gridSpacing * (cardSize.rows - 1)
    // 连续跟手的宽高上下限：单格 0.8 倍 ~ 双格满宽
    val minW = cellWidth * 0.8f
    val maxW = cellWidth * 2 + gridSpacing
    val minH = cellWidth * 0.8f
    val maxH = cellWidth * 2 + gridSpacing
    // 拖动累积偏移（像素）
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    // 由当前偏移算出的连续宽高（Dp），即跟手实时尺寸
    val liveW = (startW + with(density) { dragX.toDp() }).coerceIn(minW, maxW)
    val liveH = (startH + with(density) { dragY.toDp() }).coerceIn(minH, maxH)
    // 预览档位：四舍五入到最近格，用于快捷按钮高亮和尺寸标签
    val previewSize = if (resizing) {
        PlanCardSize.fromColsRows(
            (liveW / cellWidth).roundToInt().coerceIn(1, 2),
            (liveH / cellWidth).roundToInt().coerceIn(1, 2)
        )
    } else {
        cardSize
    }
    // 常驻手柄呼吸动画：温和提示"我可拖动"
    val breathe = rememberInfiniteTransition(label = "breathe")
    val breatheScale by breathe.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breatheScale"
    )

    if (!resizing) {
        // 常驻可见手柄：右下角弧形勾状标记 + 呼吸动画，点击进入调整模式
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(36.dp)
                .scale(breatheScale)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onEnterResize() })
                }
        ) {
            // 弧形勾状标记：⌟ 形弧线，提示"可拖动调整大小"
            Canvas(modifier = Modifier.size(26.dp)) {
                val stroke = 2.5.dp.toPx()
                val arcLen = size.width * 0.7f
                drawArc(
                    color = Color.White.copy(alpha = 0.85f),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                    size = Size(arcLen, arcLen),

                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
    } else {
        // 调整模式：整卡半透明遮罩 + 白色边框高亮，点空白退出
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.20f))
                .border(2.dp, Color.White, RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onExitResize() })
                }
        )
        // 右下角拖动手柄：放大弧形勾状标记，手动消费事件跟手拖动调整宽高
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(52.dp)
                .pointerInput(cardSize, cellWidth, gridSpacing) {
                    // 手动消费每个指针事件，阻止外层 HorizontalPager 接管水平拖动
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        dragX = 0f
                        dragY = 0f
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.changedToUp()) {
                                change.consume()
                                // 松手吸附：四舍五入到最近档位落库
                                val w = (startW + with(density) { dragX.toDp() }).coerceIn(minW, maxW)
                                val h = (startH + with(density) { dragY.toDp() }).coerceIn(minH, maxH)
                                val cols = (w / cellWidth).roundToInt().coerceIn(1, 2)
                                val rows = (h / cellWidth).roundToInt().coerceIn(1, 2)
                                onResizeEnd(PlanCardSize.fromColsRows(cols, rows))
                                break
                            }
                            dragX += change.positionChange().x
                            dragY += change.positionChange().y
                            change.consume()
                            // 连续跟手：由当前偏移即时算出宽高并上报
                            val w = (startW + with(density) { dragX.toDp() }).coerceIn(minW, maxW)
                            val h = (startH + with(density) { dragY.toDp() }).coerceIn(minH, maxH)
                            onResizeLive(w, h)
                        }
                    }
                }
        ) {
            // 弧形勾状标记：高对比白色，表示拖动中
            Canvas(modifier = Modifier.size(32.dp)) {
                val stroke = 3.dp.toPx()
                val arcLen = size.width * 0.7f
                drawArc(
                    color = Color.White,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                    size = Size(arcLen, arcLen),

                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        // 左上角当前预览尺寸标签
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = previewSize.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
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
    resizing: Boolean,
    onClick: () -> Unit,
    onResizeEnd: (PlanCardSize) -> Unit,
    onEnterResize: () -> Unit,
    onExitResize: () -> Unit
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
    // 拖动中连续跟手的实时宽高（Dp）；非调整态不参与渲染
    var liveWidth by remember { mutableStateOf(cellWidth * cardSize.cols) }
    var liveHeight by remember { mutableStateOf(cellWidth * cardSize.rows) }
    LaunchedEffect(resizing, cardSize) {
        if (resizing) {
            // 进入调整：以当前档位尺寸为拖动起点
            liveWidth = cellWidth * cardSize.cols + gridSpacing * (cardSize.cols - 1)
            liveHeight = cellWidth * cardSize.rows + gridSpacing * (cardSize.rows - 1)
        }
    }
    // 内容布局尺寸：拖动中取最近档位，松手后回 cardSize
    val effectiveSize = if (resizing) {
        PlanCardSize.fromColsRows(
            (liveWidth / cellWidth).roundToInt().coerceIn(1, 2),
            (liveHeight / cellWidth).roundToInt().coerceIn(1, 2)
        )
    } else {
        cardSize
    }
    // 高度：拖动中连续跟手，松手后动画吸附到档位
    val targetHeight = cellWidth * cardSize.rows + gridSpacing * (cardSize.rows - 1)
    val animatedHeight by animateDpAsState(targetValue = targetHeight, label = "tripCardHeight")
    val displayHeight = if (resizing) liveHeight else animatedHeight

    // 外层占满 grid slot 宽度（slot 有固定宽度约束，无法直接缩小 item 本身）
    Box(modifier = Modifier.fillMaxWidth().height(displayHeight)) {
        // 卡片本体：调整态宽度跟手连续变化，非调整态填满 slot
        Box(
            modifier = Modifier
                .then(if (resizing) Modifier.width(liveWidth) else Modifier.fillMaxWidth())
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(base, deep)))
                .clickable(onClick = onClick)
        ) {
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
        }
        // 调整大小覆盖层（与计划卡片共用同一组件）
        CardResizeOverlay(
            cardSize = cardSize,
            cellWidth = cellWidth,
            gridSpacing = gridSpacing,
            resizing = resizing,
            onEnterResize = onEnterResize,
            onResizeLive = { w, h ->
                liveWidth = w
                liveHeight = h
            },
            onResizeEnd = onResizeEnd,
            onExitResize = onExitResize
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