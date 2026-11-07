package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 开屏格言数据的纯 JVM 测试。
 */
class WarmQuoteTest {

    @Test
    fun `每条格言都有正文与署名`() {
        assertTrue(WarmQuotes.ALL.isNotEmpty())
        WarmQuotes.ALL.forEach { quote ->
            assertTrue(quote.text.isNotBlank())
            assertTrue(quote.author.isNotBlank())
        }
    }

    @Test
    fun `句子不会长到开屏排不下`() {
        // 这批语录偏长（最长 53 字），开屏里超过 30 字会自动降一号字，上限按 54 字卡
        WarmQuotes.ALL.forEach { quote ->
            assertTrue("${quote.text} 太长", quote.text.length <= 54)
            assertTrue("${quote.author} 太长", quote.author.length <= 12)
        }
    }

    @Test
    fun `按下标取 负数与越界都取模`() {
        val size = WarmQuotes.ALL.size
        assertEquals(WarmQuotes.ALL[0], WarmQuotes.at(0))
        assertEquals(WarmQuotes.ALL[0], WarmQuotes.at(size))
        assertEquals(WarmQuotes.ALL[size - 1], WarmQuotes.at(-1))
        assertEquals(WarmQuotes.ALL[size - 1], WarmQuotes.at(-1 - size))
    }

    @Test
    fun `随机结果一定来自格言库`() {
        repeat(50) {
            assertTrue(WarmQuotes.random() in WarmQuotes.ALL)
        }
    }

    @Test
    fun `署名不重复堆在同一句上`() {
        // 数据里同一署名可以重复，但不应出现完全相同的两句
        assertEquals(WarmQuotes.ALL.size, WarmQuotes.ALL.toSet().size)
    }
}
