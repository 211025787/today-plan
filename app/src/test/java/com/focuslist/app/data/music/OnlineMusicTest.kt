package com.focuslist.app.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在线音乐接口解析与地址拼装的单元测试。
 *
 * 只覆盖纯 JSON 解析与 URL 规则，不发起真实网络请求。
 */
class OnlineMusicTest {

    @Test
    fun `parse hot list tracks`() {
        val body = """
            {"result":{"tracks":[
              {"id":101,"name":"晴天","artists":[{"name":"周杰伦"}],"fee":0},
              {"id":102,"name":"夜曲","artists":[{"name":"周杰伦"},{"name":"费玉清"}],"fee":1},
              {"id":103,"name":"免费歌","artists":[],"fee":8}
            ]}}
        """.trimIndent()
        val songs = OnlineMusic.parseSongs(body, limit = 30, fromSearch = false)
        assertEquals(3, songs.size)
        assertEquals("晴天", songs[0].name)
        assertEquals("周杰伦", songs[0].artist)
        assertEquals(false, songs[0].vip)
        assertEquals("周杰伦 / 费玉清", songs[1].artist)
        assertEquals(true, songs[1].vip)
        assertEquals(false, songs[2].vip)
        assertEquals("免费歌", songs[2].displayName)
    }

    @Test
    fun `parse search songs respects limit`() {
        val body = """
            {"result":{"songs":[
              {"id":1,"name":"A","artists":[{"name":"X"}],"fee":0},
              {"id":2,"name":"B","artists":[{"name":"Y"}],"fee":8}
            ]}}
        """.trimIndent()
        val songs = OnlineMusic.parseSongs(body, limit = 1, fromSearch = true)
        assertEquals(1, songs.size)
        assertEquals("A", songs[0].name)
    }

    @Test
    fun `parse skips invalid entries and malformed body`() {
        val body = """
            {"result":{"tracks":[
              {"id":0,"name":"坏数据","artists":[]},
              {"id":5,"name":"","artists":[]},
              {"id":6,"name":"好数据","artists":[{"name":"歌手"}],"fee":0}
            ]}}
        """.trimIndent()
        val songs = OnlineMusic.parseSongs(body, limit = 30, fromSearch = false)
        assertEquals(1, songs.size)
        assertEquals("好数据", songs[0].name)
        assertTrue(OnlineMusic.parseSongs("not json", 10, false).isEmpty())
        assertTrue(OnlineMusic.parseSongs("{}", 10, false).isEmpty())
    }

    @Test
    fun `outer url and search url`() {
        assertEquals(
            "https://music.163.com/song/media/outer/url?id=42.mp3",
            OnlineMusic.outerUrl(42)
        )
        val search = OnlineMusic.searchUrl("晴天 周杰伦", 10)
        assertTrue(search.startsWith("https://music.163.com/api/search/get/web?s="))
        assertTrue(search.contains("&type=1"))
        assertTrue(search.contains("&limit=10"))
    }
}
