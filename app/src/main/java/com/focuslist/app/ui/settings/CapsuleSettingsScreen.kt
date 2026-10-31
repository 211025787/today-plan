package com.focuslist.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.plan.TimerCapsule
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 胶囊可选颜色（名称与色值） */
private val CapsuleColorOptions = listOf(
    "云雾白" to "#FCFDFF",
    "浅蓝" to "#E8F0FF",
    "主题蓝" to "#4772FA",
    "薰衣草" to "#8E7BF0",
    "薄荷" to "#3FA796",
    "墨黑" to "#1B1B2F"
)

/**
 * 悬浮胶囊设置页：开关与权限、实时预览、背景/文字/进度条配色与透明度、尺寸、位置重置。
 *
 * 拆成独立页面，避免「我的」页堆叠过多控件，交互更聚焦。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsuleSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsStore) }
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingEnable by remember { mutableStateOf(false) }
    var previewTick by remember { mutableStateOf(0) }
    val capsule = remember { TimerCapsule(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从系统授权页返回：刷新权限并自动开启此前想开启的开关
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                if (overlayGranted && pendingEnable) {
                    viewModel.saveCapsule(true)
                    pendingEnable = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 预览：样式变化或点击预览时展示 5 秒
    LaunchedEffect(
        previewTick,
        settings.capsuleColorHex,
        settings.capsuleAlpha,
        settings.capsuleWidthDp,
        settings.capsuleRowHeightDp,
        settings.capsuleTextColorHex,
        settings.capsuleTextAlpha,
        settings.capsuleProgressColorHex,
        settings.capsuleProgressAlpha
    ) {
        if (previewTick > 0) {
            capsule.show(
                entries = listOf(
                    TimerCapsule.Entry("示例计划", 12 * 60, 30 * 60),
                    TimerCapsule.Entry("学 Java", 5 * 60, 45 * 60)
                ),
                config = settings.toCapsuleConfig()
            )
            delay(5000)
            capsule.hide()
        }
    }
    DisposableEffect(Unit) { onDispose { capsule.hide() } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("悬浮计时胶囊", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 开关与状态
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("后台显示计时胶囊", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (overlayGranted) "悬浮窗权限已授予" else "需要「显示在其他应用上层」权限",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (overlayGranted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        Switch(
                            checked = settings.capsuleEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !Settings.canDrawOverlays(context)) {
                                    pendingEnable = true
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    } catch (_: Exception) {
                                        // 部分定制系统无该页面时忽略
                                    }
                                } else {
                                    viewModel.saveCapsule(checked)
                                }
                            }
                        )
                    }
                    Text(
                        text = "应用退到后台时在屏幕上方显示正在计时的计划；ColorOS 的灵动岛不向第三方开放，这里用悬浮窗实现同形态胶囊。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (overlayGranted) {
                        TextButton(onClick = { previewTick++ }) { Text("预览胶囊（5 秒）") }
                    }
                }
            }

            if (settings.capsuleEnabled) {
                SectionCard(title = "背景") {
                    CapsuleColorRow(selected = settings.capsuleColorHex) { hex ->
                        viewModel.saveCapsuleStyle(
                            hex, settings.capsuleAlpha, settings.capsuleWidthDp, settings.capsuleRowHeightDp,
                            settings.capsuleTextColorHex, settings.capsuleTextAlpha,
                            settings.capsuleProgressColorHex, settings.capsuleProgressAlpha
                        )
                    }
                    CapsuleSliderDraft(
                        label = "不透明度",
                        initial = settings.capsuleAlpha.toFloat(),
                        range = 10f..100f,
                        unit = "%"
                    ) { value ->
                        viewModel.saveCapsuleStyle(
                            settings.capsuleColorHex, value, settings.capsuleWidthDp, settings.capsuleRowHeightDp,
                            settings.capsuleTextColorHex, settings.capsuleTextAlpha,
                            settings.capsuleProgressColorHex, settings.capsuleProgressAlpha
                        )
                    }
                }

                SectionCard(title = "文字") {
                    CapsuleColorRow(selected = settings.capsuleTextColorHex) { hex ->
                        viewModel.saveCapsuleStyle(
                            settings.capsuleColorHex, settings.capsuleAlpha, settings.capsuleWidthDp, settings.capsuleRowHeightDp,
                            hex, settings.capsuleTextAlpha,
                            settings.capsuleProgressColorHex, settings.capsuleProgressAlpha
                        )
                    }
                    CapsuleSliderDraft(
                        label = "不透明度",
                        initial = settings.capsuleTextAlpha.toFloat(),
                        range = 10f..100f,
                        unit = "%"
                    ) { value ->
                        viewModel.saveCapsuleStyle(
                            settings.capsuleColorHex, settings.capsuleAlpha, settings.capsuleWidthDp, settings.capsuleRowHeightDp,
                            settings.capsuleTextColorHex, value,
                            settings.capsuleProgressColorHex, settings.capsuleProgressAlpha
                        )
                    }
                }

                SectionCard(title = "进度条") {
                    CapsuleColorRow(selected = settings.capsuleProgressColorHex) { hex ->
                        viewModel.saveCapsuleStyle(
                            settings.capsuleColorHex, settings.capsuleAlpha, settings.capsuleWidthDp, settings.capsuleRowHeightDp,
                            settings.capsuleTextColorHex, settings.capsuleTextAlpha,
                            hex, settings.capsuleProgressAlpha
                        )
                    }
                    CapsuleSliderDraft(
                        label = "不透明度",
                        initial = settings.capsuleProgressAlpha.toFloat(),
                        range = 10f..100f,
                        unit = "%"
                    ) { value ->
                        viewModel.saveCapsuleStyle(
                            settings.capsuleColorHex, settings.capsuleAlpha, settings.capsuleWidthDp, settings.capsuleRowHeightDp,
                            settings.capsuleTextColorHex, settings.capsuleTextAlpha,
                            settings.capsuleProgressColorHex, value
                        )
                    }
                }

                SectionCard(title = "尺寸", subtitle = "0 表示自适应内容") {
                    CapsuleSliderDraft(
                        label = "宽度",
                        initial = settings.capsuleWidthDp.toFloat(),
                        range = 0f..300f,
                        unit = "dp"
                    ) { value ->
                        viewModel.saveCapsuleStyle(
                            settings.capsuleColorHex, settings.capsuleAlpha, value, settings.capsuleRowHeightDp,
                            settings.capsuleTextColorHex, settings.capsuleTextAlpha,
                            settings.capsuleProgressColorHex, settings.capsuleProgressAlpha
                        )
                    }
                    CapsuleSliderDraft(
                        label = "行高",
                        initial = settings.capsuleRowHeightDp.toFloat(),
                        range = 0f..56f,
                        unit = "dp"
                    ) { value ->
                        viewModel.saveCapsuleStyle(
                            settings.capsuleColorHex, settings.capsuleAlpha, settings.capsuleWidthDp, value,
                            settings.capsuleTextColorHex, settings.capsuleTextAlpha,
                            settings.capsuleProgressColorHex, settings.capsuleProgressAlpha
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable {
                                viewModel.saveCapsulePosition(-1, -1)
                                previewTick++
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = "重置胶囊位置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "胶囊出现后可按住拖到屏幕任意位置，松手自动记住",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 分组卡片：标题 + 副标题 + 内容。
 *
 * @param title 标题
 * @param subtitle 副标题
 * @param content 内容
 */
