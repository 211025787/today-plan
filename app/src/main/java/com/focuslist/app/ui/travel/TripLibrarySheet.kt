package com.focuslist.app.ui.travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.ui.theme.FlagRed
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.TravelFormat
import com.focuslist.app.util.TravelMode
import com.focuslist.app.util.TravelSummaries
import com.focuslist.app.util.TripTemplate
import com.focuslist.app.util.TravelTemplates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 行程库里的更新时间格式 */
private val tripDateFmt = SimpleDateFormat("M月d日", Locale.CHINA)

/**
 * 行程库：切换 / 新建 / 编辑 / 删除行程，并可从模板一键开始。
 *
 * @param trips 全部行程
 * @param activeTripId 当前行程 ID
 * @param onDismiss 关闭回调
 * @param onSelect 切换到某条行程
 * @param onCreate 新建空行程
 * @param onEdit 编辑行程（标题 / 封面色）
 * @param onDelete 删除行程
 * @param onUseTemplate 用模板新建
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripLibrarySheet(
    trips: List<TripEntity>,
    activeTripId: Long,
    routeEndpoint: String = "",
    onSaveRouteEndpoint: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    onEdit: (TripEntity) -> Unit,
    onDelete: (TripEntity) -> Unit,
    onExport: (TripEntity) -> Unit,
    onUseTemplate: (TripTemplate) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<TripEntity?>(null) }
    var endpoint by remember(routeEndpoint) { mutableStateOf(routeEndpoint) }
    var editingEndpoint by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "我的行程",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "切换一趟，或从模板开始",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新建")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (trips.isEmpty()) {
                    item {
                        Text(
                            text = "还没有行程。选一个模板，或者点右上角「新建」从空白开始。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                items(trips, key = { it.id }) { trip ->
                    TripRow(
                        trip = trip,
                        active = trip.id == activeTripId,
                        onSelect = { onSelect(trip.id) },
                        onEdit = { onEdit(trip) },
                        onDelete = { pendingDelete = trip },
                        onExport = { onExport(trip) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "从模板开始",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(TravelTemplates.all.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { template ->
                            TemplateCard(
                                template = template,
                                onClick = { onUseTemplate(template) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    RouteEndpointRow(
                        endpoint = endpoint,
                        onClick = { editingEndpoint = true }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "行程只保存在本机；底图与真实道路路线需要联网。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    pendingDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${trip.title}」？") },
            text = { Text("行程里的地点与交通会一起删除，且不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(trip)
                    }
                ) { Text("删除", color = FlagRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (editingEndpoint) {
        var draft by remember { mutableStateOf(endpoint) }
        AlertDialog(
            onDismissRequest = { editingEndpoint = false },
            title = { Text("真实路线服务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "公交、大巴、自驾、骑行、步行的真实道路路线由 OSRM 服务计算。留空使用官方演示服务；访问不畅时可以换成自建或镜像地址。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.trim() },
                        placeholder = { Text("https://router.project-osrm.org") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        endpoint = draft.trim()
                        onSaveRouteEndpoint(endpoint)
                        editingEndpoint = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingEndpoint = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 行程库底部的「真实路线服务」入口。
 *
 * @param endpoint 当前服务地址；空表示默认
 * @param onClick 点击修改
 */
