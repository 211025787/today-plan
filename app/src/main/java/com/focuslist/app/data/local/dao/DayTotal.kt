package com.focuslist.app.data.local.dao

import androidx.room.ColumnInfo

/**
 * 按天聚合的专注时长结果（Room 查询投影 POJO）。
 *
 * 日期以 **本地时区的 `yyyy-MM-dd`** 字符串表示：SQLite 的 `strftime('%s', ..., 'localtime')`
 * 不会补偿时区偏移（会把本地日期串当 UTC 解释），因此不能直接取时间戳，
 * 改为取日期串后在 Kotlin 侧用 [com.focuslist.app.util.DayKeys.parse] 转回本地 0 点。
 *
 * @property day 本地日期，格式 `yyyy-MM-dd`
 * @property seconds 当天累计专注秒数
 */
data class DayTotal(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "seconds") val seconds: Long
)
