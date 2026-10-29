package com.focuslist.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.prefs.SettingsStore
import com.focuslist.app.data.prefs.TimerSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel：读写 DataStore 中的计时参数与开关。
 *
 * @property settingsStore 设置存储
 */
class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    /** 当前设置状态流 */
    val settings: StateFlow<TimerSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerSettings())

    /**
     * 保存计时参数。
     *
     * @param focus 番茄分钟数
     * @param shortBreak 短休息分钟数
     * @param longBreak 长休息分钟数
     * @param interval 长休息间隔
     */
    fun saveDurations(focus: Int, shortBreak: Int, longBreak: Int, interval: Int) {
        viewModelScope.launch {
            settingsStore.updateDurations(focus, shortBreak, longBreak, interval)
        }
    }

    /**
     * 保存开关项。
     *
     * @param autoBreak 自动开始休息
     * @param autoPomo 自动开始下一番茄
     * @param sound 响铃
     * @param vibrate 震动
     * @param strict 严格模式
     */
    fun saveSwitches(autoBreak: Boolean, autoPomo: Boolean, sound: Boolean, vibrate: Boolean, strict: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSwitches(autoBreak, autoPomo, sound, vibrate, strict)
        }
    }

    /**
     * 保存主题模式。
     *
     * @param mode SYSTEM / LIGHT / DARK
     */
    fun saveTheme(mode: String) {
        viewModelScope.launch {
            settingsStore.updateTheme(mode)
        }
    }

    /**
     * 保存主题色。
     *
     * @param colorHex 颜色值，格式 "#RRGGBB"
     */
    fun saveThemeColor(colorHex: String) {
        viewModelScope.launch {
            settingsStore.updateThemeColor(colorHex)
        }
    }

    /**
     * 保存默认白噪音类型。
     *
     * @param type NONE / WHITE / BROWN
     */
    fun saveWhiteNoise(type: String) {
        viewModelScope.launch {
            settingsStore.updateWhiteNoise(type)
        }
    }

    /**
     * 保存计划倒计时到达目标时的提醒开关。
     *
     * @param sound 是否响铃
     * @param vibrate 是否震动
     */
    fun savePlanReachGoal(sound: Boolean, vibrate: Boolean) {
        viewModelScope.launch {
            settingsStore.updatePlanReachGoal(sound, vibrate)
        }
    }
}