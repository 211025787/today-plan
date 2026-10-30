package com.focuslist.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.ui.common.AntiBurnInGlow
import com.focuslist.app.ui.common.ExpandableFab
import com.focuslist.app.ui.common.FabMenuItem
import com.focuslist.app.ui.common.antiBurnInDrift
import com.focuslist.app.ui.note.DayNotePreviewCard
import com.focuslist.app.ui.plan.AddPlanSheet
import kotlinx.coroutines.launch
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 月份标题格式 */
private val monthTitleFormat = SimpleDateFormat("yyyy 年 M 月", Locale.getDefault())

/** 选中日期标题格式 */
private val dayTitleFormat = SimpleDateFormat("M 月 d 日", Locale.getDefault())

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
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarHomeScreen(
    container: AppContainer,
    onOpenNote: (Long, Long) -> Unit = { _, _ -> }
) {
    // LocalContext 需在 Composable 作用域先取出，再传入 ViewModel 工厂
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container.planRepository, appContext) }
    val state by viewModel.uiState.collectAsState()
    // 是否有计划正在计时：用于防烧屏（页面持续微动 + 隐藏 Dock）
    val anyPlanRunning = state.timers.values.any { it.running }

    // 卡片页（已选中某天）时拦截返回键：回到日历而不是退出应用
    androidx.activity.compose.BackHandler(enabled = state.selectedDay != null) {
        viewModel.clearSelection()
    }

    // 添加计划/备注弹层
    var showAddPlan by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 多选管理模式（纯 UI 状态）：选中日卡片页点击"管理"进入
    var manageMode by remember { mutableStateOf(false) }
    val selectedIds by viewModel.selectMode.collectAsState()
    // 长按单卡片弹出的操作菜单：编辑时长 / 删除
    var menuPlan by remember { mutableStateOf<PlanEntity?>(null) }
    // 编辑今日时长对话框的目标计划
    var editPlan by remember { mutableStateOf<PlanEntity?>(null) }
    // 删除确认对话框：单删时为该计划，批量删时为 null（用 selectedIds 判断）
    var deleteConfirmPlan by remember { mutableStateOf<PlanEntity?>(null) }
    var deleteConfirmBatch by remember { mutableStateOf(false) }
    // 按日期范围删除计划对话框
    var showRangeDelete by remember { mutableStateOf(false) }

    // 月历翻页器：跟手拖动 + 惯性吸附，页号即月份偏移
    val pagerState = rememberPagerState(initialPage = PAGER_INITIAL_PAGE) { PAGER_PAGE_COUNT }
    // 吸附完成后把目标月同步给 ViewModel（触发当月计时记录查询）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.setMonthOffset(page - PAGER_INITIAL_PAGE)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // 日历展开时标题跟随翻页器目标页，拖动过半即预告新月
                        text = state.selectedDay?.let { dayTitleFormat.format(Date(it)) }
                            ?: monthTitleFormat.format(Date(monthStartForOffset(pagerState.targetPage - PAGER_INITIAL_PAGE))),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    if (state.selectedDay == null) {
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
            // 右下角圆点：添加计划 / 备注
            Box(modifier = Modifier.padding(bottom = 92.dp)) {
                ExpandableFab(
                    items = listOf(
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
                        )
                    )
                )
            }
        }
    ) { padding ->
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
                }
                val dayPlans = viewModel.plansOfDay(state, day)
                // 当日备注：按关联日期查询
                val dayNotes by container.noteRepository.observeByDate(day).collectAsState(initial = emptyList())
                // 备注默认最多展示 3 条，更多可展开（随日期重置）
                var notesExpanded by remember(day) { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize().antiBurnInDrift(anyPlanRunning)) {
                    // 计时中叠加呼吸光晕，画面持续流动
                    if (anyPlanRunning) {
                        AntiBurnInGlow()
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = if (manageMode) 200.dp else 170.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 当天没有计划时的提示（仍可展示备注）
                        if (dayPlans.isEmpty()) {
                            item(span = { GridItemSpan(2) }, key = "empty_hint") {
                                Text(
                                    text = "这一天没有生效中的计划，点右下角圆点添加",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                                )
                            }
                        }
                        items(dayPlans, key = { it.id }) { plan ->
                            // 当日总秒数 = 库中累计 + 未落库段（暂停冻结也计入）
                            val totalSec = viewModel.daySecondsOf(state, plan.id, day)
                            val isSelected = plan.id in selectedIds
                            GradientPlanCard(
                                plan = plan,
                                todaySec = totalSec,
                                running = state.timers[plan.id]?.running == true,
                                manageMode = manageMode,
                                selected = isSelected,
                                onToggleTimer = { viewModel.toggleTimer(plan.id) },
                                onClick = {
                                    if (manageMode) {
                                        viewModel.toggleSelectPlan(plan.id)
                                    }
                                },
                                onLongClick = {
                                    if (manageMode) {
                                        viewModel.toggleSelectPlan(plan.id)
                                    } else {
                                        menuPlan = plan
                                    }
                                }
                            )
                        }

                        // 当日备注区：标题 + 最多 3 条预览 + 展开全部
                        if (dayNotes.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }, key = "notes_header") {
                                Text(
                                    text = "📝 当日备注 · ${dayNotes.size} 条",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                                )
                            }
                            val shownNotes = if (notesExpanded) dayNotes else dayNotes.take(3)
                            items(shownNotes, key = { "note_${it.id}" }, span = { GridItemSpan(2) }) { note ->
                                DayNotePreviewCard(note = note, onClick = { onOpenNote(note.id, 0L) })
                            }
                            if (dayNotes.size > 3) {
                                item(span = { GridItemSpan(2) }, key = "notes_toggle") {
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

    // 添加计划弹层
    if (showAddPlan) {
        AddPlanSheet(
            onDismiss = { showAddPlan = false },
            onSave = { plan ->
                scope.launch { container.planRepository.save(plan) }
                showAddPlan = false
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
            // 事项标签（至多 3 个，超出显示 +N）：每行即迷你进度条，行尾小字为当日完成率数值
            plans.take(3).forEach { plan ->
                val goalSec = plan.dailyGoalMinutes * 60L
                val progress = if (goalSec > 0) {
                    (viewModel.daySecondsOf(state, plan.id, dayStart) * 100 / goalSec).toInt()
                } else {
                    0
                }
                val fraction = progress.coerceIn(0, 100) / 100f
                val baseColor = parseThemeColor(plan.colorHex)
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
            if (plans.size > 3) {
                Text(
                    text = "+${plans.size - 3}",
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
    onToggleTimer: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(base, deep)))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (!manageMode) {
            // 非管理模式：右上角开始/暂停圆钮
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
                    .clickable(onClick = onToggleTimer)
            ) {
                if (running) {
                    Text(text = "⏸", color = Color.White, fontSize = 14.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "开始计时", tint = Color.White, modifier = Modifier.size(20.dp))
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

        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(text = plan.icon, fontSize = 26.sp)
            Text(
                text = plan.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            // 完成率大数字
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = "${DateUtils.formatCountdown(remainSec)} / ${plan.dailyGoalMinutes} 分钟" +
                    if (running) " · 计时中" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
/** 范围删除日期显示格式 */
private val rangeDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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