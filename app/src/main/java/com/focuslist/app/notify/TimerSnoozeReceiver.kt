package com.focuslist.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslist.app.FocusListApp
import com.focuslist.app.ui.common.AlertPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 稍后提醒到点：重新播放提示音并弹出带快捷按钮的完成通知。
 */
class TimerSnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerActions.ACTION_SNOOZE_FIRE) {
            return
        }
        val title = intent.getStringExtra(TimerActions.EXTRA_TITLE).orEmpty().ifBlank { "番茄完成" }
        val text = intent.getStringExtra(TimerActions.EXTRA_TEXT).orEmpty()
        val primary = intent.getStringExtra(TimerActions.EXTRA_PRIMARY).orEmpty()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = (context.applicationContext as FocusListApp)
                    .container.settingsStore.settings.first()
                if (settings.soundEnabled || settings.vibrateEnabled) {
                    AlertPlayer.play(
                        context = context,
                        chime = settings.chimeType,
                        sound = settings.soundEnabled,
                        vibrate = settings.vibrateEnabled,
                        soundSec = settings.alertSoundSec,
                        vibrateSec = settings.alertVibrateSec,
                        toneKind = settings.focusAlertToneKind,
                        toneValue = settings.focusAlertToneValue,
                        toneStartMs = settings.focusAlertToneStartMs.toLong(),
                        toneEndMs = settings.focusAlertToneEndMs.toLong(),
                        toneDurationMs = settings.focusAlertToneDurationMs.toLong()
                    )
                }
                val notifier = Notifier(context)
                notifier.ensureChannels()
                notifier.showTimerFinished(
                    title = title,
                    text = text,
                    primary = primary,
                    taskId = intent.getLongExtra(TimerActions.EXTRA_TASK_ID, 0L),
                    taskTitle = intent.getStringExtra(TimerActions.EXTRA_TASK_TITLE).orEmpty(),
                    durationMin = intent.getIntExtra(TimerActions.EXTRA_DURATION, 0),
                    planId = intent.getLongExtra(TimerActions.EXTRA_PLAN_ID, 0L),
                    cycle = intent.getIntExtra(TimerActions.EXTRA_CYCLE, 0)
                )
            } catch (_: Exception) {
                // 任何异常都不能让广播进程崩溃
            } finally {
                pending.finish()
            }
        }
    }
}
