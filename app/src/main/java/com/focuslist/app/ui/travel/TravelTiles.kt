package com.focuslist.app.ui.travel

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 地图瓦片仓库：内存 + 磁盘两级缓存，断网时安静地失败（地图回到纸面网格）。
 *
 * 瓦片本身只包含地理底图，不携带任何用户数据；行程、地点与路线都只存在本机数据库。
 *
 * @property context 应用上下文（磁盘缓存放在 cacheDir）
 */
class TravelTileStore(private val context: Context) {

    /** 已加载的瓦片，Compose 读取它会在地图到达时自动重绘 */
    val images = mutableStateMapOf<String, ImageBitmap>()

    /** 最近一次失败次数，用于界面提示「地图未能加载」 */
    var failureCount by mutableIntStateOf(0)
        private set

    /** 已经加载出的瓦片数量 */
    var loadedCount by mutableIntStateOf(0)
        private set

    /** 正在请求中的瓦片，避免重复下载 */
    private val inFlight = mutableSetOf<String>()

    /** 插入顺序，用于超出上限时淘汰旧瓦片 */
    private val order = ArrayDeque<String>()

    /** 当前屏幕可见的瓦片，淘汰时避开 */
    private var visible: Set<String> = emptySet()

    /** 永久失败的瓦片（404 等），本次会话不再重试 */
    private val failed = mutableSetOf<String>()

    /**
     * 更新当前可见瓦片集合，并顺手把看不见的旧瓦片清出内存。
     *
     * @param keys 可见瓦片 key
     */
    fun setVisible(keys: Set<String>) {
        visible = keys
        prune()
    }

    /**
     * 读取一张瓦片：内存 → 磁盘 → 网络。
     *
     * @param z 缩放级别
     * @param x 瓦片横坐标
     * @param y 瓦片纵坐标
     * @return 瓦片位图；失败返回 null
     */
    suspend fun load(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = keyOf(z, x, y)
        images[key]?.let { return it }
        if (key in inFlight || key in failed) return null
        inFlight.add(key)
        try {
            val bitmap = withContext(Dispatchers.IO) {
                readFromDisk(key) ?: download(z, x, y)?.also { writeToDisk(key, it) }
            }
            if (bitmap == null) {
                failed.add(key)
                failureCount += 1
                return null
            }
            images[key] = bitmap
            order.addLast(key)
            loadedCount += 1
            prune()
            return bitmap
        } catch (e: CancellationException) {
            // 手势继续时旧请求会被取消，不能把它当成「瓦片加载失败」
            throw e
        } catch (e: Exception) {
            failed.add(key)
            failureCount += 1
            return null
        } finally {
            inFlight.remove(key)
        }
    }

    /**
     * 缓存 key：`z/x/y`。
     *
     * @param z 缩放级别
     * @param x 横坐标
     * @param y 纵坐标
     * @return key
     */
    fun keyOf(z: Int, x: Int, y: Int): String = "$z/$x/$y"

    /**
     * 内存超过上限时按插入顺序淘汰不可见瓦片。
     */
    private fun prune() {
        while (images.size > MAX_CACHED_TILES) {
            val iterator = order.iterator()
            var removed = false
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (key !in visible || images.size > MAX_CACHED_TILES + 40) {
                    iterator.remove()
                    images.remove(key)
                    removed = true
                    break
                }
            }
            if (!removed) break
        }
    }

    /**
     * 从磁盘缓存读。
     *
     * @param key 瓦片 key
     * @return 位图；不存在或损坏为 null
     */
    private fun readFromDisk(key: String): ImageBitmap? {
        val file = cacheFile(key)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 写磁盘缓存（先写临时文件再改名，避免写出半张图）。
     *
     * @param key 瓦片 key
     * @param bitmap 位图
     */
    private fun writeToDisk(key: String, bitmap: ImageBitmap) {
        try {
            val dir = cacheDir()
            if (!dir.exists()) dir.mkdirs()
            val file = cacheFile(key)
            val temp = File(dir, "${file.name}.tmp")
            temp.outputStream().use { out ->
                bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            if (file.exists()) file.delete()
            temp.renameTo(file)
        } catch (e: Exception) {
            // 磁盘缓存失败不影响显示
        }
    }

    /**
     * 联网下载一张瓦片。
     *
     * @param z 缩放级别
     * @param x 横坐标
     * @param y 纵坐标
     * @return 位图；失败为 null
     */
    private fun download(z: Int, x: Int, y: Int): ImageBitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(TravelTiles.url(z, x, y)).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            connection.setRequestProperty("User-Agent", "FocusList/1.0 (Android; personal travel planner)")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.w("TravelTileStore", "disconnect failed", e)
            }
        }
    }

    /** 磁盘缓存目录 */
    private fun cacheDir(): File = File(context.cacheDir, "map_tiles")

    /** 磁盘缓存文件 */
    private fun cacheFile(key: String): File = File(cacheDir(), key.replace('/', '_') + ".png")

    private companion object {
        /** 内存里最多保留多少张 256×256 瓦片（约 30 MB） */
        const val MAX_CACHED_TILES = 120
    }
}

/**
 * 瓦片地址与版权信息。
 */
object TravelTiles {

    /** 高德标准底图（GCJ-02 坐标，境外区域为 WGS-84） */
    private const val AMAP_URL =
        "https://webrd0%d.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=%d&y=%d&z=%d"

    /**
     * 拼装瓦片地址。
     *
     * @param z 缩放级别
     * @param x 横坐标
     * @param y 纵坐标
     * @return 瓦片 URL
     */
    fun url(z: Int, x: Int, y: Int): String {
        // 四个子域轮询，浏览器与地图 App 都这么做，能明显加快首屏
        val subdomain = ((x + y) % 4 + 4) % 4 + 1
        return String.format(Locale.US, AMAP_URL, subdomain, x, y, z)
    }

    /** 地图版权说明 */
    const val ATTRIBUTION = "© 高德地图 · 路线 OSRM"
}
