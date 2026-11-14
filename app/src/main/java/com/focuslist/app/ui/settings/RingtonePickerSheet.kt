package com.focuslist.app.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.music.AudioWaveform
import com.focuslist.app.data.music.OnlineMusic
import com.focuslist.app.data.music.OnlineSong
import com.focuslist.app.data.music.RingtoneStore
import com.focuslist.app.data.prefs.PLAN_TONE_CHIME
import com.focuslist.app.data.prefs.PLAN_TONE_FILE
import com.focuslist.app.data.prefs.PLAN_TONE_SYSTEM
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.ui.common.ChimePlayer
import com.focuslist.app.ui.common.TonePlayer
import java.io.File
import kotlinx.coroutines.launch

/**
 * 计划提醒铃声选择弹层：内置音色 / 在线热歌榜 / 关键词搜索 / 系统铃声。
 *
 * 在线歌曲会先下载到应用私有目录再设为铃声，保证息屏、断网时闹钟也能正常响；
 * 会员曲目的外链通常是错误页，下载校验失败时给一句提示并保留原选择。
 *
 * @param settings 当前设置快照
 * @param onPick 选中回调（来源、取值、显示名）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtonePickerSheet(
    settings: TimerSettings,
    onPick: (kind: String, value: String, name: String, durationMs: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { RingtoneStore(context) }

    var hotSongs by remember { mutableStateOf<List<OnlineSong>?>(null) }
    var hotFailed by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<List<OnlineSong>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    // 离开弹层时停掉试听，避免和系统其他声音叠在一起
    DisposableEffect(Unit) {
        onDispose { TonePlayer.stop() }
    }

    LaunchedEffect(Unit) {
        hotSongs = OnlineMusic.hotSongs(limit = 30)
        hotFailed = hotSongs.isNullOrEmpty()
    }

    // 系统铃声选择器：选完复制到应用目录，后续闹钟不依赖 URI 授权是否还在
    val systemPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data
            ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (result.resultCode != android.app.Activity.RESULT_OK || uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val title = runCatching {
                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            }.getOrNull().orEmpty().ifBlank { "系统铃声" }
            val file = store.importUri(uri, title)
            if (file != null) {
                val durationMs = mediaDurationMs(file)
                onPick(PLAN_TONE_FILE, file.absolutePath, title, durationMs)
                AudioWaveform.warm(file)
            } else {
                // 复制失败时退回直接引用系统 URI，至少当前设备可用
                onPick(PLAN_TONE_SYSTEM, uri.toString(), title, 0)
            }
        }
    }

    val activeChime = settings.planAlertToneValue.ifBlank { settings.chimeType }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    "计划提醒铃声",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                Text(
                    text = "当前：${planToneLabel(settings)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    when (settings.planAlertToneKind) {
                        PLAN_TONE_CHIME -> ChimePlayer.play(context, activeChime)
                        else -> TonePlayer.preview(
                            context,
                            settings.planAlertToneKind,
                            settings.planAlertToneValue
                        )
                    }
                }) { Text("▶ 试听当前铃声") }
                hint?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            item { SectionTitle("内置音色") }
            items(ChimePlayer.options.size) { index ->
                val (type, name, icon) = ChimePlayer.options[index]
                val selected = settings.planAlertToneKind == PLAN_TONE_CHIME && activeChime == type
                ToneRow(
                    title = "$icon $name",
                    subtitle = if (selected) "当前使用" else null,
                    selected = selected,
                    actionText = "选用",
                    onRowClick = { ChimePlayer.play(context, type) },
                    onAction = {
                        ChimePlayer.play(context, type)
                        onPick(PLAN_TONE_CHIME, type, name, 0)
                    }
                )
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SectionTitle("在线热门音乐")
                Text(
                    text = "来自网易云热歌榜，选用的歌曲会下载到本机，断网也能响",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    label = { Text("搜索歌名或歌手") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    trailingIcon = {
                        TextButton(
                            enabled = keyword.isNotBlank() && !searching,
                            onClick = {
                                scope.launch {
                                    searching = true
                                    hint = null
                                    searchResult = OnlineMusic.search(keyword, limit = 20)
                                    searching = false
                                    if (searchResult.isNullOrEmpty()) {
                                        hint = "没搜到可用的歌曲，换个关键词试试"
                                    }
                                }
                            }
                        ) { Text(if (searching) "搜索中" else "搜索") }
                    }
                )
            }

            val list = searchResult ?: hotSongs
            if (list == null) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "正在加载热歌榜…",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            } else if (list.isEmpty()) {
                item {
                    Text(
                        text = if (hotFailed && searchResult == null) {
                            "热歌榜加载失败，可能是网络不稳定，稍后再试"
                        } else {
                            "没有找到歌曲"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(list, key = { it.id }) { song ->
                    val selected = settings.planAlertToneKind == PLAN_TONE_FILE &&
                        settings.planAlertToneName == song.displayName
                    ToneRow(
                        title = song.name,
                        subtitle = buildString {
                            append(song.artist.ifBlank { "未知歌手" })
                            if (song.vip) append(" · 会员曲目可能不可用")
                        },
                        selected = selected,
                        actionText = if (downloadId == song.id) "下载中…" else "选用",
                        enabled = downloadId == null,
                        onRowClick = {
                            // 先试听：已缓存直接播本地，未缓存解析出 https 地址后流式播放
                            val cached = store.fileOf(song.id)
                            if (cached != null) {
                                TonePlayer.preview(context, PLAN_TONE_FILE, cached.absolutePath)
                            } else {
                                scope.launch {
                                    val url = OnlineMusic.resolvePlayUrl(song.id)
                                    if (url == null || !TonePlayer.previewUrl(url)) {
                                        hint = "这首歌暂时无法试听，换一首试试"
                                    }
                                }
                            }
                        },
                        onAction = {
                            scope.launch {
                                downloadId = song.id
                                hint = null
                                val file = store.download(song)
                                if (file != null) {
                                    val durationMs = mediaDurationMs(file)
                                    downloadId = null
                                    // 先让选择立即生效，波形在后台慢慢算，界面不卡
                                    onPick(PLAN_TONE_FILE, file.absolutePath, song.displayName, durationMs)
                                    AudioWaveform.warm(file)
                                } else {
                                    downloadId = null
                                    hint = "《${song.name}》暂不能用作铃声（可能是会员曲目），换一首试试"
                                }
                            }
                        }
                    )
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SectionTitle("系统铃声")
                ToneRow(
                    title = "从系统闹钟铃声里选",
                    subtitle = "会复制一份到本机，避免权限失效后不响",
                    selected = settings.planAlertToneKind == PLAN_TONE_SYSTEM,
                    actionText = "选择",
                    onRowClick = { launchSystemPicker(systemPicker) { hint = it } },
                    onAction = { launchSystemPicker(systemPicker) { hint = it } }
                )
                Text(
                    text = "选用在线歌曲会下载整首歌作为铃声，建议在 Wi-Fi 下操作。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            }
        }
    }
}

/**
 * 打开系统闹钟铃声选择器，打不开时回传提示文案。
 *
 * @param launcher 选择器 launcher
 * @param onError 失败提示
 */
