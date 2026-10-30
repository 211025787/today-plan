package com.focuslist.app.ui.note

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focuslist.app.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备注关联日期显示格式 */
private val noteDateFormat = SimpleDateFormat("yyyy-MM-dd EEE", Locale.getDefault())

/**
 * 快速备注底部弹层：标题、关联日期（默认当前查看日）、Markdown 正文与图片/视频附件。
 *
 * @param defaultDate 默认关联日期（当天 0 点时间戳）
 * @param onDismiss 关闭回调
 * @param onSave 保存回调（标题, 正文, 关联日期, 附件列表：类型 IMAGE/VIDEO 与 URI 成对）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteSheet(
    defaultDate: Long,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, date: Long, attachments: List<Pair<String, String>>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var noteDate by remember { mutableStateOf(defaultDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    // 附件临时列表：类型（IMAGE/VIDEO）与 URI 成对
    val attachments = remember { mutableStateListOf<Pair<String, String>>() }
    val context = LocalContext.current

    /** 选取媒体后取持久读权限，防止重启后无法读取 */
    fun pickResult(uri: android.net.Uri?, type: String) {
        uri ?: return
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // 部分选择器不授予持久权限，仅本次会话可用
        }
        attachments.add(type to uri.toString())
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickResult(uri, "IMAGE")
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickResult(uri, "VIDEO")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("快速备注", style = MaterialTheme.typography.titleMedium)

            // 标题
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("标题（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 关联日期
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("📅 关联日期", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = noteDateFormat.format(Date(noteDate)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 正文（Markdown）
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("随手记点什么… 支持 Markdown：**加粗** # 标题 - 列表\n在线图片：![](https://…)") },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )

            // 附件选取
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("🖼 图片") }
                OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("🎬 视频") }
            }

            // 已选附件（可移除）
            if (attachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    attachments.forEachIndexed { index, (type, _) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(start = 10.dp)
                        ) {
                            Text(
                                text = "${if (type == "IMAGE") "🖼" else "🎬"} 附件 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(onClick = { attachments.removeAt(index) }) {
                                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        onSave(title, content, noteDate, attachments.toList())
                    },
                    enabled = title.isNotBlank() || content.isNotBlank() || attachments.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }

    // 关联日期选择
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = noteDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { noteDate = DateUtils.startOfDay(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
