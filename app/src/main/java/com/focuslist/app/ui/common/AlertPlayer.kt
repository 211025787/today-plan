package com.focuslist.app.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.focuslist.app.data.music.AudioWaveform
import com.focuslist.app.data.prefs.PLAN_TONE_CHIME
import com.focuslist.app.data.prefs.PLAN_TONE_FILE
import com.focuslist.app.data.prefs.PLAN_TONE_SYSTEM
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 提醒播放器：统一处理「响铃 + 震动」及其自定义持续时长。
 *
 * - 内置音色：SoundPool 循环，播放一遍约 2.5 秒；
 * - 在线热歌 / 系统铃声：MediaPlayer 播放用户选中的片段，可按设置循环到指定时长，
 *   并让震动强度跟着波形起伏（震动同频）；
 * - 自定义铃声不可用时自动回退到内置音色，保证提醒不会因为文件被清理而彻底失效；
 * - 两者互不影响，可只开其一；异常一律静默降级，不影响计时。
 */
object AlertPlayer {

    /** 应用级协程域：用于到点停止铃声/震动 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 震动波形：等待 0ms，震动 500ms，间隔 350ms */
    private val vibrationPattern = longArrayOf(0L, 500L, 350L)

    /** 内置音色「播放一遍」的近似时长 */
    private const val CHIME_ONCE_MS = 2_500L

    /**
     * 播放一次提醒。
     *
     * @param context 上下文
     * @param chime 内置音色标识
     * @param sound 是否响铃
     * @param vibrate 是否震动
     * @param soundSec 响铃持续秒数（内置音色 / 系统铃声未设置循环时长时使用）
     * @param vibrateSec 震动持续秒数（自定义铃声不同频时使用）
     * @param toneKind 铃声来源：CHIME / FILE / SYSTEM
     * @param toneValue 本地音频路径或系统铃声 URI
     * @param toneStartMs 片段起点（毫秒）
     * @param toneEndMs 片段终点（毫秒），0 表示到结尾
     * @param toneDurationMs 音频总时长（毫秒）
     * @param loop 是否循环播放
     * @param loopDurationSec 循环总时长（秒），0 表示只播一遍
     */
    fun play(
        context: Context,
        chime: String,
        sound: Boolean,
        vibrate: Boolean,
        soundSec: Int,
        vibrateSec: Int,
        toneKind: String = PLAN_TONE_CHIME,
        toneValue: String = "",
        toneStartMs: Long = 0L,
        toneEndMs: Long = 0L,
        toneDurationMs: Long = 0L,
        loop: Boolean = false,
        loopDurationSec: Int = 0
    ) {
        val appContext = context.applicationContext
        val loopMs = loopDurationSec.coerceAtLeast(0) * 1000L
        var vibrationHandled = false

        if (sound) {
            when (toneKind) {
                PLAN_TONE_FILE -> {
                    val file = File(toneValue)
                    val start = toneStartMs.coerceAtLeast(0L)
                    val total = when {
                        toneEndMs > start -> toneEndMs
                        toneDurationMs > start -> toneDurationMs
                        else -> 0L
                    }
                    val peaks = if (file.isFile) (AudioWaveform.load(file) ?: FloatArray(0)) else FloatArray(0)
                    val played = total > start && TonePlayer.playClip(
                        context = appContext,
                        file = file,
                        startMs = start,
                        endMs = total,
                        loop = loop,
                        totalMs = loopMs,
                        peaks = peaks,
                        vibrate = vibrate
                    )
                    if (played) {
                        // 文件路径已经在播放循环里驱动震动，避免再叠一层固定波形
                        vibrationHandled = vibrate
                    } else {
                        playChime(appContext, chime, if (loop && loopMs > 0L) loopMs else CHIME_ONCE_MS)
                    }
                }
                PLAN_TONE_SYSTEM -> {
                    val millis = if (loop && loopMs > 0L) loopMs else CHIME_ONCE_MS
                    if (!TonePlayer.playLoop(appContext, toneKind, toneValue, millis)) {
                        playChime(appContext, chime, millis)
                    }
                }
                else -> {
                    val millis = if (loop && loopMs > 0L) loopMs else CHIME_ONCE_MS
                    playChime(appContext, chime, millis)
                }
            }
        }

        if (vibrate && !vibrationHandled) {
            vibratePattern(appContext, vibrateSec)
        }
    }

    /**
     * 循环播放内置音色指定时长。
     *
     * @param context 应用上下文
     * @param chime 音色
     * @param millis 时长
     */
    private fun playChime(context: Context, chime: String, millis: Long) {
        try {
            val streamId = ChimePlayer.playLoop(context, chime)
            if (streamId != 0) {
                scope.launch {
                    delay(millis.coerceAtLeast(500L))
                    ChimePlayer.stop(streamId)
                }
            }
        } catch (_: Exception) {
            // 音频不可用时静默降级
        }
    }

    /**
     * 播放固定波形震动。
     *
     * @param context 应用上下文
     * @param seconds 持续秒数
     */
    private fun vibratePattern(context: Context, seconds: Int) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, 0)
            }
            scope.launch {
                delay(seconds.coerceIn(1, 20) * 1000L)
                vibrator.cancel()
            }
        } catch (_: Exception) {
            // 无马达或被系统限制时忽略
        }
    }
}
