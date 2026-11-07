package com.focuslist.app.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.CardBoard
import com.focuslist.app.util.CardBox
import com.focuslist.app.util.CardShape
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 卡片挪位的弹簧：临界阻尼（dampingRatio = 1）。
 *
 * 刻意不给回弹——冲过头就等于短暂压到邻居，而这块板子的核心承诺是「任何时候都不重叠」。
 * 顺滑感来自速度连续（弹簧会接着上一段速度走），不需要靠过冲。
 */
private val CardMoveSpring = spring<IntOffset>(dampingRatio = 1f, stiffness = 420f)

/** 尺寸变化用的弹簧，同样不越界 */
private val CardSizeSpring = spring<Dp>(dampingRatio = 1f, stiffness = 520f)

/** 松手归位用的弹簧，比挪位更紧，让"啪嗒"落定得干脆 */
private val SettleSpring = spring<Float>(dampingRatio = 1f, stiffness = 700f)

/** 抓起时的抬升弹簧 */
private val CardLiftSpring = spring<Float>(dampingRatio = 1f, stiffness = 600f)

/**
 * 日卡片展示数据：计划 + 当天实时秒数 + 是否计时中 + 节奏角标。
 *
 * @property plan 计划实体
 * @property todaySec 当天累计秒数（含未落库的当前段）
 * @property running 是否正在计时
 * @property paceText 节奏角标；null 表示不显示
 */
data class DayPlanCardData(
    val plan: PlanEntity,
    val todaySec: Long,
    val running: Boolean,
    val paceText: String?
)

/**
 * 日卡片展示数据：旅游行程。
 *
 * @property trip 行程实体
 */
data class DayTripCardData(val trip: TripEntity)

/** 当前手势类型 */
private enum class BoardGesture { NONE, MOVE, RESIZE }

/**
 * 自由摆放的计划 / 行程卡片画布。
 *
 * 与原来的 `LazyVerticalGrid` 不同：
 * - 卡片坐标由数据库里的 (cardX, cardY) 决定，不再自动流式重排；
 * - 长按卡片进入拖动，松手落到最近的格子，压到的卡自动整理；
 * - 右下角手柄拖动改大小，按格取整，被压到的卡**实时**让位（不用等松手）；
 * - 第一次打开的旧数据会自动补位并写回，之后位置不再自动变化。
 *
 * @param plans 当天生效的计划
 * @param trips 当天生效的行程
 * @param cellWidth 单格宽度
 * @param spacing 卡片间距
 * @param manageMode 是否多选管理
 * @param selectedPlanIds 已选计划
 * @param onPlanClick 点击计划（非管理态打开统计）
 * @param onPlanLongClick 长按且没有拖动（打开操作菜单）
 * @param onTripClick 点击行程
 * @param onToggleTimer 开始 / 暂停计划计时
 * @param onToggleSelect 管理态切换勾选
 * @param onPlanLayout 计划位置 / 大小落库
 * @param onTripLayout 行程位置 / 大小落库
 * @param modifier 布局修饰
 */
