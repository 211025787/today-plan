package com.focuslist.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.backup.BackupCodec
import com.focuslist.app.data.backup.BackupPayload
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.ThemeColors
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.BackupHealth
import com.focuslist.app.util.BackupNames
import com.focuslist.app.util.Goal
import com.focuslist.app.util.PlanTemplates
import com.focuslist.app.widget.WidgetRefresher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页（我的）：按「外观 / 提醒 / 悬浮胶囊 / 关于」分组，
 * 每组一张玻璃卡，避免所有控件挤在一张卡里。
 *
 * @param container 依赖容器
 * @param onOpenStats 打开专注统计页回调
 * @param onOpenCapsule 打开悬浮胶囊设置页回调
 * @param onOpenAchievements 打开成就殿堂回调
 * @param onOpenMonths 打开月度回顾回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenStats: () -> Unit = {},
    onOpenCapsule: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenMonths: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.settingsStore, container.focusRepository)
    }
    val settings by viewModel.settings.collectAsState()
    val suggestedGoal by viewModel.suggestedGoal.collectAsState()
    val templates by viewModel.planTemplates.collectAsState()
    val lastBackupAt by viewModel.lastBackupAt.collectAsState()
    val context = LocalContext.current
    // 版本号从包信息读取，避免手写
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    // ---- 数据备份 / 恢复 ----
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupPayload?>(null) }

    /** 导出：拉起系统「另存为」，把全部数据写成一个 JSON 文件 */
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = true
        scope.launch {
            backupMessage = runCatching {
                val payload = container.backupManager.collect(versionName)
                val json = BackupCodec.encode(payload)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选位置")
                viewModel.markBackupExported()
                "已导出 ${payload.totalCount()} 条：${payload.describe()}"
            }.getOrElse { "导出失败：${it.message ?: "未知错误"}" }
            backupBusy = false
        }
    }

    /** 导入：选一个备份文件，先解析出摘要再让用户确认导入方式 */
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = true
        scope.launch {
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val text = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                    ?: error("无法读取所选文件")
                BackupCodec.decode(text)
            }.onSuccess { payload ->
                pendingImport = payload
                backupMessage = null
            }.onFailure {
                backupMessage = "导入失败：${it.message ?: "无法解析该文件"}"
            }
            backupBusy = false
        }
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

            // 每日目标
            SectionLabel("每日目标")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("每天想专注多久", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Goal.PRESETS.forEach { minutes ->
                            FilterChip(
                                selected = settings.dailyGoalMinutes == minutes,
                                onClick = { viewModel.saveDailyGoal(minutes) },
                                label = { Text("$minutes") }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            text = "${settings.dailyGoalMinutes}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "分钟 / 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.saveDailyGoal(settings.dailyGoalMinutes - Goal.STEP_MINUTES) }) {
                            Text("−${Goal.STEP_MINUTES}")
                        }
                        TextButton(onClick = { viewModel.saveDailyGoal(settings.dailyGoalMinutes + Goal.STEP_MINUTES) }) {
                            Text("+${Goal.STEP_MINUTES}")
                        }
                    }
                    suggestedGoal?.let { suggestion ->
                        if (suggestion != settings.dailyGoalMinutes) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "按近 14 天表现建议 $suggestion 分钟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.saveDailyGoal(suggestion) }) { Text("采用") }
                            }
                        }
                    }
                }
            }

            // 计划模板
            SectionLabel("计划模板")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (templates.isEmpty()) {
                        Text(
                            text = "还没有模板。添加计划后会自动记住配置，也可以长按计划卡选「存为模板」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        templates.forEachIndexed { index, template ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(template.icon, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(template.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        text = "${PlanTemplates.describe(template)} · 用过 ${template.useCount} 次",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.removePlanTemplate(template.id) }) {
                                    Text("删除")
                                }
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

            // 数据
            SectionLabel("数据")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // 备份体检：上次备份多久了 / 是否该再备份一次
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("备份状态", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${BackupHealth.statusText(lastBackupAt)} · ${BackupHealth.level(lastBackupAt).label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text(
                                text = BackupHealth.countdownText(lastBackupAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (BackupHealth.shouldRemind(lastBackupAt)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            )
                        }
                        // 距提醒线的进度条：用两个 Box 画，避免依赖高版本进度条 API
                        val progress = BackupHealth.progress(lastBackupAt)
                        val barColor = if (BackupHealth.shouldRemind(lastBackupAt)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (progress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(barColor)
                                )
                            }
                        }
                        Text(
                            text = BackupHealth.adviceText(lastBackupAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ActionRow(
                        title = "专注统计",
                        subtitle = "累计时长、连续天数与年度热力图",
                        onClick = onOpenStats
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ActionRow(
                        title = "月度回顾",
                        subtitle = "整月逐日日历，和上月同期比一比",
                        onClick = onOpenMonths
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ActionRow(
                        title = "成就殿堂",
                        subtitle = "12 枚徽章，解锁记录随备份一起保存",
                        onClick = onOpenAchievements
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ActionRow(
                        title = "导出备份",
                        subtitle = "把数据、偏好设置、计划模板与成就保存成一个 JSON 文件",
                        enabled = !backupBusy,
                        onClick = { exportLauncher.launch(BackupNames.suggestFileName(System.currentTimeMillis())) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ActionRow(
                        title = "导入备份",
                        subtitle = "从之前导出的 JSON 文件恢复",
                        enabled = !backupBusy,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                    )
                    backupMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
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

    // 导入确认：解析成功后先展示备份内容，再让用户选择合并或覆盖
    pendingImport?.let { payload ->
        val exportedText = remember(payload) {
            if (payload.exportedAt > 0) {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(payload.exportedAt))
            } else {
                "未知时间"
            }
        }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入备份") },
            text = {
                Text(
                    text = "备份导出于 $exportedText" +
                        (if (payload.appVersion.isNotBlank()) "（v${payload.appVersion}）" else "") +
                        "\n共 ${payload.totalCount()} 条：${payload.describe()}\n\n" +
                        "覆盖导入：先清空本机数据，结果等同于这份备份，并一并还原偏好设置、计划模板与成就解锁记录。\n" +
                        "合并导入：保留本机数据与设置，仅覆盖 ID 相同的记录，并补充本机没有的模板与尚未解锁的成就。",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    backupBusy = true
                    scope.launch {
                        backupMessage = runCatching {
                            val result = container.backupManager.restore(payload, overwrite = true)
                            WidgetRefresher.refreshAll(context)
                            "已覆盖导入 ${result.recordCount} 条数据" +
                                if (result.achievementsTotal > 0) {
                                    "，徽章 ${result.achievementsTotal} 枚按备份还原"
                                } else {
                                    "，本机徽章保留不变"
                                }
                        }.getOrElse { "导入失败：${it.message ?: "未知错误"}" }
                        backupBusy = false
                    }
                }) { Text("覆盖导入") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingImport = null
                        backupBusy = true
                        scope.launch {
                            backupMessage = runCatching {
                                val result = container.backupManager.restore(payload, overwrite = false)
                                WidgetRefresher.refreshAll(context)
                                "已合并导入 ${result.recordCount} 条数据" +
                                    if (result.achievementsAdded > 0) {
                                        "，补充 ${result.achievementsAdded} 枚徽章"
                                    } else {
                                        ""
                                    }
                            }.getOrElse { "导入失败：${it.message ?: "未知错误"}" }
                            backupBusy = false
                        }
                    }) { Text("合并导入") }
                    TextButton(onClick = { pendingImport = null }) { Text("取消") }
                }
            }
        )
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

/**
 * 可点击的操作行（标题 + 副标题 + 右箭头）。
 *
 * @param title 主文案
 * @param subtitle 副文案
 * @param enabled 是否可点击
 * @param onClick 点击回调
 */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
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
