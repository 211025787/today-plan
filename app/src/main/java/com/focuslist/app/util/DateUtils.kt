package com.focuslist.app.util

import java.util.Calendar

/**
 * 日期时间工具：统一处理"当天 0 点"等边界换算，避免各模块各自实现导致偏差。
 */
object DateUtils {

    /**
     * 计算指定时间戳所在天的 0 点时间戳。
     *
     * @param timeMillis 任意时间戳（毫秒）
     * @return 当天 00:00:00.000 的时间戳
     */
    fun startOfDay(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 计算指定时间戳所在天的最后一刻（23:59:59.999）。
     *
     * @param timeMillis 任意时间戳（毫秒）
     * @return 当天结束时间戳
     */
    fun endOfDay(timeMillis: Long): Long {
        return startOfDay(timeMillis) + DAY_MILLIS - 1
    }

    /**
     * 计算本周周一 0 点时间戳。
     *
     * @param timeMillis 任意时间戳（毫秒）
     * @return 本周第一天 0 点时间戳
     */
    fun startOfWeek(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(timeMillis)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val offset = if (dow == Calendar.SUNDAY) {
            -6
        } else {
            Calendar.MONDAY - dow
        }
        cal.add(Calendar.DAY_OF_YEAR, offset)
        return cal.timeInMillis
    }

    /**
     * 计算本月 1 号 0 点时间戳。
     *
     * @param timeMillis 任意时间戳（毫秒）
     * @return 本月第一天 0 点时间戳
     */
    fun startOfMonth(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(timeMillis)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    /**
     * 将秒数格式化为 "MM:SS" 倒计时文案。
     *
     * @param totalSec 剩余秒数，负值按 0 处理
     * @return 格式化字符串，例如 "25:00"
     */
    fun formatCountdown(totalSec: Long): String {
        val sec = if (totalSec < 0) {
            0
        } else {
            totalSec
        }
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    /** 一天的毫秒数 */
    const val DAY_MILLIS: Long = 24L * 60 * 60 * 1000
}