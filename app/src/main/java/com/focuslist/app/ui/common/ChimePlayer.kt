package com.focuslist.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.focuslist.app.R

/**
 * 提示音播放器：SoundPool 预加载全部音色，低延迟播放，互不打断。
 *
 * 音频资源均为程序化合成（正弦波 + 包络），不含任何第三方录音素材，可直接分发。
 */
object ChimePlayer {

    /** 可选音色与 raw 资源映射（顺序即界面展示顺序） */
    private val resources = linkedMapOf(
        "BELL" to R.raw.chime_bell,
        "WOOD" to R.raw.chime_wood,
        "DROP" to R.raw.chime_drop,
        "BOWL" to R.raw.chime_bowl,
        "SOFT" to R.raw.chime_soft
    )

    /** 音色显示名与图标（候选列表与设置页共用） */
    val options: List<Triple<String, String, String>> = listOf(
        Triple("BELL", "清脆铃", "🔔"),
        Triple("WOOD", "木鱼", "🪵"),
        Triple("DROP", "水滴", "💧"),
        Triple("BOWL", "颂钵", "🪘"),
        Triple("SOFT", "柔音", "🎵")
    )

    /** 默认音色 */
    const val DEFAULT = "BELL"

    private var pool: SoundPool? = null

    /** 音色 -> SoundPool 资源 ID */
    private val ids = mutableMapOf<String, Int>()

    /**
     * 预加载全部音色（应用启动时调用一次，首次播放无需等待）。
     *
     * @param context 应用上下文
     */
    fun preload(context: Context) {
        val soundPool = pool ?: SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { pool = it }
        resources.forEach { (type, res) ->
            if (!ids.containsKey(type)) {
                ids[type] = soundPool.load(context.applicationContext, res, 1)
            }
        }
    }

    /**
     * 播放指定音色（未加载完成时静默跳过，不抛异常）。
     *
     * @param context 应用上下文
     * @param type 音色标识（BELL / WOOD / DROP / BOWL / SOFT）
     */
    fun play(context: Context, type: String) {
        preload(context)
        val id = ids[type] ?: ids[DEFAULT] ?: return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }

    /**
     * 循环播放指定音色，返回音频流 ID（用于稍后停止）。
     *
     * @param context 应用上下文
     * @param type 音色标识
     * @return 流 ID；无法播放时返回 0
     */
    fun playLoop(context: Context, type: String): Int {
        preload(context)
        val id = ids[type] ?: ids[DEFAULT] ?: return 0
        return pool?.play(id, 1f, 1f, 1, -1, 1f) ?: 0
    }

    /**
     * 停止指定音频流。
     *
     * @param streamId [playLoop] 返回的流 ID
     */
    fun stop(streamId: Int) {
        if (streamId != 0) {
            pool?.stop(streamId)
        }
    }
}
