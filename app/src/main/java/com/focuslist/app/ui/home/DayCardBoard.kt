package com.focuslist.app.ui.home

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
 * - 长按卡片进入拖动，松手吸附到最近的格子；
 * - 右下角手柄直接拖动改大小，不再弹「选尺寸」弹层；
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
    onPlanLayout: (Long, Int, Int, Int, Int) -> Unit,
    onTripLayout: (Long, Int, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val step = cellWidth + spacing
    val stepPx = with(density) { step.toPx() }

    // 数据库里的尺寸/坐标 → 算法占位。cardCols/cardRows 为 0 的旧数据用 cardSize 兜底；
    // 非六种规格（例如之前拖出的 4×1 / 3×2）在这里归一化到最近的合法档位。
    val boxes = remember(plans, trips) {
        buildList {
            plans.forEach { data ->
                val plan = data.plan
                val shape = CardBoard.allowedShape(
                    if (plan.cardCols > 0) plan.cardCols else legacyCols(plan.cardSize),
                    if (plan.cardRows > 0) plan.cardRows else legacyRows(plan.cardSize)
                )
                add(
                    CardBox(
                        key = plan.id,
                        trip = false,
                        cols = shape.cols,
                        rows = shape.rows,
                        x = plan.cardX,
                        y = plan.cardY
                    )
                )
            }
            trips.forEach { data ->
                val trip = data.trip
                val shape = CardBoard.allowedShape(
                    if (trip.cardCols > 0) trip.cardCols else legacyCols(trip.cardSize),
                    if (trip.cardRows > 0) trip.cardRows else legacyRows(trip.cardSize)
                )
                add(
                    CardBox(
                        key = trip.id,
                        trip = true,
                        cols = shape.cols,
                        rows = shape.rows,
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
                    onPlanLayout(plan.id, box.cols, box.rows, box.x, box.y)
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
                    onTripLayout(trip.id, box.cols, box.rows, box.x, box.y)
                }
            }
        }
    }

    // 当前手势：只允许一张卡同时被拖动/缩放
    var activeKey by remember { mutableStateOf<String?>(null) }
    var gesture by remember { mutableStateOf(BoardGesture.NONE) }
    var activeStart by remember { mutableStateOf<CardBox?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    // 缩放中的实时像素宽高：跟手连续变化，松手才吸附到六种规格
    var liveWidthPx by remember { mutableFloatStateOf(0f) }
    var liveHeightPx by remember { mutableFloatStateOf(0f) }
    var startWidthPx by remember { mutableFloatStateOf(0f) }
    var startHeightPx by remember { mutableFloatStateOf(0f) }
    // 最近操作的卡片置顶：缩放后压在邻居上时，用户仍能完整看到自己刚调整的卡
    var foregroundKey by remember { mutableStateOf<String?>(null) }
    val spacingPx = with(density) { spacing.toPx() }

    fun commit(box: CardBox) {
        if (box.trip) {
            onTripLayout(box.key, box.cols, box.rows, box.x, box.y)
        } else {
            onPlanLayout(box.key, box.cols, box.rows, box.x, box.y)
        }
    }

    fun startMove(box: CardBox) {
        if (manageMode) return
        foregroundKey = if (box.trip) "t${box.key}" else "p${box.key}"
        activeKey = if (box.trip) "t${box.key}" else "p${box.key}"
        gesture = BoardGesture.MOVE
        activeStart = box
        dragX = 0f
        dragY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun startResize(box: CardBox) {
        if (manageMode) return
        foregroundKey = if (box.trip) "t${box.key}" else "p${box.key}"
        activeKey = if (box.trip) "t${box.key}" else "p${box.key}"
        gesture = BoardGesture.RESIZE
        activeStart = box
        dragX = 0f
        dragY = 0f
        startWidthPx = box.cols * stepPx - spacingPx
        startHeightPx = box.rows * stepPx - spacingPx
        liveWidthPx = startWidthPx
        liveHeightPx = startHeightPx
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
        // 连续跟手：卡片的右下角跟着手指走，不做整格吸附；
        // 最小 1×1，最大 3 列 / 3 行，右边界限制在当前行剩余列数内。
        val minWidth = stepPx - spacingPx
        val maxWidth = minOf(3, CardBoard.COLUMNS - start.x) * stepPx - spacingPx
        val maxHeight = 3 * stepPx - spacingPx
        liveWidthPx = (startWidthPx + dragX).coerceIn(minWidth, maxWidth)
        liveHeightPx = (startHeightPx + dragY).coerceIn(minWidth, maxHeight)
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
            // 松手时如果目标压到别人，自动吸到最近的空位
            commit(CardBoard.resolveMove(packed, start, nx, ny))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        resetGesture()
    }

    fun endResize() {
        val start = activeStart
        if (gesture == BoardGesture.RESIZE && start != null) {
            // 松手才把实时宽高吸附到允许的六种规格
            val cols = (liveWidthPx + spacingPx) / stepPx
            val rows = (liveHeightPx + spacingPx) / stepPx
            val shape = CardBoard.nearestShape(cols, rows)
            commit(CardBoard.resizeToAllowed(start, shape))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        resetGesture()
    }

    val activeResizeBottomPx = if (gesture == BoardGesture.RESIZE && activeStart != null) {
        activeStart!!.y * stepPx + liveHeightPx + spacingPx
    } else {
        0f
    }
    val boardRows = maxOf(
        CardBoard.canvasRows(packed),
        ceil(activeResizeBottomPx / stepPx).toInt(),
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

        plans.forEachIndexed { index, data ->
            val box = planBoxes.getOrNull(index) ?: return@forEachIndexed
            val key = "p${data.plan.id}"
            val active = activeKey == key
            val resizing = active && gesture == BoardGesture.RESIZE
            val faceCols = if (resizing) {
                ((liveWidthPx + spacingPx) / stepPx).roundToInt().coerceIn(1, 3)
            } else {
                box.cols
            }
            val faceRows = if (resizing) {
                ((liveHeightPx + spacingPx) / stepPx).roundToInt().coerceIn(1, 3)
            } else {
                box.rows
            }
            val xPx = box.x * stepPx + if (active && gesture == BoardGesture.MOVE) dragX else 0f
            val yPx = box.y * stepPx + if (active && gesture == BoardGesture.MOVE) dragY else 0f
            val w = if (resizing) {
                with(density) { liveWidthPx.toDp() }
            } else {
                cellWidth * box.cols + spacing * (box.cols - 1)
            }
            val h = if (resizing) {
                with(density) { liveHeightPx.toDp() }
            } else {
                cellWidth * box.rows + spacing * (box.rows - 1)
            }
            PlanBoardCard(
                data = data,
                cols = faceCols,
                rows = faceRows,
                width = w,
                height = h,
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
                onMoveStart = { startMove(box) },
                onMove = ::moveBy,
                onMoveEnd = ::endMove,
                onMoveCancel = ::resetGesture,
                onResizeStart = { startResize(box) },
                onResize = ::resizeBy,
                onResizeEnd = ::endResize,
                onResizeCancel = ::resetGesture,
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .size(w, h)
                    .zIndex(if (active) 10f else if (foregroundKey == key) 5f else 1f)
            )
        }

        trips.forEachIndexed { index, data ->
            val box = tripBoxes.getOrNull(index) ?: return@forEachIndexed
            val key = "t${data.trip.id}"
            val active = activeKey == key
            val resizing = active && gesture == BoardGesture.RESIZE
            val faceCols = if (resizing) {
                ((liveWidthPx + spacingPx) / stepPx).roundToInt().coerceIn(1, 3)
            } else {
                box.cols
            }
            val faceRows = if (resizing) {
                ((liveHeightPx + spacingPx) / stepPx).roundToInt().coerceIn(1, 3)
            } else {
                box.rows
            }
            val xPx = box.x * stepPx + if (active && gesture == BoardGesture.MOVE) dragX else 0f
            val yPx = box.y * stepPx + if (active && gesture == BoardGesture.MOVE) dragY else 0f
            val w = if (resizing) {
                with(density) { liveWidthPx.toDp() }
            } else {
                cellWidth * box.cols + spacing * (box.cols - 1)
            }
            val h = if (resizing) {
                with(density) { liveHeightPx.toDp() }
            } else {
                cellWidth * box.rows + spacing * (box.rows - 1)
            }
            TripBoardCard(
                trip = data.trip,
                cols = faceCols,
                rows = faceRows,
                width = w,
                height = h,
                active = active,
                onClick = {
                    foregroundKey = key
                    onTripClick(data.trip)
                },
                onMoveStart = { startMove(box) },
                onMove = ::moveBy,
                onMoveEnd = ::endMove,
                onMoveCancel = ::resetGesture,
                onResizeStart = { startResize(box) },
                onResize = ::resizeBy,
                onResizeEnd = ::endResize,
                onResizeCancel = ::resetGesture,
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .size(w, h)
                    .zIndex(if (active) 10f else if (foregroundKey == key) 5f else 1f)
            )
        }
    }
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

    fun suppressBriefly() {
        suppressClick = true
        scope.launch {
            delay(320)
            suppressClick = false
        }
    }

    return this
        .clickable {
            if (!suppressClick) onClick()
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    moved = false
                    suppressBriefly()
                    onMoveStart()
                },
                onDrag = { change, amount ->
                    change.consume()
                    if (abs(amount.x) + abs(amount.y) > 0.5f) moved = true
                    onMove(amount.x, amount.y)
                },
                onDragEnd = {
                    if (moved) {
                        onMoveEnd()
                    } else {
                        onMoveCancel()
                        onLongClick()
                    }
                    suppressBriefly()
                },
                onDragCancel = {
                    onMoveCancel()
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
    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        onStart()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.x, amount.y)
                    },
                    onDragEnd = {
                        onEnd()
                    },
                    onDragCancel = {
                        onCancel()
                    }
                )
            }
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .padding(end = 5.dp, bottom = 5.dp)
                .size(30.dp)
        ) {
            val stroke = 4.5.dp.toPx()
            // 更醒目的圆角括号，类似系统 Dock 右下角的白色角标
            drawArc(
                color = Color.White.copy(alpha = 0.96f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawLine(
                color = Color.White.copy(alpha = 0.75f),
                start = Offset(size.width * 0.56f, size.height * 0.9f),
                end = Offset(size.width * 0.9f, size.height * 0.56f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
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
