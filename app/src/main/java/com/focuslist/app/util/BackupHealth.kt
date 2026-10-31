package com.focuslist.app.util

/**
 * 备份体检：根据「上次备份时间」判断当前备份是否健康，并给出一句话建议。
 *
 * 纯 Kotlin 实现（只依赖 [DateUtils]），不触碰 Android 框架，便于单元测试。
 *
 * 分级规则（按**自然日**算，忽略当天具体时刻）：
 * - [Level.NEVER]：从未备份过（时间戳 <= 0）
 * - [Level.FRESH]：距上次备份 < [SOON_DAYS] 天 —— 健康
 * - [Level.SOON]：已达到 [SOON_DAYS] 天但还没到 [OVERDUE_DAYS] 天 —— 临近提醒线
 * - [Level.OVERDUE]：达到或超过 [OVERDUE_DAYS] 天 —— 该备份了
 */
object BackupHealth {

    /** 距上次备份多少天起算「逾期」 */
    const val OVERDUE_DAYS: Int = 30

    /** 距上次备份多少天起算「临近逾期」 */
    const val SOON_DAYS: Int = 14

    /** 备份健康度分级 */
    enum class Level(val label: String) {
        /** 从未备份 */
        NEVER("从未备份"),

        /** 备份较新，状态良好 */
        FRESH("状态良好"),

        /** 临近提醒线 */
        SOON("临近提醒"),

        /** 已逾期 */
        OVERDUE("已逾期")
    }

    /**
     * 距上次备份过去了多少个自然日。
     *
     * 按「当天 0 点」相减，因此同一天内的任何时刻备份都算 0 天前。
     *
     * @param lastBackupAt 上次备份时间戳（毫秒），<= 0 视为从未备份
     * @param now 当前时间戳（毫秒）
     * @return 天数（>= 0）；从未备份时返回 null
     */
    fun daysSince(lastBackupAt: Long, now: Long = System.currentTimeMillis()): Int? {
        if (lastBackupAt <= 0L) return null
        val diff = DateUtils.startOfDay(now) - DateUtils.startOfDay(lastBackupAt)
        return (diff / DateUtils.DAY_MILLIS).toInt().coerceAtLeast(0)
    }

    /**
     * 判断当前备份健康度。
     *
     * @param lastBackupAt 上次备份时间戳（毫秒）
     * @param now 当前时间戳（毫秒）
     * @return 健康度分级
     */
    fun level(lastBackupAt: Long, now: Long = System.currentTimeMillis()): Level {
        val days = daysSince(lastBackupAt, now) ?: return Level.NEVER
        return when {
            days >= OVERDUE_DAYS -> Level.OVERDUE
            days >= SOON_DAYS -> Level.SOON
            else -> Level.FRESH
        }
    }

    /**
     * 备份状态一句话文案。
     *
     * @param lastBackupAt 上次备份时间戳（毫秒）
     * @param now 当前时间戳（毫秒）
     * @return 如「今天已备份」「上次备份在 3 天前」「还没备份过」
     */
    fun statusText(lastBackupAt: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysSince(lastBackupAt, now) ?: return "还没备份过"
        return when (days) {
            0 -> "今天已备份"
            1 -> "上次备份在昨天"
            else -> "上次备份在 $days 天前"
        }
    }

    /**
     * 针对当前备份状态给出的建议文案。
     *
     * @param lastBackupAt 上次备份时间戳（毫秒）
     * @param now 当前时间戳（毫秒）
     * @return 一句话建议
     */
    fun adviceText(lastBackupAt: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysSince(lastBackupAt, now)
        return when (level(lastBackupAt, now)) {
            Level.NEVER -> "数据只存在这台手机上，建议现在导出一份备份"
            Level.OVERDUE -> "已超过 $OVERDUE_DAYS 天没备份了，建议尽快导出"
            Level.SOON -> "距上次备份已 $days 天，快到 ${OVERDUE_DAYS} 天提醒线"
            Level.FRESH -> "备份状态良好，无需额外操作"
        }
    }

    /**
     * 是否需要提醒用户去备份。
     *
     * @param lastBackupAt 上次备份时间戳（毫秒）
     * @param now 当前时间戳（毫秒）
     * @return 从未备份、临近提醒或已逾期时为 true
     */
    fun shouldRemind(lastBackupAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        return level(lastBackupAt, now) != Level.FRESH
    }

    /**
     * 距离「逾期提醒线」的进度：0 表示刚备份，1 表示已到或超过提醒线。
     *
     * 供界面画一条细进度条，直观呈现还剩多少余量。
     *
     * @param lastBackupAt 上次备份时间戳（毫秒）
     * @param now 当前时间戳（毫秒）
     * @return 0f..1f 之间的进度；从未备份时为 1f
     */
    fun progress(lastBackupAt: Long, now: Long = System.currentTimeMillis()): Float {
        val days = daysSince(lastBackupAt, now) ?: return 1f
        return (days.toFloat() / OVERDUE_DAYS.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 进度条旁的补充文案，说明还剩多少天到提醒线。
     *
     * @param lastBackupAt 上次备份时间戳（毫秒）
     * @param now 当前时间戳（毫秒）
     * @return 如「12 / 30 天」「已逾期 5 天」「尚未备份」
     */
    fun countdownText(lastBackupAt: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysSince(lastBackupAt, now) ?: return "尚未备份"
        return if (days >= OVERDUE_DAYS) {
            "已逾期 ${days - OVERDUE_DAYS} 天"
        } else {
            "$days / $OVERDUE_DAYS 天"
        }
    }
}
