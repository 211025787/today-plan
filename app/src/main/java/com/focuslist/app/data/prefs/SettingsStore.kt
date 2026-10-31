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
 * @property chimeType 计时结束提示音音色：BELL 清脆铃 / WOOD 木鱼 / DROP 水滴 / BOWL 颂钵 / SOFT 柔音
 * @property alertSoundSec 提醒响铃持续秒数，取值 1-20
 * @property alertVibrateSec 提醒震动持续秒数，取值 1-20
 * @property capsuleEnabled 是否在后台上方显示悬浮计时胶囊
 * @property capsuleColorHex 胶囊背景色，格式 "#RRGGBB"
 * @property capsuleAlpha 胶囊不透明度百分比，取值 10-100
 * @property capsuleWidthDp 胶囊宽度（dp），0 表示自适应内容，取值范围 0-300
 * @property capsuleRowHeightDp 胶囊单行高度（dp），0 表示自适应，取值范围 0-56
 * @property capsuleTextColorHex 胶囊文字颜色，格式 "#RRGGBB"
 * @property capsuleTextAlpha 文字不透明度百分比，取值 10-100
 * @property capsuleProgressColorHex 进度条颜色，格式 "#RRGGBB"
 * @property capsuleProgressAlpha 进度条不透明度百分比，取值 10-100
 * @property capsuleX 胶囊拖动后的 X 坐标（px）；-1 表示默认顶部居中
 * @property capsuleY 胶囊拖动后的 Y 坐标（px）；-1 表示默认顶部居中
 * @property strictMode 严格模式：开启后计时期间离开页面视为放弃
 * @property themeMode 主题模式：SYSTEM 跟随系统、LIGHT 浅色、DARK 深色
 * @property themeColor 主题色，格式 "#RRGGBB"，默认滴答蓝 #4772FA
 * @property whiteNoiseType 专注时白噪音类型：NONE 关闭、WHITE 白噪声、BROWN 棕噪声
 * @property planReachGoalSound 计划倒计时到达目标时是否响铃
 * @property planReachGoalVibrate 计划倒计时到达目标时是否震动
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
    val chimeType: String = "BELL",
    val alertSoundSec: Int = 4,
    val alertVibrateSec: Int = 3,
    val capsuleEnabled: Boolean = false,
    val capsuleColorHex: String = "#FCFDFF",
    val capsuleAlpha: Int = 65,
    val capsuleWidthDp: Int = 196,
    val capsuleRowHeightDp: Int = 34,
    val capsuleTextColorHex: String = "#1B1B2F",
    val capsuleTextAlpha: Int = 100,
    val capsuleProgressColorHex: String = "#4772FA",
    val capsuleProgressAlpha: Int = 100,
    val capsuleX: Int = -1,
    val capsuleY: Int = -1,
    val strictMode: Boolean = false,
    val themeMode: String = "SYSTEM",
    val themeColor: String = "#4772FA",
    val whiteNoiseType: String = "NONE",
    val planReachGoalSound: Boolean = true,
    val planReachGoalVibrate: Boolean = true
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

        /** 提示音音色 key */
        val CHIME = stringPreferencesKey("chime_type")

        /** 响铃持续秒数 key */
        val ALERT_SOUND_SEC = intPreferencesKey("alert_sound_sec")

        /** 震动持续秒数 key */
        val ALERT_VIBRATE_SEC = intPreferencesKey("alert_vibrate_sec")

        /** 悬浮计时胶囊开关 key */
        val CAPSULE = booleanPreferencesKey("capsule_enabled")

        /** 胶囊背景色 key */
        val CAPSULE_COLOR = stringPreferencesKey("capsule_color")

        /** 胶囊不透明度 key */
        val CAPSULE_ALPHA = intPreferencesKey("capsule_alpha")

        /** 胶囊宽度 key */
        val CAPSULE_WIDTH = intPreferencesKey("capsule_width_dp")

        /** 胶囊单行高度 key */
        val CAPSULE_ROW_HEIGHT = intPreferencesKey("capsule_row_height_dp")

        /** 胶囊文字颜色 key */
        val CAPSULE_TEXT_COLOR = stringPreferencesKey("capsule_text_color")

        /** 胶囊文字不透明度 key */
        val CAPSULE_TEXT_ALPHA = intPreferencesKey("capsule_text_alpha")

        /** 胶囊进度条颜色 key */
        val CAPSULE_PROGRESS_COLOR = stringPreferencesKey("capsule_progress_color")

        /** 胶囊进度条不透明度 key */
        val CAPSULE_PROGRESS_ALPHA = intPreferencesKey("capsule_progress_alpha")

        /** 胶囊位置 X key */
        val CAPSULE_X = intPreferencesKey("capsule_x")

        /** 胶囊位置 Y key */
        val CAPSULE_Y = intPreferencesKey("capsule_y")

        /** 严格模式 key */
        val STRICT = booleanPreferencesKey("strict_mode")

        /** 主题模式 key */
        val THEME = stringPreferencesKey("theme_mode")

        /** 主题色 key */
        val THEME_COLOR = stringPreferencesKey("theme_color")

        /** 白噪音类型 key */
        val WHITE_NOISE = stringPreferencesKey("white_noise_type")

        /** 计划到目标响铃 key */
        val PLAN_REACH_SOUND = booleanPreferencesKey("plan_reach_goal_sound")

        /** 计划到目标震动 key */
        val PLAN_REACH_VIBRATE = booleanPreferencesKey("plan_reach_goal_vibrate")
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
            chimeType = prefs[Keys.CHIME] ?: "BELL",
            alertSoundSec = prefs[Keys.ALERT_SOUND_SEC] ?: 4,
            alertVibrateSec = prefs[Keys.ALERT_VIBRATE_SEC] ?: 3,
            capsuleEnabled = prefs[Keys.CAPSULE] ?: false,
            capsuleColorHex = prefs[Keys.CAPSULE_COLOR] ?: "#FCFDFF",
            capsuleAlpha = prefs[Keys.CAPSULE_ALPHA] ?: 65,
            capsuleWidthDp = prefs[Keys.CAPSULE_WIDTH] ?: 196,
            capsuleRowHeightDp = prefs[Keys.CAPSULE_ROW_HEIGHT] ?: 34,
            capsuleTextColorHex = prefs[Keys.CAPSULE_TEXT_COLOR] ?: "#1B1B2F",
            capsuleTextAlpha = prefs[Keys.CAPSULE_TEXT_ALPHA] ?: 100,
            capsuleProgressColorHex = prefs[Keys.CAPSULE_PROGRESS_COLOR] ?: "#4772FA",
            capsuleProgressAlpha = prefs[Keys.CAPSULE_PROGRESS_ALPHA] ?: 100,
            capsuleX = prefs[Keys.CAPSULE_X] ?: -1,
            capsuleY = prefs[Keys.CAPSULE_Y] ?: -1,
            strictMode = prefs[Keys.STRICT] ?: false,
            themeMode = prefs[Keys.THEME] ?: "SYSTEM",
            themeColor = prefs[Keys.THEME_COLOR] ?: "#4772FA",
            whiteNoiseType = prefs[Keys.WHITE_NOISE] ?: "NONE",
            planReachGoalSound = prefs[Keys.PLAN_REACH_SOUND] ?: true,
            planReachGoalVibrate = prefs[Keys.PLAN_REACH_VIBRATE] ?: true
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
     * 更新计时结束提示音音色。
     *
     * @param type 音色标识：BELL / WOOD / DROP / BOWL / SOFT
     */
    suspend fun updateChime(type: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHIME] = type
        }
    }

    /**
     * 更新提醒的响铃与震动持续时长。
     *
     * @param soundSec 响铃秒数（1-20）
     * @param vibrateSec 震动秒数（1-20）
     */
    suspend fun updateAlertDurations(soundSec: Int, vibrateSec: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALERT_SOUND_SEC] = soundSec.coerceIn(1, 20)
            prefs[Keys.ALERT_VIBRATE_SEC] = vibrateSec.coerceIn(1, 20)
        }
    }

    /**
     * 更新悬浮计时胶囊开关。
     *
     * @param enabled 是否启用
     */
    suspend fun updateCapsule(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAPSULE] = enabled
        }
    }

    /**
     * 更新悬浮计时胶囊外观（背景 / 文字 / 进度条 / 尺寸）。
     *
     * @param colorHex 背景色，格式 "#RRGGBB"
     * @param alphaPercent 背景不透明度百分比（10-100）
     * @param widthDp 宽度（dp，0 表示自适应，0-300）
     * @param rowHeightDp 单行高度（dp，0 表示自适应，0-56）
     * @param textColorHex 文字颜色
     * @param textAlpha 文字不透明度百分比（10-100）
     * @param progressColorHex 进度条颜色
     * @param progressAlpha 进度条不透明度百分比（10-100）
     */
    suspend fun updateCapsuleStyle(
        colorHex: String,
        alphaPercent: Int,
        widthDp: Int,
        rowHeightDp: Int,
        textColorHex: String,
        textAlpha: Int,
        progressColorHex: String,
        progressAlpha: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAPSULE_COLOR] = colorHex
            prefs[Keys.CAPSULE_ALPHA] = alphaPercent.coerceIn(10, 100)
            prefs[Keys.CAPSULE_WIDTH] = widthDp.coerceIn(0, 300)
            prefs[Keys.CAPSULE_ROW_HEIGHT] = rowHeightDp.coerceIn(0, 56)
            prefs[Keys.CAPSULE_TEXT_COLOR] = textColorHex
            prefs[Keys.CAPSULE_TEXT_ALPHA] = textAlpha.coerceIn(10, 100)
            prefs[Keys.CAPSULE_PROGRESS_COLOR] = progressColorHex
            prefs[Keys.CAPSULE_PROGRESS_ALPHA] = progressAlpha.coerceIn(10, 100)
        }
    }

    /**
     * 更新悬浮胶囊拖动后的位置（-1 恢复默认顶部居中）。
     *
     * @param x X 坐标（px）
     * @param y Y 坐标（px）
     */
    suspend fun updateCapsulePosition(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAPSULE_X] = x
            prefs[Keys.CAPSULE_Y] = y
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

    /**
     * 更新计划倒计时到达目标时的提醒开关。
     *
     * @param sound 是否响铃
     * @param vibrate 是否震动
     */
    suspend fun updatePlanReachGoal(sound: Boolean, vibrate: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAN_REACH_SOUND] = sound
            prefs[Keys.PLAN_REACH_VIBRATE] = vibrate
        }
    }
}