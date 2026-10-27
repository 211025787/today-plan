package com.focuslist.app.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 计时阶段枚举。
 */
enum class TimerPhase {
    /** 空闲（未在计时） */
    IDLE,

    /** 专注（番茄钟） */
    WORK,

    /** 短休息 */
    SHORT_BREAK,

    /** 长休息 */
    LONG_BREAK
}

/**
 * 计时器对外暴露的界面状态快照。
 *
 * @property phase 当前阶段
 * @property remainSec 剩余秒数
 * @property totalSec 本阶段总秒数（用于进度环）
 * @property running 是否正在走动（暂停时为 false）
 * @property taskId 关联任务 ID，自由专注为 null
 * @property taskTitle 关联任务标题
 * @property cyclePomos 本轮已连续完成的番茄数（决定下个休息是长是短）
 */
data class TimerUiState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val remainSec: Long = 0,
    val totalSec: Long = 0,
    val running: Boolean = false,
    val taskId: Long? = null,
    val taskTitle: String = "",
    val cyclePomos: Int = 0
)

/**
 * 计时状态全局持有者：服务写入，Compose 界面与桌面小组件读取。
 */
object TimerStateHolder {

    private val mutableState = MutableStateFlow(TimerUiState())

    /** 只读状态流 */
    val state: StateFlow<TimerUiState> = mutableState.asStateFlow()

    /**
     * 原子更新状态。
     *
     * @param transform 基于当前状态生成新状态的函数
     */
    fun update(transform: (TimerUiState) -> TimerUiState) {
        mutableState.value = transform(mutableState.value)
    }

    /**
     * 直接覆盖状态。
     *
     * @param newState 新状态
     */
    fun set(newState: TimerUiState) {
        mutableState.value = newState
    }

    /**
     * 读取当前状态快照。
     *
     * @return 当前状态
     */
    fun current(): TimerUiState {
        return mutableState.value
    }
}