package com.focuslist.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslist.app.util.WeekDay
import com.focuslist.app.util.WeekStrip

/**
 * 当日卡片顶部的周条：一周 7 天 + 本周小结，点左右箭头整周前后翻。
 *
 * 存在的意义是省掉「返回日历 → 找那天 → 再点进去」这三步：
 * 看昨天的记录、把明天的计划提前看一眼，都只需要点一下。
 *
 * @param days 周一到周日 7 天数据
 * @param selected 当前选中日 0 点时间戳
 * @param summary 本周小结文案
 * @param onSelect 点某一天的回调
 * @param onShiftWeek 翻周回调（-1 上一周 / +1 下一周）
 * @param modifier 外部布局修饰
 */
@Composable
fun WeekStripBar(
    days: List<WeekDay>,
    selected: Long,
    summary: String,
    onSelect: (Long) -> Unit,
    onShiftWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            WeekArrow(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = "上一周",
                onClick = { onShiftWeek(-1) }
            )
            days.forEach { day ->
                DayCell(
                    day = day,
                    selected = day.start == selected,
                    onClick = { onSelect(day.start) },
                    modifier = Modifier.weight(1f)
                )
            }
            WeekArrow(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = "下一周",
                onClick = { onShiftWeek(1) }
            )
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )
    }
}

/**
 * 周条上的一天：星期 + 日期圆点，圆点颜色即完成度。
 *
 * @param day 当天数据
 * @param selected 是否为当前选中日
 * @param onClick 点击回调
 * @param modifier 外部布局修饰
 */
@Composable
private fun DayCell(
    day: WeekDay,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // 四档由浅到深，达标的当天最实；没有记录的日子只是一点点灰，不形成噪音
    val fill = when (day.level) {
        WeekStrip.LEVEL_STARTED -> scheme.primary.copy(alpha = 0.16f)
        WeekStrip.LEVEL_HALF -> scheme.primary.copy(alpha = 0.34f)
        WeekStrip.LEVEL_DONE -> scheme.primary
        else -> scheme.onSurfaceVariant.copy(alpha = 0.08f)
    }
    val textColor = when (day.level) {
        WeekStrip.LEVEL_DONE -> scheme.onPrimary
        WeekStrip.LEVEL_HALF -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = day.weekdayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isToday) scheme.primary else scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(fill)
                .then(circleBorder(day, selected, scheme.primary))
        ) {
            Text(
                text = "${day.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/**
 * 圆点描边：选中日最醒目（主色实线），今天次之（主色淡线），其余不描边。
 *
 * @param day 当天数据
 * @param selected 是否为当前选中日
 * @param primary 主色
 * @return 描边修饰符
 */
private fun circleBorder(day: WeekDay, selected: Boolean, primary: Color): Modifier {
    return when {
        selected -> Modifier.border(1.5.dp, primary, CircleShape)
        day.isToday -> Modifier.border(1.dp, primary.copy(alpha = 0.55f), CircleShape)
        else -> Modifier
    }
}

/**
 * 翻周箭头：透明的点击区域，避免挤压 7 个日期。
 *
 * @param icon 图标
 * @param label 无障碍文案
 * @param onClick 点击回调
 */
@Composable
private fun WeekArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}
