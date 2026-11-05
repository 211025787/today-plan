package com.focuslist.app.ui.settings

import android.content.Intent
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
import com.focuslist.app.ui.common.GlassCard

/**
 * 提醒设置页：计划达标时的响铃 / 震动，以及通知权限的当前状态。
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

    // 通知权限：进页面查一次，从系统设置返回后再查一次
    var notificationsOn by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsDetailScreen(
        title = "提醒",
        subtitle = "计划达标时怎么提醒你",
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

        // 通知权限：没开的话提醒根本发不出去，给一条直达系统设置的入口
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("通知权限", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (notificationsOn) {
                                "已允许，提醒可以正常送达。"
                            } else {
                                "已关闭，习惯提醒与到期提醒都不会出现。"
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
                    text = "· 番茄结束的提示音与音色：在「番茄」页底部设置\n" +
                        "· 每个习惯的每日提醒时间：在「习惯」页为单个习惯设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
