package com.focuslist.app.data.music

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 音频波形提取：把一首歌解码成固定数量的峰值点，用于在界面上画音频条。
 *
 * 为什么不用 MediaMetadataRetriever：它只能给时长/封面，拿不到振幅。
 * 这里用 MediaExtractor + MediaCodec 解码成 PCM，再按块取绝对值峰值并降采样。
 *
 * 峰值会缓存成同名 `.peaks` 文件（240 个 float），这样闹钟触发时不必在广播里
 * 重新解码整首歌，直接读几百字节就能驱动「震动跟音量同频」。
 */
object AudioWaveform {

    /** 波形柱数量 */
    const val BUCKETS = 240

    /** 解码时每块采样点数（先按块取峰值，再降采样到 [BUCKETS]） */
    private const val BLOCK_SAMPLES = 1_024

    /** 解码总时长上限（毫秒）：模拟器软件解码较慢，超时就先用空波形兜底 */
    private const val DECODE_BUDGET_MS = 6_000L

    /** 后台预热用的协程域 */
    private val ioScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /**
     * 后台生成波形缓存（不阻塞调用方，界面不等待）。
     *
     * @param file 音频文件
     */
    fun warm(file: File) {
        ioScope.launch {
            if (load(file) == null) {
                compute(file)
            }
        }
    }

    /**
     * 计算并缓存波形。
     *
     * @param file 音频文件
     * @return 峰值数组（0-1）；解码失败返回空数组
     */
    fun compute(file: File): FloatArray {
        val peaks = decode(file)
        if (peaks.isNotEmpty()) {
            save(file, peaks)
        }
        return peaks
    }

    /**
     * 读取已缓存的波形。
     *
     * @param file 音频文件
     * @return 峰值数组；没有缓存或文件损坏时返回 null
     */
    fun load(file: File): FloatArray? {
        val cache = cacheFile(file)
        if (!cache.isFile || cache.length() != BUCKETS * 4L) return null
        return try {
            val bytes = cache.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(BUCKETS) { buffer.float.coerceIn(0f, 1f) }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * 保存波形缓存。
     *
     * @param file 音频文件
     * @param peaks 峰值数组
     */
    fun save(file: File, peaks: FloatArray) {
        try {
            val buffer = ByteBuffer.allocate(peaks.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            peaks.forEach { buffer.putFloat(it) }
            cacheFile(file).writeBytes(buffer.array())
        } catch (_: IOException) {
            // 缓存失败不影响播放，只是下次要重新解码
        }
    }

    /** 删除波形缓存（替换铃声时清理） */
    fun clear(file: File) {
        runCatching { cacheFile(file).delete() }
    }

    /**
     * 波形缓存文件路径。
     *
     * @param file 音频文件
     * @return 同名 `.peaks`
     */
    private fun cacheFile(file: File): File = File(file.parentFile, "${file.name}.peaks2")

    /**
     * 解码音频并计算峰值。
     *
     * @param file 音频文件
     * @return 峰值数组；失败返回空数组
     */
    private fun decode(file: File): FloatArray {
        if (!file.isFile || file.length() <= 0L) return FloatArray(0)
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return try {
            extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return FloatArray(0)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val blocks = ArrayList<Float>(4_096)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var guard = 0
            val deadline = SystemClock.elapsedRealtime() + DECODE_BUDGET_MS
            while (!outputDone && guard++ < 20_000 && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outIndex >= 0) {
                    val out = codec.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.asShortBuffer()
                        val samples = ShortArray(shorts.remaining())
                        shorts.get(samples)
                        appendPeaks(samples, blocks)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
            downsample(blocks)
        } catch (_: Exception) {
            FloatArray(0)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    /**
     * 按 [BLOCK_SAMPLES] 分块取均方根（RMS）。
     *
     * 用 RMS 而不是峰值：音乐峰值几乎一直贴顶，画出来会整片顶满上下；
     * RMS 保留响度差异，配合界面归一化后能看出真实的起伏。
     *
     * @param samples PCM 采样（可能多声道交错）
     * @param out 输出块峰值
     */
    private fun appendPeaks(samples: ShortArray, out: MutableList<Float>) {
        var index = 0
        while (index < samples.size) {
            val end = min(index + BLOCK_SAMPLES, samples.size)
            var sum = 0.0
            for (i in index until end) {
                val value = samples[i].toDouble()
                sum += value * value
            }
            val rms = kotlin.math.sqrt(sum / (end - index).coerceAtLeast(1))
            out.add((rms / 32_768.0).toFloat().coerceIn(0f, 1f))
            index = end
        }
    }

    /**
     * 把块峰值降采样到 [BUCKETS] 个。
     *
     * @param blocks 块峰值
     * @return 固定长度数组
     */
    private fun downsample(blocks: MutableList<Float>): FloatArray {
        if (blocks.isEmpty()) return FloatArray(0)
        val result = FloatArray(BUCKETS)
        for (i in 0 until BUCKETS) {
            val from = (i.toLong() * blocks.size / BUCKETS).toInt()
            val to = (((i + 1).toLong() * blocks.size / BUCKETS).toInt()).coerceAtMost(blocks.size)
            var peak = 0f
            for (j in from until max(from + 1, to)) {
                if (j < blocks.size) {
                    peak = max(peak, blocks[j])
                }
            }
            result[i] = peak.coerceIn(0f, 1f)
        }
        return result
    }

    /**
     * 取出某个时间点对应的波形幅度。
     *
     * @param peaks 波形数组
     * @param positionMs 相对音频开头的位置
     * @param durationMs 音频总时长
     * @return 0-1 的幅度
     */
    fun amplitudeAt(peaks: FloatArray, positionMs: Long, durationMs: Long): Float {
        if (peaks.isEmpty() || durationMs <= 0L) return 0.4f
        val ratio = (positionMs.coerceIn(0L, durationMs).toDouble() / durationMs).toFloat()
        val index = (ratio * (peaks.size - 1)).toInt().coerceIn(0, peaks.size - 1)
        return peaks[index]
    }
}