@Composable
private fun RouteEndpointRow(endpoint: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("真实路线服务", style = MaterialTheme.typography.labelMedium)
            Text(
                text = endpoint.ifBlank { "默认 OSRM（router.project-osrm.org）" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "修改",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 行程库中的一行。
 *
 * @param trip 行程
 * @param active 是否为当前行程
 * @param onSelect 切换
 * @param onEdit 编辑
 * @param onDelete 删除
 */
@Composable
private fun TripRow(
    trip: TripEntity,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val color = parseThemeColor(trip.colorHex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) color.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            )
            .border(
                width = 1.dp,
                color = if (active) color.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.14f))
        ) {
            Text(trip.emoji, fontSize = 19.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (active) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("当前", style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
            }
            Text(
                text = "最近编辑 ${tripDateFmt.format(Date(trip.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onExport) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = "导出到日历",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "编辑行程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除行程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 模板卡片。
 *
 * @param template 模板
 * @param onClick 使用模板
 * @param modifier 修饰符
 */
@Composable
private fun TemplateCard(template: TripTemplate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = parseThemeColor(template.colorHex)
    val stays = template.stops.map { it.stayMinutes }
    val travels = template.stops.map { it.travelMinutes }
    val summary = TravelSummaries.of(stays, travels, emptyList())
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(template.emoji, fontSize = 17.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = template.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = template.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${template.stops.size} 站 · 约 ${summary.approxDays} 天",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 行程单：竖排的「站点 → 交通 → 站点」时间线，点任意一行去修改。
 *
 * @param plan 当前行程
 * @param color 行程主题色
 * @param onDismiss 关闭回调
 * @param onEditStop 编辑站点
 * @param onEditLeg 编辑某段交通（参数为起点站顺序）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlanSheet(
    plan: TravelPlanUi,
    color: Color,
    onDismiss: () -> Unit,
    onEditStop: (Long) -> Unit,
    onEditLeg: (Int) -> Unit
) {
    val trip = plan.trip ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val summary = TravelSummaries.of(
        stayMinutes = plan.stops.map { it.stayMinutes },
        legMinutes = plan.legs.map { it.durationMinutes },
        distances = plan.legs.map { it.distanceMeters }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
            Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trip.emoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trip.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = travelMetaText(
                                stops = plan.stops.size,
                                days = if (summary.approxDays > 0) "约 ${summary.approxDays} 天" else "",
                                distanceMeters = summary.distanceMeters
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanStat("停留", TravelFormat.duration(summary.stayMinutes), Modifier.weight(1f))
                    PlanStat("在路上", TravelFormat.duration(summary.travelMinutes), Modifier.weight(1f))
                    PlanStat("里程", TravelFormat.distance(summary.distanceMeters), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
            ) {
                if (plan.stops.isEmpty()) {
                    Text(
                        text = "这条行程还没有地点。回到地图上搜索，或长按地图任意位置添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                plan.stops.forEachIndexed { index, stop ->
                    PlanStopRow(
                        index = index,
                        name = stop.name,
                        stayMinutes = stop.stayMinutes,
                        color = color,
                        onClick = { onEditStop(stop.id) }
                    )
                    if (index < plan.stops.size - 1) {
                        val leg = plan.legAfter(index)
                        PlanLegRow(
                            leg = leg,
                            color = color,
                            onClick = { onEditLeg(index) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "点任意一行可以修改；交通方式与站点会同步显示在地图上。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 行程单里的一站。
 *
 * @param index 顺序（0 开始）
 * @param name 名称
 * @param stayMinutes 停留
 * @param color 主题色
 * @param onClick 点击
 */
@Composable
private fun PlanStopRow(index: Int, name: String, stayMinutes: Int, color: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (stayMinutes > 0) TravelFormat.stay(stayMinutes) else "路过，不作停留",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * 行程单里的一段交通：左侧虚线 + 方式图标，右侧是方式、时长、站点与里程。
 *
 * @param leg 交通；null 表示缺失（数据异常时给个占位）
 * @param color 主题色
 * @param onClick 点击
 */
@Composable
private fun PlanLegRow(leg: com.focuslist.app.data.local.entity.TripLegEntity?, color: Color, onClick: () -> Unit) {
    val mode = TravelMode.of(leg?.mode)
    val detail = if (leg == null) {
        "选择交通方式"
    } else {
        TravelFormat.transfer(leg.fromStation, leg.toStation, leg.serviceNo)
    }
    val lines = leg?.lines.orEmpty().split('|').filter { it.isNotBlank() }.joinToString(" → ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(width = 28.dp, height = 44.dp)) {
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    cap = StrokeCap.Round
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = mode.icon(),
                contentDescription = mode.label,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if ((leg?.durationMinutes ?: 0) > 0) {
                        TravelFormat.duration(leg?.durationMinutes ?: 0)
                    } else {
                        "时长待定"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (lines.isNotBlank()) {
                Text(
                    text = lines,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if ((leg?.distanceMeters ?: 0) > 0) {
            Text(
                text = TravelFormat.distance(leg?.distanceMeters ?: 0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 行程单顶部的统计小块。
 *
 * @param label 标题
 * @param value 值
 * @param modifier 修饰符
 */
@Composable
private fun PlanStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 新建 / 编辑行程对话框。
 *
 * @param initial 编辑时传入原行程；新建为 null
 * @param onDismiss 取消
 * @param onConfirm 确认（名称 / emoji / 色值）
 */
@Composable
fun TripEditDialog(
    initial: TripEntity?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, emoji: String, colorHex: String) -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var emoji by remember(initial?.id) { mutableStateOf(initial?.emoji ?: TravelEmojiPool.first()) }
    var colorHex by remember(initial?.id) { mutableStateOf(initial?.colorHex ?: TravelColorPool.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建行程" else "编辑行程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(20) },
                    label = { Text("行程名称") },
                    placeholder = { Text("如：川西小环线") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "封面",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TravelEmojiPool.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { item ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (item == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else Color.Transparent
                                        )
                                        .clickable { emoji = item }
                                ) {
                                    Text(item, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "路线颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TravelColorRow(selected = colorHex, onSelect = { colorHex = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) onConfirm(title.trim(), emoji, colorHex)
                }
            ) {
                if (title.isNotBlank()) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(if (initial == null) "创建" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