@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 8.dp)) { content() }
        }
    }
}

/**
 * 颜色选择行：一排色点，选中项带主题色描边。
 *
 * @param selected 当前选中色值
 * @param onSelect 选择回调
 */
@Composable
private fun CapsuleColorRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        CapsuleColorOptions.forEach { (_, hex) ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(parseColorSafe(hex))
                    .then(
                        if (hex == selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        }
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

/**
 * 滑杆行：拖动过程中只更新本地值（页面即时反馈），松手才落库。
 *
 * @param label 名称
 * @param initial 初始值（设置变化时同步）
 * @param range 取值范围
 * @param unit 单位
 * @param onCommit 松手回调（取整后的值）
 */
@Composable
private fun CapsuleSliderDraft(
    label: String,
    initial: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onCommit: (Int) -> Unit
) {
    // 仅在外部设置变化时重置草稿，拖动期间不受回流影响
    var value by remember(initial) { mutableFloatStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "${value.roundToInt()}$unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onCommit(value.roundToInt()) },
            valueRange = range
        )
    }
}

/**
 * 十六进制色值安全解析。
 *
 * @param hex 形如 "#RRGGBB"
 * @return Compose 颜色；解析失败时回落白色
 */
private fun parseColorSafe(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color.White
    }
}

/**
 * 设置快照转胶囊配置。
 *
 * @return 胶囊配置
 */
private fun TimerSettings.toCapsuleConfig(): TimerCapsule.Config {
    return TimerCapsule.Config(
        bgColorHex = capsuleColorHex,
        alphaPercent = capsuleAlpha,
        widthDp = capsuleWidthDp,
        rowHeightDp = capsuleRowHeightDp,
        textColorHex = capsuleTextColorHex,
        textAlphaPercent = capsuleTextAlpha,
        progressColorHex = capsuleProgressColorHex,
        progressAlphaPercent = capsuleProgressAlpha,
        xPx = capsuleX,
        yPx = capsuleY
    )
}
