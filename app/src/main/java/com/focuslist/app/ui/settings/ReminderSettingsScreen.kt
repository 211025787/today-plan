package com.focuslist.app.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.prefs.PLAN_TONE_CHIME
import com.focuslist.app.data.prefs.PLAN_TONE_FILE
import com.focuslist.app.ui.common.AlertPlayer
import com.focuslist.app.ui.common.ChimePlayer
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.common.TonePlayer
import com.focuslist.app.util.PlanLead

/**
 * 提醒设置页：计划提前几分钟提醒、响铃 / 震动、提醒铃声，以及通知权限的当前状态。
 *
 * 番茄结束的提示音与音色在番茄页维护，这里只做指引，避免同一份设置出现两套入口。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 */
@Composable
fun ReminderSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsStore) }
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var showTonePicker by remember { mutableStateOf(false) }

    // 通知权限：进页面查一次，从系统设置返回后再查一次
    var notificationsOn by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    // 精确闹钟权限：Android 12+ 未授予时系统会把闹钟批量延后，这里给出入口
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var exactAlarmOn by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
                exactAlarmOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 离开提醒页时停掉试听，避免音乐跟到别的页面
    DisposableEffect(Unit) {
        onDispose { TonePlayer.stop() }
    }

    val lead = settings.planReachGoalLeadMinutes
    SettingsDetailScreen(
        title = "提醒",
        subtitle = "计划倒计时快结束时怎么提醒你",
        onBack = onBack
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SettingsSwitchRow(
                    label = "计划到达目标时响铃",
                    description = "一个计划的今日投入达成每日目标时提醒一次",
                    checked = settings.planReachGoalSound
                ) { checked -> viewModel.savePlanReachGoal(checked, settings.planReachGoalVibrate) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsSwitchRow(
                    label = "计划到达目标时震动",
                    checked = settings.planReachGoalVibrate
                ) { checked -> viewModel.savePlanReachGoal(settings.planReachGoalSound, checked) }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsStepperRow(
                    label = "提前提醒",
                    description = if (lead > 0) {
                        "倒计时还剩 $lead 分钟时先响一次，达标时再响一次；0 表示只在达标时提醒"
                    } else {
                        "当前只在每日目标达成时提醒一次"
                    },
                    value = lead,
                    unit = "分钟",
                    range = PlanLead.MIN_MINUTES..PlanLead.MAX_MINUTES
                ) { viewModel.savePlanLeadMinutes(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("提醒铃声", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = toneLabel(
                                ToneState(
                                    settings.planAlertToneKind,
                                    settings.planAlertToneValue,
                                    settings.planAlertToneName,
                                    settings.chimeType
                                )
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    TextButton(onClick = {
                        when (settings.planAlertToneKind) {
                            PLAN_TONE_CHIME -> ChimePlayer.play(
                                context,
                                settings.planAlertToneValue.ifBlank { settings.chimeType }
                            )
                            PLAN_TONE_FILE -> TonePlayer.previewClip(
                                context = context,
                                file = java.io.File(settings.planAlertToneValue),
                                startMs = settings.planAlertToneStartMs.toLong(),
                                endMs = com.focuslist.app.util.RingtoneClip.effectiveEnd(
                                    settings.planAlertToneEndMs.toLong(),
                                    settings.planAlertToneDurationMs.toLong()
                                )
                            )
                            else -> TonePlayer.preview(
                                context,
                                settings.planAlertToneKind,
                                settings.planAlertToneValue
                            )
                        }
                    }) { Text("试听") }
                    TextButton(onClick = { showTonePicker = true }) { Text("更换") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("测试提醒", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "按上面的开关立即播放一次响铃与震动，先确认手机能响",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    TextButton(onClick = {
                        AlertPlayer.play(
                            context = context,
                            chime = settings.chimeType,
                            sound = settings.planReachGoalSound,
                            vibrate = settings.planReachGoalVibrate,
                            soundSec = 3,
                            vibrateSec = 2,
                            toneKind = settings.planAlertToneKind,
                            toneValue = settings.planAlertToneValue,
                            toneStartMs = settings.planAlertToneStartMs.toLong(),
                            toneEndMs = settings.planAlertToneEndMs.toLong(),
                            toneDurationMs = settings.planAlertToneDurationMs.toLong(),
                            // 测试只响 3 秒，避免把整首片段或很长的循环真的放满
                            loop = true,
                            loopDurationSec = 3
                        )
                    }) { Text("测试") }
                }
            }
        }

        // 选中在线歌曲后，展示波形与可拖动的片段范围
        if (settings.planAlertToneKind == PLAN_TONE_FILE) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                RingtoneClipCard(
                    toneValue = settings.planAlertToneValue,
                    startMs = settings.planAlertToneStartMs,
                    endMs = settings.planAlertToneEndMs,
                    durationMs = settings.planAlertToneDurationMs
                ) { startMs, endMs, durationMs ->
                    viewModel.savePlanAlertClip(startMs, endMs, durationMs)
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsSwitchRow(
                    label = "循环播放铃声",
                    description = if (settings.planAlertLoop) {
                        "一直循环到设定时长：${com.focuslist.app.util.RingtoneClip.durationText(settings.planAlertDurationSec / 60, settings.planAlertDurationSec % 60)}"
                    } else {
                        "关闭时只播放一遍就停"
                    },
                    checked = settings.planAlertLoop
                ) { on ->
                    viewModel.savePlanAlertLoop(
                        on,
                        if (on && settings.planAlertDurationSec <= 0) 60 else settings.planAlertDurationSec
                    )
                }
                if (settings.planAlertLoop) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val sec = settings.planAlertDurationSec
                    SettingsStepperRow(
                        label = "循环时长的分钟",
                        description = "与下面的秒数一起组成总时长",
                        value = sec / 60,
                        unit = "分",
                        range = 0..10
                    ) { minutes ->
                        viewModel.savePlanAlertLoop(true, (minutes * 60 + sec % 60).coerceAtLeast(1))
                    }
                    SettingsStepperRow(
                        label = "循环时长的秒数",
                        value = sec % 60,
                        unit = "秒",
                        range = 0..59
                    ) { seconds ->
                        viewModel.savePlanAlertLoop(true, ((sec / 60) * 60 + seconds).coerceAtLeast(1))
                    }
                }
            }
        }

        // 通知权限：没开的话提醒根本发不出去，给一条直达系统设置的入口
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmOn) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("精确提醒权限", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "未开启，系统可能把提醒延后几十分钟。计划计时中仍会按秒准点提醒，但息屏或进程被杀后依赖系统闹钟。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text("去开启") }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("通知权限", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (notificationsOn) {
                                "已允许，提醒可以正常送达。"
                            } else {
                                "已关闭，到期提醒不会出现。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (notificationsOn) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (!notificationsOn) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }) { Text("去开启") }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("还有这些提醒", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "· 提前提醒只在计划正在计时时生效，暂停期间不会响\n" +
                        "· 番茄结束的提示音与音色：在「番茄」页底部设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    if (showTonePicker) {
        RingtonePickerSheet(
            tone = ToneState(
                settings.planAlertToneKind,
                settings.planAlertToneValue,
                settings.planAlertToneName,
                settings.chimeType
            ),
            onPick = { kind, value, name, durationMs ->
                viewModel.savePlanAlertTone(kind, value, name, durationMs)
                showTonePicker = false
            },
            onDismiss = { showTonePicker = false }
        )
    }
}
