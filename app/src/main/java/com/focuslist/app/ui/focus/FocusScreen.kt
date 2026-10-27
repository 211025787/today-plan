package com.focuslist.app.ui.focus

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslist.app.AppContainer
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.timer.FocusTimerService
import com.focuslist.app.ui.common.GlassCard
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
    // 设置（白噪音类型持久化）
    val settings by container.settingsStore.settings.collectAsState(initial = TimerSettings())
    // 页面协程：写设置等轻量操作
    val scope = rememberCoroutineScope()

    var showTaskPicker by remember { mutableStateOf(false) }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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

            // 圆形进度环 + 中央时间
            Box(contentAlignment = Alignment.Center) {
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

            // 控制按钮区
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    timer.phase == TimerPhase.IDLE -> {
                        OutlinedButton(onClick = { showTaskPicker = true }) { Text("选择计划") }
                        Button(onClick = { FocusTimerService.startPomodoro(context, null, "") }) { Text("自由专注") }
                    }
                    else -> {
                        if (timer.running) {
                            Button(onClick = { FocusTimerService.sendAction(context, FocusTimerService.ACTION_PAUSE) }) { Text("暂停") }
                        } else {
                            Button(onClick = { FocusTimerService.sendAction(context, FocusTimerService.ACTION_RESUME) }) { Text("继续") }
                        }
                        if (timer.phase == TimerPhase.WORK) {
                            OutlinedButton(
                                onClick = { FocusTimerService.sendAction(context, FocusTimerService.ACTION_STOP) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("放弃") }
                        } else {
                            OutlinedButton(onClick = { FocusTimerService.sendAction(context, FocusTimerService.ACTION_SKIP) }) { Text("跳过休息") }
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
                        TextButton(
                            onClick = {
                                // 番茄时长 = 计划每日目标时长，完成后累计到计划
                                FocusTimerService.startPomodoro(
                                    context = context,
                                    taskId = null,
                                    title = plan.title,
                                    durationMin = plan.dailyGoalMinutes,
                                    planId = plan.id
                                )
                                showTaskPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${plan.icon} ${plan.title}（${plan.dailyGoalMinutes} 分钟）",
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
}