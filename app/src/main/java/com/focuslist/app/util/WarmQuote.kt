package com.focuslist.app.util

/**
 * 开屏的一句话。
 *
 * @property text 正文
 * @property author 署名
 */
data class WarmQuote(val text: String, val author: String)

/**
 * 开屏随机格言。
 *
 * 说明：这里的句子是**为「暖历」写的原创短句**，署名是带点梗的身份泛称
 * （某位韩国前总统 / 币圈老兵 / 夜班人……），不冒充任何真实人物说过的话，
 * 也不涉及政治评价或投资建议。要换成考据过的真实语录，只改 [ALL] 里的
 * text / author 两个字段即可，动画与布局都不用动。
 */
object WarmQuotes {

    /** 全部格言：每次冷启动从中随机取一条 */
    val ALL: List<WarmQuote> = listOf(
        WarmQuote("今天不必惊天动地，往前一格就好。", "某位韩国前总统"),
        WarmQuote("熬过的时间，最后都会算数。", "某位币圈老兵"),
        WarmQuote("别急着看结果，先把今天过完。", "青瓦台的夜班人"),
        WarmQuote("真正的长期，是肯重复平凡的一天。", "不看盘的人"),
        WarmQuote("日子不会辜负每天回来的人。", "某位韩国前总统"),
        WarmQuote("账要慢慢记，路要天天走。", "币圈老兵"),
        WarmQuote("暖的不是日历，是你又来了一次。", "暖历"),
        WarmQuote("先把手边这件小事做完，天就亮了。", "凌晨四点盯盘的人"),
        WarmQuote("慢一点没关系，别停下来就行。", "某位韩国前总统"),
        WarmQuote("能留下来的，都是慢慢攒的。", "不看盘的人"),
        WarmQuote("一格一格填，日子自己会亮。", "暖历"),
        WarmQuote("昨天已经结账，今天还能重写。", "某位币圈老兵")
    )

    /** 每次打开取一条（冷启动会重新执行 setContent，自然换一句） */
    fun random(): WarmQuote = ALL.random()

    /**
     * 按下标取一条，负数与越界都安全取模——单测用，也便于以后按日期固定展示。
     *
     * @param index 任意整数
     * @return 对应位置的格言
     */
    fun at(index: Int): WarmQuote {
        val size = ALL.size
        return ALL[((index % size) + size) % size]
    }
}
