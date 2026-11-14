package com.focuslist.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.focuslist.app.data.music.AudioWaveform
import com.focuslist.app.data.prefs.PLAN_TONE_FILE
import com.focuslist.app.data.prefs.PLAN_TONE_SYSTEM
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 自定义铃声播放器：用 MediaPlayer 播放「在线热歌缓存 / 系统铃声 / 本地音频」。
 *
 * 支持三件事：
 * - 片段播放：只播用户选定的 [startMs, endMs] 区间；
 * - 循环到指定时长：到点或到时长自动停；
 * - 震动同频：按波形峰值实时调节震动强度，让马达跟着音乐起伏。
 *
 * 音频来源不可用（文件被清理、URI 无权限、解码失败）时返回 false，由调用方回退到内置音色。
 */
object TonePlayer {

    /** 应用级协程域：用于到点停止 / 驱动震动 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 当前播放器；同一时刻只保留一个，避免多次提醒叠音 */
    private var player: MediaPlayer? = null

    /** 当前停止任务 */
    private var stopJob: Job? = null

    /** 片段播放的监看任务（负责循环与震动） */
    private var clipJob: Job? = null

    /** 当前震动器 */
    private var vibrator: Vibrator? = null

    /**
     * 试听一段自定义铃声（整首）。
     *
     * @param context 上下文
     * @param kind 来源：FILE / SYSTEM
     * @param value 本地路径或 URI
     * @param millis 试听时长（毫秒）
     * @return 是否成功开始播放
     */
    fun preview(context: Context, kind: String, value: String, millis: Long = 8_000L): Boolean {
        return start(context.applicationContext, kind, value, loop = false, millis = millis)
    }

    /**
     * 试听选中的片段。
     *
     * @param context 上下文
     * @param file 本地音频
     * @param startMs 片段起点
     * @param endMs 片段终点
     * @param millis 最长试听时长
     * @return 是否成功开始播放
     */
    fun previewClip(context: Context, file: File, startMs: Long, endMs: Long, millis: Long = 10_000L): Boolean {
        val end = if (endMs > startMs) endMs else startMs + 1_500L
        return playClip(
            context = context.applicationContext,
            file = file,
            startMs = startMs,
            endMs = end,
            loop = false,
            totalMs = millis.coerceAtMost(end - startMs),
            peaks = AudioWaveform.load(file) ?: FloatArray(0),
            vibrate = false
        )
    }

    /**
     * 循环播放自定义铃声（整首，用于旧路径）。
     *
     * @param context 上下文
     * @param kind 来源：FILE / SYSTEM
     * @param value 本地路径或 URI
     * @param millis 持续时长（毫秒）
     * @return 是否成功开始播放
     */
    fun playLoop(context: Context, kind: String, value: String, millis: Long): Boolean {
        return start(context.applicationContext, kind, value, loop = true, millis = millis)
    }

    /**
     * 播放铃声片段（可循环到指定时长），并按波形驱动震动。
     *
     * @param context 应用上下文
     * @param file 本地音频文件
     * @param startMs 片段起点
     * @param endMs 片段终点
     * @param loop 是否循环
     * @param totalMs 循环总时长；0 或小于片段长度时只播一遍
     * @param peaks 波形峰值（空数组则用固定强度震动）
     * @param vibrate 是否震动
     * @return 是否成功开始播放
     */
    @Synchronized
    fun playClip(
        context: Context,
        file: File,
        startMs: Long,
        endMs: Long,
        loop: Boolean,
        totalMs: Long,
        peaks: FloatArray,
        vibrate: Boolean
    ): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        stop()
        val begin = startMs.coerceAtLeast(0L)
        val end = if (endMs > begin) endMs else begin + 1_000L
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { it ->
                runCatching { it.seekTo(begin.toInt()) }
                it.start()
                player = it
                clipJob = scope.launch {
                    drive(it, context, peaks, begin, end, loop, totalMs, vibrate, SystemClock.elapsedRealtime())
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mp.prepareAsync()
            player = mp
            true
        } catch (_: Exception) {
            release()
            false
        }
    }

