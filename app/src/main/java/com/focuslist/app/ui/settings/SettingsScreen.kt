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
import androidx.compose.material3.Slider
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
import kotlin.math.roundToInt

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
            // 计时参数卡片
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("番茄钟参数", style = MaterialTheme.typography.titleSmall)
                    DurationSlider(
                        label = "专注时长", value = settings.focusMinutes, range = 5f..120f, unit = "分钟"
                    ) { value ->
                        viewModel.saveDurations(value, settings.shortBreakMinutes, settings.longBreakMinutes, settings.longBreakInterval)
                    }
                    DurationSlider(
                        label = "短休息", value = settings.shortBreakMinutes, range = 1f..30f, unit = "分钟"
                    ) { value ->
                        viewModel.saveDurations(settings.focusMinutes, value, settings.longBreakMinutes, settings.longBreakInterval)
                    }
                    DurationSlider(
                        label = "长休息", value = settings.longBreakMinutes, range = 5f..60f, unit = "分钟"
                    ) { value ->
                        viewModel.saveDurations(settings.focusMinutes, settings.shortBreakMinutes, value, settings.longBreakInterval)
                    }
                    DurationSlider(
                        label = "长休息间隔", value = settings.longBreakInterval, range = 2f..8f, unit = "个番茄"
                    ) { value ->
                        viewModel.saveDurations(settings.focusMinutes, settings.shortBreakMinutes, settings.longBreakMinutes, value)
                    }
                }
            }

            // 行为开关卡片
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("自动化", style = MaterialTheme.typography.titleSmall)
                    SwitchRow(label = "番茄结束后自动开始休息", checked = settings.autoStartBreak) { checked ->
                        viewModel.saveSwitches(checked, settings.autoStartNextPomo, settings.soundEnabled, settings.vibrateEnabled, settings.strictMode)
                    }
                    SwitchRow(label = "休息结束后自动开始下一番茄", checked = settings.autoStartNextPomo) { checked ->
                        viewModel.saveSwitches(settings.autoStartBreak, checked, settings.soundEnabled, settings.vibrateEnabled, settings.strictMode)
                    }
                    SwitchRow(label = "结束时响铃", checked = settings.soundEnabled) { checked ->
                        viewModel.saveSwitches(settings.autoStartBreak, settings.autoStartNextPomo, checked, settings.vibrateEnabled, settings.strictMode)
                    }
                    SwitchRow(label = "结束时震动", checked = settings.vibrateEnabled) { checked ->
                        viewModel.saveSwitches(settings.autoStartBreak, settings.autoStartNextPomo, settings.soundEnabled, checked, settings.strictMode)
                    }
                    SwitchRow(label = "严格模式（计时期间勿退出）", checked = settings.strictMode) { checked ->
                        viewModel.saveSwitches(settings.autoStartBreak, settings.autoStartNextPomo, settings.soundEnabled, settings.vibrateEnabled, checked)
                    }
                }
            }

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
 * 时长滑杆行。
 *
 * @param label 参数名
 * @param value 当前值
 * @param range 取值范围
 * @param unit 单位文案
 * @param onChange 变更回调（取整后的值）
 */
@Composable
private fun DurationSlider(label: String, value: Int, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(text = "$value $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range
        )
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