package com.focuslist.app.util

/**
 * 把收工小结写成一则备注。
 *
 * 为什么是「存进备注」而不是「复制文本」：这个应用本来就有按天组织的备注，
 * 让一天自动留一条 Markdown 小记，比让用户自己粘到别处更符合它的定位。
 *
 * 内容刻意保持短：标题行 + 一行总量 + 逐计划列表 + 一句结语，
 * 既能在备注列表里一眼扫完，也不会把 2000 字的备注写满。
 */
object DayWrapScript {

    /** 自动备注的固定标题，用于判断今天是否已经存过 */
    const val NOTE_TITLE = "收工小结"

    /** 列表里最多列几个计划 */
    const val MAX_ROWS = 5

    /**
     * 生成备注正文（Markdown）。
     *
     * @param wrap 收工小结
     * @param dateLabel 日期文案，如「11 月 1 日」
     * @return Markdown 文本，以换行分隔，末尾不留空行
     */
    fun content(wrap: DayWrap, dateLabel: String): String {
        val builder = StringBuilder()
        builder.append("### ").append(NOTE_TITLE).append(" · ").append(dateLabel).append('\n')
        builder.append("**").append(wrap.headline).append("**")
        if (wrap.planCount > 0) {
            builder.append(" · 达标 ").append(wrap.achievedCount).append('/').append(wrap.planCount)
        }
        builder.append('\n')

        if (wrap.rows.isNotEmpty()) {
            builder.append('\n')
            wrap.rows.take(MAX_ROWS).forEach { row ->
                builder.append("- ").append(row.title).append(' ')
                if (row.goalMinutes > 0) {
                    builder.append(row.minutes).append(" / ").append(row.goalMinutes).append(" 分钟")
                } else {
                    builder.append(row.minutes).append(" 分钟")
                }
                if (row.achieved) {
                    builder.append(" ✅")
                }
                builder.append('\n')
            }
            if (wrap.rows.size > MAX_ROWS) {
                builder.append("- 另有 ").append(wrap.rows.size - MAX_ROWS).append(" 个计划未列出\n")
            }
        }

        if (wrap.closing.isNotBlank()) {
            builder.append('\n').append("> ").append(wrap.closing).append('\n')
        }
        return builder.toString().trimEnd()
    }
}
