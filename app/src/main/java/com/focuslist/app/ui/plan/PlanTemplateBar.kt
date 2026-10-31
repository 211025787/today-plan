package com.focuslist.app.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.PlanTemplate
import com.focuslist.app.util.PlanTemplates

/**
 * 模板条：横向滚动的模板 chip，点一下填入表单，长按删除。
 *
 * @param templates 模板列表（已排好序）
 * @param onPick 点击回调（把模板配置填入当前表单）
 * @param onDelete 长按回调（删除该模板）
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlanTemplateBar(
    templates: List<PlanTemplate>,
    onPick: (PlanTemplate) -> Unit,
    onDelete: (PlanTemplate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (templates.isEmpty()) return
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "常用模板",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "点一下填入 · 长按删除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            templates.forEach { template ->
                TemplateChip(
                    template = template,
                    onClick = { onPick(template) },
                    onLongClick = { onDelete(template) }
                )
            }
        }
    }
}

/**
 * 单个模板 chip：图标 + 名称 + 一句话配置说明，底色取模板自身主题色。
 *
 * @param template 模板
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateChip(template: PlanTemplate, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(parseThemeColor(template.colorHex).copy(alpha = 0.14f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(template.icon, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = template.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = PlanTemplates.describe(template),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
