package com.focuslist.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 导航胶囊
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp)
                    .glass(cornerRadius = 31.dp)
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
            // 大肥鱼桌宠只在首页常驻，避免遮挡行程地图/专注页；放大到 140dp 完整露出头/身体/尾巴
            if (currentRoute == "home") {
                WhaleGirlPet(modifier = Modifier.size(140.dp))
            }
        }
    }
}