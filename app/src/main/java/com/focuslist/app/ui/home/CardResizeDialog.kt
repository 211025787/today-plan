package com.focuslist.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.ui.theme.parseThemeColor
import kotlin.math.roundToInt

/**
 * 卡片右下角的「调整大小」角标：白色四分之一圆弧，**紧贴卡片圆角边沿**。
 *
 * 日历卡片的圆角固定 22dp；当手柄右下角与卡片右下角重合时，圆心就在
 * `(size - 22dp)`，画出来这段 90° 弧正好压在圆角轮廓上，中间不留空白。
 * 描边 5dp（原来 2.5dp 的一倍），下面垫一圈淡深色让白色在浅背景上不糊。
 *
 * 卡片本身不再承担拖动逻辑——点它只是打开屏幕级的调整弹层，
 * 这样日历网格不会在拖动过程中重排（之前的闪退与乱跳都来自这里）。
 *
 * @param onClick 点击回调（进入调整弹层）
 * @param modifier 外部布局修饰
 */
@Composable
fun CardResizeHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = 22.dp.toPx()
            val cx = size.width - r
            val cy = size.height - r
            val bounds = Offset(cx - r, cy - r)
            val boundsSize = Size(r * 2f, r * 2f)
            drawArc(
                color = Color.Black.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = bounds,
                size = boundsSize,
                style = Stroke(7.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = bounds,
                size = boundsSize,
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 卡片大小调整弹层（屏幕级遮罩）。
 *
 * 为什么不在卡片里直接拖：卡片在 `LazyVerticalGrid` 里，改宽高要同时改 item 的 span，
 * 拖动过程中反复改 span 会让整个网格重排——眼睛看到的是「卡片乱跳」，
 * 严重时 LazyGrid 内部状态不一致直接闪退。这里把调整搬到屏幕级遮罩：
 * 网格完全不动，拖动只改预览矩形，点「完成」才写库并重排一次。
 *
 * @param title 卡片标题（预览里显示）
 * @param emoji 卡片图标
 * @param colorHex 卡片主题色
 * @param initial 当前档位
 * @param cellWidth 单格宽度（用于换算拖动距离与档位）
 * @param gridSpacing 网格间距（用于换算实际宽高）
 * @param onDismiss 取消
 * @param onConfirm 确认新档位
 */
@Composable
fun CardResizeDialog(
    title: String,
    emoji: String,
    colorHex: String,
    initial: PlanCardSize,
    cellWidth: Dp,
    gridSpacing: Dp,
    onDismiss: () -> Unit,
    onConfirm: (PlanCardSize) -> Unit
) {
    val density = LocalDensity.current
    val base = parseThemeColor(colorHex)
    val deep = Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f)

    /** 某档位的实际宽高（含间距） */
    fun widthOf(size: PlanCardSize): Dp = cellWidth * size.cols + gridSpacing * (size.cols - 1)
    fun heightOf(size: PlanCardSize): Dp = cellWidth * size.rows + gridSpacing * (size.rows - 1)

    var liveWidth by remember(initial) { mutableStateOf(widthOf(initial)) }
    var liveHeight by remember(initial) { mutableStateOf(heightOf(initial)) }
    val minSide = cellWidth * 0.8f
    val maxWidth = cellWidth * 3 + gridSpacing * 2
    val maxHeight = cellWidth * 3 + gridSpacing * 2

    // 当前预览对应的档位（四舍五入到最近格）
    val preview = PlanCardSize.fromColsRows(
        cols = (liveWidth / cellWidth).roundToInt().coerceIn(1, 3),
        rows = (liveHeight / cellWidth).roundToInt().coerceIn(1, 3)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // 面板内部吞掉点击：否则点面板空白会穿透到遮罩把弹层关掉
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "调整卡片大小",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${preview.label} · 拖动右下角的手柄",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // 预览区：固定高度，卡片在中间按当前宽高实时变化
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxHeight + 24.dp)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(liveWidth)
                        .height(liveHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(base, deep)))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (preview == PlanCardSize.SMALL) 10.dp else 14.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = emoji, fontSize = if (preview == PlanCardSize.SMALL) 20.sp else 28.sp)
                        if (preview != PlanCardSize.SMALL) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // 右下角拖动手柄
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(56.dp)
                            .pointerInput(initial) {
                                detectDragGestures(
                                    onDrag = { change, amount ->
                                        change.consume()
                                        liveWidth = (liveWidth + with(density) { amount.x.toDp() })
                                            .coerceIn(minSide, maxWidth)
                                        liveHeight = (liveHeight + with(density) { amount.y.toDp() })
                                            .coerceIn(minSide, maxHeight)
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.size(30.dp)) {
                            val stroke = 3.dp.toPx()
                            val arcLen = size.width * 0.7f
                            drawArc(
                                color = Color.White,
                                startAngle = 0f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
                                size = Size(arcLen, arcLen),
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // 快捷档位
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlanCardSize.entries.forEach { size ->
                    val selected = size == preview
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable {
                                liveWidth = widthOf(size)
                                liveHeight = heightOf(size)
                            }
                            .padding(vertical = 7.dp)
                    ) {
                        Text(
                            text = size.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResizeAction(text = "取消", filled = false, onClick = onDismiss)
                ResizeAction(text = "完成", filled = true, onClick = { onConfirm(preview) })
            }
        }
    }
}

/**
 * 弹层底部按钮。
 *
 * @param text 文案
 * @param filled 是否主色填充
 * @param onClick 点击回调
 */
@Composable
private fun ResizeAction(text: String, filled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) scheme.onPrimary else scheme.onSurface
        )
    }
}
