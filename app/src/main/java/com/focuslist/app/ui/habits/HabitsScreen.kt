package com.focuslist.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.common.TimePickerDialogWrapper
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.HabitReminders
import com.focuslist.app.widget.WidgetRefresher
import java.util.Calendar

/** 可选习惯图标池 */
private val iconPool = listOf("✅", "📚", "💧", "🏃", "🧘", "🎧", "🦷", "🌞")

/** 近 7 天星期标签（从今天往前 6 天） */
private val weekShortLabels = arrayOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 习惯页：今日打卡 + 近 7 天打卡格子 + 连续天数，对齐滴答清单"打卡"模块。
 *
 * @param container 依赖容器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(container: AppContainer) {
    val context = LocalContext.current.applicationContext
    val viewModel: HabitsViewModel = viewModel {
        HabitsViewModel(container.habitRepository, container.database.habitDao(), context)
    }
    val state by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    // 新建习惯时的提醒时间（null = 不提醒）
    var addReminderAt by remember { mutableStateOf<Long?>(null) }
    var showAddTimePicker by remember { mutableStateOf(false) }
    // 正在改提醒的习惯；非空即打开时间选择器
    var reminderTarget by remember { mutableStateOf<HabitItemState?>(null) }

    // 新建习惯时选提醒时间
    if (showAddTimePicker) {
        TimePickerDialogWrapper(
            initialMillis = addReminderAt,
            onDismiss = { showAddTimePicker = false },
            onConfirm = { hour, minute ->
                addReminderAt = HabitReminders.at(DateUtils.startOfDay(System.currentTimeMillis()), hour, minute)
                showAddTimePicker = false
            }
        )
    }

    // 已有习惯改提醒时间
    reminderTarget?.let { target ->
        TimePickerDialogWrapper(
            initialMillis = target.habit.reminderAt,
            onDismiss = { reminderTarget = null },
            onConfirm = { hour, minute ->
                viewModel.setReminder(
                    target.habit,
                    HabitReminders.at(DateUtils.startOfDay(System.currentTimeMillis()), hour, minute)
                )
                reminderTarget = null
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("习惯打卡", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            // 上移避开悬浮 Dock
            FloatingActionButton(
                onClick = {
                    addReminderAt = null
                    showAddDialog = true
                },
                modifier = Modifier.padding(bottom = 88.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加习惯")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            items(state.items, key = { it.habit.id }) { item ->
                HabitCard(
                    item = item,
                    onToggle = { viewModel.toggle(item) { WidgetRefresher.refreshHabits(context) } },
                    onDelete = { viewModel.deleteHabit(item.habit) },
                    onSetReminder = { reminderTarget = item },
                    onClearReminder = { viewModel.setReminder(item.habit, null) }
                )
            }
            if (state.items.isEmpty()) {
                item {
                    Text(
                        text = "还没有习惯，点击右下角 + 创建一个吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }

    // 新建习惯弹窗：名称 + 图标选择
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var icon by remember { mutableStateOf(iconPool.first()) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新建习惯") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("习惯名称") }, singleLine = true)
                    Text("选择图标", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        iconPool.forEach { emoji ->
                            TextButton(onClick = { icon = emoji }) {
                                Text(
                                    text = emoji,
                                    style = if (emoji == icon) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    // 每日提醒（可选）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showAddTimePicker = true }) {
                            Text(
                                text = if (addReminderAt == null) {
                                    "＋ 每天提醒我"
                                } else {
                                    "⏰ 每天 ${HabitReminders.timeText(addReminderAt)}"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        if (addReminderAt != null) {
                            TextButton(onClick = { addReminderAt = null }) { Text("清除") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addHabit(name, icon, addReminderAt)
                        showAddDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 习惯卡片：勾选 + 名称 + 连续天数 + 近 7 天格子 + 删除。
 *
 * @param item 习惯条目状态
 * @param onToggle 今日打卡切换
 * @param onDelete 删除回调
 */
@Composable
private fun HabitCard(
    item: HabitItemState,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSetReminder: () -> Unit,
    onClearReminder: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.checkedToday, onCheckedChange = { onToggle() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${item.habit.icon} ${item.habit.name}", style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.streak > 0) {
                            Text(
                                text = "🔥 已连续 ${item.streak} 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        // 每日提醒：点一下改时间，右边的 × 关掉
                        val hasReminder = item.habit.reminderAt != null && item.habit.reminderAt > 0L
                        Text(
                            text = if (hasReminder) {
                                "⏰ 每天 ${HabitReminders.timeText(item.habit.reminderAt)}"
                            } else {
                                "⏰ 设个提醒"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasReminder) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onSetReminder)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        if (hasReminder) {
                            Text(
                                text = "×",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onClearReminder)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除习惯")
                }
            }

            // 近 7 天打卡格子
            Spacer(modifier = Modifier.height(8.dp))
            val today = DateUtils.startOfDay(System.currentTimeMillis())
            val habitColor = parseThemeColor(item.habit.colorHex)
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                repeat(7) { offset ->
                    val day = today - (6 - offset) * DateUtils.DAY_MILLIS
                    val checked = item.weekChecks.contains(day)
                    val cal = Calendar.getInstance().apply { timeInMillis = day }
                    val label = weekShortLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (checked) habitColor else MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}