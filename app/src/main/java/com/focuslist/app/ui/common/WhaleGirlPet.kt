package com.focuslist.app.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 大肥鱼桌宠动画。
 *
 * 帧数 / 时长来自 dafeiyu-web 的 pet-manifest：241 帧 × 42ms ≈ 10.1s，
 * dragging 144 帧、dragging_release 49 帧。一次性动画播完自动回 idle。
 *
 * @property file assets 下的 webp 文件名
 * @property loop 是否循环
 * @property durationMs 一次性动画时长（毫秒），循环动画为 0
 */
private enum class PetAnim(val file: String, val loop: Boolean, val durationMs: Long) {
    IDLE("idle.webp", loop = true, durationMs = 0),
    DRAGGING("dragging.webp", loop = true, durationMs = 0),
    HEAD_PAT("head_pat.webp", loop = false, durationMs = 10_122),
    POKE("poke.webp", loop = false, durationMs = 10_122),
    TAIL("tail.webp", loop = false, durationMs = 10_122),
    DRAGGING_RELEASE("dragging_release.webp", loop = false, durationMs = 2_058),
    EAT_TOKEN("eat_token.webp", loop = false, durationMs = 10_122),
    IDLE_STRETCH("idle_stretch.webp", loop = false, durationMs = 10_122),
    IDLE_YAWN("idle_yawn.webp", loop = false, durationMs = 10_122)
}

/** 点击区域，与 dafeiyu-web 的 hit() 一致：左右 16% 是尾巴，上方 48% 是头。 */
private enum class PetRegion { HEAD, TAIL, BODY }

/**
 * 大肥鱼桌宠（DeepSeek 鲸鱼娘）。
 *
 * 与旧版的区别：
 * - 用 Android 原生 [ImageDecoder] 播放 animated WebP（Coil 只解第一帧，所以以前像静态图）；
 * - 点击头 / 身体 / 尾巴分别播放 head_pat / poke / tail，头顶出现气泡，摸头有明确反馈；
 * - 长按拖动会播放 dragging，松手播放 dragging_release；
 * - 空闲时随机伸懒腰 / 打哈欠 / 吃 token。
 *
 * 使用方只需给一个尺寸（如 140.dp），内部自己处理命中区域和拖动边界。
 *
 * @param modifier 布局修饰（通常带 size）
 */
