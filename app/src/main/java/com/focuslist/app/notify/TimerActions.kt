package com.focuslist.app.notify

/**
 * 番茄完成通知上的快捷动作定义。
 *
 * 通知点击属于用户主动交互，系统允许此时启动前台服务或排闹钟，
 * 所以「开始休息 / 再来一个 / 稍后提醒」都可以直接从通知栏完成，不必打开应用。
 */
object TimerActions {

    /** 开始休息（番茄结束后） */
    const val ACTION_START_BREAK = "com.focuslist.app.action.TIMER_START_BREAK"

    /** 再来一个番茄（休息结束后） */
    const val ACTION_START_NEXT = "com.focuslist.app.action.TIMER_START_NEXT"

    /** 稍后 5 分钟提醒 */
    const val ACTION_SNOOZE = "com.focuslist.app.action.TIMER_SNOOZE"

    /** 稍后提醒到点（内部广播） */
    const val ACTION_SNOOZE_FIRE = "com.focuslist.app.action.TIMER_SNOOZE_FIRE"

    /** 通知标题 */
    const val EXTRA_TITLE = "title"

    /** 通知正文 */
    const val EXTRA_TEXT = "text"

    /** 关联任务 ID */
    const val EXTRA_TASK_ID = "taskId"

    /** 关联任务标题 */
    const val EXTRA_TASK_TITLE = "taskTitle"

    /** 番茄时长（分钟） */
    const val EXTRA_DURATION = "durationMin"

    /** 关联计划 ID */
    const val EXTRA_PLAN_ID = "planId"

    /** 连续番茄数 */
    const val EXTRA_CYCLE = "cycle"

    /** 主按钮类型：BREAK / NEXT */
    const val EXTRA_PRIMARY = "primary"

    /** 主按钮：开始休息 */
    const val PRIMARY_BREAK = "BREAK"

    /** 主按钮：再来一个 */
    const val PRIMARY_NEXT = "NEXT"
}
