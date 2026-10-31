package com.focuslist.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.prefs.SettingsStore
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.data.repo.FocusRepository
import com.focuslist.app.util.Goal
import com.focuslist.app.util.PlanTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel：读写 DataStore 中的外观与提醒偏好。
 *
 * 番茄参数与自动化开关已迁移到专注页（FocusScreen），不在本页维护。
 *
 * @property settingsStore 设置存储
 * @property focusRepository 专注记录仓库，仅用于推算建议目标；胶囊设置页不传
 */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val focusRepository: FocusRepository? = null
) : ViewModel() {

    /** 当前设置状态流 */
    val settings: StateFlow<TimerSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerSettings())

    /** 计划模板列表（按复用次数排序），来自 DataStore 中与设置同库的一条 JSON */
    val planTemplates: StateFlow<List<PlanTemplate>> = settingsStore.planTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 上次成功导出备份的时间戳；0 表示从未备份 */
    val lastBackupAt: StateFlow<Long> = settingsStore.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val mutableSuggestedGoal = MutableStateFlow<Int?>(null)

    /** 按近 14 天表现推算出的建议目标（分钟）；样本不足时为 null */
    val suggestedGoal: StateFlow<Int?> = mutableSuggestedGoal.asStateFlow()

    init {
        refreshSuggestedGoal()
    }

    /**
     * 重新推算建议目标：读取近期每日分钟数后交给 [Goal.suggest]。
     */
    fun refreshSuggestedGoal() {
        val repository = focusRepository ?: return
        viewModelScope.launch {
            mutableSuggestedGoal.value = Goal.suggest(repository.recentDailyMinutes())
        }
    }

    /**
     * 保存悬浮计时胶囊开关。
     *
     * @param enabled 是否启用
     */
    fun saveCapsule(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateCapsule(enabled)
        }
    }

    /**
     * 保存悬浮胶囊外观（背景 / 文字 / 进度条 / 尺寸）。
     *
     * @param colorHex 背景色 "#RRGGBB"
     * @param alphaPercent 背景不透明度百分比
     * @param widthDp 宽度（dp，0 表示自适应）
     * @param rowHeightDp 单行高度（dp，0 表示自适应）
     * @param textColorHex 文字颜色
     * @param textAlpha 文字不透明度百分比
     * @param progressColorHex 进度条颜色
     * @param progressAlpha 进度条不透明度百分比
     */
    fun saveCapsuleStyle(
        colorHex: String,
        alphaPercent: Int,
        widthDp: Int,
        rowHeightDp: Int,
        textColorHex: String,
        textAlpha: Int,
        progressColorHex: String,
        progressAlpha: Int
    ) {
        viewModelScope.launch {
            settingsStore.updateCapsuleStyle(
                colorHex, alphaPercent, widthDp, rowHeightDp,
                textColorHex, textAlpha, progressColorHex, progressAlpha
            )
        }
    }

    /**
     * 保存悬浮胶囊位置（-1 恢复默认顶部居中）。
     *
     * @param x X 坐标（px）
     * @param y Y 坐标（px）
     */
    fun saveCapsulePosition(x: Int, y: Int) {
        viewModelScope.launch {
            settingsStore.updateCapsulePosition(x, y)
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

    /**
     * 保存每日专注目标（分钟）。
     *
     * @param minutes 目标分钟数，越界值由 [SettingsStore] 统一收敛
     */
    fun saveDailyGoal(minutes: Int) {
        viewModelScope.launch {
            settingsStore.updateDailyGoal(minutes)
        }
    }

    /**
     * 删除一条计划模板。
     *
     * @param id 模板 id
     */
    fun removePlanTemplate(id: String) {
        viewModelScope.launch {
            settingsStore.removePlanTemplate(id)
        }
    }

    /**
     * 记一次成功的备份导出，用于「备份体检」判断是否该再备份。
     *
     * @param at 导出时刻（毫秒），默认当前时间
     */
    fun markBackupExported(at: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            settingsStore.markBackupExported(at)
        }
    }
}