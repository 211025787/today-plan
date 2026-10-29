package com.focuslist.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.ThemeColors
import com.focuslist.app.ui.theme.parseThemeColor

/**
 * 设置页（我的）：番茄参数、自动化开关、主题、备注入口与关于信息。
 *
 * @param container 依赖容器
 * @param onOpenNotes 打开备注页回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onOpenNotes: () -> Unit = {}) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsStore) }
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("我的", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 主题卡片
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("外观主题", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.saveTheme(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                    // 主题色圆点选择（滴答式多彩主题）
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                        ThemeColors.forEach { (name, hex) ->
                            val color = parseThemeColor(hex)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { viewModel.saveThemeColor(hex) }
                                ) {
                                    if (settings.themeColor == hex) {
                                        Text(
                                            text = "✓",
                                            color = androidx.compose.ui.graphics.Color.White,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                                Text(text = name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 专注白噪音默认设置
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("专注白噪音", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("NONE" to "关闭", "WHITE" to "白噪声", "BROWN" to "棕噪声").forEach { (type, label) ->
                            FilterChip(
                                selected = settings.whiteNoiseType == type,
                                onClick = { viewModel.saveWhiteNoise(type) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 计划倒计时到达目标提醒
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("计划倒计时提醒", style = MaterialTheme.typography.titleSmall)
                    SwitchRow(label = "到达目标时响铃", checked = settings.planReachGoalSound) { checked ->
                        viewModel.savePlanReachGoal(checked, settings.planReachGoalVibrate)
                    }
                    SwitchRow(label = "到达目标时震动", checked = settings.planReachGoalVibrate) { checked ->
                        viewModel.savePlanReachGoal(settings.planReachGoalSound, checked)
                    }
                }
            }

            // 备注入口卡片（FAB 里可添加，这里查看管理）
            GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenNotes)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("📝 我的备注", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 关于卡片
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("关于", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "滴答清单 · 离线版 2.0.0\n纯离线应用：所有任务、番茄记录与习惯数据仅保存在本机，不经过任何网络。\n垃圾桶/已完成入口在任务页左上角抽屉中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 开关行。
 *
 * @param label 文案
 * @param checked 当前状态
 * @param onChange 变更回调
 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}