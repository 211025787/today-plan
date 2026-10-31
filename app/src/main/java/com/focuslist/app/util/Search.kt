package com.focuslist.app.util

/**
 * 全局搜索的纯计算工具：分词、打分、高亮区间、摘要截取。
 *
 * 不依赖 Android / Room / Compose，可在 JVM 单元测试里直接跑；
 * 只负责"文本对不对得上、排第几"，不碰数据来源。
 *
 * 设计要点：
 * - 多关键词按空格拆分，全部命中才算匹配（AND 语义），顺序无关；
 * - 命中位置分四档：全等 > 开头 > 词首（前一位是分隔符）> 中间包含；
 * - 同一档内越靠前分越高，避免"标题里靠后的词"压过"开头的词"；
 * - 字段带权重（标题比正文重要），取该条数据得分最高的字段作为最终分。
 */
object Search {

    /** 全等：整段文本就是关键词 */
    const val SCORE_EXACT = 100

    /** 前缀：文本以关键词开头 */
    const val SCORE_PREFIX = 80

    /** 词首：命中处前一位是分隔符（空格、标点、换行等） */
    const val SCORE_WORD_START = 60

    /** 包含：夹在文本中间 */
    const val SCORE_CONTAINS = 40

    /** 未命中标记：[scoreOf] 的返回值 */
    const val NO_MATCH = -1

    /** 位置惩罚上限：命中位置超过该值不再继续减分 */
    private const val POSITION_PENALTY_MAX = 30

    /** 摘要单侧保留字符数 */
    private const val SNIPPET_RADIUS = 24

    /** 搜索结果的默认条数上限 */
    const val DEFAULT_LIMIT = 50

    /** 视为分隔符的字符（分词与词首判定共用） */
    private val separators = setOf(
        ' ', '\t', '\n', '\r', ',', '，', '、', ';', '；', ':', '：',
        '.', '。', '!', '！', '?', '？', '-', '—', '_', '/', '\\',
        '(', ')', '（', '）', '[', ']', '【', '】', '{', '}',
        '"', '\'', '“', '”', '‘', '’', '《', '》', '<', '>', '|', '#', '@', '+', '=', '*', '~'
    )

    /**
     * 判断关键词是否为空（全空白或空串都不触发搜索）。
     *
     * @param query 原始输入
     * @return 为空时 true
     */
    fun isBlank(query: String): Boolean = query.isBlank()

