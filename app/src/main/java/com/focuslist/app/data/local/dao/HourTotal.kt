package com.focuslist.app.data.local.dao

import androidx.room.ColumnInfo

/**
 * 按小时聚合的专注时长结果（Room 查询投影 POJO）。
 *
 * 小时由 SQLite 的 `strftime('%H', ..., 'unixepoch', 'localtime')` 产出，
 * 带 `localtime` 修饰符后即为**本地时区的小时**（0~23），可直接参与 Kotlin 侧计算。
 *
 * @property hour 本地小时 0~23
 * @property seconds 该小时累计专注秒数
 */
data class HourTotal(
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "seconds") val seconds: Long
)
