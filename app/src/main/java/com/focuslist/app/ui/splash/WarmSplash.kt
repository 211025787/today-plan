package com.focuslist.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

/** 「暖历」淡入的总时长：从时间轴起点算起固定 1200ms 走完，速度不由首帧早晚决定 */
private const val FADE_MS = 1200

/** 首帧迟到时最多从动画里扣掉多少毫秒——也就是文字最多提前 0.3 秒出现 */
private const val MAX_HEAD_START_MS = 300L

/**
 * 把一条缓动曲线整体后移 [offset]（0..1）。
 *
 * 用在「这句动画其实已经播了一段、只是那段没人看见」的续播场景：
 * 新的 0..1 进度映射回曲线原本的 offset..1 区间。因为 `tween` 的 easing 作用在
 * **值的增量**上（从起始值到目标值的那一段），所以这里还要按起始值归一化一次，
 * 归一化之后 value(f) = base(offset + f·(1-offset))，即绝对进度上的一一对应——
 * 曲线的形状和每毫秒的 alpha 变化量都和原动画完全一样，只是起点换了。
 */
internal class ShiftedEasing(private val base: Easing, private val offset: Float) : Easing {
    private val start: Float = base.transform(offset)

    override fun transform(fraction: Float): Float {
        val absolute = base.transform(offset + fraction * (1f - offset))
        return ((absolute - start) / (1f - start)).coerceIn(0f, 1f)
    }
}

/**
 * 开屏页：图标 60dp 全程不动 →「暖历」两字缓缓浮现 → 随机格言与署名被逐字写下 → 进入主界面。
 *
 * 手写感由四层叠出来：毛笔字体 + 每字随机微小倾角 + 从左往右的笔迹揭示 +
 * 笔尖墨点跟随。逐字推进而不是整句淡入，才有「正在写」的感觉。
 *
 * 这一屏不响应点击：从冷启动进来的人就是来看这句话的，跳过键只会让人手滑错过。
 *
 * @param quote 本次冷启动抽到的格言
 * @param startAtNanos 时间轴起点（`System.nanoTime()`），由 Activity 在创建时打点。
 *   开屏首帧之前耗掉的时间（窗口首帧、字体加载）从这里扣掉，最多扣 [MAX_HEAD_START_MS]。
 * @param onFinished 动画播完后回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarmSplash(
    quote: WarmQuote,
    darkTheme: Boolean,
    startAtNanos: Long,
    onFinished: () -> Unit,
    onQuoteWritten: () -> Unit = {}
) {
    val dark = darkTheme
    val ink = if (dark) InkDark else InkLight
    val background = if (dark) Color(0xFF1C1714) else Color(0xFFFFF7EF)
    val accent = if (dark) Color(0xFFFFB984) else WarmAccent
    val density = LocalDensity.current

    // 图标全程 60dp 不动：系统启动帧画的也是同一枚图标（放大后 ≈61dp），
    // 从点开到开屏结束，图标尺寸一次都不变。
    //
    // 真正的过渡发生在「没有文字 → 有文字」这一步：品牌名从完全透明缓缓浮现。
    // 比起常见的 200~300ms 淡入，这个时长明显更慢，也更从容。
    val brandAlpha = remember { Animatable(0f) }
    // 笔尖进度：单位是「字」，名言写完接着写署名
    val pen = remember { Animatable(0f) }
    val textLength = quote.text.length
    val authorText = "— " + quote.author
    val total = textLength + authorText.length
    val perCharMs = 46

    LaunchedEffect(quote) {
        // 时间轴从 Activity 创建那一刻起算。首帧之前（窗口首帧、字体加载）耗掉的时间
        // 在这里直接扣掉——扣多少就是「提前多少」，曲线和每秒的变化量都不变。
        // 上限 300ms：再多的话第一帧一出来文字就已经很浓了，反而像"跳"出来的。
        val headStartMs = ((System.nanoTime() - startAtNanos) / 1_000_000L)
            .coerceIn(0L, MAX_HEAD_START_MS)
        val offset = headStartMs / FADE_MS.toFloat()
        val remainMs = (FADE_MS * (1f - offset)).toInt().coerceAtLeast(1)

        // 名言这条线先挂起来等着（写完再回调去组合主界面），
        // 自己排在后面，是为了让淡入成为本协程的第一句话——
        // 中间不再经过一次协程分发，淡入就能在这一帧的同一批回调里起步，不多等一帧。
        launch {
            // 文字收尾后再留 100ms 换气才落笔
            delay(remainMs + 100L)
            pen.animateTo(total.toFloat(), tween(total * perCharMs, easing = LinearEasing))
            // 名言写完、画面静止：现在才去组合主界面，掉帧不会被看见
            onQuoteWritten()
            delay(650)
            onFinished()
        }
        // 「暖历」两字：从时间轴起点开始浮现。
        // 起点已经不是 0 的话（首帧来晚了），先跳到曲线上对应的位置再续播——
        // 相当于这句动画"已经播了 headStartMs"，只是那一段没人看见。
        if (offset > 0f) brandAlpha.snapTo(LinearOutSlowInEasing.transform(offset))
        // 缓动用 LinearOutSlowInEasing（线性起、减速收）：匀速淡入的结尾是「戛然而止」，
        // 会被读成一卡；这个曲线起手不突兀、收尾柔和。
        brandAlpha.animateTo(
            1f,
            tween(remainMs, easing = ShiftedEasing(LinearOutSlowInEasing, offset))
        )
    }

    // 用 BoxWithConstraints 拿屏高：图标位置要和系统启动帧**算得一模一样**，
    // 否则切换瞬间图标会从屏幕正中跳到偏上（真机验证过）。
    // 整屏不挂任何点击手势：从冷启动进来就是来看这句话的，手一滑跳过去反而错过唯一的内容。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                    ),
                    radius = with(density) { 300.dp.toPx() },
                    center = Offset(size.width / 2f, size.height * 0.42f)
                )
            }
    ) {
        // 与 splash_small_icon 同一套几何：
        //   画布 108dp × 系统放大 2.18 倍 = 235.44dp，居中于屏幕；
        //   图标在画布内 y 21..49，中心 35dp。
        // 换算成屏幕坐标就是下面的 iconTop，两段位置因此完全重合。
        val splashCanvas = 108.dp * 2.18f
        // -26.dp：整组内容比「与启动帧完全重合」的位置再往上挪一点，视觉重心更舒服；
        // 代价是与启动帧还剩 2~3% 屏高的位置差（用户明确选择偏上优先）。
        val iconTop = (maxHeight - splashCanvas) / 2 + 35.dp * 2.18f - 30.dp - 26.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = iconTop)
                .padding(horizontal = 32.dp)
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
                // 没轮到的字一跳而过；已经写完的字用普通绘制。
                // 只有正在写的那一两个字才走 clipRect——否则 30 个字每帧都要裁剪一次。
                if (local <= 0f) return@drawWithContent
                if (local >= 1f) {
                    drawContent()
                    return@drawWithContent
                }
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
