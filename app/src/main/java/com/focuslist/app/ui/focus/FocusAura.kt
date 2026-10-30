package com.focuslist.app.ui.focus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 光晕同心弧线的圈数 */
private const val GROOVE_COUNT = 42

/**
 * 计时器光环（唱片式光晕）：
 *
 * - 由数十圈长短不一的极细同心弧组成，透明度沿半径呈波浪变化，形成"唱片纹路"般的柔和层次；
 * - 整组弧线以 26 秒一圈极慢自转，弧线长短交替，转动时产生细碎的高光流动；
 * - 三条宽幅柔光带以相反方向缓慢扫过，模拟光线掠过唱片表面的质感；
 * - 外层叠加大范围径向柔光，让光环边缘自然晕开、与背景融为一体；
 * - 整体随 7 秒呼吸曲线轻微缩放，像呼吸一样起伏；
 * - 休息阶段整体换成更冷的色调与更慢的转速节奏。
 *
 * 未在计时时只绘制一层静态柔光，不启动任何动画，避免持续占用帧。
 *
 * @param active 是否正在计时
 * @param resting 是否处于休息阶段（冷色调）
 * @param modifier 布局修饰符（建议传入略大于计时环的尺寸，让光晕向外晕开）
 */
@Composable
fun TimerAura(active: Boolean, resting: Boolean = false, modifier: Modifier = Modifier) {
    val brand = MaterialTheme.colorScheme.primary
    // 主光晕：主题色；休息阶段用更冷的青蓝色调
    val halo = if (resting) Color(0xFF56C7D8) else brand
    // 扫光用的辅助色：偏紫的柔光（呼应唱片表面的高光反射）
    val sheen = if (resting) Color(0xFF9AD9E4) else Color(0xFF9B8CFF)

    if (!active) {
        // 未计时：静态光环，不跑动画
        Canvas(modifier = modifier) { drawStaticHalo(halo) }
        return
    }

    val transition = rememberInfiniteTransition(label = "timerAura")
    // 自转：26 秒一圈（休息时更慢，通过 breathing 换算）
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (resting) 40000 else 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    // 呼吸缩放：7 秒一次起伏；起始值取 0.5 让开场正好是 1.0 倍，避免开始时闪缩
    val breath by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    // 扫光偏移：与自转方向相反
    val sheenPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "sheen"
    )

    Canvas(modifier = modifier.scale(0.98f + 0.04f * breath)) {
        val c = center
        val radius = size.minDimension / 2f

        // 1. 外层径向柔光：中心略浓，向外自然淡出
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.28f to halo.copy(alpha = 0.20f),
                    0.62f to halo.copy(alpha = 0.10f),
                    1f to Color.Transparent
                ),
                center = c,
                radius = radius
            ),
            radius = radius,
            center = c
        )

        // 2. 同心弧线：长短交替 + 透明度波浪，整体缓慢自转
        rotate(degrees = spin) {
            repeat(GROOVE_COUNT) { index ->
                val t = index / (GROOVE_COUNT - 1f)
                // 半径由内向外铺开，留出中心计时区域
                val r = radius * (0.34f + 0.64f * t)
                // 透明度沿半径做两段波浪，模拟唱片纹路明暗
                val wave = (sin(t * 3.1f * PI.toFloat()) * 0.5f + 0.5f)
                val alpha = (0.045f + 0.075f * wave) * (1f - 0.35f * t)
                // 弧线长度与起始角彼此错开，转动时形成流动高光
                val sweep = 150f + 205f * ((sin(index * 1.7f) * 0.5f + 0.5f))
                val start = index * 137f % 360f
                drawArc(
                    color = halo.copy(alpha = alpha),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = (0.7f + 0.9f * t) * density)
                )
            }
        }

        // 3. 三条宽幅柔光带：反向慢扫，叠三层描边模拟辉光
        rotate(degrees = -sheenPhase * 360f) {
            repeat(3) { index ->
                val startAngle = index * 120f + sheenPhase * 60f
                val sweepAngle = 46f
                val bandAlpha = 0.05f + 0.03f * sin(sheenPhase * 2f * PI.toFloat() + index)
                // 由宽到窄三层叠加，做出边缘虚化的光带
                listOf(26f to 0.5f, 14f to 0.7f, 6f to 1f).forEach { (widthDp, factor) ->
                    drawArc(
                        color = sheen.copy(alpha = bandAlpha * factor),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(c.x - radius * 0.86f, c.y - radius * 0.86f),
                        size = Size(radius * 1.72f, radius * 1.72f),
                        style = Stroke(width = widthDp * density)
                    )
                }
            }
        }
    }
}

/**
 * 绘制静态光环（未计时）：一层径向柔光 + 一组低透明度同心弧，无动画。
 *
 * @param halo 主光源颜色
 */
private fun DrawScope.drawStaticHalo(halo: Color) {
    val c = center
    val radius = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.28f to halo.copy(alpha = 0.14f),
                0.62f to halo.copy(alpha = 0.06f),
                1f to Color.Transparent
            ),
            center = c,
            radius = radius
        ),
        radius = radius,
        center = c
    )
    repeat(GROOVE_COUNT) { index ->
        val t = index / (GROOVE_COUNT - 1f)
        val r = radius * (0.34f + 0.64f * t)
        val wave = (sin(t * 3.1f * PI.toFloat()) * 0.5f + 0.5f)
        drawArc(
            color = halo.copy(alpha = (0.03f + 0.05f * wave) * (1f - 0.35f * t)),
            startAngle = index * 137f % 360f,
            sweepAngle = 150f + 205f * ((sin(index * 1.7f) * 0.5f + 0.5f)),
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = (0.7f + 0.9f * t) * density)
        )
    }
}

