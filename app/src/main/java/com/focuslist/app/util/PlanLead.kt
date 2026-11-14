package com.focuslist.app.util

/**
 * 「计划还差 N 分钟达标」提前提醒的纯计算工具。
 *
 * 不依赖 Android / Room，便于在 JVM 单元测试里直接验证边界：
 * 提前提醒的触发时刻 = 现在 + (剩余秒数 - 提前分钟数 × 60)。
 *
 * 约定 [MIN_MINUTES] 为 0，表示关闭提前提醒，只在每日目标真正达成时提醒一次。
 */
object PlanLead {

    /** 提前提醒下限（分钟）：0 表示关闭 */
    const val MIN_MINUTES = 0

    /** 提前提醒上限（分钟） */
    const val MAX_MINUTES = 60

    /** 默认提前提醒分钟数 */
    const val DEFAULT_MINUTES = 5

    /** 开始计时后多少秒内允许补一次「已进入提前窗口」的提醒 */
    const val CATCH_UP_SESSION_SEC = 30L

    /**
     * 把提前分钟数约束到合法范围。
     *
     * @param minutes 输入分钟数
     * @return 落在 [MIN_MINUTES, MAX_MINUTES] 的值
     */
    fun coerce(minutes: Int): Int {
        return minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }

    /**
     * 计算提前提醒的触发时刻。
     *
     * @param nowMs 当前时间戳（毫秒）
     * @param remainSec 距离今日目标还差的秒数（可为负，表示已超出目标）
     * @param leadMinutes 提前分钟数，0 表示关闭
     * @return 触发时间戳；关闭、已达标、或剩余时间已不足提前窗口时返回 null
     */
    fun triggerAt(nowMs: Long, remainSec: Long, leadMinutes: Int): Long? {
        val lead = coerce(leadMinutes)
        if (lead <= 0 || remainSec <= 0L) {
            return null
        }
        val leadSec = lead * 60L
        // 剩余时间已经落进提前窗口：不再补一次「即将达标」，直接等达标提醒
        if (remainSec <= leadSec) {
            return null
        }
        return nowMs + (remainSec - leadSec) * 1000L
    }

    /**
     * 计算「每日目标达成」提醒的触发时刻。
     *
     * @param nowMs 当前时间戳（毫秒）
     * @param remainSec 距离今日目标还差的秒数
     * @return 触发时间戳；已达标时返回 null
     */
    fun goalAt(nowMs: Long, remainSec: Long): Long? {
        return if (remainSec <= 0L) null else nowMs + remainSec * 1000L
    }

    /**
     * 是否需要在开始计时时补一次提前提醒。
     *
     * 场景：用户设了「提前 29 分钟」，但开始时今日剩余已经只有 29 分钟（之前计过时），
     * 阈值入口已经错过，正常逻辑不会再排闹钟。这里在刚开始的 [CATCH_UP_SESSION_SEC] 秒内
     * 补一次，避免用户看到「还剩 29 分钟」却什么都没发生。
     *
     * @param remainSec 距离目标剩余秒数
     * @param leadMinutes 提前分钟数
     * @param sessionSec 本次计时已累计秒数
     * @param alerted 本次运行是否已经提醒过
     * @return 是否需要补提醒
     */
    fun shouldCatchUp(remainSec: Long, leadMinutes: Int, sessionSec: Long, alerted: Boolean): Boolean {
        val lead = coerce(leadMinutes)
        if (lead <= 0 || alerted || remainSec <= 0L) {
            return false
        }
        if (sessionSec > CATCH_UP_SESSION_SEC) {
            return false
        }
        return remainSec <= lead * 60L
    }

    /**
     * 提前提醒的通知标题。
     *
     * @param leadMinutes 提前分钟数
     * @return 中文标题
     */
    fun reminderTitle(leadMinutes: Int): String {
        return "计划还差 ${coerce(leadMinutes)} 分钟达标"
    }
}
