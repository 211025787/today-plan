package com.focuslist.app.ui.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslist.app.AppContainer
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.timer.FocusTimerService
import com.focuslist.app.ui.common.ChimePlayer
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.common.antiBurnInDrift
import com.focuslist.app.ui.plan.PlanTimerHolder
import com.focuslist.app.timer.TimerPhase
import com.focuslist.app.timer.TimerStateHolder
import com.focuslist.app.util.DateUtils

import kotlinx.coroutines.launch

/**
 * 专注页：番茄钟大圆环、白噪音、任务绑定、今日统计摘要，对齐滴答清单"专注"模块。
 *
 * @param container 依赖容器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(container: AppContainer) {
    val context = LocalContext.current
    // 订阅全局计时状态，服务在后台运行时界面也能实时刷新
    val timer by TimerStateHolder.state.collectAsState()
    // 可关联的当天计划列表
    val todayPlans by container.planRepository.observeActiveToday().collectAsState(initial = emptyList())
    // 当日已落库累计（算计划剩余用）
    val todayLogs by container.planRepository.observeTodayLogs().collectAsState(initial = emptyList())
    // 并行秒表状态（算计划剩余含未落库段）
    val planTimers by PlanTimerHolder.timers.collectAsState()
    // 设置（白噪音类型持久化）
    val settings by container.settingsStore.settings.collectAsState(initial = TimerSettings())
    // 页面协程：写设置等轻量操作
    val scope = rememberCoroutineScope()

    var showTaskPicker by remember { mutableStateOf(false) }
    // 专注设置弹层（番茄参数 / 铃声震动 / 自动化）
    var showSettings by remember { mutableStateOf(false) }

    // 计时状态联动白噪音：WORK 运行中播放，其余停止
    LaunchedEffect(timer.phase, timer.running, settings.whiteNoiseType) {
        if (timer.phase == TimerPhase.WORK && timer.running && settings.whiteNoiseType != "NONE") {
            WhiteNoisePlayer.start(settings.whiteNoiseType)
        } else {
            WhiteNoisePlayer.stop()
        }
    }


    // 今日统计摘要（进入页面时拉一次）
    var todaySummary by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val summary = container.focusRepository.summary()
        todaySummary = "今日已专注 ${summary.todayMinutes} 分钟 · ${summary.todayPomos} 个番茄"
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("专注", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    // 专注设置：番茄参数、铃声/震动、自动化
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "专注设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 是否处于计时中：用于防烧屏（画面持续微动 + 氛围光晕）
            val focusing = timer.running && timer.phase != TimerPhase.IDLE
            val resting = timer.phase == TimerPhase.SHORT_BREAK || timer.phase == TimerPhase.LONG_BREAK
            // 全屏氛围底光：随白噪音模式变化（静音/白噪/棕噪三种光场）
            FocusBackdrop(active = focusing, noiseType = settings.whiteNoiseType)
            // 内容区：空间充足时垂直居中，小屏溢出时可滚动，保证底部按钮不被裁掉
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
                    .antiBurnInDrift(focusing),
                contentAlignment = Alignment.Center
            ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // 今日统计摘要玻璃卡
            if (todaySummary.isNotEmpty()) {
                GlassCard(modifier = Modifier.padding(bottom = 16.dp), cornerRadius = 24.dp) {
                    Text(
                        text = todaySummary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 阶段与任务文案
            val phaseLabel = when (timer.phase) {
                TimerPhase.WORK -> "专注中"
                TimerPhase.SHORT_BREAK -> "短休息"
                TimerPhase.LONG_BREAK -> "长休息"
                TimerPhase.IDLE -> "准备开始"
            }
            Text(text = phaseLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (timer.taskTitle.isNotEmpty()) {
                Text(text = timer.taskTitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 圆形进度环 + 中央时间（外圈套唱片式光晕）
            Box(contentAlignment = Alignment.Center) {
                // 唱片式光环：数十圈同心弧缓慢自转 + 反向扫光 + 呼吸缩放
                TimerAura(
                    active = focusing,
                    resting = resting,
                    modifier = Modifier.size(340.dp)
                )
                val progress = if (timer.totalSec > 0) {
                    1f - timer.remainSec.toFloat() / timer.totalSec.toFloat()
                } else {
                    0f
                }
                val ringColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(modifier = Modifier.size(240.dp)) {
                    drawCircle(color = trackColor, style = Stroke(width = 18f))
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 18f)
                    )
                }
                Text(
                    text = DateUtils.formatCountdown(timer.remainSec),
                    style = MaterialTheme.typography.displayMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "本轮已完成 ${timer.cyclePomos} 个番茄", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(16.dp))

            // 白噪音选择（即时生效并持久化）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NONE" to "🔇 静音", "WHITE" to "🌬️ 白噪", "BROWN" to "🌧️ 棕噪").forEach { (type, label) ->
                    FilterChip(
                        selected = settings.whiteNoiseType == type,
                        onClick = {
                            scope.launch { container.settingsStore.updateWhiteNoise(type) }
                        },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 主操作：大圆形按钮（开始 / 暂停 / 继续）—— 与上方光环呼应的"唱针"式单一主动作
            val primaryIcon = if (timer.running) Icons.Default.Pause else Icons.Default.PlayArrow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        when {
                            timer.phase == TimerPhase.IDLE ->
                                FocusTimerService.startPomodoro(context, null, "")
                            timer.running ->
                                FocusTimerService.sendAction(context, FocusTimerService.ACTION_PAUSE)
                            else ->
                                FocusTimerService.sendAction(context, FocusTimerService.ACTION_RESUME)
                        }
                    }
            ) {
                Icon(
                    imageVector = primaryIcon,
                    contentDescription = when {
                        timer.phase == TimerPhase.IDLE -> "开始专注"
                        timer.running -> "暂停"
                        else -> "继续"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 次要操作：低调的文字按钮，不抢主按钮视觉
            when {
                timer.phase == TimerPhase.IDLE -> TextButton(onClick = { showTaskPicker = true }) {
                    Text("选择计划")
                }
                timer.phase == TimerPhase.WORK -> TextButton(
                    onClick = { FocusTimerService.sendAction(context, FocusTimerService.ACTION_STOP) }
                ) {
                    Text("放弃本轮", color = MaterialTheme.colorScheme.error)
                }
                else -> TextButton(
                    onClick = { FocusTimerService.sendAction(context, FocusTimerService.ACTION_SKIP) }
                ) {
                    Text("跳过休息")
                }
            }
            }
            }
        }
    }

    // 计划选择弹窗：番茄绑定当天计划，时长自动取计划的每日目标
    if (showTaskPicker) {
        AlertDialog(
            onDismissRequest = { showTaskPicker = false },
            title = { Text("选择要专注的计划") },
            text = {
                Column {
                    if (todayPlans.isEmpty()) {
                        Text("今天没有生效中的计划，可先去首页添加")
                    }
                    todayPlans.forEach { plan ->
                        // 计划剩余 = 目标 - 已落库 - 未落库段
                        val savedSec = todayLogs.firstOrNull { it.planId == plan.id }?.accumulatedSec ?: 0L
                        val extraSec = planTimers[plan.id]?.sessionSec ?: 0L
                        val remainSec = plan.dailyGoalMinutes * 60L - savedSec - extraSec
                        val remainMin = (remainSec / 60L).toInt()
                        val remainLabel = if (remainMin > 0) {
                            "剩余 $remainMin 分钟"
                        } else {
                            "已超目标 ${-remainMin} 分钟"
                        }
                        TextButton(
                            onClick = {
                                // 番茄时长由 Service 按计划剩余自动计算
                                FocusTimerService.startPomodoro(
                                    context = context,
                                    taskId = null,
                                    title = plan.title,
                                    durationMin = 0,
                                    planId = plan.id
                                )
                                showTaskPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${plan.icon} ${plan.title}（$remainLabel）",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskPicker = false }) { Text("取消") }
            }
        )
    }

    // 专注设置弹层：番茄参数步进调整、结束时提醒（铃声/震动动画开关）、自动化开关
    if (showSettings) {
        FocusSettingsSheet(
            settings = settings,
            onUpdateDurations = { focus, shortBreak, longBreak, interval ->
                scope.launch { container.settingsStore.updateDurations(focus, shortBreak, longBreak, interval) }
            },
            onUpdateSwitches = { autoBreak, autoPomo, sound, vibrate, strict ->
                scope.launch { container.settingsStore.updateSwitches(autoBreak, autoPomo, sound, vibrate, strict) }
            },
            onUpdateChime = { type ->
                scope.launch { container.settingsStore.updateChime(type) }
            },
            onDismiss = { showSettings = false }
        )
    }
}

/**
 * 专注设置弹层：番茄参数（步进器）、结束时提醒（铃声/震动动画开关）、提示音音色、自动化开关。
 *
 * @param settings 当前设置快照
 * @param onUpdateDurations 保存时长参数回调（专注、短休息、长休息、长休息间隔）
 * @param onUpdateSwitches 保存开关回调（自动休息、自动下一番茄、铃声、震动、严格模式）
 * @param onUpdateChime 保存提示音音色回调
 * @param onDismiss 关闭弹层回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusSettingsSheet(
    settings: TimerSettings,
    onUpdateDurations: (Int, Int, Int, Int) -> Unit,
    onUpdateSwitches: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateChime: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("番茄钟", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            StepperRow(
                label = "专注时长", value = settings.focusMinutes, unit = "分钟", range = 5..120
            ) { v ->
                onUpdateDurations(v, settings.shortBreakMinutes, settings.longBreakMinutes, settings.longBreakInterval)
            }
            StepperRow(
                label = "短休息", value = settings.shortBreakMinutes, unit = "分钟", range = 1..30
            ) { v ->
                onUpdateDurations(settings.focusMinutes, v, settings.longBreakMinutes, settings.longBreakInterval)
            }
            StepperRow(
                label = "长休息", value = settings.longBreakMinutes, unit = "分钟", range = 5..60
            ) { v ->
                onUpdateDurations(settings.focusMinutes, settings.shortBreakMinutes, v, settings.longBreakInterval)
            }
            StepperRow(
                label = "长休息间隔", value = settings.longBreakInterval, unit = "个番茄", range = 2..8
            ) { v ->
                onUpdateDurations(settings.focusMinutes, settings.shortBreakMinutes, settings.longBreakMinutes, v)
            }

            Text(
                "结束时提醒",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            ) {
                ReminderToggle(
                    onIcon = "🔔", offIcon = "🔕", label = "铃声",
                    checked = settings.soundEnabled
                ) { checked ->
                    onUpdateSwitches(settings.autoStartBreak, settings.autoStartNextPomo, checked, settings.vibrateEnabled, settings.strictMode)
                }
                ReminderToggle(
                    onIcon = "📳", offIcon = "📴", label = "震动",
                    checked = settings.vibrateEnabled
                ) { checked ->
                    onUpdateSwitches(settings.autoStartBreak, settings.autoStartNextPomo, settings.soundEnabled, checked, settings.strictMode)
                }
            }

            Text(
                "提示音",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
            )
            // 音色选择：点一下即试听并保存
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ChimePlayer.options.forEach { (type, name, icon) ->
                    FilterChip(
                        selected = settings.chimeType == type,
                        onClick = {
                            onUpdateChime(type)
                            ChimePlayer.play(context, type)
                        },
                        label = { Text("$icon $name") }
                    )
                }
            }
            Text(
                text = "点击试听，选中的音色用于番茄与计划计时结束提醒",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Text("自动化", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            FocusSwitchRow(
                label = "番茄结束后自动开始休息",
                checked = settings.autoStartBreak
            ) { checked ->
                onUpdateSwitches(checked, settings.autoStartNextPomo, settings.soundEnabled, settings.vibrateEnabled, settings.strictMode)
            }
            FocusSwitchRow(
                label = "休息结束后自动开始下一番茄",
                checked = settings.autoStartNextPomo
            ) { checked ->
                onUpdateSwitches(settings.autoStartBreak, checked, settings.soundEnabled, settings.vibrateEnabled, settings.strictMode)
            }
            FocusSwitchRow(
                label = "严格模式（计时期间勿退出）",
                checked = settings.strictMode
            ) { checked ->
                onUpdateSwitches(settings.autoStartBreak, settings.autoStartNextPomo, settings.soundEnabled, settings.vibrateEnabled, checked)
            }
        }
    }
}

/**
 * 参数步进行：名称居左，右侧 [−] 值 [+] 步进按钮。
 *
 * @param label 参数名
 * @param value 当前值
 * @param unit 单位文案
 * @param range 取值范围（含两端）
 * @param onChange 变更回调
 */
