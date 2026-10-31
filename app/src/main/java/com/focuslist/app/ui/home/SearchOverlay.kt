package com.focuslist.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.ui.theme.parseThemeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 搜索结果里备注日期的展示格式 */
private val searchDateFmt = SimpleDateFormat("M月d日", Locale.getDefault())

/**
 * 顶栏内联搜索框：无边框、透明底，专注输入本身。
 *
 * @param value 当前输入
 * @param onValueChange 输入变化回调
 * @param onClose 退出搜索态回调
 * @param modifier 布局修饰
 */
@Composable
fun HomeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 进入搜索即弹出软键盘，省一次点击
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = {
            Text(
                text = "搜索计划与备注",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * 搜索结果列表：计划与备注混排，命中片段高亮。
 *
 * @param results 排序后的命中项
 * @param query 当前关键词（决定是否展示空态提示）
 * @param onOpenPlan 点击计划：跳转到该计划生效日
 * @param onOpenNote 点击备注：打开备注详情（备注 ID，关联日期）
 * @param modifier 布局修饰
 */
@Composable
fun SearchResultList(
    results: List<HomeSearchHit>,
    query: String,
    onOpenPlan: (PlanEntity) -> Unit,
    onOpenNote: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (query.isBlank()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "输入关键词，搜索计划标题、说明与备注内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 40.dp)
            )
        }
        return
    }
    if (results.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "没有找到「$query」相关的内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 40.dp)
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "count") {
            Text(
                text = "共 ${results.size} 条结果",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(results, key = { hit ->
            when (hit) {
                is HomeSearchHit.PlanHit -> "plan_${hit.plan.id}"
                is HomeSearchHit.NoteHit -> "note_${hit.note.id}"
            }
        }) { hit ->
            when (hit) {
                is HomeSearchHit.PlanHit -> SearchResultCard(
                    badge = hit.plan.icon,
                    badgeColor = parseThemeColor(hit.plan.colorHex),
                    title = hit.title,
                    ranges = hit.ranges,
                    subtitle = hit.snippet,
                    tag = "计划",
                    onClick = { onOpenPlan(hit.plan) }
                )
                is HomeSearchHit.NoteHit -> SearchResultCard(
                    badge = "📝",
                    badgeColor = MaterialTheme.colorScheme.secondary,
                    title = hit.title,
                    ranges = hit.ranges,
                    subtitle = hit.snippet.ifBlank { "空备注" },
                    tag = if (hit.note.date > 0) searchDateFmt.format(Date(hit.note.date)) else "备注",
                    onClick = { onOpenNote(hit.note.id, hit.note.date) }
                )
            }
        }
        item(key = "tail") { Spacer(modifier = Modifier.height(88.dp).navigationBarsPadding()) }
    }
}

/**
 * 单条搜索结果卡片：左侧色块徽标 + 高亮标题 + 灰色摘要。
 *
 * @param badge 徽标文字（emoji）
 * @param badgeColor 徽标底色
 * @param title 主标题
 * @param ranges 标题内的高亮区间
 * @param subtitle 摘要
 * @param tag 右上角类型标签
 * @param onClick 点击回调
 */
@Composable
private fun SearchResultCard(
    badge: String,
    badgeColor: Color,
    title: String,
    ranges: List<IntRange>,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    val styled = buildAnnotatedString {
        append(title)
        ranges.forEach { r ->
            val start = r.first.coerceIn(0, title.length)
            val end = (r.last + 1).coerceIn(start, title.length)
            if (end > start) {
                addStyle(
                    SpanStyle(background = highlightBg, color = highlightFg, fontWeight = FontWeight.Bold),
                    start,
                    end
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = badge, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = styled,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
