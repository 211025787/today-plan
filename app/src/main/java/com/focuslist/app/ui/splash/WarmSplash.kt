package com.focuslist.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.R
import com.focuslist.app.util.WarmQuote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 毛笔手写体子集（Ma Shan Zheng，SIL OFL 1.1，见 docs/OFL-MaShanZheng.txt） */
private val HandFont = FontFamily(Font(R.font.warm_hand))

/** 暖橙主色与墨色，浅色/深色下都保证可读 */
private val WarmAccent = Color(0xFFE4763A)
private val InkLight = Color(0xFF2E2723)
private val InkDark = Color(0xFFF6EDE4)

/**
 * 开屏页：图标 60dp 全程不动 →「暖历」两字用 1200ms 缓缓浮现 → 随机格言与署名被逐字写下 → 进入主界面。
 *
 * 手写感由四层叠出来：毛笔字体 + 每字随机微小倾角 + 从左往右的笔迹揭示 +
 * 笔尖墨点跟随。逐字推进而不是整句淡入，才有「正在写」的感觉。
 *
 * 点一下任意位置可跳过。
 *
 * @param quote 本次冷启动抽到的格言
 * @param onFinished 动画播完（或被跳过）后回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarmSplash(
    quote: WarmQuote,
    darkTheme: Boolean,
    onFinished: () -> Unit
) {
    val dark = darkTheme
    val ink = if (dark) InkDark else InkLight
    val background = if (dark) Color(0xFF1C1714) else Color(0xFFFFF7EF)
    val accent = if (dark) Color(0xFFFFB984) else WarmAccent
    val density = LocalDensity.current

    // 图标全程 60dp 不动：系统启动帧画的也是同一枚图标（放大后 ≈61dp），
    // 从点开到开屏结束，图标尺寸一次都不变。
    //
    // 真正的过渡发生在「没有文字 → 有文字」这一步：品牌名从完全透明用 1200ms
    // 匀速浮现。比起常见的 200~300ms 淡入，这个时长明显更慢，也更从容。
    val brandAlpha = remember { Animatable(0f) }
    // 笔尖进度：单位是「字」，名言写完接着写署名
    val pen = remember { Animatable(0f) }
    val textLength = quote.text.length
    val authorText = "— " + quote.author
    val total = textLength + authorText.length
    val perCharMs = 46
    var skipped by remember { mutableStateOf(false) }

    // skipped 变化时取消整段动画协程，轻点跳过后不会在后台继续跑完
    LaunchedEffect(quote, skipped) {
        if (skipped) return@LaunchedEffect
        launch {
            // 「暖历」两字：180ms 后开始，用 1200ms 匀速浮现
            delay(180)
            brandAlpha.animateTo(1f, tween(1200, easing = LinearEasing))
        }
        // 等文字浮现完再落笔，画面不会同时有「字变亮」和「字在写」两件事
        delay(1500)
        pen.animateTo(total.toFloat(), tween(total * perCharMs, easing = LinearEasing))
        delay(420)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                    ),
                    radius = with(density) { 300.dp.toPx() },
                    center = Offset(size.width / 2f, size.height * 0.34f)
                )
            }
            // 轻点跳过，不显示提示文字，免得抢了手写动画的戏
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    skipped = true
                    onFinished()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 48.dp)
        ) {
            // 应用图标：60dp 方块 / 22dp 圆角，从第一帧到开屏结束都不缩放
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(22.dp))
                    // 与桌面图标同一套底色：近黑纯底把「日轮」符号托起来
                    .background(Color(0xFF1C1714)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "暖历",
                    contentScale = ContentScale.FillBounds,
                    // 108dp 画布里 42dp 的日轮主体，落进 60dp 方块后放大 1.3 倍 ≈ 30dp
                    modifier = Modifier.fillMaxSize().scale(1.3f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "暖历",
                style = TextStyle(
                    fontFamily = HandFont,
                    fontSize = 30.sp,
                    letterSpacing = 8.sp,
                    color = accent
                ),
                modifier = Modifier.graphicsLayer { alpha = brandAlpha.value }
            )

            Spacer(modifier = Modifier.height(34.dp))

            // 正文：逐字「写」出来，FlowRow 自动换行
            // 传 lambda 而不是 Float：进度在 layer / draw 阶段才读，
            // 动画每帧只失效图层，不重组整句（30+ 个字逐个重组会掉帧）
            val progress: () -> Float = { pen.value }
            val quoteStyle = TextStyle(
                fontFamily = HandFont,
                fontSize = 27.sp,
                lineHeight = 42.sp,
                color = ink
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center,
                maxItemsInEachRow = Int.MAX_VALUE
            ) {
                quote.text.forEachIndexed { index, char ->
                    HandChar(
                        char = char,
                        index = index,
                        progress = progress,
                        style = quoteStyle,
                        accent = accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 署名：接在正文之后继续写，字号更小、颜色更淡
            val authorStyle = TextStyle(
                fontFamily = HandFont,
                fontSize = 18.sp,
                color = accent
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                authorText.forEachIndexed { offset, char ->
                    HandChar(
                        char = char,
                        index = textLength + offset,
                        progress = progress,
                        style = authorStyle,
                        accent = accent
                    )
                }
            }
        }
    }
}

/**
 * 一个「正在被写下来」的字。
 *
 * - 先用 clipRect 从左往右揭示，模拟笔画展开；
 * - 再叠一个随之移动的笔尖墨点；
 * - 每个字有固定的随机小倾角和落笔位移，整句才不会像打印体。
 *
 * @param char 该字
 * @param index 在整段文字里的序号（决定什么时候轮到它）
 * @param progress 当前笔尖进度（单位：字）
 * @param style 字体样式
 * @param accent 笔尖颜色
 */
@Composable
private fun HandChar(
    char: Char,
    index: Int,
    progress: () -> Float,
    style: TextStyle,
    accent: Color
) {
    // 用序号做稳定伪随机：同一句每次进来倾角一致，不会闪
    val tilt = ((index * 37 % 9) - 4) * 0.7f
    Text(
        text = char.toString(),
        style = style,
        modifier = Modifier
            .graphicsLayer {
                val local = (progress() - index).coerceIn(0f, 1f)
                alpha = if (local > 0f) 1f else 0f
                rotationZ = tilt
                translationY = (1f - local) * 6f
            }
            .drawWithContent {
                val local = (progress() - index).coerceIn(0f, 1f)
                clipRect(
                    left = 0f,
                    top = 0f,
                    right = size.width * local,
                    bottom = size.height
                ) {
                    this@drawWithContent.drawContent()
                }
                if (local > 0.02f && local < 0.98f) {
                    drawCircle(
                        color = accent,
                        radius = 2.2f,
                        center = Offset(size.width * local, size.height * 0.74f)
                    )
                }
            }
    )
}