/**
 * 全屏氛围底光：随白噪音模式呈现不同光场，让三种模式一眼可辨。
 *
 * - NONE（静音）：主题色柔光缓慢呼吸，最安静；
 * - WHITE（白噪）：冷调蓝白柔光 + 细碎光尘飘动，像悬浮的微尘；
 * - BROWN（棕噪）：暖调琥珀柔光 + 底部缓慢起伏的波浪，像灯光下的声浪。
 *
 * 未计时时保持静态版本（仍能区分三种模式），不启动动画。
 *
 * @param active 是否正在计时（决定是否播放动画）
 * @param noiseType 白噪音模式：NONE / WHITE / BROWN
 * @param modifier 布局修饰符
 */
@Composable
fun FocusBackdrop(active: Boolean, noiseType: String, modifier: Modifier = Modifier) {
    val brand = MaterialTheme.colorScheme.primary
    // 三种模式各自的光场色调
    val glow = when (noiseType) {
        "WHITE" -> Color(0xFF6FA8FF)
        "BROWN" -> Color(0xFFD79A5B)
        else -> brand
    }
    // 光尘固定分布（remember 保证不逐帧抖动）
    val dust = remember {
        val random = kotlin.random.Random(7)
        List(34) {
            Triple(random.nextFloat(), random.nextFloat(), 1.2f + random.nextFloat() * 2.6f)
        }
    }

    if (!active) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawGlow(glow, Offset(w * 0.5f, h * 0.40f), size.minDimension * 0.78f, 0.05f)
            when (noiseType) {
                "WHITE" -> drawDust(dust, progress = 0.25f, color = Color.White)
                "BROWN" -> drawWaves(phase = 0f, color = glow)
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "focusBackdrop")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdropBreath"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(34000, easing = LinearEasing), RepeatMode.Restart),
        label = "backdropDrift"
    )
    // 光尘/波浪的持续推进量（0-1 循环）
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "backdropFlow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val base = minOf(w, h)
        // 主光晕：呼吸明暗 + 极缓慢上下浮动
        drawGlow(
            color = glow,
            center = Offset(w * 0.5f, h * (0.40f + 0.05f * sin(drift))),
            radius = base * (0.74f + 0.08f * breath),
            alpha = 0.05f + 0.035f * breath
        )
        // 副光晕：沿椭圆绕行，让大面积亮度持续流动
        drawGlow(
            color = if (noiseType == "BROWN") Color(0xFFFFC48A) else Color(0xFF9B8CFF),
            center = Offset(
                w * 0.5f + cos(drift) * w * 0.26f,
                h * 0.52f + sin(drift * 0.7f) * h * 0.12f
            ),
            radius = base * 0.44f,
            alpha = 0.045f
        )
        when (noiseType) {
            "WHITE" -> drawDust(dust, progress = flow, color = Color.White)
            "BROWN" -> drawWaves(phase = flow * 2f * PI.toFloat(), color = glow)
        }
    }
}

/**
 * 绘制漂浮光尘：细小白点缓慢左移并闪烁。
 *
 * @param dust 光尘数据（x, y 归一化坐标, 半径）
 * @param progress 推进量 0-1（循环）
 * @param color 光尘颜色
 */
private fun DrawScope.drawDust(
    dust: List<Triple<Float, Float, Float>>,
    progress: Float,
    color: Color
) {
    val w = size.width
    val h = size.height
    dust.forEach { (x0, y0, r) ->
        val x = ((x0 - progress * 0.55f) % 1f + 1f) % 1f * w
        val y = (y0 + 0.04f * sin((progress + x0) * 2f * PI.toFloat())) * h
        // 闪烁：亮度随时间与自身相位起伏
        val alpha = 0.10f + 0.22f * (0.5f + 0.5f * sin((progress * 3f + y0) * 2f * PI.toFloat()))
        drawCircle(color = color.copy(alpha = alpha), radius = r * density, center = Offset(x, y))
    }
}

/**
 * 绘制底部声浪：三层正弦波缓慢起伏并横向流动。
 *
 * @param phase 相位（随时间推进）
 * @param color 波浪颜色
 */
private fun DrawScope.drawWaves(phase: Float, color: Color) {
    val w = size.width
    val h = size.height
    val layers = listOf(
        Triple(0.78f, 0.045f, 0.16f),
        Triple(0.86f, 0.035f, 0.12f),
        Triple(0.94f, 0.028f, 0.08f)
    )
    layers.forEachIndexed { index, (baseY, amp, alpha) ->
        val path = androidx.compose.ui.graphics.Path()
        val yBase = h * baseY
        val amplitude = h * amp
        path.moveTo(0f, yBase)
        var x = 0f
        val step = w / 48f
        while (x <= w) {
            val y = yBase + amplitude * sin(
                (x / w) * (1.6f + index * 0.5f) * 2f * PI.toFloat() + phase + index
            )
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        drawPath(path = path, color = color.copy(alpha = alpha))
    }
}

/**
 * 绘制柔光斑：径向渐变由中心颜色过渡到透明。
 *
 * @param color 光斑颜色
 * @param center 圆心
 * @param radius 半径
 * @param alpha 中心透明度
 */
private fun DrawScope.drawGlow(color: Color, center: Offset, radius: Float, alpha: Float) {
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
