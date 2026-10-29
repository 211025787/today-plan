package com.focuslist.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 展开菜单项。
 *
 * @property icon 项图标（emoji 文本）
 * @property label 项文案
 * @property onClick 点击回调
 */
data class FabMenuItem(val icon: String, val label: String, val onClick: () -> Unit)

/**
 * 圆点悬浮按钮：点击后动画向上展开功能菜单（添加计划、备注等）。
 *
 * 注意：不使用全屏遮罩——遮罩若放在本组件内部会被裁剪到 FAB 容器导致菜单错位；
 * 展开后点击任意菜单项或主圆点即可收起。
 *
 * @param items 展开菜单项
 * @param modifier 布局修饰符
 */
@Composable
fun ExpandableFab(items: List<FabMenuItem>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    // 菜单列：右对齐，主圆点在最下方，菜单项向上依次弹出
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items.forEachIndexed { index, item ->
            AnimatedVisibility(
                visible = expanded,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(200, delayMillis = index * 40)
                ) + fadeIn(tween(200, delayMillis = index * 40)) + scaleIn(
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = tween(200, delayMillis = index * 40)
                ),
                exit = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(150)) +
                    fadeOut(tween(150)) + scaleOut(transformOrigin = TransformOrigin(1f, 1f), animationSpec = tween(150))
            ) {
                // 纯图标小圆按钮（label 仅作无障碍提示）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .glass(cornerRadius = 22.dp)
                        .semantics { contentDescription = item.label }
                        .clickable {
                            expanded = false
                            item.onClick()
                        }
                ) {
                    Text(text = item.icon, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // 主圆点：展开时变为关闭图标
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { expanded = !expanded }
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "收起菜单" else "展开菜单",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}