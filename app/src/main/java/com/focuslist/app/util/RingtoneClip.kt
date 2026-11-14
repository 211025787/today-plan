package com.focuslist.app.util

/**
 * 铃声片段的纯计算工具：范围收敛、时长文案。
 *
 * 不依赖 Android，便于在 JVM 单测里钉住边界。
 */
object RingtoneClip {

    /** 片段最短长度（毫秒） */
    const val MIN_GAP_MS = 1_000L

    /** 循环播放时长上限（秒） */
    const val MAX_DURATION_SEC = 1_800

    /**
     * 把用户拖动的片段范围收敛到合法区间。
     *
     * 规则：起点不小于 0；终点不超过音频总时长；终点至少比起始晚 [MIN_GAP_MS]；
     * 总时长未知（<=0）时按 [MIN_GAP_MS] 兜底。
     *
     * @param startMs 起始毫秒
     * @param endMs 结束毫秒，0 表示到结尾
     * @param durationMs 音频总时长
     * @return 收敛后的「起点..终点」
     */
    fun normalize(startMs: Long, endMs: Long, durationMs: Long): LongRange {
        val total = if (durationMs > 0L) durationMs else MIN_GAP_MS
        var start = startMs.coerceIn(0L, (total - MIN_GAP_MS).coerceAtLeast(0L))
        var end = if (endMs <= 0L) total else endMs.coerceIn(0L, total)
        if (end - start < MIN_GAP_MS) {
            // 终点离起点太近时优先把终点往后推，推不动再回退起点
            end = (start + MIN_GAP_MS).coerceAtMost(total)
            if (end - start < MIN_GAP_MS) {
                start = (end - MIN_GAP_MS).coerceAtLeast(0L)
            }
        }
        return start..end
    }

    /**
     * 实际使用的终点：没设置（<=0）时用音频总时长。
     *
     * @param endMs 用户设置的终点
     * @param durationMs 音频总时长
     * @return 终点毫秒
     */
    fun effectiveEnd(endMs: Long, durationMs: Long): Long {
        return if (endMs > 0L) endMs else durationMs
    }

    /**
     * 片段长度（毫秒），至少 0。
     *
     * @param startMs 起点
     * @param endMs 终点
     * @return 长度
     */
    fun lengthMs(startMs: Long, endMs: Long): Long {
        return (endMs - startMs).coerceAtLeast(0L)
    }

    /**
     * 播放时长约束到 0-[MAX_DURATION_SEC]，0 表示只播放一遍。
     *
     * @param seconds 秒数
     * @return 合法秒数
     */
    fun clampDurationSec(seconds: Int): Int {
        return seconds.coerceIn(0, MAX_DURATION_SEC)
    }

    /**
     * 把毫秒格式化成 `m:ss`。
     *
     * @param ms 毫秒
     * @return 时间文案
     */
    fun formatTime(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }

    /**
     * 把「分 + 秒」合成一句中文时长。
     *
     * @param minutes 分钟
     * @param seconds 秒
     * @return 例如「1 分 30 秒」「45 秒」「0 秒」
     */
    fun durationText(minutes: Int, seconds: Int): String {
        val m = minutes.coerceAtLeast(0)
        val s = seconds.coerceIn(0, 59)
        return when {
            m > 0 && s > 0 -> "$m 分 $s 秒"
            m > 0 -> "$m 分"
            else -> "$s 秒"
        }
    }
}