private fun launchSystemPicker(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onError: (String) -> Unit
) {
    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    }
    runCatching { launcher.launch(intent) }
        .onFailure { onError("系统铃声选择器打不开，换用在线音乐吧") }
}

/**
 * 当前铃声的展示文案。
 *
 * @param settings 设置快照
 * @return 中文描述
 */
internal fun planToneLabel(settings: TimerSettings): String {
    return when (settings.planAlertToneKind) {
        PLAN_TONE_FILE -> settings.planAlertToneName.ifBlank { "本地音频" }
        PLAN_TONE_SYSTEM -> settings.planAlertToneName.ifBlank { "系统铃声" }
        else -> {
            val active = settings.planAlertToneValue.ifBlank { settings.chimeType }
            ChimePlayer.options.firstOrNull { it.first == active }
                ?.let { "${it.third} ${it.second}" }
                ?: "内置音色"
        }
    }
}

/**
 * 分组标题。
 *
 * @param text 文案
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

/**
 * 铃声行：点整行试听，点右侧按钮选用。
 *
 * @param title 主文案
 * @param subtitle 副文案
 * @param selected 是否当前选中
 * @param actionText 右侧按钮文案
 * @param enabled 右侧按钮是否可点
 * @param onRowClick 整行点击
 * @param onAction 右侧按钮点击
 */
@Composable
private fun ToneRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    actionText: String,
    enabled: Boolean = true,
    onRowClick: () -> Unit,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onRowClick)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        TextButton(enabled = enabled, onClick = onAction) { Text(actionText) }
    }
}

/**
 * 读取音频时长（毫秒）。
 *
 * @param file 音频文件
 * @return 时长；读取失败返回 0
 */
private fun mediaDurationMs(file: File): Int {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val value = retriever.extractMetadata(
            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L
        retriever.release()
        value.toInt().coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }
}