@Composable
fun WhaleGirlPet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var anim by remember { mutableStateOf(PetAnim.IDLE) }
    var tick by remember { mutableIntStateOf(0) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var bubbleJob by remember { mutableStateOf<Job?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var lastAct by remember { mutableLongStateOf(0L) }

    val imageView = remember {
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // 气泡 1.8 秒后自动消失
    fun showBubble(text: String) {
        bubble = text
        bubbleJob?.cancel()
        bubbleJob = scope.launch {
            delay(1_800)
            bubble = null
        }
    }

    fun play(next: PetAnim, text: String? = null) {
        anim = next
        tick++
        if (!text.isNullOrBlank()) showBubble(text)
    }

    // 一次性动画播完回 idle
    LaunchedEffect(anim, tick) {
        val current = anim
        if (current.loop) return@LaunchedEffect
        delay(current.durationMs)
        if (anim == current) anim = PetAnim.IDLE
    }

    // 空闲小动作：25-45 秒随机一次
    LaunchedEffect(anim, tick) {
        if (anim != PetAnim.IDLE) return@LaunchedEffect
        delay(25_000L + Random.nextLong(20_000L))
        if (anim == PetAnim.IDLE) {
            val micro = listOf(PetAnim.IDLE_STRETCH, PetAnim.IDLE_YAWN, PetAnim.EAT_TOKEN)
            play(micro.random())
        }
    }

    // 原生解码 animated WebP；目标尺寸按显示尺寸缩小，避免 3.5MB 原图占内存
    LaunchedEffect(anim, tick, boxSize) {
        if (boxSize.width <= 0 || boxSize.height <= 0) return@LaunchedEffect
        val targetWidth = boxSize.width.coerceAtLeast(1)
        val targetHeight = (targetWidth * 344f / 412f).roundToInt().coerceAtLeast(1)
        val drawable = withContext(Dispatchers.IO) {
            loadPetDrawable(context, anim.file, targetWidth, targetHeight)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            stopPetAnimation(imageView)
        }
        if (drawable == null) return@LaunchedEffect
        imageView.setImageDrawable(drawable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startPetAnimation(imageView, drawable, anim.loop)
        }
        imageView.alpha = 1f
    }

    DisposableEffect(Unit) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                stopPetAnimation(imageView)
            }
            bubbleJob?.cancel()
        }
    }

    fun regionOf(position: Offset): PetRegion {
        val w = boxSize.width.toFloat()
        val h = boxSize.height.toFloat()
        if (w <= 0f || h <= 0f) return PetRegion.BODY
        val scale = min(w / 412f, h / 344f)
        val imageWidth = 412f * scale
        val imageHeight = 344f * scale
        val left = (w - imageWidth) / 2f
        val top = (h - imageHeight) / 2f
        val fx = ((position.x - left) / imageWidth).coerceIn(0f, 1f)
        val fy = ((position.y - top) / imageHeight).coerceIn(0f, 1f)
        return when {
            fx < 0.16f || fx > 0.82f -> PetRegion.TAIL
            fy < 0.48f -> PetRegion.HEAD
            else -> PetRegion.BODY
        }
    }

    fun onTap(position: Offset) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAct < 700L) return
        lastAct = now
        when (regionOf(position)) {
            PetRegion.HEAD -> {
                play(PetAnim.HEAD_PAT, "摸摸头～ 好舒服")
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            PetRegion.TAIL -> {
                play(PetAnim.TAIL, "尾巴不能拽啦！")
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            PetRegion.BODY -> {
                play(PetAnim.POKE, "戳我干嘛～")
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val edgePx = with(density) { 8.dp.toPx() }

    fun dragBy(delta: Offset) {
        val maxLeft = -(screenWidthPx - boxSize.width - edgePx)
        val maxUp = -(screenHeightPx - boxSize.height - edgePx)
        offset = Offset(
            x = (offset.x + delta.x).coerceIn(maxLeft, 0f),
            y = (offset.y + delta.y).coerceIn(maxUp, 0f)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var moved = false
                    var up = false
                    try {
                        while (!up) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                up = true
                                break
                            }
                            val delta = change.positionChange()
                            total += delta
                            if (!moved && total.getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                                dragging = true
                                play(PetAnim.DRAGGING, "放我下来～")
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (moved) {
                                change.consume()
                                dragBy(delta)
                            }
                        }
                    } finally {
                        if (dragging) {
                            dragging = false
                            play(PetAnim.DRAGGING_RELEASE, "呼…站稳了")
                        }
                    }
                    if (!moved && up) onTap(down.position)
                }
            }
    ) {
        AndroidView(
            factory = { imageView },
            modifier = Modifier.fillMaxSize()
        )

        // 头顶气泡：不参与布局高度，画在桌宠上方
        AnimatedVisibility(
            visible = bubble != null,
            enter = fadeIn(tween(130)) + scaleIn(tween(160), initialScale = 0.8f),
            exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-34).dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = bubble.orEmpty(),
                    color = Color(0xFF2E3A50),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 读取桌宠动画：API 28+ 用 [ImageDecoder] 解 animated WebP，低版本退回静态首帧。
 *
 * @param context 上下文
 * @param file assets/dafeiyu 下的文件名
 * @param width 目标显示宽度（px）
 * @param height 目标显示高度（px）
 * @return 可播放的 Drawable；失败返回 null
 */
private fun loadPetDrawable(context: Context, file: String, width: Int, height: Int): Drawable? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeAnimated(context, file, width, height)
        } else {
            context.assets.open("dafeiyu/$file").use { input ->
                BitmapFactory.decodeStream(input)?.let { BitmapDrawable(context.resources, it) }
            }
        }
    }.getOrNull()
}

/**
 * API 28+ 的 animated WebP 解码。
 *
 * @param context 上下文
 * @param file 文件名
 * @param width 目标宽
 * @param height 目标高
 * @return Drawable
 */
@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimated(context: Context, file: String, width: Int, height: Int): Drawable {
    val source = ImageDecoder.createSource(context.assets, "dafeiyu/$file")
    return ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
        decoder.setTargetSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }
}

/**
 * API 28+：停止 ImageView 上正在播放的 animated drawable。
 *
 * 单独抽出来是为了避免 API 26/27 的类验证碰到 [AnimatedImageDrawable]；
 * 调用点都有 `SDK_INT >= P` 保护。
 *
 * @param view 桌宠 ImageView
 */
@RequiresApi(Build.VERSION_CODES.P)
private fun stopPetAnimation(view: ImageView) {
    (view.drawable as? AnimatedImageDrawable)?.stop()
}

/**
 * API 28+：设置循环次数并启动 animated drawable。
 *
 * @param view 桌宠 ImageView
 * @param drawable 刚解码的 drawable
 * @param loop 是否循环
 */
@RequiresApi(Build.VERSION_CODES.P)
private fun startPetAnimation(view: ImageView, drawable: Drawable, loop: Boolean) {
    if (drawable !is AnimatedImageDrawable) return
    drawable.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 1
    drawable.start()
    view.alpha = 1f
}
