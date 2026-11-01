package com.focuslist.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 习惯的每日提醒时间计算。
 *
 * 只处理「时分」，日期部分由调用方保证：习惯的 `reminderAt` 存的是一个带时分的时间戳，
 * 每天同一时刻触发，所以每次排闹钟都要从「今天这个点」重新算，过了就顺延到明天。
 *
 * 独立成纯函数是为了能在 JVM 单测里把边界钉住（正好等于提醒时刻、跨天、午夜），
 * 而不是靠真机上等一个小时去验证。
 */
object HabitReminders {

    /**
     * 计算下一次触发时刻：今天该时刻若已过（含正好相等），顺延到明天。
     *
     * 用「相等也顺延」而不是「相等就今天」，是为了避免闹钟在触发后立刻再排一次同样的时间，
     * 造成同一分钟内连响两次。
     *
     * @param reminderAt 提醒时间戳（只取其中的时、分）
     * @param now 当前时刻
     * @return 下一次触发的时间戳
     */
    fun nextTriggerAt(reminderAt: Long, now: Long): Long {
        val source = Calendar.getInstance().apply { timeInMillis = reminderAt }
        val hour = source.get(Calendar.HOUR_OF_DAY)
        val minute = source.get(Calendar.MINUTE)
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    /**
     * 提醒时间文案「21:30」；未设置时返回空串。
     *
     * @param reminderAt 提醒时间戳
     * @return 时:分 文案
     */
    fun timeText(reminderAt: Long?): String {
        if (reminderAt == null || reminderAt <= 0L) {
            return ""
        }
        return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(reminderAt))
    }

    /**
     * 把「某天 0 点 + 时分」合成一个时间戳。
     *
     * @param dayStart 当天 0 点
     * @param hour 小时 0-23
     * @param minute 分钟 0-59
     * @return 合成后的时间戳
     */
    fun at(dayStart: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = DateUtils.startOfDay(dayStart)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * 提醒时间的小时。
     *
     * @param reminderAt 提醒时间戳
     * @return 0-23
     */
    fun hourOf(reminderAt: Long): Int {
        return Calendar.getInstance().apply { timeInMillis = reminderAt }.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * 提醒时间的分钟。
     *
     * @param reminderAt 提醒时间戳
     * @return 0-59
     */
    fun minuteOf(reminderAt: Long): Int {
        return Calendar.getInstance().apply { timeInMillis = reminderAt }.get(Calendar.MINUTE)
    }
}