    /**
     * 播放过程中驱动循环与震动。
     *
     * @param mp 播放器
     * @param context 上下文
     * @param peaks 波形
     * @param startMs 片段起点
     * @param endMs 片段终点
     * @param loop 是否循环
     * @param totalMs 总时长（0 表示只播一遍）
     * @param vibrate 是否震动
     * @param beginElapsed 开始时刻（elapsedRealtime）
     */
    private suspend fun drive(
        mp: MediaPlayer,
        context: Context,
        peaks: FloatArray,
        startMs: Long,
        endMs: Long,
        loop: Boolean,
        totalMs: Long,
        vibrate: Boolean,
        beginElapsed: Long
    ) {
        val length = (endMs - startMs).coerceAtLeast(500L)
        // 只有「开启循环且设置了有效时长」才允许重复；否则播到片段结尾就停
        val repeat = loop && totalMs > 0L
        val stopAtMs = if (repeat) totalMs else 0L
        val vib = if (vibrate) {
            runCatching { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }.getOrNull()
        } else {
            null
        }
        val canAmplitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            vib?.hasAmplitudeControl() == true
        if (vib?.hasVibrator() == true) {
            vibrator = vib
        }
        while (currentCoroutineContext().isActive) {
            delay(90L)
            val elapsed = SystemClock.elapsedRealtime() - beginElapsed
            if (stopAtMs > 0L && elapsed >= stopAtMs) {
                break
            }
            val position = runCatching { mp.currentPosition.toLong() }.getOrDefault(startMs)
            if (vib != null && vib.hasVibrator()) {
                // 震动强度跟着当前波形走；60ms 的短脉冲连起来就是"随音乐起伏"
                val amplitude = AudioWaveform.amplitudeAt(peaks, position - startMs, length)
                if (canAmplitude) {
                    val level = (amplitude * 255f).toInt().coerceIn(20, 255)
                    runCatching { vib.vibrate(VibrationEffect.createOneShot(80L, level)) }
                } else {
                    runCatching { vib.vibrate(VibrationEffect.createOneShot(55L, VibrationEffect.DEFAULT_AMPLITUDE)) }
                }
            }
            if (position >= endMs - 40L) {
                if (repeat) {
                    runCatching { mp.seekTo(startMs.toInt()) }
                    Log.d("FocusAlarm", "clip loop -> ${startMs}ms")
                } else {
                    break
                }
            }
        }
        stop()
    }

    /**
     * 在线试听：直接从外链流式播放一小段，不必先下载整首。
     *
     * @param url mp3 外链
     * @param millis 试听时长（毫秒）
     * @return 是否成功发起播放（真正的解码失败走异步回调，静默停止）
     */
    @Synchronized
    fun previewUrl(url: String, millis: Long = 15_000L): Boolean {
        if (url.isBlank()) return false
        stop()
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(url)
            mp.isLooping = false
            mp.setOnPreparedListener {
                it.start()
                stopJob = scope.launch {
                    delay(millis.coerceAtLeast(1_000L))
                    stop()
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mp.prepareAsync()
            player = mp
            true
        } catch (_: Exception) {
            release()
            false
        }
    }

    /** 停止并释放当前播放器与震动 */
    @Synchronized
    fun stop() {
        stopJob?.cancel()
        stopJob = null
        clipJob?.cancel()
        clipJob = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        release()
    }

    /**
     * 启动播放（整首 / 系统铃声）。
     *
     * @param context 应用上下文
     * @param kind 来源
     * @param value 路径或 URI
     * @param loop 是否循环
     * @param millis 播放时长
     * @return 是否成功开始
     */
    @Synchronized
    private fun start(
        context: Context,
        kind: String,
        value: String,
        loop: Boolean,
        millis: Long
    ): Boolean {
        if (value.isBlank()) return false
        release()
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            when (kind) {
                PLAN_TONE_FILE -> {
                    val file = File(value)
                    if (!file.isFile || file.length() <= 0L) {
                        mp.release()
                        return false
                    }
                    mp.setDataSource(file.absolutePath)
                }
                PLAN_TONE_SYSTEM -> mp.setDataSource(context, Uri.parse(value))
                else -> {
                    mp.release()
                    return false
                }
            }
            mp.isLooping = loop
            mp.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            stopJob = scope.launch {
                delay(millis.coerceAtLeast(500L))
                stop()
            }
            true
        } catch (_: Exception) {
            release()
            false
        }
    }

    /** 释放播放器 */
    @Synchronized
    private fun release() {
        try {
            player?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {
            // 释放失败无需处理
        }
        player = null
    }
}
