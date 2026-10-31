package com.focuslist.app.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 提醒播放器：统一处理「响铃 + 震动」及其自定义持续时长。
 *
 * - 铃声按所选音色循环播放，到达设定秒数后自动停止；
 * - 震动用波形循环（震 500ms / 停 350ms），到达设定秒数后取消；
 * - 两者互不影响，可只开其一；异常一律静默降级，不影响计时。
 */
object AlertPlayer {

    /** 应用级协程域：用于到点停止铃声/震动 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 震动波形：等待 0ms，震动 500ms，间隔 350ms */
    private val vibrationPattern = longArrayOf(0L, 500L, 350L)

    /**
     * 播放一次提醒。
     *
     * @param context 上下文
     * @param chime 音色标识（BELL / WOOD / DROP / BOWL / SOFT）
     * @param sound 是否响铃
     * @param vibrate 是否震动
     * @param soundSec 响铃持续秒数
     * @param vibrateSec 震动持续秒数
     */
    fun play(
        context: Context,
        chime: String,
        sound: Boolean,
        vibrate: Boolean,
        soundSec: Int,
        vibrateSec: Int
    ) {
        val appContext = context.applicationContext
        if (sound) {
            try {
                val streamId = ChimePlayer.playLoop(appContext, chime)
                if (streamId != 0) {
                    scope.launch {
                        delay(soundSec.coerceIn(1, 20) * 1000L)
                        ChimePlayer.stop(streamId)
                    }
                }
            } catch (_: Exception) {
                // 音频不可用时静默降级
            }
        }
        if (vibrate) {
            try {
                val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(vibrationPattern, 0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(vibrationPattern, 0)
                    }
                    scope.launch {
                        delay(vibrateSec.coerceIn(1, 20) * 1000L)
                        vibrator.cancel()
                    }
                }
            } catch (_: Exception) {
                // 无马达或被系统限制时忽略
            }
        }
    }
}
