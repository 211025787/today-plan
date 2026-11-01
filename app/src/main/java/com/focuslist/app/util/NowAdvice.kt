package com.focuslist.app.util

/**
 * 「现在」建议的类型。
 *
 * 每种类型对应一套完全不同的措辞与操作，界面按 [NowTip.actionText] 决定是否显示按钮。
 */
enum class NowKind {
    /** 今天没有生效中的计划 */
    NO_PLAN,

    /** 有计划正在计时 */
    RUNNING,

    /** 今天还没有任何投入 */
    FRESH_START,

    /** 还有计划没达标，挑最容易达成的那个 */
    CLOSE_TO_GOAL,

    /** 今天所有计划都达标 */
    ALL_DONE,

    /** 已经很晚且还没做完，建议收工 */
    REST
}

/**
 * 「现在」建议需要的单个计划快照。
 *
 * 刻意不用 Room 实体：这样整个建议引擎都能在 JVM 单测里直接跑。
 *
 * @property id 计划 ID
 * @property title 计划名称
 * @property icon 图标 emoji
 * @property todayMinutes 今天已投入分钟数
 * @property goalMinutes 每日目标分钟数
 * @property running 是否正在计时
 */
data class NowPlan(
    val id: Long,
    val title: String,
    val icon: String,
    val todayMinutes: Int,
    val goalMinutes: Int,
    val running: Boolean = false
)

/**
 * 一条「现在」建议。
 *
 * @property kind 建议类型
 * @property emoji 左侧图标
 * @property headline 主文案，一句话给结论
 * @property detail 副文案，补充数字或理由
 * @property planId 点「开始」时对应的计划；null 表示本条无可直接启动的计划
 * @property actionText 按钮文案，空串表示只展示不提供按钮
 * @property progress 关联计划的完成度 0-1，用于底部细进度线；无关联计划时为 0
 */
data class NowTip(
    val kind: NowKind,
    val emoji: String,
    val headline: String,
    val detail: String,
    val planId: Long? = null,
    val actionText: String = "",
    val progress: Float = 0f
)

/**
 * 「现在该做什么」建议引擎：把今天的计划、投入与当前时刻压成一句话。
 *
 * 设计约定（改之前先读）：
 * - **只做一件事**：一次只给一条建议，且尽量给可直接执行的动作（点一下就开计时）；
 * - **先易后难**：多条计划都没达标时，挑「离达标最近」的那个，先拿到正反馈；
 * - **不制造焦虑**：22:30 之后若还没做完，改成「今天先到这里」，不再催进度；
 * - **全部达成**才说收工，避免半途就鼓励停下。
 *
 * 判定优先级：无计划 → 计时中 → 全部达成 → 深夜收工 → 全新开始 → 最接近达标。
 */
object NowAdvice {

    /** 建议收工的分钟线：22:30 */
    const val LATE_MINUTE = 22 * 60 + 30

    /** 一天的分钟数 */
    private const val DAY_MINUTES = 24 * 60

    /**
     * 计算「现在」建议。
     *
     * @param plans 今天生效的计划快照（顺序即展示顺序，平局时取靠前的）
     * @param nowMinutes 当前时刻从 0 点起的分钟数，越界值会被夹到 0..1439
     * @return 建议；计划为空时返回「今天还没有计划」
     */
    fun of(plans: List<NowPlan>, nowMinutes: Int): NowTip {
        if (plans.isEmpty()) {
            return NowTip(
                kind = NowKind.NO_PLAN,
                emoji = "✨",
                headline = "今天还没有计划",
                detail = "用一句话建一个，把今天安排明白",
                actionText = "添加"
            )
        }

        val minute = nowMinutes.coerceIn(0, DAY_MINUTES - 1)

        // 计时中最优先：正在跑的事不需要再被建议
        plans.firstOrNull { it.running }?.let { running ->
            return NowTip(
                kind = NowKind.RUNNING,
                emoji = "⏱",
                headline = "正在专注：${running.title}",
                detail = "已投入 ${running.todayMinutes} / ${goalOf(running)} 分钟",
                planId = running.id,
                progress = progressOf(running)
            )
        }

        val pending = plans.filterNot { achieved(it) }
        if (pending.isEmpty()) {
            return NowTip(
                kind = NowKind.ALL_DONE,
                emoji = "✅",
                headline = "今天的计划都达标了",
                detail = "写一句复盘，把今天留住",
                progress = 1f
            )
        }

        val closest = closest(pending)
        if (minute >= LATE_MINUTE) {
            return NowTip(
                kind = NowKind.REST,
                emoji = "🌙",
                headline = "今天先到这里",
                detail = "还差 ${remainingOf(closest)} 分钟，明天补上，别熬夜",
                progress = progressOf(closest)
            )
        }

        if (plans.all { it.todayMinutes <= 0 }) {
            val easiest = plans.minByOrNull { goalOf(it) } ?: plans.first()
            return NowTip(
                kind = NowKind.FRESH_START,
                emoji = "▶",
                headline = "从「${easiest.title}」开始",
                detail = "今天还没开始，先来 ${goalOf(easiest)} 分钟",
                planId = easiest.id,
                actionText = "开始",
                progress = progressOf(easiest)
            )
        }

        return NowTip(
            kind = NowKind.CLOSE_TO_GOAL,
            emoji = "🎯",
            headline = "再 ${remainingOf(closest)} 分钟达成「${closest.title}」",
            detail = "今天已投入 ${closest.todayMinutes} / ${goalOf(closest)} 分钟",
            planId = closest.id,
            actionText = "开始",
            progress = progressOf(closest)
        )
    }

    /**
     * 是否已达标：目标非正数视为无需投入，直接算达成（避免除零）。
     *
     * @param plan 计划快照
     * @return 是否达标
     */
    fun achieved(plan: NowPlan): Boolean {
        return plan.goalMinutes <= 0 || plan.todayMinutes >= plan.goalMinutes
    }

    /**
     * 还差多少分钟达标。
     *
     * @param plan 计划快照
     * @return 非负分钟数
     */
    fun remainingOf(plan: NowPlan): Int {
        return (plan.goalMinutes - plan.todayMinutes).coerceAtLeast(0)
    }

    /**
     * 完成度 0-1（目标非正数时为 1）。
     *
     * @param plan 计划快照
     * @return 完成度
     */
    fun progressOf(plan: NowPlan): Float {
        if (plan.goalMinutes <= 0) {
            return 1f
        }
        return (plan.todayMinutes.toFloat() / plan.goalMinutes.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 未达标计划里「离达标最近」的那个；平局取列表靠前的。
     *
     * @param pending 未达标计划
     * @return 目标计划；空列表时抛异常由调用方保证非空
     */
    private fun closest(pending: List<NowPlan>): NowPlan {
        return pending.minByOrNull { remainingOf(it) } ?: pending.first()
    }

    /**
     * 目标分钟数，非正数按 1 处理（仅用于文案展示，避免出现「0 分钟」）。
     *
     * @param plan 计划快照
     * @return 展示用目标分钟数
     */
    private fun goalOf(plan: NowPlan): Int {
        return if (plan.goalMinutes > 0) plan.goalMinutes else 1
    }
}
