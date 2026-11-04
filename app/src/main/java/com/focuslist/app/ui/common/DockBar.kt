package com.focuslist.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    Box(
        // 横向 padding 收窄到 2dp 让导航胶囊更长；不加 navigationBarsPadding，让桌宠可延伸覆盖系统导航条
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            // 间距 0dp 让导航胶囊紧贴桌宠，边缘覆盖桌宠白边
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            // 底部对齐：桌宠贴屏幕底部覆盖导航条，导航胶囊靠上避开导航条
            verticalAlignment = Alignment.Bottom
        ) {
            // 导航胶囊：加高到 72dp；navigationBarsPadding 抬到导航条上方；offset 整体上移 16dp 并右移 6dp（在 glass 外侧，背景与图标一起移动）
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .navigationBarsPadding()
                    .offset(y = -16.dp, x = 6.dp)
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
            // 大肥鱼桌宠在所有主 Tab 常驻，与首页保持一致；放大到 140dp 完整露出头/身体/尾巴
            // 不再向右偏移，由导航胶囊紧贴覆盖桌宠左侧白边
            WhaleGirlPet(modifier = Modifier.size(140.dp))
        }
    }
}