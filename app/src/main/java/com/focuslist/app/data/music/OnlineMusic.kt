package com.focuslist.app.data.music

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 在线热门音乐客户端（网易云音乐公开接口）。
 *
 * 只取「热歌榜」与「关键词搜索」两类数据，播放走 `song/media/outer/url` 外链，
 * 不登录、不涉及用户账号。需要注意：
 * - 这是公开但非官方的接口，可能随时调整或限流，失败时一律返回空列表，由界面提示重试；
 * - 会员/付费曲目外链可能返回 HTML 而不是音频，下载时会做内容类型校验并提示换一首；
 * - 若未来要换成自有音乐服务，只需替换本文件的三个地址与解析函数，上层不用改。
 */
object OnlineMusic {

    /** 热歌榜歌单 ID（网易云「热歌榜」） */
    const val HOT_LIST_ID = 3778678L

    /** 热歌榜接口 */
    private const val PLAYLIST_ENDPOINT = "https://music.163.com/api/playlist/detail?id=$HOT_LIST_ID"

    /** 搜索接口 */
    private const val SEARCH_ENDPOINT = "https://music.163.com/api/search/get/web"

    /** 外链播放模板 */
    private const val OUTER_TEMPLATE = "https://music.163.com/song/media/outer/url?id=%d.mp3"

    /** 连接超时（毫秒） */
    private const val CONNECT_TIMEOUT_MS = 8_000

    /** 读取超时（毫秒） */
    private const val READ_TIMEOUT_MS = 12_000

    /** 请求头：直接访问接口需要桌面 UA 与 Referer */
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"

    /** 最多跟几轮重定向 */
    private const val MAX_REDIRECTS = 3

    /**
     * 拉取热歌榜。
     *
     * @param limit 最多返回条数
     * @return 曲目列表；网络失败或解析失败时为空列表
     */
    suspend fun hotSongs(limit: Int = 30): List<OnlineSong> = withContext(Dispatchers.IO) {
        val body = get(PLAYLIST_ENDPOINT) ?: return@withContext emptyList()
        parseSongs(body, limit, fromSearch = false)
    }

    /**
     * 按关键词搜索歌曲。
     *
     * @param keyword 关键词（歌名 / 歌手）
     * @param limit 最多返回条数
     * @return 曲目列表；关键词为空或请求失败时为空列表
     */
    suspend fun search(keyword: String, limit: Int = 20): List<OnlineSong> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val body = get(searchUrl(trimmed, limit)) ?: return@withContext emptyList()
            parseSongs(body, limit, fromSearch = true)
        }
    }

    /**
     * 曲目的外链播放地址。
     *
     * @param id 曲目 ID
     * @return mp3 外链
     */
    fun outerUrl(id: Long): String = String.format(OUTER_TEMPLATE, id)

    /**
     * 解析出可以直接播放 / 下载的最终地址。
     *
     * 外链会 302 跳到 `http://m701.music.126.net/...` 这样的 CDN 地址，而 Android 9+
     * 默认禁止明文 HTTP，直接交给 MediaPlayer 或 HttpURLConnection 会失败。
     * 这里手动跟一轮重定向，并把 http 升级成 https（CDN 同时支持）。
     *
     * @param id 曲目 ID
     * @return 最终的 https 地址；解析失败返回 null
     */
    suspend fun resolvePlayUrl(id: Long): String? = withContext(Dispatchers.IO) {
        var url = outerUrl(id)
        repeat(MAX_REDIRECTS) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Referer", "https://music.163.com/")
                }
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return@withContext url
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: return@withContext null
                        url = upgradeToHttps(URL(url), location)
                    }
                    else -> return@withContext null
                }
            } catch (_: IOException) {
                return@withContext null
            } catch (_: SecurityException) {
                return@withContext null
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    /**
     * 把重定向地址转成绝对 https 地址。
     *
     * @param base 当前地址（用于解析相对路径）
     * @param location 响应里的 Location
     * @return 绝对 https 地址
     */
    internal fun upgradeToHttps(base: URL, location: String): String {
        val absolute = runCatching { URL(base, location).toString() }.getOrDefault(location)
        return when {
            absolute.startsWith("http://") -> "https://" + absolute.removePrefix("http://")
            absolute.startsWith("//") -> "https:$absolute"
            else -> absolute
        }
    }

    /**
     * 拼装搜索地址。
     *
     * @param keyword 关键词
     * @param limit 条数
     * @return 完整 URL
     */
    fun searchUrl(keyword: String, limit: Int = 20): String {
        return "$SEARCH_ENDPOINT?s=${URLEncoder.encode(keyword, "UTF-8")}" +
            "&type=1&offset=0&limit=${limit.coerceIn(1, 50)}"
    }

    /**
     * 解析接口返回的 JSON。
     *
     * 热歌榜在 `result.tracks`，搜索在 `result.songs`，两者字段结构一致。
     *
     * @param body 响应 JSON
     * @param limit 最多返回条数
     * @param fromSearch true 表示搜索接口
     * @return 曲目列表；结构异常时为空列表
     */
    fun parseSongs(body: String, limit: Int, fromSearch: Boolean): List<OnlineSong> {
        return try {
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) return emptyList()
            val result = root.asJsonObject.getAsJsonObject("result") ?: return emptyList()
            val array = result.getAsJsonArray(if (fromSearch) "songs" else "tracks") ?: return emptyList()
            val songs = ArrayList<OnlineSong>(array.size())
            for (i in 0 until array.size()) {
                if (songs.size >= limit.coerceAtLeast(1)) break
                val item = array.get(i)
                if (!item.isJsonObject) continue
                val obj = item.asJsonObject
                val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: continue
                if (id <= 0L) continue
                val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                if (name.isEmpty()) continue
                val artist = obj.getAsJsonArray("artists")?.let { artists ->
                    (0 until artists.size())
                        .mapNotNull { index ->
                            val artistObj = artists.get(index)
                            if (!artistObj.isJsonObject) null else artistObj.asJsonObject
                                .get("name")?.takeIf { !it.isJsonNull }?.asString
                        }
                        .joinToString(" / ")
                }.orEmpty()
                val fee = obj.get("fee")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                songs.add(OnlineSong(id = id, name = name, artist = artist, vip = fee != 0 && fee != 8))
            }
            songs
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 发起一次 GET 请求。
     *
     * @param url 完整地址
     * @return 响应体；失败返回 null
     */
    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://music.163.com/")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
