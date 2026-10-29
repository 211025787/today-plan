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
 * 设置页 ViewModel：读写 DataStore 中的外观与提醒偏好。
 *
 * 番茄参数与自动化开关已迁移到专注页（FocusScreen），不在本页维护。
 *
 * @property settingsStore 设置存储
 */
class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    /** 当前设置状态流 */
    val settings: StateFlow<TimerSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerSettings())

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