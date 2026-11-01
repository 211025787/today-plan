package com.focuslist.app.data.local.dao

import androidx.room.ColumnInfo

/**
 * 按计划聚合的累计投入结果（Room 查询投影 POJO）。
 *
 * @property planId 计划 ID
 * @property seconds 该计划在查询区间内的累计投入秒数
 */
data class PlanTotal(
    @ColumnInfo(name = "planId") val planId: Long,
    @ColumnInfo(name = "seconds") val seconds: Long
)