    /**
     * 分词：按空白与常见标点切开，转小写，去空串与重复项。
     *
     * @param query 原始输入
     * @return 去重后的关键词列表（保持首次出现顺序）
     */
    fun tokenize(query: String): List<String> {
        val out = LinkedHashSet<String>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                out.add(buf.toString().lowercase())
                buf.setLength(0)
            }
        }
        for (ch in query) {
            if (ch.isWhitespace() || separators.contains(ch)) {
                flush()
            } else {
                buf.append(ch)
            }
        }
        flush()
        return out.toList()
    }

    /**
     * 单字段打分：所有关键词都命中才返回分数，否则返回 [NO_MATCH]。
     *
     * @param text 待匹配的文本
     * @param tokens 关键词（建议先过 [tokenize]，且非空）
     * @return 分数（越大越靠前）；未命中为 [NO_MATCH]
     */
    fun scoreOf(text: String, tokens: List<String>): Int {
        if (tokens.isEmpty() || text.isEmpty()) {
            return NO_MATCH
        }
        var total = 0
        for (token in tokens) {
            val idx = text.indexOf(token, ignoreCase = true)
            if (idx < 0) {
                return NO_MATCH
            }
            val base = when {
                idx == 0 && text.length == token.length -> SCORE_EXACT
                idx == 0 -> SCORE_PREFIX
                idx > 0 && !text[idx - 1].isLetterOrDigit() -> SCORE_WORD_START
                else -> SCORE_CONTAINS
            }
            // 同一档内越靠前越优；*10 保证档位差距不被位置惩罚抹平
            total += base * 10 - idx.coerceAtMost(POSITION_PENALTY_MAX)
        }
        return total
    }

    /**
     * 计算高亮区间：所有关键词在文本中的出现位置，合并重叠后按起点排序。
     *
     * @param text 原文
     * @param tokens 关键词
     * @param limit 最多返回的区间数（防止长文本里上万次命中拖慢渲染）
     * @return 升序、互不重叠的闭区间列表
     */
    fun highlightRanges(text: String, tokens: List<String>, limit: Int = 8): List<IntRange> {
        if (text.isEmpty() || tokens.isEmpty()) {
            return emptyList()
        }
        val raw = ArrayList<IntRange>()
        for (token in tokens) {
            if (token.isEmpty()) {
                continue
            }
            var from = 0
            while (raw.size < limit * 4) {
                val idx = text.indexOf(token, startIndex = from, ignoreCase = true)
                if (idx < 0) {
                    break
                }
                raw.add(idx until (idx + token.length))
                from = idx + token.length
            }
        }
        if (raw.isEmpty()) {
            return emptyList()
        }
        raw.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        var cur = raw.first()
        for (next in raw.drop(1)) {
            cur = if (next.first <= cur.last + 1) {
                cur.first..maxOf(cur.last, next.last)
            } else {
                merged.add(cur)
                next
            }
        }
        merged.add(cur)
        return merged.take(limit)
    }

    /**
     * 摘要：围绕首个命中位置截取一段正文，两端按需补省略号，换行压成空格。
     *
     * @param text 正文
     * @param ranges [highlightRanges] 的结果；为空时直接取开头
     * @param radius 命中位置两侧各保留的字符数
     * @return 单行摘要；原文为空时返回空串
     */
    fun snippet(text: String, ranges: List<IntRange>, radius: Int = SNIPPET_RADIUS): String {
        if (text.isEmpty()) {
            return ""
        }
        // 换行/制表符都是 1 字符换 1 空格，压平后下标不变，可直接使用命中位置
        val flat = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        val first = ranges.firstOrNull()
        val anchor = (first?.first ?: 0).coerceIn(0, flat.length)
        val start = (anchor - radius).coerceAtLeast(0)
        val end = (anchor + radius).coerceAtMost(flat.length)
        val body = flat.substring(start, end).trim()
        val head = if (start > 0) "…" else ""
        val tail = if (end < flat.length) "…" else ""
        return "$head$body$tail"
    }

    /**
     * 参与匹配的字段：文本 + 权重。
     *
     * @property text 字段内容
     * @property weight 权重（标题用 3，正文用 1）
     */
    data class Field(val text: String, val weight: Int = 1)

    /**
     * 一条数据的匹配结果。
     *
     * @property item 原数据
     * @property score 最终得分（已乘字段权重）
     * @property fieldIndex 命中的字段下标（对应 [fields] 的顺序）
     * @property ranges 命中字段内的高亮区间
     */
    data class Match<T>(
        val item: T,
        val score: Int,
        val fieldIndex: Int,
        val ranges: List<IntRange>
    )

    /**
     * 排序检索：逐条取"得分最高的字段"作为代表分，降序排列后截断。
     *
     * @param items 原始数据
     * @param tokens 关键词；为空时直接返回空列表（表示不进入搜索态）
     * @param limit 结果上限
     * @param fields 数据的待匹配字段（按重要性从高到低排列）
     * @return 按分数降序的匹配列表
     */
    fun <T> rank(
        items: List<T>,
        tokens: List<String>,
        limit: Int = DEFAULT_LIMIT,
        fields: (T) -> List<Field>
    ): List<Match<T>> {
        if (tokens.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<Match<T>>(items.size)
        for (item in items) {
            val fs = fields(item)
            var best: Match<T>? = null
            fs.forEachIndexed { index, field ->
                val raw = scoreOf(field.text, tokens)
                if (raw == NO_MATCH) {
                    return@forEachIndexed
                }
                val score = raw * field.weight.coerceAtLeast(1)
                if (best == null || score > best!!.score) {
                    best = Match(item, score, index, highlightRanges(field.text, tokens))
                }
            }
            best?.let { out.add(it) }
        }
        out.sortWith(compareByDescending<Match<T>> { it.score }.thenBy { fsIndexOf(it) })
        return out.take(limit)
    }

    /** 二次排序：同分时按命中字段下标（越靠前的字段越重要） */
    private fun <T> fsIndexOf(match: Match<T>): Int = match.fieldIndex
}
