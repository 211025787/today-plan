package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Search] 纯逻辑单测：分词 / 四档打分 / 高亮区间 / 摘要 / 排序。
 */
class SearchTest {

    /** 分词：空格与标点混排 */
    @Test
    fun tokenize_splitsOnWhitespaceAndPunctuation() {
        assertEquals(listOf("阅读", "30"), Search.tokenize("阅读 30"))
        assertEquals(listOf("hello", "world"), Search.tokenize("Hello, World!"))
        assertEquals(listOf("读书", "笔记"), Search.tokenize("读书\n笔记"))
    }

    /** 分词：空输入、去重、大小写归一 */
    @Test
    fun tokenize_normalizes() {
        assertEquals(emptyList<String>(), Search.tokenize("   "))
        assertEquals(listOf("a", "b"), Search.tokenize("a a b"))
        assertEquals(listOf("java"), Search.tokenize("Java"))
    }

    /** 空查询判定 */
    @Test
    fun isBlank_detectsEmptyQuery() {
        assertTrue(Search.isBlank(""))
        assertTrue(Search.isBlank("   \t"))
        assertTrue(!Search.isBlank("a"))
    }

    /** 打分：全等 > 前缀 > 词首 > 包含 */
    @Test
    fun scoreOf_ranksByMatchPosition() {
        val exact = Search.scoreOf("阅读", listOf("阅读"))
        val prefix = Search.scoreOf("阅读打卡", listOf("阅读"))
        val wordStart = Search.scoreOf("今天 阅读", listOf("阅读"))
        val contains = Search.scoreOf("今天阅读", listOf("阅读"))
        assertTrue("全等应最高", exact > prefix)
        assertTrue("前缀应高于词首", prefix > wordStart)
        assertTrue("词首应高于包含", wordStart > contains)
        assertEquals(Search.NO_MATCH, Search.scoreOf("健身", listOf("阅读")))
    }

    /** 打分：档位基准值 */
    @Test
    fun scoreOf_baseValues() {
        assertEquals(Search.SCORE_EXACT * 10, Search.scoreOf("阅读", listOf("阅读")))
        assertEquals(Search.SCORE_PREFIX * 10, Search.scoreOf("阅读打卡", listOf("阅读")))
        // 命中位置 3，前一位是空格 → 词首档，减 3 分位置惩罚
        assertEquals(Search.SCORE_WORD_START * 10 - 3, Search.scoreOf("今天 阅读", listOf("阅读")))
        // 命中位置 2，前一位是中文（字母或数字）→ 包含档
        assertEquals(Search.SCORE_CONTAINS * 10 - 2, Search.scoreOf("今天阅读", listOf("阅读")))
    }

    /** 打分：多关键词求和，且必须全部命中 */
    @Test
    fun scoreOf_multipleTokens() {
        val one = Search.scoreOf("阅读 30 分钟", listOf("阅读"))
        val two = Search.scoreOf("阅读 30 分钟", listOf("阅读", "分钟"))
        assertTrue("两词命中应高于单词", two > one)
        assertEquals(Search.NO_MATCH, Search.scoreOf("阅读 30 分钟", listOf("阅读", "健身")))
    }

    /** 打分：大小写不敏感 */
    @Test
    fun scoreOf_ignoresCase() {
        assertTrue(Search.scoreOf("学习 Java 技术", listOf("java")) >= Search.SCORE_CONTAINS * 10)
        assertTrue(Search.scoreOf("JAVA", listOf("java")) == Search.SCORE_EXACT * 10)
    }

    /** 打分：空关键词或空文本视为未命中 */
    @Test
    fun scoreOf_emptyInputs() {
        assertEquals(Search.NO_MATCH, Search.scoreOf("阅读", emptyList()))
        assertEquals(Search.NO_MATCH, Search.scoreOf("", listOf("阅读")))
    }

    /** 打分：位置惩罚有上限，超长前缀不会把分扣成负 */
    @Test
    fun scoreOf_positionPenaltyCapped() {
        val far = "x".repeat(100) + "阅读"
        val score = Search.scoreOf(far, listOf("阅读"))
        assertTrue(score > 0)
        // 惩罚封顶 30：与刚好 30 个前缀字符时同分
        assertEquals(Search.scoreOf("x".repeat(30) + "阅读", listOf("阅读")), score)
    }

    /** 高亮：多处命中按序返回 */
    @Test
    fun highlightRanges_findsAllOccurrences() {
        assertEquals(listOf(0..1, 4..5), Search.highlightRanges("阅读打卡阅读", listOf("阅读")))
    }

    /** 高亮：相邻命中合并为一个区间 */
    @Test
    fun highlightRanges_mergesAdjacent() {
        assertEquals(listOf(0..3), Search.highlightRanges("aaaa", listOf("aa")))
        assertEquals(listOf(0..1, 3..4), Search.highlightRanges("abcab", listOf("ab")))
    }

    /** 高亮：无命中或空输入返回空 */
    @Test
    fun highlightRanges_emptyCases() {
        assertEquals(emptyList<IntRange>(), Search.highlightRanges("阅读", listOf("健身")))
        assertEquals(emptyList<IntRange>(), Search.highlightRanges("", listOf("阅读")))
        assertEquals(emptyList<IntRange>(), Search.highlightRanges("阅读", emptyList()))
    }

