package com.focuslist.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.music.AudioWaveform
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.ui.common.TonePlayer
import com.focuslist.app.util.RingtoneClip
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 铃声片段卡片：在音频图谱上直接拖动两端选段。
 *
 * - 图谱是细长竖条组成的柱状波形，与手机自带铃声编辑器的观感接近；
 * - 拖动按「位移」计算，手柄严格跟手，不会在按下瞬间跳到手指位置；
 * - 拖中间可以整体平移选中窗口；
 * - 松手即自动试听，右上角图标可随时播放 / 停止；
 * - 离开页面时停止试听。
 *
 * @param settings 当前设置
 * @param onClipChange 片段保存回调（起点、终点、总时长，单位毫秒）
 */
@Composable
fun RingtoneClipCard(
    settings: TimerSettings,
    onClipChange: (startMs: Int, endMs: Int, durationMs: Int) -> Unit
) {
    val context = LocalContext.current
    val path = settings.planAlertToneValue
    val totalMs = settings.planAlertToneDurationMs.toLong()
    if (path.isBlank() || totalMs <= 0L) {
        return
    }
    val file = remember(path) { File(path) }
    if (!file.isFile) {
        return
    }

    var peaks by remember(path) { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(path) {
        peaks = withContext(Dispatchers.IO) {
            AudioWaveform.load(file) ?: AudioWaveform.compute(file)
        }
    }
    // 归一化到 90% 高度并压缩动态范围，避免响度高的歌整片顶满
    val displayPeaks = remember(peaks) { normalizeWave(peaks) }

    val totalSec = (totalMs / 1000L).coerceAtLeast(1L).toFloat()
    val initialStart = (settings.planAlertToneStartMs / 1000f).coerceIn(0f, totalSec - 1f)
    val initialEnd = (RingtoneClip.effectiveEnd(
        settings.planAlertToneEndMs.toLong(),
        totalMs
    ) / 1000f).coerceIn(initialStart + 1f, totalSec)
    // 只用 path 做 key：保存后 settings 会回流，若把它算进 key 会重建 state，
    // 导致 pointerInput 闭包握着旧 state，第二次拖动就写不进去了。
    var range by remember(path) { mutableStateOf(initialStart..initialEnd) }

    var widthPx by remember { mutableStateOf(0f) }
    var activeHandle by remember { mutableStateOf(0) }
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartRange by remember { mutableStateOf(0f..0f) }
    var playing by remember { mutableStateOf(false) }
    var stopJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val padPx = with(LocalDensity.current) { 2.dp.toPx() }

    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant

    /** 停止试听并把图标切回播放态 */
    fun stopPreview() {
        TonePlayer.stop()
        stopJob?.cancel()
        stopJob = null
        playing = false
    }

    /** 播放当前选中片段 */
    fun startPreview() {
        val from = (range.start * 1000f).toLong()
        val to = (range.endInclusive * 1000f).toLong()
        if (to - from < 200L) return
        TonePlayer.previewClip(context, file, from, to)
        playing = true
        stopJob?.cancel()
        stopJob = scope.launch {
            delay((to - from).coerceIn(500L, 20_000L) + 150L)
            playing = false
        }
    }

    // 离开页面 / 卡片消失时关掉音乐
    DisposableEffect(Unit) {
        onDispose { TonePlayer.stop() }
    }

    /** 时间（秒）→ 画布 x */
    fun xOf(seconds: Float): Float {
        val usable = (widthPx - padPx * 2f).coerceAtLeast(1f)
        return padPx + (seconds / totalSec).coerceIn(0f, 1f) * usable
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // 标题与播放/停止图标同排，图标不带底色
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("铃声片段", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "在波形上拖动两端选择片段，拖中间整体平移，松手自动试听",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "停止试听" else "播放选中片段",
                tint = handleColor,
                modifier = Modifier
                    .size(30.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        if (playing) stopPreview() else startPreview()
                    }
                    .padding(4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(41.dp)
                .padding(top = 8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .onSizeChanged { widthPx = it.width.toFloat() }
                    .pointerInput(peaks, totalSec, widthPx) {
                        // 不用 detectDragGestures：它要等触摸斜率判定，手柄会慢半拍。
                        // 这里按下即记录起点，随后每个移动事件都直接更新，做到 1:1 跟手。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            dragStartX = down.position.x
                            dragStartRange = range
                            val startX0 = xOf(range.start)
                            val endX0 = xOf(range.endInclusive)
                            val nearStart = abs(down.position.x - startX0)
                            val nearEnd = abs(down.position.x - endX0)
                            activeHandle = when {
                                nearStart <= 28f -> 1
                                nearEnd <= 28f -> 2
                                down.position.x in (startX0 + 28f)..(endX0 - 28f) -> 3
                                nearStart < nearEnd -> 1
                                else -> 2
                            }
                            var decided = false
                            var horizontal = false
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                val dx = change.position.x - dragStartX
                                val dy = change.position.y - down.position.y
                                if (!decided && (abs(dx) > 6f || abs(dy) > 6f)) {
                                    decided = true
                                    horizontal = abs(dx) >= abs(dy)
                                }
                                // 明显是竖向滑动时让给外层滚动，避免把页面卡住
                                if (decided && !horizontal) break
                                if (decided) {
                                    moved = true
                                    val usable = (widthPx - padPx * 2f).coerceAtLeast(1f)
                                    val deltaSec = dx / usable * totalSec
                                    range = when (activeHandle) {
                                        1 -> {
                                            val start = (dragStartRange.start + deltaSec)
                                                .coerceIn(0f, dragStartRange.endInclusive - 1f)
                                            start..dragStartRange.endInclusive
                                        }
                                        2 -> {
                                            val end = (dragStartRange.endInclusive + deltaSec)
                                                .coerceIn(dragStartRange.start + 1f, totalSec)
                                            dragStartRange.start..end
                                        }
                                        else -> {
                                            val length = dragStartRange.endInclusive - dragStartRange.start
                                            val start = (dragStartRange.start + deltaSec)
                                                .coerceIn(0f, totalSec - length)
                                            start..(start + length)
                                        }
                                    }
                                    change.consume()
                                }
                            }
                            if (moved) {
                                onClipChange(
                                    (range.start * 1000f).toInt(),
                                    (range.endInclusive * 1000f).toInt(),
                                    totalMs.toInt()
                                )
                                startPreview()
                            }
                            activeHandle = 0
                        }
                    }
            ) {
                val midY = size.height / 2f
                val startX = xOf(range.start)
                val endX = xOf(range.endInclusive)

                // 选中区域底色
                drawRect(
                    color = primary.copy(alpha = 0.08f),
                    topLeft = Offset(startX, 0f),
                    size = Size((endX - startX).coerceAtLeast(1f), size.height)
                )

                // 细竖条组成的波形
                val count = displayPeaks.size
                if (count > 0) {
                    val slot = size.width / count
                    val barWidth = (slot * 0.5f).coerceAtLeast(1.4f)
                    val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    fun drawBars(color: Color) {
                        for (i in 0 until count) {
                            val peak = displayPeaks[i].coerceIn(0.02f, 1f)
                            val barHeight = (peak * size.height * 0.9f).coerceAtLeast(3f)
                            val left = i * slot + (slot - barWidth) / 2f
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(left, midY - barHeight / 2f),
                                size = Size(barWidth, barHeight),
                                cornerRadius = radius
                            )
                        }
                    }
                    drawBars(track)
                    clipRect(left = startX, right = endX) {
                        drawBars(primary)
                    }
                } else {
                    drawLine(track, Offset(padPx, midY), Offset(size.width - padPx, midY), 2f)
                }

                // 两端手柄：细线 + 小圆点
                listOf(startX, endX).forEach { x ->
                    drawLine(handleColor, Offset(x, 8f), Offset(x, size.height - 8f), strokeWidth = 1.5f)
                    drawCircle(handleColor, radius = 5.5f, center = Offset(x, midY))
                    drawCircle(Color.White, radius = 2f, center = Offset(x, midY))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${RingtoneClip.formatTime((range.start * 1000f).toLong())} - " +
                    RingtoneClip.formatTime((range.endInclusive * 1000f).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = primary
            )
            Text(
                text = "共 ${(range.endInclusive - range.start).toInt()} 秒",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * 波形归一化：以 90 分位为参考压缩动态范围，并把最高点限制在 90% 高度。
 *
 * 直接画 RMS 时，响度高的歌大部分时间都接近满幅，看起来像「顶满上下」；
 * 归一化 + 幂次压缩后安静段和副歌的差异才看得出来。
 *
 * @param raw 原始 RMS 数组
 * @return 0-1 的展示值
 */
private const val DISPLAY_BUCKETS = 160

private fun normalizeWave(raw: FloatArray): FloatArray {
    if (raw.isEmpty()) return raw
    // 先降采样到 160 根，竖条之间留出清晰空隙
    val buckets = FloatArray(DISPLAY_BUCKETS)
    for (i in 0 until DISPLAY_BUCKETS) {
        val from = i * raw.size / DISPLAY_BUCKETS
        val to = max(from + 1, (i + 1) * raw.size / DISPLAY_BUCKETS).coerceAtMost(raw.size)
        var sum = 0f
        for (j in from until to) sum += raw[j]
        buckets[i] = sum / (to - from).coerceAtLeast(1)
    }
    val sorted = buckets.sortedArray()
    val low = sorted[(sorted.size * 0.08f).toInt().coerceIn(0, sorted.size - 1)]
    val high = sorted[(sorted.size * 0.97f).toInt().coerceIn(0, sorted.size - 1)]
        .coerceAtLeast(low + 0.01f)
    return FloatArray(buckets.size) { index ->
        val ratio = ((buckets[index] - low) / (high - low)).coerceIn(0f, 1f)
        (ratio.pow(0.75f) * 0.85f).coerceIn(0.03f, 0.85f)
    }
}