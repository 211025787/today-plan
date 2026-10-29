package com.focuslist.app.ui.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 专注页全屏氛围动画背景：根据白噪音类型显示不同视觉效果。
 *
 * - WHITE：粒子飘动（白色小圆点随机飘动 + 闪烁），宁静专注氛围
 * - BROWN：波浪流动（多层正弦波缓慢起伏，深色调），沉浸海浪氛围
 * - NONE：不显示
 *
 * @param type 白噪音类型：NONE / WHITE / BROWN
 * @param modifier 布局修饰符
 */
@Composable
fun AmbientBackground(type: String, modifier: Modifier = Modifier) {
    when (type) {
        "WHITE" -> ParticleBackground(modifier)
        "BROWN" -> WaveBackground(modifier)
        else -> {}
    }
}

/**
 * 粒子飘动背景：白色小圆点随机飘动 + 闪烁，营造宁静专注氛围。
 *
 * @param modifier 布局修饰符
 */
@Composable
private fun ParticleBackground(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "particle")
    // 驱动粒子移动的时间动画
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )
    // 驱动闪烁的时间动画
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )
    // 粒子初始状态（固定随机种子保证不闪烁重建）
    val particles = remember {
        List(40) {
            ParticleData(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = 0.02f + Random.nextFloat() * 0.06f,
                size = 2f + Random.nextFloat() * 4f,
                phase = Random.nextFloat()
            )
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        // 深色半透明遮罩：确保粒子在任何主题下可见，营造专注氛围
        drawRect(Color(0xFF1A1A2E).copy(alpha = 0.75f))
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            // 缓慢飘动：位置 + 速度 * 时间，取模实现循环
            val px = ((p.x + p.speed * drift) % 1f) * w
            val py = ((p.y + p.speed * drift * 0.7f) % 1f) * h
            // 闪烁：sin 波控制 alpha
            val alpha = 0.3f + 0.4f * (0.5f + 0.5f * sin((twinkle + p.phase) * 2f * PI.toFloat()))
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = p.size,
                center = Offset(px, py)
            )
        }
    }
}

/**
 * 波浪流动背景：多层正弦波缓慢起伏，深色调营造沉浸氛围。
 *
 * @param modifier 布局修饰符
 */
@Composable
private fun WaveBackground(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    // 波浪水平流动
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "flow"
    )
    // 三层波浪参数：不同颜色、振幅、频率、相位
    val layers = remember {
        listOf(
            WaveLayer(color = Color(0xFF1A237E).copy(alpha = 0.5f), amp = 0.08f, freq = 1.5f, phase = 0f, yBase = 0.6f),
            WaveLayer(color = Color(0xFF311B92).copy(alpha = 0.45f), amp = 0.06f, freq = 2.0f, phase = 1f, yBase = 0.7f),
            WaveLayer(color = Color(0xFF006064).copy(alpha = 0.4f), amp = 0.05f, freq = 2.5f, phase = 2f, yBase = 0.8f)
        )
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        // 深色半透明遮罩：确保波浪在任何主题下可见
        drawRect(Color(0xFF0D1B2A).copy(alpha = 0.65f))
        val w = size.width
        val h = size.height
        layers.forEach { layer ->
            val path = Path()
            val yBase = h * layer.yBase
            val amp = h * layer.amp
            path.moveTo(0f, yBase)
            val step = w / 60f
            var x = 0f
            while (x <= w) {
                val y = yBase + amp * sin((x / w * layer.freq * 2f * PI.toFloat()) + flow + layer.phase)
                path.lineTo(x, y)
                x += step
            }
            // 闭合到底部填充
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            drawPath(path, layer.color)
        }
    }
}

/** 粒子初始状态数据。 */
private data class ParticleData(
    /** 初始 x 归一化坐标 0-1 */
    val x: Float,
    /** 初始 y 归一化坐标 0-1 */
    val y: Float,
    /** 飘动速度 */
    val speed: Float,
    /** 半径（像素） */
    val size: Float,
    /** 闪烁相位 0-1 */
    val phase: Float
)

/** 波浪层数据。 */
private data class WaveLayer(
    /** 填充颜色 */
    val color: Color,
    /** 振幅占比（相对画布高度） */
    val amp: Float,
    /** 频率（波数） */
    val freq: Float,
    /** 相位偏移 */
    val phase: Float,
    /** 基线 y 归一化坐标 0-1 */
    val yBase: Float
)