@Composable
fun DayCardBoard(
    plans: List<DayPlanCardData>,
    trips: List<DayTripCardData>,
    cellWidth: Dp,
    spacing: Dp,
    manageMode: Boolean,
    selectedPlanIds: Set<Long>,
    onPlanClick: (PlanEntity) -> Unit,
    onPlanLongClick: (PlanEntity) -> Unit,
    onTripClick: (TripEntity) -> Unit,
    onToggleTimer: (Long) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onPlanLayout: (Long, Int, Int, Int, Int, Long) -> Unit,
    onTripLayout: (Long, Int, Int, Int, Int, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val step = cellWidth + spacing
    val stepPx = with(density) { step.toPx() }

    // 数据库里的尺寸/坐标 → 算法占位。cardCols/cardRows 为 0 的旧数据用 cardSize 兜底；
    // 超出 1..4 的脏数据由 pack 里的 normalize 夹回去。
    val boxes = remember(plans, trips) {
        buildList {
            plans.forEach { data ->
                val plan = data.plan
                add(
                    CardBox(
                        key = plan.id,
                        trip = false,
                        cols = if (plan.cardCols > 0) plan.cardCols else legacyCols(plan.cardSize),
                        rows = if (plan.cardRows > 0) plan.cardRows else legacyRows(plan.cardSize),
                        x = plan.cardX,
                        y = plan.cardY
                    )
                )
            }
            trips.forEach { data ->
                val trip = data.trip
                add(
                    CardBox(
                        key = trip.id,
                        trip = true,
                        cols = if (trip.cardCols > 0) trip.cardCols else legacyCols(trip.cardSize),
                        rows = if (trip.cardRows > 0) trip.cardRows else legacyRows(trip.cardSize),
                        x = trip.cardX,
                        y = trip.cardY
                    )
                )
            }
        }
    }
    val packed = remember(boxes) { CardBoard.pack(boxes) }
    val planBoxes = packed.take(plans.size)
    val tripBoxes = packed.drop(plans.size)

    // 松手落库到列表重新发射之间有一小段空窗。这期间如果直接按 packed 画，
    // 卡片会先弹回原位、再跳到新位置——看起来就像"拖了却没放对"。
    // 用一条乐观值顶住，等 packed 里出现同样的坐标再撤掉。
    var pending by remember { mutableStateOf<Map<String, CardBox>>(emptyMap()) }

    // 旧卡 / 新卡第一次出现：自动补位并写回，从此位置固定；
    // 超出 1–4 格的脏数据由 normalize 夹回去，这里负责把夹过的结果写回数据库。
    val autoPlaced = remember { mutableSetOf<String>() }
    // 历史重叠整理是否已经做过（每次进页面只做一次，见下面的说明）
    var legacyRepaired by remember { mutableStateOf(false) }
    LaunchedEffect(packed) {
        plans.forEachIndexed { index, data ->
            val plan = data.plan
            val box = planBoxes.getOrNull(index) ?: return@forEachIndexed
            val needsPlace = plan.cardX < 0 || plan.cardY < 0
            val needsShape = plan.cardCols > 0 &&
                (plan.cardCols != box.cols || plan.cardRows != box.rows)
            if (needsPlace || needsShape) {
                val key = "p${plan.id}"
                if (autoPlaced.add(key)) {
                    onPlanLayout(plan.id, box.cols, box.rows, box.x, box.y, plan.cardZ)
                }
            }
        }
        trips.forEachIndexed { index, data ->
            val trip = data.trip
            val box = tripBoxes.getOrNull(index) ?: return@forEachIndexed
            val needsPlace = trip.cardX < 0 || trip.cardY < 0
            val needsShape = trip.cardCols > 0 &&
                (trip.cardCols != box.cols || trip.cardRows != box.rows)
            if (needsPlace || needsShape) {
                val key = "t${trip.id}"
                if (autoPlaced.add(key)) {
                    onTripLayout(trip.id, box.cols, box.rows, box.x, box.y, trip.cardZ)
                }
            }
        }

        // 老数据修复：早期缩放不检查重叠，可能已经有卡叠在一起。
        // 按当前顺序逐张"落位"，后到的卡若压到前面的卡就挪到最近空位，一次修好。
        //
        // 只做一次，而且只在进页面后的第一次快照上做：松手时是逐张落库的，
        // 中间态（A 已挪走、B 还没挪）看起来就是"重叠"，若每次都修，
        // 就会把用户刚放下的卡再挪走——表现正是"放不了，自己跑到下一行"。
        val hasCards = plans.isNotEmpty() || trips.isNotEmpty()
        if (!legacyRepaired && hasCards) {
            legacyRepaired = true
            val settled = mutableListOf<CardBox>()
            plans.forEachIndexed { index, data ->
                val box = planBoxes.getOrNull(index) ?: return@forEachIndexed
                val target = if (CardBoard.fitsAt(settled, box)) {
                    box
                } else {
                    CardBoard.resolveMove(settled, box, box.x, box.y)
                }
                // 修复标记要带上目标坐标：同一张卡一局里可能被压到好几次
                if (target != box && autoPlaced.add("fix-p${data.plan.id}@${target.x},${target.y}")) {
                    onPlanLayout(
                        data.plan.id, target.cols, target.rows, target.x, target.y, data.plan.cardZ
                    )
                }
                settled.add(target)
            }
            trips.forEachIndexed { index, data ->
                val box = tripBoxes.getOrNull(index) ?: return@forEachIndexed
                val target = if (CardBoard.fitsAt(settled, box)) {
                    box
                } else {
                    CardBoard.resolveMove(settled, box, box.x, box.y)
                }
                if (target != box && autoPlaced.add("fix-t${data.trip.id}@${target.x},${target.y}")) {
                    onTripLayout(
                        data.trip.id, target.cols, target.rows, target.x, target.y, data.trip.cardZ
                    )
                }
                settled.add(target)
            }
        }

        // packed 里已经出现同样的坐标，对应的乐观值就可以撤了
        if (pending.isNotEmpty()) {
            val actual = (planBoxes + tripBoxes).associateBy {
                if (it.trip) "t${it.key}" else "p${it.key}"
            }
            val left = pending.filter { (key, want) -> actual[key] != want }
            if (left.size != pending.size) pending = left
        }
    }

    // 当前手势：只允许一张卡同时被拖动/缩放
    var activeKey by remember { mutableStateOf<String?>(null) }
    var gesture by remember { mutableStateOf(BoardGesture.NONE) }
    var activeStart by remember { mutableStateOf<CardBox?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    // 缩放中的实时像素宽高：跟手变化，再由 resizeShape 逐轴取整成格子数
    var liveWidthPx by remember { mutableFloatStateOf(0f) }
    var liveHeightPx by remember { mutableFloatStateOf(0f) }
    var startWidthPx by remember { mutableFloatStateOf(0f) }
    var startHeightPx by remember { mutableFloatStateOf(0f) }
    // 上一次吸附到的规格：跨格时给一次轻微触感，让「吸附」这件事有反馈
    var lastSnap by remember { mutableStateOf(CardShape(0, 0)) }
    // 松手后还在滑向格子的那张卡：用它把「手指停下的位置 → 格子」的残差补间掉，
    // 这样松手是"啪嗒"归位，而不是瞬移
    var settlingKey by remember { mutableStateOf<String?>(null) }
    // 拖动中卡片当前落到哪一格。用状态而不是每次现算，是为了做滞回：
    // 手指停在格线上时不会在相邻两格之间反复横跳，邻居也就不会来回抖
    var moveCell by remember { mutableStateOf(IntOffset.Zero) }
    // 最近操作的卡片置顶：整理前先压在邻居上时，用户仍能完整看到自己刚调整的卡
    var foregroundKey by remember { mutableStateOf<String?>(null) }
    val spacingPx = with(density) { spacing.toPx() }

    fun boxKeyOf(box: CardBox): String = if (box.trip) "t${box.key}" else "p${box.key}"

    fun zOf(box: CardBox): Long = if (box.trip) {
        trips.firstOrNull { it.trip.id == box.key }?.trip?.cardZ ?: 0L
    } else {
        plans.firstOrNull { it.plan.id == box.key }?.plan?.cardZ ?: 0L
    }

    fun commitOne(box: CardBox, z: Long) {
        pending = pending + (boxKeyOf(box) to box)
        if (box.trip) {
            onTripLayout(box.key, box.cols, box.rows, box.x, box.y, z)
        } else {
            onPlanLayout(box.key, box.cols, box.rows, box.x, box.y, z)
        }
    }

    // 这一帧真正要画的布局：数据库快照 + 还没追平的乐观值。
    // 画位置、算画布高度、落库全用它——三份数据各算各的，就会在松手那一帧
    // 出现"卡片跳一下""画布缩一行把卡片裁掉半截"这类怪事。
    val effective = packed.map { pending[boxKeyOf(it)] ?: it }

    // 松手后的归位补间：dragX/dragY 里存的是「格子位置与手指位置的差」，
    // 用弹簧把它推到 0，卡片的视觉位置就从手指处滑进格子。
    // 临界阻尼（dampingRatio = 1）保证不会冲过头压到邻居。
    LaunchedEffect(settlingKey) {
        if (settlingKey == null) return@LaunchedEffect
        coroutineScope {
            launch { animate(dragX, 0f, animationSpec = SettleSpring) { v, _ -> dragX = v } }
            launch { animate(dragY, 0f, animationSpec = SettleSpring) { v, _ -> dragY = v } }
        }
        dragX = 0f
        dragY = 0f
        settlingKey = null
    }

    // 缩放的目标规格：按当前像素尺寸**逐轴**取整到格子（横向只改列、纵向只改行）。
    // 卡片本体也按这个规格绘制，这样「画出来的大小」和「占的格子」永远一致——
    // 邻居被挤开时边缘才对得齐，也不会出现半个身位的重叠。
    val resizeBase = if (gesture == BoardGesture.RESIZE) activeStart else null
    val resizeShape: CardShape? = resizeBase?.let { start ->
        CardBoard.snapShape(
            cols = (liveWidthPx + spacingPx) / stepPx,
            rows = (liveHeightPx + spacingPx) / stepPx,
            maxCols = CardBoard.COLUMNS - start.x,
            maxRows = CardBoard.MAX_SPAN
        )
    }
    val resizeAnchor: CardBox? = if (resizeBase != null && resizeShape != null) {
        resizeBase.copy(cols = resizeShape.cols, rows = resizeShape.rows)
    } else {
        null
    }
    // 移动中的目标格子：卡片视觉上跟手自由移动，但占位始终是它身下那个格子
    val moveAnchor: CardBox? = if (gesture == BoardGesture.MOVE) {
        activeStart?.copy(x = moveCell.x, y = moveCell.y)
    } else {
        null
    }
    val previewAnchor = moveAnchor ?: resizeAnchor
    // 实时整理：拿当前占位当锚点跑一遍 [CardBoard.arrange]，被压到的卡当场挪到最近空位。
    // 移动、缩放两种手势都走这条路——和手机桌面一样，邻居不用等松手就让开。
    // 这份结果既用于渲染，也**原样**用于落库（见 settle）。
    val previewArrangement: List<CardBox>? = previewAnchor?.let {
        CardBoard.arrange(effective, it)
    }
    val pushed: Map<String, CardBox> = if (previewArrangement == null || previewAnchor == null) {
        emptyMap()
    } else {
        val anchorId = boxKeyOf(previewAnchor)
        val out = HashMap<String, CardBox>()
        effective.forEachIndexed { index, box ->
            val id = boxKeyOf(box)
            if (id != anchorId && previewArrangement[index] != box) {
                out[id] = previewArrangement[index]
            }
        }
        out
    }

    /**
     * 松手收尾：把**屏幕上已经排好的那一版**写进数据库，不再重算。
     *
     * 重算看着更"干净"，实际是 bug 源：落库是逐张写，数据库会在中途发射一次
     * 「A 已挪走、B 还没挪」的中间态，拿它重算就会得到另一个结果——
     * 表现就是"我明明放在这里，它自己跑到下一行"。
     * 只有预览不可用（理论上不会发生）时才退回即时计算。
     *
     * @param anchor 刚被拖动 / 缩放过的卡片（已带最终位置与规格）
     */
    fun settle(anchor: CardBox) {
        val seen = previewAnchor
        val arranged = if (
            previewArrangement != null && seen != null &&
            boxKeyOf(seen) == boxKeyOf(anchor) &&
            seen.cols == anchor.cols && seen.rows == anchor.rows &&
            seen.x == anchor.x && seen.y == anchor.y
        ) {
            previewArrangement
        } else {
            CardBoard.arrange(effective, anchor)
        }
        val changed = arranged.indices.filter { arranged[it] != effective[it] }
        if (changed.isEmpty()) return
        val anchorId = boxKeyOf(anchor)
        // 先写被挤走的卡，最后写锚点卡：锚点拿到最新时间戳，整理完仍在最上层
        changed.filter { boxKeyOf(arranged[it]) != anchorId }
            .forEach { commitOne(arranged[it], zOf(arranged[it])) }
        changed.firstOrNull { boxKeyOf(arranged[it]) == anchorId }
            ?.let { commitOne(arranged[it], System.currentTimeMillis()) }
    }

    fun startMove(box: CardBox) {
        if (manageMode) return
        // 缩放手势优先：手柄已经开拖了就别再被卡身的长按接管
        if (gesture == BoardGesture.RESIZE) return
        settlingKey = null
        foregroundKey = boxKeyOf(box)
        activeKey = boxKeyOf(box)
        gesture = BoardGesture.MOVE
        activeStart = box
        dragX = 0f
        dragY = 0f
        moveCell = IntOffset(box.x.coerceAtLeast(0), box.y.coerceAtLeast(0))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun startResize(box: CardBox) {
        if (manageMode) return
        if (gesture == BoardGesture.MOVE && activeKey != null) return
        settlingKey = null
        foregroundKey = boxKeyOf(box)
        activeKey = boxKeyOf(box)
        gesture = BoardGesture.RESIZE
        activeStart = box
        dragX = 0f
        dragY = 0f
        startWidthPx = box.cols * stepPx - spacingPx
        startHeightPx = box.rows * stepPx - spacingPx
        liveWidthPx = startWidthPx
        liveHeightPx = startHeightPx
        lastSnap = CardShape(box.cols, box.rows)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun moveBy(dx: Float, dy: Float) {
        if (gesture != BoardGesture.MOVE) return
        dragX += dx
        dragY += dy
        val start = activeStart ?: return
        // 滞回：只有离开当前格子中心超过 0.6 格才换格。
        // 否则手指停在格线上时会在相邻两格之间反复触发，邻居跟着抖。
        val fx = (start.x * stepPx + dragX) / stepPx
        val fy = (start.y * stepPx + dragY) / stepPx
        val cx = CardBoard.snapCell(moveCell.x, fx)
        val cy = CardBoard.snapCell(moveCell.y, fy)
        val cell = IntOffset(
            cx.coerceIn(0, CardBoard.COLUMNS - start.cols),
            cy.coerceAtLeast(0)
        )
        if (cell != moveCell) {
            moveCell = cell
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun resizeBy(dx: Float, dy: Float) {
        if (gesture != BoardGesture.RESIZE) return
        dragX += dx
        dragY += dy
        val start = activeStart ?: return
        // 右下角完全跟手：只夹在「最小 1 格、最大 4 格、不出右边界」内。
        // 具体落到几格由 resizeShape 取整决定，邻居会跟着立刻让位。
        val minSize = stepPx - spacingPx
        val maxWidth = (CardBoard.COLUMNS - start.x) * stepPx - spacingPx
        val maxHeight = CardBoard.MAX_SPAN * stepPx - spacingPx
        liveWidthPx = (startWidthPx + dragX).coerceIn(minSize, maxWidth)
        liveHeightPx = (startHeightPx + dragY).coerceIn(minSize, maxHeight)
        val shape = CardBoard.snapShape(
            cols = (liveWidthPx + spacingPx) / stepPx,
            rows = (liveHeightPx + spacingPx) / stepPx,
            maxCols = CardBoard.COLUMNS - start.x,
            maxRows = CardBoard.MAX_SPAN
        )
        if (shape != lastSnap && lastSnap.cols > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastSnap = shape
    }

    fun resetGesture() {
        activeKey = null
        gesture = BoardGesture.NONE
        activeStart = null
        dragX = 0f
        dragY = 0f
        liveWidthPx = 0f
        liveHeightPx = 0f
    }

    /** 只有当前确实是移动手势时才收尾：别把正在进行的缩放一起清掉 */
    fun cancelMove() {
        if (gesture == BoardGesture.MOVE) resetGesture()
    }

    /** 同上，缩放版本 */
    fun cancelResize() {
        if (gesture == BoardGesture.RESIZE) resetGesture()
    }

    fun endMove() {
        val start = activeStart
        if (gesture == BoardGesture.MOVE && start != null) {
            // 用拖动过程中算好的格子：和预览完全一致，松手不会换目标
            val nx = moveCell.x
            val ny = moveCell.y
            // 残差 = 手指停下的位置 - 目标格子。落库后卡片会立刻画在格子上，
            // 把这段残差先留在 dragX/dragY 里，再由 LaunchedEffect 补间到 0，
            // 视觉上就是"从手指处滑进格子"，而不是瞬移半格。
            val restX = start.x * stepPx + dragX - nx * stepPx
            val restY = start.y * stepPx + dragY - ny * stepPx
            val id = boxKeyOf(start)
            settle(start.copy(x = nx, y = ny))
            resetGesture()
            if (abs(restX) > 0.5f || abs(restY) > 0.5f) {
                dragX = restX
                dragY = restY
                settlingKey = id
            }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            resetGesture()
        }
    }

    fun endResize() {
        val start = activeStart
        if (gesture == BoardGesture.RESIZE && start != null) {
            // 松手就按当前规格落定。占位和画出来的大小本来就一致，不再二次吸附，
            // 因此松手瞬间卡片尺寸不会跳、被挤走的卡也不用再挪第二次。
            val shape = CardBoard.snapShape(
                cols = (liveWidthPx + spacingPx) / stepPx,
                rows = (liveHeightPx + spacingPx) / stepPx,
                maxCols = CardBoard.COLUMNS - start.x,
                maxRows = CardBoard.MAX_SPAN
            )
            settle(start.copy(cols = shape.cols, rows = shape.rows))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        resetGesture()
    }

    // 画布高度要同时容纳：拖动中卡片实际停的位置、手势锚点占位、以及被挤出去的卡。
    // 缩放时只按吸附后的规格算——用连续像素会多出一行空白，整页高度会跟着手指呼吸。
    val dragBottomPx = if (gesture == BoardGesture.MOVE && activeStart != null) {
        activeStart!!.y * stepPx + dragY + activeStart!!.rows * stepPx
    } else {
        0f
    }
    // 归位中的卡片带着残差，也得算进画布，否则画布会提前收缩把它裁掉
    val settleBottomPx = settlingKey?.let { id ->
        pending[id]?.let { box -> box.y * stepPx + dragY + box.rows * stepPx } ?: 0f
    } ?: 0f
    val previewBottomPx = previewAnchor?.let { (it.y + it.rows) * stepPx } ?: 0f
    val boardRows = maxOf(
        CardBoard.canvasRows(effective),
        ceil(maxOf(dragBottomPx, settleBottomPx, previewBottomPx) / stepPx).toInt(),
        // 被挤出去的卡可能落到更下面，画布得跟着长，否则会贴边被裁掉
        pushed.values.maxOfOrNull { it.y + it.rows } ?: 0,
        1
    )
    val boardHeight = cellWidth * boardRows + spacing * (boardRows - 1)

    // 网格提示淡入淡出：松手时跟着归位一起淡掉，而不是"啪"地消失
    val guidesAlpha by animateFloatAsState(
        targetValue = if (gesture != BoardGesture.NONE) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "gridGuides"
    )

    Box(modifier = modifier.fillMaxWidth().height(boardHeight)) {
        // 细网格提示：拖动 / 缩放时才显示，方便对齐
        if (guidesAlpha > 0.01f) {
            GridGuides(
                cellWidth = cellWidth,
                spacing = spacing,
                rows = boardRows,
                alpha = guidesAlpha,
                modifier = Modifier.fillMaxSize()
            )
        }

        plans.indices.sortedBy { plans[it].plan.cardZ }.forEach { index ->
            val data = plans[index]
            val box = planBoxes.getOrNull(index) ?: return@forEach
            val key = "p${data.plan.id}"
            // 刚落库、列表还没刷新时先用乐观值顶上，避免松手后闪一下原位；
            // 缩放中还要把「已经被挤到新位置的卡」画在预览位上
            val shown = pushed[key] ?: pending[key] ?: box
            val active = activeKey == key
            // 松手后还在滑向格子的那张：它虽然不再是 active，但偏移仍由 dragX/dragY 驱动
            val settling = settlingKey == key
            val resizing = active && gesture == BoardGesture.RESIZE
            val faceCols = if (resizing) resizeShape?.cols ?: shown.cols else shown.cols
            val faceRows = if (resizing) resizeShape?.rows ?: shown.rows else shown.rows
            val dragging = (active && gesture == BoardGesture.MOVE) || settling
            val xPx = shown.x * stepPx + if (dragging) dragX else 0f
            val yPx = shown.y * stepPx + if (dragging) dragY else 0f
            val w = cellWidth * faceCols + spacing * (faceCols - 1)
            val h = cellWidth * faceRows + spacing * (faceRows - 1)
            // 尺寸也走弹簧：跨格时和邻居一起弹过去，不会一格一格地"哆嗦"
            val animW by animateDpAsState(
                targetValue = w,
                animationSpec = CardSizeSpring,
                label = "cardW"
            )
            val animH by animateDpAsState(
                targetValue = h,
                animationSpec = CardSizeSpring,
                label = "cardH"
            )
            // 被挤走的卡用弹簧滑过去，正在跟手 / 归位的那张逐帧直给
            val cardOffset = rememberCardOffset(
                target = IntOffset(xPx.roundToInt(), yPx.roundToInt()),
                active = active || settling,
                gestureRunning = gesture != BoardGesture.NONE || settling
            )
            // key()：卡片的组合身份必须跟着 id 走。少了它，置顶（cardZ 变化）会改变
            // forEach 顺序，Compose 按位置复用节点，手势节点就可能挂到另一张卡上——
            // 表现正是「点了这张卡的右下角，缩放的却是别的卡」。
            key(key) {
                PlanBoardCard(
                    data = data,
                    cols = faceCols,
                    rows = faceRows,
                    width = animW,
                    height = animH,
                    manageMode = manageMode,
                    selected = data.plan.id in selectedPlanIds,
                    active = active,
                    onToggleTimer = { onToggleTimer(data.plan.id) },
                    onClick = {
                        foregroundKey = key
                        if (manageMode) onToggleSelect(data.plan.id) else onPlanClick(data.plan)
                    },
                    onLongClick = {
                        foregroundKey = key
                        if (manageMode) onToggleSelect(data.plan.id) else onPlanLongClick(data.plan)
                    },
                    onMoveStart = { startMove(shown) },
                    onMove = ::moveBy,
                    onMoveEnd = ::endMove,
                    onMoveCancel = ::cancelMove,
                    onResizeStart = { startResize(shown) },
                    onResize = ::resizeBy,
                    onResizeEnd = ::endResize,
                    onResizeCancel = ::cancelResize,
                    modifier = Modifier
                        .offset { cardOffset }
                        .size(animW, animH)
                        .zIndex(if (active || settling) 10f else if (foregroundKey == key) 5f else 1f)
                )
            }
        }

        trips.indices.sortedBy { trips[it].trip.cardZ }.forEach { index ->
            val data = trips[index]
            val box = tripBoxes.getOrNull(index) ?: return@forEach
            val key = "t${data.trip.id}"
            val shown = pushed[key] ?: pending[key] ?: box
            val active = activeKey == key
            // 松手后还在滑向格子的那张：它虽然不再是 active，但偏移仍由 dragX/dragY 驱动
            val settling = settlingKey == key
            val resizing = active && gesture == BoardGesture.RESIZE
            val faceCols = if (resizing) resizeShape?.cols ?: shown.cols else shown.cols
            val faceRows = if (resizing) resizeShape?.rows ?: shown.rows else shown.rows
            val dragging = (active && gesture == BoardGesture.MOVE) || settling
            val xPx = shown.x * stepPx + if (dragging) dragX else 0f
            val yPx = shown.y * stepPx + if (dragging) dragY else 0f
            val w = cellWidth * faceCols + spacing * (faceCols - 1)
            val h = cellWidth * faceRows + spacing * (faceRows - 1)
            // 尺寸也走弹簧：跨格时和邻居一起弹过去，不会一格一格地"哆嗦"
            val animW by animateDpAsState(
                targetValue = w,
                animationSpec = CardSizeSpring,
                label = "cardW"
            )
            val animH by animateDpAsState(
                targetValue = h,
                animationSpec = CardSizeSpring,
                label = "cardH"
            )
            val cardOffset = rememberCardOffset(
                target = IntOffset(xPx.roundToInt(), yPx.roundToInt()),
                active = active || settling,
                gestureRunning = gesture != BoardGesture.NONE || settling
            )
            key(key) {
                TripBoardCard(
                    trip = data.trip,
                    cols = faceCols,
                    rows = faceRows,
                    width = animW,
                    height = animH,
                    active = active,
                    onClick = {
                        foregroundKey = key
                        onTripClick(data.trip)
                    },
                    onMoveStart = { startMove(shown) },
                    onMove = ::moveBy,
                    onMoveEnd = ::endMove,
                    onMoveCancel = ::cancelMove,
                    onResizeStart = { startResize(shown) },
                    onResize = ::resizeBy,
                    onResizeEnd = ::endResize,
                    onResizeCancel = ::cancelResize,
                    modifier = Modifier
                        .offset { cardOffset }
                        .size(animW, animH)
                        .zIndex(if (active || settling) 10f else if (foregroundKey == key) 5f else 1f)
                )
            }
        }
    }
}

/**
 * 卡片偏移：手势进行中给非拖拽卡加一段弹簧补间，
 * 被挤走时是「弹」出去而不是瞬移；正在跟手的卡必须逐帧直给，否则会落后于手指。
 *
 * @param target 目标像素偏移
 * @param active 是否是当前正在拖拽 / 缩放的卡
 * @param gestureRunning 是否处于移动或缩放手势中
 * @return 实际用于布局的偏移
 */
@Composable
private fun rememberCardOffset(
    target: IntOffset,
    active: Boolean,
    gestureRunning: Boolean
): IntOffset {
    if (active || !gestureRunning) return target
    val animated by animateIntOffsetAsState(
        targetValue = target,
        animationSpec = CardMoveSpring,
        label = "cardOffset"
    )
    return animated
}

/**
 * 拖动时的 4 列细网格提示。
 *
 * @param cellWidth 单格宽度
 * @param spacing 间距
 * @param rows 行数
 * @param modifier 修饰
 */
@Composable
private fun GridGuides(
    cellWidth: Dp,
    spacing: Dp,
    rows: Int,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val step = (cellWidth + spacing).toPx()
        val cell = cellWidth.toPx()
        val color = Color.White.copy(alpha = 0.18f * alpha)
        for (row in 0 until rows) {
            for (col in 0..3) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(col * step, row * step),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    )
                )
            }
        }
    }
}

/**
 * 计划卡片的自由画布条目：背景、手势、计时按钮、缩放手柄与响应式内容。
 */
@Composable
private fun PlanBoardCard(
    data: DayPlanCardData,
    cols: Int,
    rows: Int,
    width: Dp,
    height: Dp,
    manageMode: Boolean,
    selected: Boolean,
    active: Boolean,
    onToggleTimer: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveStart: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onMoveEnd: () -> Unit,
    onMoveCancel: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = data.plan
    val base = parseThemeColor(plan.colorHex)
    val deep = Color(base.red * 0.58f, base.green * 0.58f, base.blue * 0.58f)
    val goalSec = plan.dailyGoalMinutes * 60L
    val percent = if (goalSec > 0) {
        (data.todaySec.toFloat() / goalSec.toFloat() * 100f).roundToInt().coerceAtLeast(0)
    } else {
        0
    }
    val remain = goalSec - data.todaySec
    val countdown = if (remain >= 0) "剩 ${clock(remain)}" else "超 ${clock(-remain)}"
    val shape = RoundedCornerShape(if (width < 100.dp || height < 100.dp) 16.dp else 20.dp)

    // 拿起时轻轻抬起来：放大一点点 + 一层投影，和手机桌面抓起图标的手感对齐
    val lift by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = CardLiftSpring,
        label = "lift"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = 1f + 0.035f * lift
                scaleY = 1f + 0.035f * lift
            }
            .shadow(elevation = 18.dp * lift, shape = shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, deep)))
            .border(
                width = if (selected) 2.dp else if (active) 1.5.dp else 1.dp,
                color = if (selected || active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.22f),
                shape = shape
            )
            .cardBodyGestures(
                key = plan.id,
                enabled = !manageMode,
                onClick = onClick,
                onLongClick = onLongClick,
                onMoveStart = onMoveStart,
                onMove = onMove,
                onMoveEnd = onMoveEnd,
                onMoveCancel = onMoveCancel
            )
    ) {
        // 玻璃拟态顶部高光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        PlanFace(
            plan = plan,
            data = data,
            cols = cols,
            rows = rows,
            percent = percent,
            countdown = countdown,
            manageMode = manageMode
        )

        if (!manageMode) {
            // 计时按钮：固定右上角，内容区已为它留出 34dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.26f))
                    .clickable(onClick = onToggleTimer)
            ) {
                Icon(
                    imageVector = if (data.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (data.running) "暂停计时" else "开始计时",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
            // 缩放手柄：直接拖动改大小，不再点开弹层
            ResizeGrip(
                onStart = onResizeStart,
                onDrag = onResize,
                onEnd = onResizeEnd,
                onCancel = onResizeCancel,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        } else {
            // 管理态：左上角勾选圈
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.24f))
                    .clickable(onClick = onClick)
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "已选择",
                        tint = base,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * 计划卡片的响应式正面内容。
 *
 * 根据实际宽高切换四档：极简（1×1）、横向（宽而扁）、纵向（窄而高）、完整（大卡）。
 * 所有文字和进度条都避开了右上角计时按钮与右下角缩放手柄。
 */
@Composable
private fun PlanFace(
    plan: PlanEntity,
    data: DayPlanCardData,
    cols: Int,
    rows: Int,
    percent: Int,
    countdown: String,
    manageMode: Boolean
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        // 以「占几列」而不是纯 dp 判断：1 列卡在宽屏上也可能有 130dp+
        val tiny = w < 100.dp && h < 100.dp
        val narrow = cols == 1 && rows >= 2
        val short = !narrow && rows == 1
        val goalLine = if (w >= 190.dp) {
            "今日 ${data.todaySec / 60} 分 · 目标 ${plan.dailyGoalMinutes} 分"
        } else {
            "${data.todaySec / 60} / ${plan.dailyGoalMinutes} 分"
        }
        val startPad = if (manageMode) 38.dp else 10.dp
        val endPad = if (manageMode) 8.dp else 38.dp
        val bottomPad = if (manageMode) 8.dp else 36.dp

        when {
            tiny -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 36.dp, top = 7.dp, bottom = 34.dp)
                ) {
                    Text(text = plan.icon, fontSize = 17.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$percent%",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            narrow -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = startPad,
                            end = if (manageMode) 8.dp else 10.dp,
                            top = 7.dp,
                            bottom = bottomPad
                        ),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(end = if (manageMode) 0.dp else 30.dp)) {
                        Text(text = plan.icon, fontSize = 20.sp)
                    }
                    Text(
                        text = plan.title,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (h >= 150.dp) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = if (manageMode) 0.dp else 32.dp)
                    )
                    if (data.paceText != null && !manageMode && h >= 126.dp) {
                        PaceChip(text = data.paceText)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = if (manageMode) 0.dp else 36.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (data.running) {
                            Text("计时中", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
                        }
                    }
                    Text(
                        text = countdown,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = if (manageMode) 0.dp else 36.dp)
                    )
                    ProgressBar(
                        percent = percent,
                        color = Color.White,
                        modifier = Modifier.padding(end = if (manageMode) 0.dp else 36.dp)
                    )
                    Text(
                        text = goalLine,
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 8.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = if (manageMode) 0.dp else 36.dp)
                    )
                    if (plan.note.isNotBlank() && h >= 238.dp) {
                        Text(
                            text = plan.note,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = if (manageMode) 0.dp else 36.dp)
                        )
                    }
                }
            }

            short -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = startPad, end = endPad, top = 7.dp, bottom = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = plan.icon, fontSize = 18.sp)
                        if (w >= 108.dp) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = plan.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = if (w >= 160.dp) 20.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (w >= 140.dp) {
                            Text(
                                text = if (data.running) "计时中" else countdown,
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                    if (h >= 88.dp && w >= 110.dp) {
                        ProgressBar(
                            percent = percent,
                            color = Color.White,
                            modifier = Modifier.padding(end = if (manageMode) 0.dp else 34.dp)
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = startPad,
                            end = if (manageMode) 8.dp else 12.dp,
                            top = 9.dp,
                            bottom = bottomPad
                        ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = if (manageMode) 0.dp else 34.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = plan.icon, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = plan.title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (h >= 170.dp) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (data.paceText != null && !manageMode && w >= 128.dp) {
                        PaceChip(text = data.paceText)
                    }
                    if (plan.note.isNotBlank() && h >= 160.dp) {
                        Text(
                            text = plan.note,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 9.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = if (manageMode) 0.dp else 36.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = if (w >= 210.dp) 30.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (data.running) "计时中" else countdown,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = goalLine,
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 8.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = if (manageMode) 0.dp else 36.dp)
                    )
                    ProgressBar(
                        percent = percent,
                        color = Color.White,
                        modifier = Modifier.padding(end = if (manageMode) 0.dp else 36.dp)
                    )
                }
            }
        }
    }
}

