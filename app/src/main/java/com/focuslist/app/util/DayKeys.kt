package com.focuslist.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日期键工具：在「本地日期串 `yyyy-MM-dd`」与「本地 0 点时间戳」之间做无损互转。
 *
 * 之所以走日期串而不是 SQLite 时间戳分桶，是因为：
 * - `(startAt / 86400000) * 86400000` 是按 **UTC** 分桶，东八区会整体错位一天；
 * - `strftime('%s', ..., 'localtime')` 又不会补偿时区偏移，得到的其实是
 *   「本地日期按 UTC 解析」的时间戳，同样错 8 小时。
 *
 * 因此统一由 SQLite 的 `date(..., 'localtime')` 产出日期串，再由本工具转回本地 0 点。
 */
object DayKeys {

    /** 系统默认时区（每次读取，适配用户切换时区 / 夏令时） */
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * 日期串转本地 0 点时间戳。
     *
     * @param day 格式 `yyyy-MM-dd`
     * @return 当天本地 0 点时间戳；解析失败返回 0
     */
    fun parse(day: String): Long {
        return try {
            LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 时间戳转本地日期串。
     *
     * @param timeMillis 任意时间戳（毫秒）
     * @return 格式 `yyyy-MM-dd`
     */
    fun format(timeMillis: Long): String {
        return LocalDate.ofInstant(Instant.ofEpochMilli(timeMillis), zone).toString()
    }
}
