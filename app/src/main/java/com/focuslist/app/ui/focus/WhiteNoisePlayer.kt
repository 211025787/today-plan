package com.focuslist.app.ui.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.random.Random

/**
 * 离线白噪音播放器：用 AudioTrack 实时生成噪声 PCM，无需任何音频资源文件。
 *
 * 支持两种噪声：
 * - WHITE 白噪声：各频率能量均匀，类似风扇声
 * - BROWN 棕噪声：低频占优，类似海浪/大雨声
 */
object WhiteNoisePlayer {

    /** 采样率 */
    private const val SAMPLE_RATE = 22050

    /** 输出音量（0.0-1.0），避免盖过提示音 */
    private const val VOLUME = 0.25f

    @Volatile
    private var playing = false

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    /** 当前噪声类型 */
    @Volatile
    var currentType: String = "NONE"
        private set

    /**
     * 开始播放指定噪声（已在播放则切换类型）。
     *
     * @param type WHITE / BROWN；其他值忽略
     */
    @Synchronized
    fun start(type: String) {
        if (type != "WHITE" && type != "BROWN") {
            return
        }
        if (playing && currentType == type) {
            return
        }
        stopInternal()
        try {
            startInternal(type)
        } catch (e: Exception) {
            // 音频设备异常时静默降级，不影响计时与页面
            playing = false
            currentType = "NONE"
        }
    }

    /**
     * 内部启动：构造 AudioTrack 并起生成线程。
     *
     * @param type WHITE / BROWN
     */
    private fun startInternal(type: String) {
        currentType = type
        playing = true

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE / 2)

        val newTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track = newTrack
        newTrack.play()

        // 生成线程：按 2048 样本块循环写入
        thread = Thread {
            val buffer = ShortArray(2048)
            var lastBrown = 0.0
            while (playing) {
                for (i in buffer.indices) {
                    val sample = if (currentType == "WHITE") {
                        Random.nextDouble(-1.0, 1.0)
                    } else {
                        // 棕噪声：对白噪声做漏积分并衰减防爆音
                        lastBrown = (lastBrown + 0.02 * Random.nextDouble(-1.0, 1.0)) / 1.02
                        (lastBrown * 3.5).coerceIn(-1.0, 1.0)
                    }
                    buffer[i] = (sample * Short.MAX_VALUE * VOLUME).toInt().toShort()
                }
                try {
                    track?.write(buffer, 0, buffer.size)
                } catch (e: IllegalStateException) {
                    break
                }
            }
        }.also { it.start() }
    }

    /**
     * 停止播放。
     */
    @Synchronized
    fun stop() {
        stopInternal()
    }

    /**
     * 内部停止：标记退出、释放 AudioTrack；生成线程检测到 playing=false 后自然退出，不 join 避免阻塞 UI 线程。
     */
    private fun stopInternal() {
        playing = false
        currentType = "NONE"
        thread = null
        try {
            track?.stop()
            track?.release()
        } catch (e: IllegalStateException) {
            // 已释放时忽略
        }
        track = null
    }
}