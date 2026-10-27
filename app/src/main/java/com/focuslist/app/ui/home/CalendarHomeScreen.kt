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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.focuslist.app.ui.common.ExpandableFab
import com.focuslist.app.ui.common.FabMenuItem
import com.focuslist.app.ui.note.QuickNoteSheet
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
 * 日历主页（打开即展示）：月历格子内展示当天事项标签与完成度百分比；
 * 点击某天收起日历，切换为该日计划的渐变方形卡片（右上角开始/暂停计时）。
 *
 * @param container 依赖容器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHomeScreen(container: AppContainer) {
    // LocalContext 需在 Composable 作用域先取出，再传入 ViewModel 工厂
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container.planRepository, appContext) }
    val state by viewModel.uiState.collectAsState()

    // 卡片页（已选中某天）时拦截返回键：回到日历而不是退出应用
    androidx.activity.compose.BackHandler(enabled = state.selectedDay != null) {
        viewModel.clearSelection()
    }

    // 添加计划/快速备注弹层
    var showAddPlan by remember { mutableStateOf(false) }
    var showQuickNote by remember { mutableStateOf(false) }
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.selectedDay?.let { dayTitleFormat.format(Date(it)) }
                            ?: monthTitleFormat.format(Date(state.shownMonth)),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    if (state.selectedDay == null) {
                        // 日历展开时允许翻月
                        IconButton(onClick = { viewModel.shiftMonth(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一月")
                        }
                        IconButton(onClick = { viewModel.shiftMonth(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一月")
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
                        FabMenuItem(icon = "📝", label = "备注", onClick = { showQuickNote = true })
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
                Column {
                    // 星期表头
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
                    HomeMonthGrid(
                        state = state,
                        viewModel = viewModel,
                        onSelectDay = viewModel::selectDay
                    )
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

                if (dayPlans.isEmpty()) {
                    Text(
                        text = "这一天没有生效中的计划，点右下角圆点添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = if (manageMode) 200.dp else 170.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
            onDismiss = { showAddPlan = false },
            onSave = { plan ->
                scope.launch { container.planRepository.save(plan) }
                showAddPlan = false
            }
        )
    }

    // 快速备注弹层
    if (showQuickNote) {
        QuickNoteSheet(
            onDismiss = { showQuickNote = false },
            onSave = { content ->
                scope.launch { container.noteRepository.add(content) }
                showQuickNote = false
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
}

/**
 * 首页月历网格：单元格内展示日期、至多 2 个事项标签、当日完成度百分比。
 *
 * @param state 首页 UI 状态
 * @param viewModel 首页 ViewModel（取按天数据）
 * @param onSelectDay 选中日期回调
 */
@Composable
private fun HomeMonthGrid(state: HomeUiState, viewModel: HomeViewModel, onSelectDay: (Long) -> Unit) {
    val monthStart = state.shownMonth
    // 网格起点：当月 1 号所在周的周一
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    val leadOffset = if (firstDow == Calendar.SUNDAY) 6 else firstDow - Calendar.MONDAY
    val gridStart = monthStart - leadOffset * DateUtils.DAY_MILLIS
    val today = DateUtils.startOfDay(System.currentTimeMillis())
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        repeat(6) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayStart = gridStart + (row * 7 + col) * DateUtils.DAY_MILLIS
                    val inMonth = dayStart >= monthStart && dayStart < monthStart + daysInMonth * DateUtils.DAY_MILLIS
                    DayCellHome(
                        dayStart = dayStart,
                        inMonth = inMonth,
                        isToday = dayStart == today,
                        plans = if (inMonth) viewModel.plansOfDay(state, dayStart) else emptyList(),
                        percent = if (inMonth) viewModel.percentOfDay(state, dayStart) else -1,
                        onClick = { onSelectDay(dayStart) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 首页日历单元格：日期数字 + 事项标签 + 完成度百分比。
 *
 * @param dayStart 当天 0 点时间戳
 * @param inMonth 是否属于当前展示月
 * @param isToday 是否今天
 * @param plans 当天生效计划（标签来源）
 * @param percent 当日完成度百分比；-1 表示不显示
 * @param onClick 点击回调
 * @param modifier 布局修饰符
 */
@Composable
private fun DayCellHome(
    dayStart: Long,
    inMonth: Boolean,
    isToday: Boolean,
    plans: List<PlanEntity>,
    percent: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayNum = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_MONTH)
    // Box 结构：日期左上、标签区顶部紧随、完成度绝对钉在右下角，标签再多也不会把百分比挤出去
    Box(
        modifier = modifier
            .height(74.dp)
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
            // 事项标签（至多 2 个，超出显示 +N）
            plans.take(2).forEach { plan ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(parseThemeColor(plan.colorHex).copy(alpha = 0.18f))
                        .padding(horizontal = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(parseThemeColor(plan.colorHex))
                    )
                    Text(
                        text = plan.title,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            if (plans.size > 2) {
                Text(
                    text = "+${plans.size - 2}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }
        }
        // 当日完成度百分比：钉在右下角，不受标签行数影响
        if (percent >= 0 && inMonth) {
            Text(
                text = "$percent%",
                fontSize = 9.sp,
                color = if (percent >= 100) Color(0xFF43A047) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
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
                text = "${DateUtils.formatCountdown(todaySec)} / ${plan.dailyGoalMinutes} 分钟" +
                    if (running) " · 计时中" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}