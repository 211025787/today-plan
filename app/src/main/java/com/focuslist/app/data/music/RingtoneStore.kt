package com.focuslist.app.data.music

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 铃声缓存仓库：把在线曲目下载到应用私有目录，或把系统铃声复制进来。
 *
 * 为什么落盘而不是每次在线播放：AlarmManager 触发提醒时进程可能刚被唤醒，
 * 网络不一定可用；提前缓存到本地能保证息屏/断网时也能按用户选的铃声响。
 *
 * 缓存策略：最多保留 [MAX_FILES] 个文件，超出时删除最久未使用的，避免长期占用空间。
 *
 * @property context 应用上下文
 */
class RingtoneStore(private val context: Context) {

    /** 缓存目录 */
    private val dir: File
        get() = File(context.filesDir, "ringtones").apply { if (!exists()) mkdirs() }

    /**
     * 按曲目 ID 找已缓存的铃声。
     *
     * @param songId 曲目 ID
     * @return 可播放的文件；未缓存或文件损坏时返回 null
     */
    fun fileOf(songId: Long): File? {
        return File(dir, "$songId.mp3").takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * 按路径找铃声文件。
     *
     * @param path 绝对路径
     * @return 可播放的文件；不存在时返回 null
     */
    fun fileOfPath(path: String): File? {
        if (path.isBlank()) return null
        return File(path).takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * 下载在线曲目到本地缓存（已存在则直接复用）。
     *
     * 下载后会校验内容类型与文件头：会员曲目的外链常常返回 HTML 错误页，
     * 这类响应必须拦截，否则会把网页存成「铃声」。
     *
     * @param song 曲目
     * @return 本地文件；不可用/网络失败时返回 null
     */
    suspend fun download(song: OnlineSong): File? = withContext(Dispatchers.IO) {
        val target = File(dir, "${song.id}.mp3")
        if (target.isFile && target.length() > 0L && looksLikeAudio(target)) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        val tmp = File(dir, "${song.id}.part")
        var connection: HttpURLConnection? = null
        try {
            // 外链会 302 到 http CDN，Android 默认禁明文，先解析成可用的 https 地址
            val source = OnlineMusic.resolvePlayUrl(song.id) ?: OnlineMusic.outerUrl(song.id)
            connection = (URL(source).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                setRequestProperty("Referer", "https://music.163.com/")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val contentType = connection.contentType.orEmpty().lowercase()
            val length = connection.contentLengthLong
            // 会员/版权受限时返回的是 HTML 错误页；过小的响应也不可能是完整音频
            if (contentType.startsWith("text/")) return@withContext null
            if (length in 1L..200_000L) return@withContext null
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!looksLikeAudio(tmp)) return@withContext null
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            prune(keep = target)
            target
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } finally {
            connection?.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * 把系统铃声 / 本地音频复制到缓存目录。
     *
     * @param uri 内容 URI
     * @param displayName 显示名（用于推断扩展名）
     * @return 复制后的文件；读取失败时返回 null
     */
    suspend fun importUri(uri: Uri, displayName: String): File? = withContext(Dispatchers.IO) {
        var output: File? = null
        try {
            val mime = context.contentResolver.getType(uri)
            val ext = extensionOf(mime, displayName)
            output = File(dir, "sys_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                output.outputStream().use { out -> input.copyTo(out) }
            } ?: return@withContext null
            if (!looksLikeAudio(output)) {
                output.delete()
                return@withContext null
            }
            output.setLastModified(System.currentTimeMillis())
            prune(keep = output)
            output
        } catch (_: IOException) {
            output?.delete()
            null
        } catch (_: SecurityException) {
            output?.delete()
            null
        }
    }

    /** 清空全部缓存（用户主动清理时调用） */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /**
     * 只保留最近使用的 [MAX_FILES] 个铃声文件。
     *
     * @param keep 当前正在使用的文件，必须保留
     */
    private fun prune(keep: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_FILES) return
        files.filter { it != keep }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_FILES - 1)
            .forEach { it.delete() }
    }

    /**
     * 用文件头判断是不是音频：MP3（ID3 或 MPEG 帧同步）与常见容器。
     *
     * @param file 待检查文件
     * @return 是否像音频
     */
    private fun looksLikeAudio(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return try {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            val id3 = header[0].toInt() == 0x49 && header[1].toInt() == 0x44 && header[2].toInt() == 0x33
            val mpeg = (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0
            val ogg = String(header, Charsets.US_ASCII) == "OggS"
            val ftyp = String(header, Charsets.US_ASCII) == "ftyp"
            val riff = String(header, Charsets.US_ASCII) == "RIFF"
            id3 || mpeg || ogg || ftyp || riff
        } catch (_: IOException) {
            false
        }
    }

    /**
     * 推断扩展名。
     *
     * @param mime 内容类型
     * @param displayName 文件名
     * @return 扩展名（不含点）
     */
    private fun extensionOf(mime: String?, displayName: String): String {
        val fromMime = when {
            mime == null -> ""
            mime.contains("mpeg", ignoreCase = true) -> "mp3"
            mime.contains("ogg", ignoreCase = true) -> "ogg"
            mime.contains("flac", ignoreCase = true) -> "flac"
            mime.contains("wav", ignoreCase = true) -> "wav"
            mime.contains("aac", ignoreCase = true) || mime.contains("mp4", ignoreCase = true) -> "m4a"
            else -> ""
        }
        if (fromMime.isNotEmpty()) return fromMime
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        return fromName.takeIf { it in setOf("mp3", "ogg", "flac", "wav", "m4a", "aac") } ?: "mp3"
    }

    private companion object {
        /** 最多保留的铃声文件数 */
        const val MAX_FILES = 3
    }
}
