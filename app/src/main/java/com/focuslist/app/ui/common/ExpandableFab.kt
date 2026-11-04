package com.focuslist.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 展开菜单项。
 *
 * @property icon 项图标（emoji 文本）
 * @property label 项文案（展示与无障碍提示）
 * @property onClick 点击回调
 */
data class FabMenuItem(val icon: String, val label: String, val onClick: () -> Unit)

/**
 * 锚点下方右对齐的弹出位置提供器：菜单从按钮右下角向下展开，右边缘与按钮对齐。
 */
private val BelowEndProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        x = anchorBounds.right - popupContentSize.width,
        y = anchorBounds.bottom
    )
}

/**
 * 右上角功能按钮：渐变圆形（Apps 网格图标），点击向下展开玻璃胶囊菜单。
 *
 * 菜单项为 emoji 圆 + 文字标签，依次向下排列；点击任意项收起菜单并执行回调。
 * 用 Popup 实现浮层，不受 TopAppBar 裁剪限制。
 *
 * @param items 展开菜单项
 * @param modifier 布局修饰符
 */
@Composable
fun ExpandableFab(items: List<FabMenuItem>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // 主按钮渐变色：主题色到稍透亮，配阴影突出悬浮感
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        // 渐变圆形功能按钮：Apps 网格图标表示功能集合（非加号）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(primary, primary.copy(alpha = 0.72f)),
                        start = Offset(0f, 0f),
                        end = Offset.Infinite
                    )
                )
                .clickable { expanded = !expanded }
                .semantics { contentDescription = "功能菜单" }
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        // 向下展开的玻璃胶囊菜单：Popup 浮层不受父容器裁剪
        if (expanded) {
            Popup(
                popupPositionProvider = BelowEndProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .glass(cornerRadius = 20.dp, fillAlpha = 0.95f)
                        .padding(8.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = false
                                    item.onClick()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .semantics { contentDescription = item.label }
                        ) {
                            // emoji 小圆：主题色淡底衬托
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(primary.copy(alpha = 0.16f))
                            ) {
                                Text(text = item.icon, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}