@Composable
private fun StepperRow(label: String, value: Int, unit: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        StepButton(symbol = "−", enabled = value > range.first) { onChange(value - 1) }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text(text = unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.size(12.dp))
        StepButton(symbol = "+", enabled = value < range.last) { onChange(value + 1) }
    }
}

/**
 * 圆形步进按钮：到达边界时降低透明度不可点。
 *
 * @param symbol 按钮符号（"−" 或 "+"）
 * @param enabled 是否可点
 * @param onClick 点击回调
 */
@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.2f))
            .clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
        )
    }
}

/**
 * 提醒动画开关：点击后背景色平滑过渡，图标随状态切换（缩放 + 淡入淡出）。
 *
 * @param onIcon 开启时的图标（emoji）
 * @param offIcon 关闭时的图标（emoji）
 * @param label 开关名称
 * @param checked 当前状态
 * @param onChange 切换回调
 */
@Composable
private fun ReminderToggle(onIcon: String, offIcon: String, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // 背景色在主色与灰底之间平滑过渡
    val bgColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(300),
        label = "toggleBg"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable { onChange(!checked) }
        ) {
            // 图标随状态切换：旧图标缩小淡出，新图标放大淡入
            AnimatedContent(
                targetState = if (checked) onIcon else offIcon,
                transitionSpec = {
                    (scaleIn(tween(200)) + fadeIn(tween(200))).togetherWith(
                        scaleOut(tween(150)) + fadeOut(tween(150))
                    )
                },
                label = "toggleIcon"
            ) { icon ->
                Text(text = icon, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$label · ${if (checked) "开" else "关"}",
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 通用开关行。
 *
 * @param label 文案
 * @param checked 当前状态
 * @param onChange 变更回调
 */
@Composable
private fun FocusSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}