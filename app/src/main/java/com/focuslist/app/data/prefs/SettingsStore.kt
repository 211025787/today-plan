package com.focuslist.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "focus_settings")

/**
 * 计时器与用户偏好设置，全部由 DataStore 离线持久化。
 *
 * @property focusMinutes 单个番茄专注时长（分钟），取值 5-120，默认 25
 * @property shortBreakMinutes 短休息时长（分钟），取值 1-30，默认 5
 * @property longBreakMinutes 长休息时长（分钟），取值 5-60，默认 15
 * @property longBreakInterval 每完成多少个番茄进入一次长休息，取值 2-8，默认 4
 * @property autoStartBreak 番茄结束后是否自动开始休息
 * @property autoStartNextPomo 休息结束后是否自动开始下一个番茄
 * @property soundEnabled 计时结束是否响铃
 * @property vibrateEnabled 计时结束是否震动
 * @property strictMode 严格模式：开启后计时期间离开页面视为放弃
 * @property themeMode 主题模式：SYSTEM 跟随系统、LIGHT 浅色、DARK 深色
 * @property themeColor 主题色，格式 "#RRGGBB"，默认滴答蓝 #4772FA
 * @property whiteNoiseType 专注时白噪音类型：NONE 关闭、WHITE 白噪声、BROWN 棕噪声
 */
data class TimerSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakInterval: Int = 4,
    val autoStartBreak: Boolean = true,
    val autoStartNextPomo: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val strictMode: Boolean = false,
    val themeMode: String = "SYSTEM",
    val themeColor: String = "#4772FA",
    val whiteNoiseType: String = "NONE"
)

/**
 * 设置读写仓库：封装 DataStore 的 key 与序列化细节。
 *
 * @property context 应用上下文
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        /** 番茄时长 key */
        val FOCUS_MINUTES = intPreferencesKey("focus_minutes")

        /** 短休息时长 key */
        val SHORT_BREAK = intPreferencesKey("short_break_minutes")

        /** 长休息时长 key */
        val LONG_BREAK = intPreferencesKey("long_break_minutes")

        /** 长休息间隔 key */
        val LONG_INTERVAL = intPreferencesKey("long_break_interval")

        /** 自动开始休息 key */
        val AUTO_BREAK = booleanPreferencesKey("auto_start_break")

        /** 自动开始下一番茄 key */
        val AUTO_POMO = booleanPreferencesKey("auto_start_next_pomo")

        /** 响铃开关 key */
        val SOUND = booleanPreferencesKey("sound_enabled")

        /** 震动开关 key */
        val VIBRATE = booleanPreferencesKey("vibrate_enabled")

        /** 严格模式 key */
        val STRICT = booleanPreferencesKey("strict_mode")

        /** 主题模式 key */
        val THEME = stringPreferencesKey("theme_mode")

        /** 主题色 key */
        val THEME_COLOR = stringPreferencesKey("theme_color")

        /** 白噪音类型 key */
        val WHITE_NOISE = stringPreferencesKey("white_noise_type")
    }

    /** 设置流：任何字段变更都会发射最新完整快照 */
    val settings: Flow<TimerSettings> = context.dataStore.data.map { prefs ->
        TimerSettings(
            focusMinutes = prefs[Keys.FOCUS_MINUTES] ?: 25,
            shortBreakMinutes = prefs[Keys.SHORT_BREAK] ?: 5,
            longBreakMinutes = prefs[Keys.LONG_BREAK] ?: 15,
            longBreakInterval = prefs[Keys.LONG_INTERVAL] ?: 4,
            autoStartBreak = prefs[Keys.AUTO_BREAK] ?: true,
            autoStartNextPomo = prefs[Keys.AUTO_POMO] ?: false,
            soundEnabled = prefs[Keys.SOUND] ?: true,
            vibrateEnabled = prefs[Keys.VIBRATE] ?: true,
            strictMode = prefs[Keys.STRICT] ?: false,
            themeMode = prefs[Keys.THEME] ?: "SYSTEM",
            themeColor = prefs[Keys.THEME_COLOR] ?: "#4772FA",
            whiteNoiseType = prefs[Keys.WHITE_NOISE] ?: "NONE"
        )
    }

    /**
     * 更新计时相关参数。
     *
     * @param focus 番茄时长（分钟）
     * @param shortBreak 短休息时长（分钟）
     * @param longBreak 长休息时长（分钟）
     * @param interval 长休息间隔（个）
     */
    suspend fun updateDurations(focus: Int, shortBreak: Int, longBreak: Int, interval: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FOCUS_MINUTES] = focus.coerceIn(5, 120)
            prefs[Keys.SHORT_BREAK] = shortBreak.coerceIn(1, 30)
            prefs[Keys.LONG_BREAK] = longBreak.coerceIn(5, 60)
            prefs[Keys.LONG_INTERVAL] = interval.coerceIn(2, 8)
        }
    }

    /**
     * 更新开关类偏好。
     *
     * @param autoBreak 是否自动开始休息
     * @param autoPomo 是否自动开始下一番茄
     * @param sound 是否响铃
     * @param vibrate 是否震动
     * @param strict 是否严格模式
     */
    suspend fun updateSwitches(autoBreak: Boolean, autoPomo: Boolean, sound: Boolean, vibrate: Boolean, strict: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_BREAK] = autoBreak
            prefs[Keys.AUTO_POMO] = autoPomo
            prefs[Keys.SOUND] = sound
            prefs[Keys.VIBRATE] = vibrate
            prefs[Keys.STRICT] = strict
        }
    }

    /**
     * 更新主题模式。
     *
     * @param mode SYSTEM / LIGHT / DARK
     */
    suspend fun updateTheme(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME] = mode
        }
    }

    /**
     * 更新主题色。
     *
     * @param colorHex 颜色值，格式 "#RRGGBB"
     */
    suspend fun updateThemeColor(colorHex: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_COLOR] = colorHex
        }
    }

    /**
     * 更新白噪音类型。
     *
     * @param type NONE / WHITE / BROWN
     */
    suspend fun updateWhiteNoise(type: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WHITE_NOISE] = type
        }
    }
}