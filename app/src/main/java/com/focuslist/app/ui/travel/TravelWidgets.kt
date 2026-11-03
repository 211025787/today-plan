package com.focuslist.app.ui.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.util.TravelFormat
import com.focuslist.app.util.TravelMode

/** 行程可选主题色 */
val TravelColorPool: List<String> = listOf(
    "#4772FA", "#E94B3C", "#43A047", "#FB8C00",
    "#8E44AD", "#00897B", "#D81B60", "#546E7A"
)

/** 行程封面 emoji 可选池（优先选各机型字体都覆盖的常见图案） */
val TravelEmojiPool: List<String> = listOf(
    "🏔", "🌿", "🚄", "🚗", "🌊", "🌸", "🏖", "🏙", "🚢", "🏝"
)

/**
 * 交通方式对应的图标。
 *
 * @return Material 图标
 */
fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.FLIGHT -> Icons.Default.FlightTakeoff
    TravelMode.HSR -> Icons.Default.Train
    TravelMode.TRAIN -> Icons.Default.DirectionsRailway
    TravelMode.COACH -> Icons.Default.DirectionsBus
    TravelMode.BUS -> Icons.Default.DirectionsBus
    TravelMode.SUBWAY -> Icons.Default.DirectionsSubway
    TravelMode.CAR -> Icons.Default.DirectionsCar
    TravelMode.CRUISE -> Icons.Default.DirectionsBoat
    TravelMode.BIKE -> Icons.Default.DirectionsBike
    TravelMode.WALK -> Icons.Default.DirectionsWalk
    TravelMode.OTHER -> Icons.Default.Explore
}

/**
 * 玻璃圆形图标按钮（地图右侧的缩放 / 复位）。
 *
 * @param icon 图标
 * @param description 无障碍描述
 * @param onClick 点击回调
 * @param size 直径，默认 40dp
 */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

/**
 * 圆角小胶囊按钮。
 *
 * @param text 文案
 * @param selected 是否选中
 * @param color 主题色
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun TravelChip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) color.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                width = 1.dp,
                color = if (selected) color.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * 小节标题。
 *
 * @param title 标题
 * @param hint 右侧说明（可选）
 * @param modifier 修饰符
 */
@Composable
fun TravelSectionTitle(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 圆形加减按钮。
 *
 * @param plus true 为加号，false 为减号
 * @param enabled 是否可用
 * @param onClick 点击回调
 */
@Composable
fun RoundStepButton(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.8f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = if (plus) Icons.Default.Add else Icons.Default.Remove,
            contentDescription = if (plus) "增加" else "减少",
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 时长编辑器：大号时长 + 加减按钮 + 一组快捷选项。
 *
 * @param value 当前分钟数
 * @param onChange 变化回调
 * @param presets 快捷选项（分钟 → 展示文案）
 * @param color 主题色
 * @param label 标题
 * @param step 加减步长（分钟）
 * @param max 上限（分钟）
 */
@Composable
fun DurationEditor(
    value: Int,
    onChange: (Int) -> Unit,
    presets: List<Pair<Int, String>>,
    color: Color,
    label: String,
    step: Int = 30,
    max: Int = 60 * 24 * 30
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (value <= 0) "未设置" else TravelFormat.duration(value),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (value <= 0) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundStepButton(
                    plus = false,
                    enabled = value > 0,
                    onClick = { onChange((value - step).coerceAtLeast(0)) }
                )
                RoundStepButton(
                    plus = true,
                    enabled = value < max,
                    onClick = { onChange((value + step).coerceAtMost(max)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (minutes, text) ->
                TravelChip(
                    text = text,
                    selected = value == minutes,
                    color = color,
                    onClick = { onChange(minutes) }
                )
            }
        }
    }
}

/**
 * 交通方式选择网格（4 列）。
 *
 * @param selected 当前方式
 * @param color 主题色
 * @param onSelect 选择回调
 */
@Composable
fun TravelModeGrid(selected: TravelMode, color: Color, onSelect: (TravelMode) -> Unit) {
    val modes = TravelMode.pickerOrder
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.chunked(4).forEach { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowModes.forEach { mode ->
                    val isSelected = mode == selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) color.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) color.copy(alpha = 0.55f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(mode) }
                    ) {
                        Icon(
                            imageVector = mode.icon(),
                            contentDescription = mode.label,
                            tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                // 最后一行不满 4 个时补空位，保持格子大小一致
                repeat(4 - rowModes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 主题色选择行。
 *
 * @param selected 当前色值
 * @param onSelect 选择回调
 */
@Composable
fun TravelColorRow(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TravelColorPool.forEach { hex ->
            val color = com.focuslist.app.ui.theme.parseThemeColor(hex)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected == hex) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            ) {
                if (selected == hex) {
                    Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 一篇行程的简要统计文案，如「5 站 · 约 9 天 · 1,240 公里」。
 *
 * @param stops 站点数
 * @param days 天数文案
 * @param distanceMeters 里程
 * @return 文案
 */
fun travelMetaText(stops: Int, days: String, distanceMeters: Long): String {
    val parts = mutableListOf<String>()
    if (stops > 0) parts.add("$stops 站")
    if (days.isNotBlank()) parts.add(days)
    if (distanceMeters > 0) parts.add(TravelFormat.distance(distanceMeters))
    return if (parts.isEmpty()) "还没有地点" else parts.joinToString(" · ")
}

/**
 * 空态小卡片（虚线边框），用于「添加下一站」这类占位。
 *
 * @param text 文案
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun DashedAddCard(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
