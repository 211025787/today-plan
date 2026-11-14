package com.focuslist.app.notify

import android.content.Context
import android.util.Log
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.ui.common.AlertPlayer

/**
 * 计划提醒的统一触发入口：播放铃声 / 震动并发送通知。
 *
 * 为什么闹钟之外还要有这一层：Android 13+ 默认不授予「精确闹钟」权限，
 * `setAndAllowWhileIdle` 会被系统批量延后（实测窗口可达几十分钟）。
 * 计划秒表的前台服务本身每秒都知道剩余时间，由它在跨过阈值的瞬间直接提醒，
 * 才能做到「还差 29 分钟」就准点响；AlarmManager 只作为进程被冻结时的兜底。
 *
 * 两条路径都走 [fire]，用 [DEDUP_MS] 内的去重表保证同一次提醒不会响两遍。
 */
object PlanAlert {

    /** 同一条提醒的去重窗口（毫秒） */
    private const val DEDUP_MS = 60_000L

    /** 去重表过期时间（毫秒），顺便防止无限增长 */
    private const val EXPIRE_MS = 10 * 60_000L

    /** `planId:kind` -> 最近一次触发时间 */
    private val fired = mutableMapOf<String, Long>()

    /**
     * 触发一条计划提醒（去重）。
     *
     * @param context 上下文
     * @param settings 当前设置快照
     * @param planId 计划 ID
     * @param title 计划标题
     * @param kind 类型：LEAD / GOAL
     * @param leadMinutes 提前分钟数（仅提前提醒用于通知标题）
     * @return true 表示本次确实触发了；false 表示刚刚已经响过
     */
    fun fire(
        context: Context,
        settings: TimerSettings,
        planId: Long,
        title: String,
        kind: String,
        leadMinutes: Int
    ): Boolean {
        val key = "$planId:$kind"
        val now = System.currentTimeMillis()
        synchronized(fired) {
            val last = fired[key]
            if (last != null && now - last < DEDUP_MS) {
                return false
            }
            fired[key] = now
            fired.entries.removeAll { now - it.value > EXPIRE_MS }
        }
        Log.d("FocusAlarm", "fire kind=$kind plan=$planId lead=$leadMinutes")
        if (settings.planReachGoalSound || settings.planReachGoalVibrate) {
            AlertPlayer.play(
                context = context,
                chime = settings.chimeType,
                sound = settings.planReachGoalSound,
                vibrate = settings.planReachGoalVibrate,
                soundSec = settings.alertSoundSec,
                vibrateSec = settings.alertVibrateSec,
                toneKind = settings.planAlertToneKind,
                toneValue = settings.planAlertToneValue,
                toneStartMs = settings.planAlertToneStartMs.toLong(),
                toneEndMs = settings.planAlertToneEndMs.toLong(),
                toneDurationMs = settings.planAlertToneDurationMs.toLong(),
                loop = settings.planAlertLoop,
                loopDurationSec = settings.planAlertDurationSec
            )
        }
        val notifier = Notifier(context)
        notifier.ensureChannels()
        if (kind == PlanGoalAlarms.KIND_LEAD) {
            notifier.showPlanLead(planId, title, leadMinutes)
        } else {
            notifier.showPlanGoal(planId, title)
        }
        return true
    }
}
