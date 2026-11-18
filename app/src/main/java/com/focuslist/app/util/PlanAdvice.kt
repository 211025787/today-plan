package com.focuslist.app.util

/**
 * 计划智能建议：按标题识别类别，给出更合理的执行节奏。
 *
 * 不同目标的节奏差异很大：
 * - 健身 / 运动需要恢复日，每天硬练反而效果差，建议每周 4 练；
 * - 语言 / 读书 / 备考靠高频重复，建议每天固定时长；
 * - 冥想、写作、喝水这类习惯则宜「短而勤」。
 *
 * 只做推荐，不强制覆盖用户选择；界面用一张建议卡让用户一键应用。
 */
object PlanAdvice {

    /**
     * 一条建议。
     *
     * @property category 识别出的类别名
     * @property mask 建议的周几掩码
     * @property rule 建议的执行日类型，见 [DayRule]
     * @property minutes 建议的每日分钟数
     * @property reason 一句话理由
     */
    data class Advice(
        val category: String,
        val mask: Int,
        val rule: Int,
        val minutes: Int,
        val reason: String
    ) {
        /** 「每天」「每周一、三、五」这类规则文案 */
        val summary: String
            get() = PlanSchedule.describe(mask, rule)
    }

    /** 健身 / 运动类关键词 */
    private val FITNESS = listOf(
        "健身", "锻炼", "力量", "增肌", "减脂", "减肥", "跑步", "慢跑", "游泳",
        "骑行", "瑜伽", "拉伸", "俯卧撑", "深蹲", "运动", "有氧", "hiit", "晨跑", "夜跑", "跑"
    )

    /** 学习 / 阅读类关键词 */
    private val LEARNING = listOf(
        "英语", "单词", "背单词", "学习", "读书", "阅读", "备考", "考研", "复习",
        "刷题", "雅思", "托福", "口语", "编程", "代码", "课程"
    )

    /** 写作类 */
    private val WRITING = listOf("写作", "日记", "复盘", "总结")

    /** 正念类 */
    private val MINDFUL = listOf("冥想", "正念", "呼吸")

    /** 乐器练习 */
    private val INSTRUMENT = listOf("练琴", "钢琴", "吉他", "乐器")

    /** 生活习惯类 */
    private val HEALTH = listOf("早睡", "早起", "喝水")

    /**
     * 按标题给出建议。
     *
     * @param title 计划标题
     * @return 识别出的建议；无法归类时返回 null（界面不展示建议卡）
     */
    fun suggest(title: String): Advice? {
        val text = title.trim()
        if (text.isEmpty()) {
            return null
        }
        return when {
            containsAny(text, FITNESS) -> Advice(
                category = "健身",
                mask = WeekdayMask.MON or WeekdayMask.WED or WeekdayMask.FRI or WeekdayMask.SUN,
                rule = DayRule.EVERY_DAY,
                minutes = 45,
                reason = "力量和有氧都需要恢复时间，建议每周 4 练（一、三、五、日），其余日子休息，比每天硬练效果更好。"
            )
            containsAny(text, LEARNING) -> Advice(
                category = "学习",
                mask = WeekdayMask.ALL,
                rule = DayRule.EVERY_DAY,
                minutes = 30,
                reason = "语言和知识类靠高频重复，建议每天 20–30 分钟，比周末集中突击更稳。"
            )
            containsAny(text, WRITING) -> Advice(
                category = "写作",
                mask = WeekdayMask.ALL,
                rule = DayRule.EVERY_DAY,
                minutes = 20,
                reason = "写作靠持续输出，建议每天固定 20 分钟，先完成再完美。"
            )
            containsAny(text, MINDFUL) -> Advice(
                category = "冥想",
                mask = WeekdayMask.ALL,
                rule = DayRule.EVERY_DAY,
                minutes = 10,
                reason = "正念练习宜短而勤，每天 10 分钟就能积累效果。"
            )
            containsAny(text, INSTRUMENT) -> Advice(
                category = "练习",
                mask = WeekdayMask.WEEKDAYS,
                rule = DayRule.EVERY_DAY,
                minutes = 30,
                reason = "练琴建议工作日每天 30 分钟保持手感，周末可自由加练。"
            )
            containsAny(text, HEALTH) -> Advice(
                category = "习惯",
                mask = WeekdayMask.ALL,
                rule = DayRule.EVERY_DAY,
                minutes = 10,
                reason = "生活习惯适合每天固定时间做，先求不断、再求时长。"
            )
            else -> null
        }
    }

    /**
     * 文本是否包含任意关键词（忽略大小写）。
     *
     * @param text 标题
     * @param keywords 关键词表
     * @return true 表示命中
     */
    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }
}
