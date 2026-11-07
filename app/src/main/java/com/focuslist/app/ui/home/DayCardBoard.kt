package com.focuslist.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    // 已有的非六种规格也一并写回最近的合法档位。
    val autoPlaced = remember { mutableSetOf<String>() }
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
        // 按当前顺序逐张"落位"，后到的卡若压到前面的卡就挪到最近空位，一次修好；
        // 不修的话，这些卡既缩不大（找不到不重叠的规格），也会继续出现"点错卡"。
        val settled = mutableListOf<CardBox>()
        plans.forEachIndexed { index, data ->
            val box = planBoxes.getOrNull(index) ?: return@forEachIndexed
            val target = if (CardBoard.fitsAt(settled, box)) {
                box
            } else {
                CardBoard.resolveMove(settled, box, box.x, box.y)
            }
            // 修复标记要带上目标坐标：同一张卡在一局里可能被压到好几次
            // （缩一次、再缩一次），只按 id 去重会让第二次起再也修不动。
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

    /**
     * 松手收尾：锚点卡落定后交给 [CardBoard.arrange] 自动整理，
     * 被它压到的卡会被挪到最近的空位——整块板子永远不会留下重叠。
     *
     * 基底用「乐观值优先」的当前画面，而不是数据库里的 [packed]：
     * 缩放时屏幕上已经被挤走的卡就在这些乐观值里，跟着它整理才能做到
     * 「松手就是眼前这个画面」，不会先弹回旧位置再挪一次。
     */
    fun settle(anchor: CardBox) {
        val base = packed.map { pending[boxKeyOf(it)] ?: it }
        val arranged = CardBoard.arrange(base, anchor)
        val changed = arranged.indices.filter { arranged[it] != base[it] }
        if (changed.isEmpty()) return
        val anchorId = boxKeyOf(anchor)
        // 先写被挤走的卡，最后写锚点卡：锚点拿到最新时间戳，整理完仍在最上层
        changed.filter { boxKeyOf(arranged[it]) != anchorId }
            .forEach { commitOne(arranged[it], zOf(arranged[it])) }
        changed.firstOrNull { boxKeyOf(arranged[it]) == anchorId }
            ?.let { commitOne(arranged[it], System.currentTimeMillis()) }
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
    // 实时整理：拿这个规格当锚点跑一遍 [CardBoard.arrange]，被压到的卡当场挪到最近空位。
    // 只用于渲染、不落库；松手时 [settle] 用同一套算法和同一个规格，
    // 所以手指停在哪儿、松手就是哪儿，不会出现「松手才突然挪开」的跳变。
    val pushed: Map<String, CardBox> = if (resizeAnchor == null) {
        emptyMap()
    } else {
        val base = packed.map { pending[boxKeyOf(it)] ?: it }
        val arranged = CardBoard.arrange(base, resizeAnchor)
        val anchorId = boxKeyOf(resizeAnchor)
        val out = HashMap<String, CardBox>()
        base.forEachIndexed { index, box ->
            val id = boxKeyOf(box)
            if (id != anchorId && arranged[index] != box) out[id] = arranged[index]
        }
        out
    }

    fun startMove(box: CardBox) {
        if (manageMode) return
        foregroundKey = boxKeyOf(box)
        activeKey = boxKeyOf(box)
        gesture = BoardGesture.MOVE
        activeStart = box
        dragX = 0f
        dragY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun startResize(box: CardBox) {
        if (manageMode) return
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

    fun endMove() {
        val start = activeStart
        if (gesture == BoardGesture.MOVE && start != null) {
            val nx = ((start.x * stepPx + dragX) / stepPx).roundToInt()
                .coerceIn(0, CardBoard.COLUMNS - start.cols)
            val ny = ((start.y * stepPx + dragY) / stepPx).roundToInt().coerceAtLeast(0)
            settle(start.copy(x = nx, y = ny))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        resetGesture()
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

    // 缩放时画布只需要容纳「吸附后的规格」：用连续像素算会多出一行空白，
    // 整页高度会跟着手指呼吸
    val activeResizeBottomPx = if (resizeShape != null && resizeBase != null) {
        (resizeBase.y + resizeShape.rows) * stepPx
    } else {
        0f
    }
    val boardRows = maxOf(
        CardBoard.canvasRows(packed),
        ceil(activeResizeBottomPx / stepPx).toInt(),
        // 被挤出去的卡可能落到更下面，画布得跟着长，否则会贴边被裁掉
        pushed.values.maxOfOrNull { it.y + it.rows } ?: 0,
        1
    )
    val boardHeight = cellWidth * boardRows + spacing * (boardRows - 1)

    Box(modifier = modifier.fillMaxWidth().height(boardHeight)) {
        // 细网格提示：拖动 / 缩放时才显示，方便对齐
        if (gesture != BoardGesture.NONE) {
            GridGuides(
                cellWidth = cellWidth,
                spacing = spacing,
                rows = boardRows,
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
            val resizing = active && gesture == BoardGesture.RESIZE
            val faceCols = if (resizing) resizeShape?.cols ?: shown.cols else shown.cols
            val faceRows = if (resizing) resizeShape?.rows ?: shown.rows else shown.rows
            val xPx = shown.x * stepPx + if (active && gesture == BoardGesture.MOVE) dragX else 0f
            val yPx = shown.y * stepPx + if (active && gesture == BoardGesture.MOVE) dragY else 0f
            val w = cellWidth * faceCols + spacing * (faceCols - 1)
            val h = cellWidth * faceRows + spacing * (faceRows - 1)
            // 尺寸只做 90ms 补间：跨界时和邻居一起挪，不会一格一格地"哆嗦"
            val animW by animateDpAsState(
                targetValue = w,
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
                label = "cardW"
            )
            val animH by animateDpAsState(
                targetValue = h,
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
                label = "cardH"
            )
            // 被挤走的卡用 150ms 补间滑过去，正在跟手的那张逐帧直给
            val cardOffset = rememberCardOffset(
                target = IntOffset(xPx.roundToInt(), yPx.roundToInt()),
                active = active,
                gestureRunning = gesture != BoardGesture.NONE
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
                    onMoveCancel = ::resetGesture,
                    onResizeStart = { startResize(shown) },
                    onResize = ::resizeBy,
                    onResizeEnd = ::endResize,
                    onResizeCancel = ::resetGesture,
                    modifier = Modifier
                        .offset { cardOffset }
                        .size(animW, animH)
                        .zIndex(if (active) 10f else if (foregroundKey == key) 5f else 1f)
                )
            }
        }

        trips.indices.sortedBy { trips[it].trip.cardZ }.forEach { index ->
            val data = trips[index]
            val box = tripBoxes.getOrNull(index) ?: return@forEach
            val key = "t${data.trip.id}"
            val shown = pushed[key] ?: pending[key] ?: box
            val active = activeKey == key
            val resizing = active && gesture == BoardGesture.RESIZE
            val faceCols = if (resizing) resizeShape?.cols ?: shown.cols else shown.cols
            val faceRows = if (resizing) resizeShape?.rows ?: shown.rows else shown.rows
            val xPx = shown.x * stepPx + if (active && gesture == BoardGesture.MOVE) dragX else 0f
            val yPx = shown.y * stepPx + if (active && gesture == BoardGesture.MOVE) dragY else 0f
            val w = cellWidth * faceCols + spacing * (faceCols - 1)
            val h = cellWidth * faceRows + spacing * (faceRows - 1)
            // 尺寸只做 90ms 补间：跨界时和邻居一起挪，不会一格一格地"哆嗦"
            val animW by animateDpAsState(
                targetValue = w,
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
                label = "cardW"
            )
            val animH by animateDpAsState(
                targetValue = h,
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
                label = "cardH"
            )
            val cardOffset = rememberCardOffset(
                target = IntOffset(xPx.roundToInt(), yPx.roundToInt()),
                active = active,
                gestureRunning = gesture != BoardGesture.NONE
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
                    onMoveCancel = ::resetGesture,
                    onResizeStart = { startResize(shown) },
                    onResize = ::resizeBy,
                    onResizeEnd = ::endResize,
                    onResizeCancel = ::resetGesture,
                    modifier = Modifier
                        .offset { cardOffset }
                        .size(animW, animH)
                        .zIndex(if (active) 10f else if (foregroundKey == key) 5f else 1f)
                )
            }
        }
    }
}

/**
 * 卡片偏移：手势进行中给非拖拽卡加一段 150ms 补间，
 * 被挤走时是「滑」出去而不是瞬移；正在跟手的卡必须逐帧直给，否则会落后于手指。
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
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
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
private fun GridGuides(cellWidth: Dp, spacing: Dp, rows: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val step = (cellWidth + spacing).toPx()
        val cell = cellWidth.toPx()
        val color = Color.White.copy(alpha = 0.18f)
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

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (active) 1.02f else 1f
                scaleY = if (active) 1.02f else 1f
            }
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

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (active) 1.02f else 1f
                scaleY = if (active) 1.02f else 1f
            }
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
            .size(40.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        startNow.value()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragNow.value(amount.x, amount.y)
                    },
                    onDragEnd = {
                        endNow.value()
                    },
                    onDragCancel = {
                        cancelNow.value()
                    }
                )
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