    /** 高亮：条数上限 */
    @Test
    fun highlightRanges_respectsLimit() {
        val text = "ab".repeat(20)
        assertTrue(Search.highlightRanges(text, listOf("ab"), limit = 3).size <= 3)
    }

    /** 摘要：长文本两侧补省略号且包含命中词 */
    @Test
    fun snippet_clipsAroundMatch() {
        val text = "前面是一大段无关的话".repeat(5) + "阅读" + "后面也是一大段无关的话".repeat(5)
        val ranges = Search.highlightRanges(text, listOf("阅读"))
        val snip = Search.snippet(text, ranges)
        assertTrue(snip.contains("阅读"))
        assertTrue(snip.startsWith("…"))
        assertTrue(snip.endsWith("…"))
        assertTrue("摘要应远短于原文", snip.length < text.length / 2)
    }

    /** 摘要：短文本原样返回，不加省略号 */
    @Test
    fun snippet_keepsShortText() {
        val snip = Search.snippet("每天阅读", listOf(0..1))
        assertEquals("每天阅读", snip)
        assertTrue(!snip.contains("…"))
    }

    /** 摘要：换行压成空格，下标不错位 */
    @Test
    fun snippet_flattensNewlines() {
        val text = "第一行\n第二行\n第三行关键词"
        val ranges = Search.highlightRanges(text, listOf("关键词"))
        val snip = Search.snippet(text, ranges)
        assertTrue(!snip.contains("\n"))
        assertTrue(snip.contains("关键词"))
    }

    /** 摘要：空文本 */
    @Test
    fun snippet_emptyText() {
        assertEquals("", Search.snippet("", listOf(0..0)))
    }

    /** 排序：标题权重高于正文 */
    @Test
    fun rank_prefersTitleOverBody() {
        data class Doc(val title: String, val body: String)
        val titleHit = Doc("阅读", "无关")
        val bodyHit = Doc("无关", "阅读")
        val out = Search.rank(listOf(bodyHit, titleHit), listOf("阅读")) { d ->
            listOf(Search.Field(d.title, 3), Search.Field(d.body, 1))
        }
        assertEquals(listOf(titleHit, bodyHit), out.map { it.item })
    }

    /** 排序：按分数降序 */
    @Test
    fun rank_sortsByScoreDesc() {
        data class Doc(val title: String)
        val items = listOf(Doc("今天阅读"), Doc("阅读"), Doc("阅读打卡"))
        val out = Search.rank(items, listOf("阅读")) { listOf(Search.Field(it.title)) }
        assertEquals(listOf("阅读", "阅读打卡", "今天阅读"), out.map { it.item.title })
    }

    /** 排序：未命中的条目被剔除 */
    @Test
    fun rank_dropsNonMatching() {
        data class Doc(val title: String)
        val out = Search.rank(listOf(Doc("健身"), Doc("阅读")), listOf("阅读")) { listOf(Search.Field(it.title)) }
        assertEquals(1, out.size)
        assertEquals("阅读", out.first().item.title)
    }

    /** 排序：空关键词不产出结果（表示不进入搜索态） */
    @Test
    fun rank_emptyTokens() {
        data class Doc(val title: String)
        assertEquals(0, Search.rank(listOf(Doc("阅读")), emptyList()) { listOf(Search.Field(it.title)) }.size)
    }

    /** 排序：条数上限 */
    @Test
    fun rank_respectsLimit() {
        data class Doc(val title: String)
        val items = (1..200).map { Doc("阅读$it") }
        assertEquals(5, Search.rank(items, listOf("阅读"), limit = 5) { listOf(Search.Field(it.title)) }.size)
    }

    /** 排序：命中字段下标随结果返回，供 UI 决定摘要来源 */
    @Test
    fun rank_reportsFieldIndexAndRanges() {
        data class Doc(val title: String, val body: String)
        val out = Search.rank(listOf(Doc("无关", "阅读笔记")), listOf("阅读")) { d ->
            listOf(Search.Field(d.title, 3), Search.Field(d.body, 1))
        }
        val match = out.single()
        assertEquals(1, match.fieldIndex)
        assertEquals(listOf(0..1), match.ranges)
    }

    /** 综合：真实场景——搜「英语」能同时命中计划标题与备注正文 */
    @Test
    fun rank_mixedRealWorldCase() {
        data class Doc(val title: String, val body: String)
        val docs = listOf(
            Doc("学习英语", "每天背单词"),
            Doc("健身", "练英语的时候听播客"),
            Doc("写日记", "今天没学英语"),
            Doc("阅读", " unrelated ")
        )
        val out = Search.rank(docs, listOf("英语")) {
            listOf(Search.Field(it.title, 3), Search.Field(it.body, 1))
        }
        assertEquals(3, out.size)
        assertEquals("学习英语", out.first().item.title)
        assertTrue(out.map { it.item.title }.containsAll(listOf("健身", "写日记")))
    }
}
