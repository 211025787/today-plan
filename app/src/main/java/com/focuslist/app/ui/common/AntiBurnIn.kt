package com.focuslist.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 防烧屏漂移修饰符：计时中让内容在若干 dp 范围内极缓慢游走，静态像素不会长时间停在同一位置。
 *
 * 关键交互特性：
 * - **不会跳变**：内部维护一个从 (0,0) 开始的连续位移，激活后从当前位置平滑出发；
 * - **暂停即冻结**：计时暂停时动画停止，内容保持暂停那一刻的位置；
 * - **续跑无跳变**：再次开始时从冻结点继续移动，不会回弹左上角。
 *
 * 目标点按黄金角步进分布，轨迹长时间不重复，肉眼几乎察觉不到内容在动。
 *
 * @param active 是否启用（传入"正在计时"）
 * @param range 漂移半径，默认 6dp
 * @return 修饰符
 */
@Composable
fun Modifier.antiBurnInDrift(active: Boolean, range: Dp = 6.dp): Modifier {
    val px = with(LocalDensity.current) { range.toPx() }
    // 连续位移量（像素）：remember 保证暂停/继续之间保持当前值
    val drift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    LaunchedEffect(active) {
        if (!active) {
            // 暂停：直接返回，位移停在当前位置，不再移动
            return@LaunchedEffect
        }
        var index = 0
        while (true) {
            index++
            // 黄金角步进：相邻目标点方向不重复，轨迹自然散开
            val angle = index * 2.39996f
            val target = Offset(
                x = cos(angle) * px * 0.85f,
                y = sin(angle) * px * 0.85f
            )
            // 单段 11 秒匀速缓行：位移变化率极低，看不出"在动"但像素一直在变
            drift.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 11000, easing = LinearEasing)
            )
        }
    }

    // offset 只影响绘制位置，不触发重新测量
    return this.offset {
        IntOffset(drift.value.x.roundToInt(), drift.value.y.roundToInt())
    }
}

/**
 * 防烧屏呼吸光晕：三个低透明度色斑沿不同圆周缓慢游走，整屏大面积像素持续微变，
 * 既起到防烧屏作用，也让专注画面更"活"。
 *
 * @param modifier 布局修饰符
 * @param alpha 光斑透明度，默认 0.10
 */
@Composable
fun AntiBurnInGlow(modifier: Modifier = Modifier, alpha: Float = 0.10f) {
    val transition = rememberInfiniteTransition(label = "burnInGlow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "glowPhase"
    )
    val primary = MaterialTheme.colorScheme.primary
    val amber = Color(0xFFFFB300)
    val violet = Color(0xFF8E44AD)
    Canvas(modifier = modifier.fillMaxSize()) {
        val angle = t * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        // 三个光斑各自沿椭圆轨迹缓慢漂移，产生持续的明暗流动
        drawGlow(
            color = primary,
            center = Offset(w * (0.30f + 0.18f * cos(angle)), h * (0.22f + 0.10f * sin(angle))),
            radius = w * 0.65f,
            alpha = alpha
        )
        drawGlow(
            color = amber,
            center = Offset(w * (0.76f + 0.14f * cos(angle + 2.1f)), h * (0.48f + 0.16f * sin(angle + 2.1f))),
            radius = w * 0.55f,
            alpha = alpha * 0.8f
        )
        drawGlow(
            color = violet,
            center = Offset(w * (0.45f + 0.20f * cos(angle + 4.2f)), h * (0.82f + 0.10f * sin(angle + 4.2f))),
            radius = w * 0.60f,
            alpha = alpha * 0.7f
        )
    }
}

/**
 * 绘制单个柔光斑（径向渐变到透明）。
 *
 * @param color 光斑颜色
 * @param center 圆心
 * @param radius 半径
 * @param alpha 中心透明度
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(
    color: Color,
    center: Offset,
    radius: Float,
    alpha: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
