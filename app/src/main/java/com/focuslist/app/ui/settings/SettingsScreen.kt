package com.focuslist.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.prefs.TravelPrefs
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.ThemeColors
import com.focuslist.app.util.BackupHealth
import com.focuslist.app.util.PlanStats

/**
 * 「我的」页：只放入口，具体设置在各自的子页面里。
 *
 * 每一项都是一行「图标 + 名称 + 当前状态摘要」，一眼能看出现在是什么配置，
 * 点进去再改——设置项变多以后不会再挤成一大张卡片。
 *
 * @param container 依赖容器
 * @param onOpen 打开子页面回调，参数为 [SettingsRoutes] 中的路由
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpen: (String) -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsStore) }
    val settings by viewModel.settings.collectAsState()
    val templates by viewModel.planTemplates.collectAsState()
    val lastBackupAt by viewModel.lastBackupAt.collectAsState()
    // 只订阅「今天」的计划日志，给计划总览入口一行实时摘要
    val todayLogs by container.planRepository.observeTodayLogs().collectAsState(initial = emptyList())
    val context = LocalContext.current
    // 版本号从包信息读取，避免手写
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    // 从子页面返回时重读一遍只在页面外维护的状态（例如高德 Key）
    val travelPrefs = remember { TravelPrefs(context) }
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val amapConfigured = remember(resumeTick) { travelPrefs.amapKey.isNotBlank() }

    // 各入口右侧的状态摘要
    val themeSummary = remember(settings.themeMode, settings.themeColor) {
        val mode = when (settings.themeMode) {
            "LIGHT" -> "浅色"
            "DARK" -> "深色"
            else -> "跟随系统"
        }
        val colorName = ThemeColors.firstOrNull { it.second == settings.themeColor }?.first ?: "自定义"
        "$mode · $colorName"
    }
    val reminderSummary = if (settings.planReachGoalSound || settings.planReachGoalVibrate) {
        listOfNotNull(
            "响铃".takeIf { settings.planReachGoalSound },
            "震动".takeIf { settings.planReachGoalVibrate }
        ).joinToString("、") + " · 计划达标时提醒"
    } else {
        "已关闭，计划达标时不会提醒"
    }
    val templateSummary = if (templates.isEmpty()) {
        "还没有模板，添加计划后会自动记住"
    } else {
        "${templates.size} 个模板 · 用得最多的排前面"
    }
    val capsuleSummary = if (settings.capsuleEnabled) {
        "已开启 · 宽度 ${if (settings.capsuleWidthDp == 0) "自适应" else "${settings.capsuleWidthDp}dp"} · 不透明度 ${settings.capsuleAlpha}%"
    } else {
        "未开启，点此设置外观与权限"
    }
    val mapSummary = if (amapConfigured) {
        "高德 Key 已配置，可按名称搜具体地点"
    } else {
        "未配置 Key，仅用内置目的地与地图点选"
    }
    val backupSummary = "${BackupHealth.statusText(lastBackupAt)} · ${BackupHealth.level(lastBackupAt).label}"
    val planTodayMinutes = (todayLogs.sumOf { it.accumulatedSec.coerceAtLeast(0L) } / 60L).toInt()
    val planSummary = "今日 ${PlanStats.durationText(planTodayMinutes)} · 按日历里的计划计时"

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

            SettingsSectionLabel("设置")
            SettingsEntryCard("🎨", "外观", themeSummary) { onOpen(SettingsRoutes.APPEARANCE) }
            SettingsEntryCard("🎯", "每日目标", "${settings.dailyGoalMinutes} 分钟 / 天") {
                onOpen(SettingsRoutes.DAILY_GOAL)
            }
            SettingsEntryCard("🔔", "提醒", reminderSummary) { onOpen(SettingsRoutes.REMINDER) }
            SettingsEntryCard("🧩", "计划模板", templateSummary) { onOpen(SettingsRoutes.TEMPLATES) }
            SettingsEntryCard("🫧", "悬浮胶囊", capsuleSummary) { onOpen(SettingsRoutes.CAPSULE) }
            SettingsEntryCard("🗺", "地图与搜索", mapSummary) { onOpen(SettingsRoutes.MAP_SEARCH) }
            SettingsEntryCard("💾", "数据备份", backupSummary) { onOpen(SettingsRoutes.BACKUP) }

            // 日历计划维度：数据源是 plan_logs，和下面的番茄专注统计完全分开
            SettingsSectionLabel("日历计划")
            SettingsEntryCard("📊", "计划总览", planSummary) { onOpen(SettingsRoutes.PLAN_STATS) }
            SettingsEntryCard("🗂", "计划节奏", "6 周时间轴 · 到期计划的结业与续期") {
                onOpen(SettingsRoutes.PLAN_REVIEW)
            }

            SettingsSectionLabel("专注（番茄）")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingsActionRow(
                        title = "专注统计",
                        subtitle = "番茄专注时长、连续天数与年度热力图",
                        onClick = { onOpen(SettingsRoutes.STATS) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsActionRow(
                        title = "月度回顾",
                        subtitle = "按番茄专注时长着色，和上月同期比一比",
                        onClick = { onOpen(SettingsRoutes.MONTH_REVIEW) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsActionRow(
                        title = "成就殿堂",
                        subtitle = "12 枚专注徽章，解锁记录随备份一起保存",
                        onClick = { onOpen(SettingsRoutes.ACHIEVEMENTS) }
                    )
                }
            }

            SettingsSectionLabel("关于")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "数据本地优先：计划、番茄记录、备注与行程都只保存在本机，不上传；" +
                            "只有旅游规划的地图底图、真实路线与地点搜索按需联网。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
