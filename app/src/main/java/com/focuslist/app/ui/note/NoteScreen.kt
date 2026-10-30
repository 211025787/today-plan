package com.focuslist.app.ui.note

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.focuslist.app.AppContainer
import com.focuslist.app.data.local.entity.NoteAttachmentEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备注查看页元信息格式 */
private val noteMetaFormat = SimpleDateFormat("yyyy-MM-dd EEE HH:mm", Locale.getDefault())

/** 关联日期格式 */
private val noteDateOnlyFormat = SimpleDateFormat("yyyy-MM-dd EEE", Locale.getDefault())

/**
 * 全屏备注页：新建、查看、编辑共用。
 *
 * - 新建（noteId <= 0）：直接进入编辑态；
 * - 查看：Markdown 正文与图片/视频附件完整呈现，右上角「编辑」可切换到编辑态；
 * - 编辑：标题、关联日期、Markdown 正文与附件均可修改，右上角保存，底部可删除。
 *
 * @param container 依赖容器
 * @param noteId 备注 ID；<= 0 表示新建
 * @param defaultDate 新建时的默认关联日期（0 点时间戳）
 * @param onBack 返回上一页回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    container: AppContainer,
    noteId: Long,
    defaultDate: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = noteId <= 0L

    // 已保存的备注快照（查看模式渲染用）
    var note by remember { mutableStateOf<QuickNoteEntity?>(null) }
    var savedAttachments by remember { mutableStateOf<List<NoteAttachmentEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(isNew) }

    // 编辑态
    var editing by remember { mutableStateOf(isNew) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var noteDate by remember {
        mutableStateOf(if (defaultDate > 0L) defaultDate else DateUtils.startOfDay(System.currentTimeMillis()))
    }
    // 编辑中的附件：类型（IMAGE/VIDEO）与 URI 成对
    val attachments = remember { mutableStateListOf<Pair<String, String>>() }

    // 载入已有备注（含附件）
    LaunchedEffect(noteId) {
        if (!isNew) {
            val loadedNote = container.noteRepository.getNote(noteId)
            note = loadedNote
            if (loadedNote != null) {
                title = loadedNote.title
                content = loadedNote.content
                if (loadedNote.date > 0L) {
                    noteDate = loadedNote.date
                }
                val atts = container.noteRepository.observeAttachmentsOf(noteId).first()
                savedAttachments = atts
                attachments.clear()
                atts.forEach { attachments.add(it.type to it.uri) }
            }
            loaded = true
        }
    }

    /** 选取媒体后取持久读权限，防止重启后无法读取 */
    fun pickResult(uri: Uri?, type: String) {
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

    val canSave = title.isNotBlank() || content.isNotBlank() || attachments.isNotEmpty()

    /** 保存：新建走插入，编辑走原地更新（附件整体替换） */
    fun save() {
        if (!canSave) {
            return
        }
        val atts = attachments.map { (type, uri) ->
            NoteAttachmentEntity(noteId = noteId, type = type, uri = uri)
        }
        scope.launch {
            if (isNew) {
                container.noteRepository.add(title, content, noteDate, atts)
            } else {
                note?.let { origin ->
                    container.noteRepository.update(
                        origin.copy(
                            title = title.trim(),
                            content = content.trim(),
                            date = noteDate
                        ),
                        atts
                    )
                }
            }
            onBack()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isNew -> "新建备注"
                            editing -> "编辑备注"
                            else -> "备注"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (editing) {
                        TextButton(onClick = { save() }, enabled = canSave) { Text("保存") }
                    } else {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (editing) {
                // ---------- 编辑态 ----------
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("标题（可选）") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
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
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("📅 关联日期", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = noteDateOnlyFormat.format(Date(noteDate)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = {
                        Text("写下你的想法…\n支持 Markdown：# 标题  **加粗**  - 列表  [链接](url)\n在线图片：![](https://…)")
                    },
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                )
                Text(
                    text = "正文支持 Markdown 语法，图片可用 ![](地址) 插入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 附件
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("🖼 添加图片") }
                    OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("🎬 添加视频") }
                }
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

                if (!isNew) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("删除这条备注", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // ---------- 查看态 ----------
                Text(
                    text = note?.title?.ifBlank { "无标题" } ?: "",
                    style = MaterialTheme.typography.headlineSmall
                )
                val meta = buildString {
                    val created = note?.createdAt ?: System.currentTimeMillis()
                    if (noteDate > 0L) {
                        append("关联 ")
                        append(noteDateOnlyFormat.format(Date(noteDate)))
                        append(" · ")
                    }
                    append("记录于 ")
                    append(noteMetaFormat.format(Date(created)))
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (content.isNotBlank()) {
                    MarkdownText(markdown = content, modifier = Modifier.padding(top = 4.dp))
                }

                savedAttachments.forEach { attachment ->
                    if (attachment.type == "IMAGE") {
                        Box(
                            modifier = Modifier.clickable {
                                openAttachment(context, attachment.uri, attachment.type)
                            }
                        ) {
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
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Text("🎬 视频", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "点击播放 ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (content.isBlank() && savedAttachments.isEmpty()) {
                    Text(
                        text = "这条备注还没有内容，点右上角编辑补充",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

    // 删除确认
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除备注") },
            text = { Text("确定删除这条备注？附件引用一并移除，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        container.noteRepository.delete(noteId)
                        onBack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}
