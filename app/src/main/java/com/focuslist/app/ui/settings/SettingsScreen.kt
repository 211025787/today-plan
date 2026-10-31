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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.ThemeColors
import com.focuslist.app.ui.theme.parseThemeColor

/**
 * 设置页（我的）：按「外观 / 提醒 / 悬浮胶囊 / 关于」分组，
 * 每组一张玻璃卡，避免所有控件挤在一张卡里。
 *
 * @param container 依赖容器
 * @param onOpenCapsule 打开悬浮胶囊设置页回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenCapsule: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsStore) }
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    // 版本号从包信息读取，避免手写
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("我的", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 概览
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("今日规划", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (versionName.isNotEmpty()) "v$versionName · 数据仅保存在本机" else "数据仅保存在本机",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text("🍅", style = MaterialTheme.typography.headlineSmall)
                }
            }

            // 外观
            SectionLabel("外观")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题模式", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.saveTheme(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        "主题色",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        ThemeColors.forEach { (name, hex) ->
                            val color = parseThemeColor(hex)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { viewModel.saveThemeColor(hex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (settings.themeColor == hex) {
                                        Text("✓", color = Color.White)
                                    }
                                }
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 提醒
            SectionLabel("提醒")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SwitchRow(
                        label = "计划到达目标时响铃",
                        checked = settings.planReachGoalSound
                    ) { checked -> viewModel.savePlanReachGoal(checked, settings.planReachGoalVibrate) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SwitchRow(
                        label = "计划到达目标时震动",
                        checked = settings.planReachGoalVibrate
                    ) { checked -> viewModel.savePlanReachGoal(settings.planReachGoalSound, checked) }
                }
            }

            // 悬浮胶囊
            SectionLabel("悬浮胶囊")
            GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCapsule)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("后台计时胶囊", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (settings.capsuleEnabled) {
                                "已开启 · 宽度 ${if (settings.capsuleWidthDp == 0) "自适应" else "${settings.capsuleWidthDp}dp"} · 不透明度 ${settings.capsuleAlpha}%"
                            } else {
                                "未开启，点此设置外观与权限"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 关于
            SectionLabel("关于")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "纯离线应用：所有计划、番茄记录与备注都只保存在本机，不经过网络。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 分组标题（卡片外的小标题）。
 *
 * @param text 标题文案
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp)
    )
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