/**
 * 行程卡片的自由画布条目。
 */
@Composable
private fun TripBoardCard(
    trip: TripEntity,
    cols: Int,
    rows: Int,
    width: Dp,
    height: Dp,
    active: Boolean,
    onClick: () -> Unit,
    onMoveStart: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onMoveEnd: () -> Unit,
    onMoveCancel: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val base = parseThemeColor(trip.colorHex)
    val deep = Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f)
    val shape = RoundedCornerShape(if (width < 100.dp || height < 100.dp) 16.dp else 20.dp)
    val dateFormat = remember { SimpleDateFormat("M/d", Locale.CHINA) }
    val dateText = if (trip.startDate > 0L && trip.endDate > 0L) {
        "${dateFormat.format(Date(trip.startDate))}–${dateFormat.format(Date(trip.endDate))}"
    } else {
        ""
    }

    // 拿起时轻轻抬起来：放大一点点 + 一层投影，和手机桌面抓起图标的手感对齐
    val lift by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = CardLiftSpring,
        label = "lift"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = 1f + 0.035f * lift
                scaleY = 1f + 0.035f * lift
            }
            .shadow(elevation = 18.dp * lift, shape = shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, deep)))
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.22f),
                shape = shape
            )
            .cardBodyGestures(
                key = -trip.id,
                enabled = true,
                onClick = onClick,
                onLongClick = {},
                onMoveStart = onMoveStart,
                onMove = onMove,
                onMoveEnd = onMoveEnd,
                onMoveCancel = onMoveCancel
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent))
                )
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val tiny = w < 96.dp && h < 96.dp
            val short = h < 100.dp
            when {
                tiny -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = 34.dp, top = 7.dp, bottom = 30.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(text = trip.emoji, fontSize = 21.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = trip.title.take(4),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                short -> {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 10.dp, end = 38.dp, top = 7.dp, bottom = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = trip.emoji, fontSize = 19.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = trip.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (dateText.isNotEmpty() && w >= 130.dp) {
                                Text(
                                    text = dateText,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 34.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = trip.emoji, fontSize = 25.sp)
                        Text(
                            text = trip.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (h >= 160.dp) 3 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (dateText.isNotEmpty()) {
                            Text(
                                text = dateText,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "旅行规划",
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        ResizeGrip(
            onStart = onResizeStart,
            onDrag = onResize,
            onEnd = onResizeEnd,
            onCancel = onResizeCancel,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

/**
 * 卡片主体的长按拖动 + 轻点手势。
 *
 * 长按后移动超过阈值才开始真正移动；长按后原地松手仍然打开原来的操作菜单。
 * 用 suppressClick 避免长按松手时又触发一次轻点（统计弹层）。
 */
@Composable
private fun Modifier.cardBodyGestures(
    key: Long,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveStart: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onMoveEnd: () -> Unit,
    onMoveCancel: () -> Unit
): Modifier {
    if (!enabled) {
        return this.clickable(onClick = onClick)
    }
    val scope = rememberCoroutineScope()
    var suppressClick by remember(key) { mutableStateOf(false) }
    var moved by remember(key) { mutableStateOf(false) }
    // 同 ResizeGrip：手势块只跑一次，回调必须从最新组合里取
    val clickNow = rememberUpdatedState(onClick)
    val longClickNow = rememberUpdatedState(onLongClick)
    val moveStartNow = rememberUpdatedState(onMoveStart)
    val moveNow = rememberUpdatedState(onMove)
    val moveEndNow = rememberUpdatedState(onMoveEnd)
    val moveCancelNow = rememberUpdatedState(onMoveCancel)

    fun suppressBriefly() {
        suppressClick = true
        scope.launch {
            delay(320)
            suppressClick = false
        }
    }

    return this
        .clickable {
            if (!suppressClick) clickNow.value()
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    moved = false
                    suppressBriefly()
                    moveStartNow.value()
                },
                onDrag = { change, amount ->
                    change.consume()
                    if (abs(amount.x) + abs(amount.y) > 0.5f) moved = true
                    moveNow.value(amount.x, amount.y)
                },
                onDragEnd = {
                    if (moved) {
                        moveEndNow.value()
                    } else {
                        moveCancelNow.value()
                        longClickNow.value()
                    }
                    suppressBriefly()
                },
                onDragCancel = {
                    moveCancelNow.value()
                    suppressBriefly()
                }
            )
        }
}

/**
 * 右下角缩放按钮：拖动直接改大小。
 */
@Composable
private fun ResizeGrip(
    onStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // pointerInput(Unit) 只会跑一次，里面捕获的是**首次组合**的这组 lambda。
    // 不套 rememberUpdatedState 的话，松手时调用的仍是旧画布上的整理逻辑，
    // 它会以为"什么都没变"从而不落库——重叠就一直留在那儿。
    val startNow = rememberUpdatedState(onStart)
    val dragNow = rememberUpdatedState(onDrag)
    val endNow = rememberUpdatedState(onEnd)
    val cancelNow = rememberUpdatedState(onCancel)
    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = modifier
            // 只覆盖可见角标附近的一小块：56dp 时整张 1×1 卡的右下角都成了手柄，
            // 想点卡片本身反而点不到
            .size(44.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 手指按下就吃掉这个事件：卡身的「长按拖动」收不到干净的 down，
                    // 就不会在 500ms 后把缩放手势抢走——那正是"长按右下角拖不动"的由来。
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var started = false
                    var lifted = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            lifted = true
                            break
                        }
                        // 直接用坐标差：positionChange() 在事件被别人消费过之后会返回 0，
                        // 一旦上游有谁碰过这个事件，手柄就再也拖不动了。
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        if (dx != 0f || dy != 0f) {
                            // 真移动了才进入缩放态：轻点一下手柄不该闪出网格
                            if (!started) {
                                started = true
                                startNow.value()
                            }
                            change.consume()
                            dragNow.value(dx, dy)
                        }
                    }
                    if (started) {
                        if (lifted) endNow.value() else cancelNow.value()
                    }
                }
            }
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .padding(end = 5.dp, bottom = 5.dp)
                .size(30.dp)
        ) {
            val stroke = 4.dp.toPx()
            val inset = 3.dp.toPx()
            val radius = 7.dp.toPx()
            // 更醒目的白色直角括号，类似系统 Dock 右下角的白色角标
            val path = Path().apply {
                moveTo(size.width * 0.16f, size.height - inset)
                lineTo(size.width - inset - radius, size.height - inset)
                quadraticBezierTo(
                    size.width - inset,
                    size.height - inset,
                    size.width - inset,
                    size.height - inset - radius
                )
                lineTo(size.width - inset, size.height * 0.16f)
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.96f),
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * 小进度条。
 */
@Composable
private fun ProgressBar(percent: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.24f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color.copy(alpha = 0.95f))
        )
    }
}

/**
 * 节奏小胶囊。
 */
@Composable
private fun PaceChip(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 8.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 秒数格式化为 mm:ss（超过一小时 h:mm:ss）。
 */
private fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.US, "%02d:%02d", m, sec)
    }
}

/** 旧 cardSize 编码 → 列数 */
private fun legacyCols(code: Int): Int = when (code) {
    0 -> 1
    1 -> 1
    2 -> 2
    3 -> 2
    4 -> 1
    5 -> 3
    else -> 2
}

/** 旧 cardSize 编码 → 行数 */
private fun legacyRows(code: Int): Int = when (code) {
    0 -> 1
    1 -> 2
    2 -> 1
    3 -> 2
    4 -> 3
    5 -> 1
    else -> 2
}
