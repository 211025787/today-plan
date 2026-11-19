package com.focuslist.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Dock 式底部导航项。
 *
 * @property route 路由名
 * @property label 无障碍标签
 * @property icon 图标
 */
data class DockItem(val route: String, val label: String, val icon: ImageVector)

/**
 * iOS Dock 风格悬浮底部导航：玻璃胶囊 + 圆形图标，选中项主色圆底高亮。
 * 右侧附带大肥鱼桌宠（DeepSeek 鲸鱼娘），与导航栏同高，可拖动互动。
 *
 * @param items 导航项列表
 * @param currentRoute 当前路由
 * @param onSelect 选择回调
 */
@Composable
fun GlassDockBar(items: List<DockItem>, currentRoute: String?, onSelect: (DockItem) -> Unit) {
    // 桌宠是独立浮层；它离原位越远，Dock 右侧留白越小，胶囊会自然铺开、图标居中
    var petOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val awayDp = with(density) {
        maxOf((-petOffset.x).coerceAtLeast(0f), (-petOffset.y).coerceAtLeast(0f)).toDp()
    }
    val progress = (awayDp / 120.dp).coerceIn(0f, 1f)
    // 宽度保持固定，只做水平位移：桌宠离得越远，胶囊越往屏幕中间靠
    val dockShift by animateDpAsState(
        targetValue = 12.dp + 58.dp * progress,
        animationSpec = tween(180),
        label = "dockShift"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // 右侧给桌宠留出固定 140dp，胶囊宽度因此不会随桌宠拖动变化
                .fillMaxWidth()
                .padding(end = PET_SIZE)
                .height(72.dp)
                .navigationBarsPadding()
                .offset(y = (-16).dp, x = dockShift)
                .glass(cornerRadius = 36.dp)
                .padding(horizontal = 8.dp)
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelect(item) }
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 桌宠浮层：贴右下角，可自由拖动，不影响 Dock 布局
        WhaleGirlPet(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(PET_SIZE),
            onOffsetChange = { petOffset = it }
        )
    }
}

/** 桌宠占位尺寸：胶囊右侧为它留出的固定宽度 */
private val PET_SIZE = 140.dp
