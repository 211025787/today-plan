package com.focuslist.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.backup.BackupCodec
import com.focuslist.app.data.backup.BackupPayload
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.BackupHealth
import com.focuslist.app.util.BackupNames
import com.focuslist.app.widget.WidgetRefresher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份页：备份体检（上次备份多久了）、导出、导入。
 *
 * 导出把所有数据写成一个 JSON 交给系统「另存为」；导入先解析出摘要，
 * 再让用户选覆盖导入还是合并导入，避免误把本机数据洗掉。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 */
@Composable
fun BackupSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsStore) }
    val lastBackupAt by viewModel.lastBackupAt.collectAsState()
    val context = LocalContext.current
    // 版本号从包信息读取，避免手写
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

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

    SettingsDetailScreen(
        title = "数据备份",
        subtitle = "备份状态、导出与导入",
        onBack = onBack
    ) {
        // 备份体检：上次备份多久了 / 是否该再备份一次
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("备份状态", style = MaterialTheme.typography.titleSmall)
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
                        .padding(top = 12.dp)
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
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SettingsActionRow(
                    title = "导出备份",
                    subtitle = "计划、备注、旅游规划（含站点与交通）、偏好设置、模板与成就保存成一个 JSON 文件",
                    enabled = !backupBusy,
                    onClick = { exportLauncher.launch(BackupNames.suggestFileName(System.currentTimeMillis())) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsActionRow(
                    title = "导入备份",
                    subtitle = "从之前导出的 JSON 文件恢复，可选覆盖或合并",
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

        Text(
            text = "备份文件由你自己保管：应用不会自动上传到任何服务器。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
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
