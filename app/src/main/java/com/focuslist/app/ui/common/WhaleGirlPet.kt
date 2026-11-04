package com.focuslist.app.ui.common

import android.widget.ImageView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 大肥鱼桌宠动画类型，对应 assets/dafeiyu/ 下的 animated webp 文件。
 *
 * @property file webp 文件名（不含扩展名）
 */
private enum class PetAnim(val file: String) {
    /** 待机呼吸，循环播放 */
    IDLE("idle"),
    /** 打哈欠，一次性 */
    IDLE_YAWN("idle_yawn"),
    /** 伸懒腰，一次性 */
    IDLE_STRETCH("idle_stretch"),
    /** 被拖拽挣扎，循环播放 */
    DRAGGING("dragging"),
    /** 被摸头，一次性 */
    HEAD_PAT("head_pat")
}

/**
 * DeepSeek 大肥鱼桌宠（Web 复刻版素材）。
 *
 * 使用 cheng123623/dafeiyu-web 的 animated webp 动画帧，通过 Coil ImageLoader
 * 加载到 ImageView（原生自动播放 AnimatedImageDrawable），Compose 状态机驱动切换：
 * - 待机时循环播放 idle，每 12-20 秒随机触发打哈欠或伸懒腰微动作
 * - 点击触发摸头动画（head_pat），5 秒后回到待机
 * - 拖动时切换到 dragging 挣扎动画，松手回到待机
 *
 * 透明背景无容器底色，直接显示角色像素。素材来源：
 * https://github.com/cheng123623/dafeiyu-web/tree/main/anim
 * 原始角色：QCYTSN/dsh-dafeiyu（MIT License）
 *
 * @param modifier 尺寸约束
 */
@Composable
fun WhaleGirlPet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    // 拖动偏移（像素）
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    // 拖动中标志
    var isDragging by remember { mutableStateOf(false) }
    // 当前播放的动画
    var currentAnim by remember { mutableStateOf(PetAnim.IDLE) }
    // 互动触发器（递增以重新启动 LaunchedEffect 计时）
    var interactTick by remember { mutableStateOf(0) }

    // 待机微动作定时器：每 12-20 秒随机触发一次打哈欠或伸懒腰
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(12000, 20000))
            // 仅在待机且非拖动时触发微动作
            if (!isDragging && currentAnim == PetAnim.IDLE) {
                currentAnim = if (Random.nextBoolean()) PetAnim.IDLE_YAWN else PetAnim.IDLE_STRETCH
                interactTick++
            }
        }
    }

    // 一次性动画（打哈欠/伸懒腰/摸头）展示 5 秒后回到待机
    LaunchedEffect(currentAnim, interactTick) {
        when (currentAnim) {
            PetAnim.IDLE_YAWN, PetAnim.IDLE_STRETCH, PetAnim.HEAD_PAT -> {
                delay(5000)
                // 若期间未被拖动则回到待机
                if (!isDragging) {
                    currentAnim = PetAnim.IDLE
                }
            }
            else -> {}
        }
    }

    // 大肥鱼 animated webp（透明背景，无容器底色）
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            // 一个手势处理器同时区分「轻点」与「拖动」：
            // 之前用 detectTapGestures + detectDragGestures 两个 pointerInput 并存，
            // 拖动检测会抢走按下/抬起事件，导致点击头经常不触发摸头动画。
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            break
                        }
                        val delta = change.positionChange()
                        total += delta
                        if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            isDragging = true
                            currentAnim = PetAnim.DRAGGING
                        }
                        if (dragging) {
                            offsetX += delta.x
                            offsetY += delta.y
                            change.consume()
                        }
                    }
                    if (dragging) {
                        isDragging = false
                        currentAnim = PetAnim.IDLE
                    } else {
                        // 轻点：摸摸头（连点也重新播放）
                        currentAnim = PetAnim.HEAD_PAT
                        interactTick++
                    }
                }
            }
    ) {
        // key 带上 interactTick：同一动画重复触发时也会重建 ImageView，从头播放
        key(currentAnim, interactTick) {
            AndroidView(
                factory = { ctx ->
                    // ImageView 原生自动播放 AnimatedImageDrawable
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data("file:///android_asset/dafeiyu/${currentAnim.file}.webp")
                                .target(this)
                                .build()
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}