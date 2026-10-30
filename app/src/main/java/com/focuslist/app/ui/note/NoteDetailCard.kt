package com.focuslist.app.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.local.entity.NoteAttachmentEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import com.focuslist.app.ui.common.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备注卡片时间格式 */
private val noteTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

/**
 * 当日备注预览卡：标题 + 两行内容摘要 + 时间，点击查看详情。
 *
 * @param note 备注实体
 * @param attachmentCount 附件数量（>0 时显示角标）
 * @param onClick 点击查看详情回调
 */
@Composable
fun DayNotePreviewCard(note: QuickNoteEntity, attachmentCount: Int = 0, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "备注" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (attachmentCount > 0) {
                    Text(
                        text = "🖼 $attachmentCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                }
                Text(
                    text = noteTimeFormat.format(Date(note.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val preview = markdownPreview(note.content)
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 备注详情弹窗：Markdown 正文（可滚动）、图片/视频附件预览，点击附件用系统应用打开。
 *
 * @param note 备注实体
 * @param attachments 附件列表
 * @param onDismiss 关闭回调
 * @param onDelete 删除回调
 */
@Composable
fun NoteDetailDialog(
    note: QuickNoteEntity,
    attachments: List<NoteAttachmentEntity>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = note.title.ifBlank { "备注" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = noteTimeFormat.format(Date(note.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (note.content.isNotBlank()) {
                    MarkdownText(markdown = note.content)
                }
                // 附件：图片直接预览，视频显示播放占位
                attachments.forEach { attachment ->
                    if (attachment.type == "IMAGE") {
                        Box(modifier = Modifier.clickable {
                            openAttachment(context, attachment.uri, attachment.type)
                        }) {
                            NoteImage(uri = attachment.uri)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { openAttachment(context, attachment.uri, attachment.type) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(text = "🎬 视频", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "点击播放 ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
