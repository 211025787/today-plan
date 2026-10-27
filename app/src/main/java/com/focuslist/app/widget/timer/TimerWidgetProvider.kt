package com.focuslist.app.widget.timer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.focuslist.app.R
import com.focuslist.app.data.prefs.SettingsStore
import com.focuslist.app.timer.FocusTimerService
import com.focuslist.app.timer.TimerPhase
import com.focuslist.app.timer.TimerStateHolder
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 番茄钟桌面小组件：实时显示倒计时，支持开始/暂停/停止快捷操作。
 */
class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            // 开始：空闲时启动自由专注；暂停时恢复
            ACTION_START -> {
                val state = TimerStateHolder.current()
                if (state.phase == TimerPhase.IDLE) {
                    FocusTimerService.startPomodoro(context, null, "")
                } else {
                    FocusTimerService.sendAction(context, FocusTimerService.ACTION_RESUME)
                }
            }
            ACTION_PAUSE -> FocusTimerService.sendAction(context, FocusTimerService.ACTION_PAUSE)
            ACTION_STOP -> FocusTimerService.sendAction(context, FocusTimerService.ACTION_STOP)
        }
    }

    companion object {
        /** 小组件按钮：开始/继续 */
        const val ACTION_START = "com.focuslist.app.action.WIDGET_TIMER_START"

        /** 小组件按钮：暂停 */
        const val ACTION_PAUSE = "com.focuslist.app.action.WIDGET_TIMER_PAUSE"

        /** 小组件按钮：停止 */
        const val ACTION_STOP = "com.focuslist.app.action.WIDGET_TIMER_STOP"

        /**
         * 渲染并更新所有实例：从 [TimerStateHolder] 读取最新计时状态。
         *
         * @param context 上下文
         * @param manager 小组件管理器
         * @param ids 小组件实例 ID 数组
         */
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val state = TimerStateHolder.current()
            val phaseText = when (state.phase) {
                TimerPhase.WORK -> if (state.running) "专注中" else "已暂停"
                TimerPhase.SHORT_BREAK, TimerPhase.LONG_BREAK -> if (state.running) "休息中" else "已暂停"
                TimerPhase.IDLE -> "准备专注"
            }
            // 空闲时展示用户设置的番茄时长占位
            val timeText = if (state.phase == TimerPhase.IDLE) {
                // 小组件刷新频率低，runBlocking 同步读一次设置可接受
                val focusMinutes = runBlocking {
                    SettingsStore(context.applicationContext).settings.first().focusMinutes
                }
                DateUtils.formatCountdown(focusMinutes * 60L)
            } else {
                DateUtils.formatCountdown(state.remainSec)
            }
            // 走动中显示暂停图标，其余显示播放图标
            val toggleIcon = if (state.running) {
                R.drawable.ic_widget_pause
            } else {
                R.drawable.ic_widget_play
            }

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_timer)
                views.setTextViewText(R.id.widget_timer_phase, phaseText)
                views.setTextViewText(R.id.widget_timer_time, timeText)
                views.setImageViewResource(R.id.widget_timer_btn_toggle, toggleIcon)

                // 主按钮：运行中发暂停，否则发开始
                val toggleAction = if (state.running) ACTION_PAUSE else ACTION_START
                views.setOnClickPendingIntent(
                    R.id.widget_timer_btn_toggle,
                    broadcastPending(context, toggleAction, 10)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_timer_btn_stop,
                    broadcastPending(context, ACTION_STOP, 11)
                )
                manager.updateAppWidget(id, views)
            }
        }

        /**
         * 构造自发自收的广播 PendingIntent。
         *
         * @param context 上下文
         * @param action 广播动作
         * @param requestCode 请求码（区分按钮）
         * @return PendingIntent
         */
        private fun broadcastPending(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, TimerWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}