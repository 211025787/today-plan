package com.focuslist.app.ui.travel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.data.local.entity.TripStopEntity
import com.focuslist.app.data.prefs.TravelPrefs
import com.focuslist.app.data.travel.TravelPoiClient
import com.focuslist.app.data.travel.TravelRouteClient
import com.focuslist.app.ui.common.BottomFadeEdge
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.common.TopFadeEdge
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.Gcj02
import com.focuslist.app.util.Ics
import com.focuslist.app.util.IcsTrip
import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelFormat
import com.focuslist.app.util.TravelGeo
import com.focuslist.app.util.TravelMode
import com.focuslist.app.util.TravelPlace
import com.focuslist.app.util.TravelSummaries
import com.focuslist.app.util.TravelTemplates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 旅游规划页：全屏地图 + 顶部行程标题 / 搜索 + 底部横向行程条。
 *
 * 交互主线：
 * 1. 搜索地点（或长按地图落点）→ 设置停留时长；
 * 2. 选择从上一站过来的交通方式，飞机 / 高铁 / 火车填站点，公交填线路，地铁多选线路；
 * 3. 公交 / 大巴 / 自驾 / 骑行 / 步行自动取真实道路折线，其余方式画弧线；
 * 4. 地图上连线标注方式与时长，地点标注停留时长。
 *
 * @param container 依赖容器
 * @param focusTripId 从首页 FAB 进入时指定的行程 ID；<=0 表示不指定，沿用上次活跃行程
 * @param onBack 返回上一页回调（边缘滑动手势松手 / 系统返回键时调用）
 */
@Composable
fun TravelScreen(
    container: AppContainer,
    focusTripId: Long = -1L,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { TravelPrefs(context) }
    val routeClient = remember {
        TravelRouteClient { prefs.routeEndpoint.ifBlank { TravelRouteClient.DEFAULT_ENDPOINT } }
    }
    val poiClient = remember { TravelPoiClient { prefs.amapKey } }
    val viewModel: TravelViewModel = viewModel {
        TravelViewModel(container.tripRepository, prefs, routeClient)
    }
    // 从首页 FAB 带行程 ID 进入时切到该行程；<=0 表示不指定，沿用上次活跃行程
    LaunchedEffect(focusTripId) {
        if (focusTripId > 0L) viewModel.selectTrip(focusTripId)
    }
    val trips by viewModel.trips.collectAsState()
    val plan by viewModel.plan.collectAsState()
    val ready by viewModel.ready.collectAsState()
    val tripColor = plan.trip?.let { parseThemeColor(it.colorHex) } ?: MaterialTheme.colorScheme.primary
    val mapState = remember { TravelMapState() }
    val tileStore = remember { TravelTileStore(context) }
    val density = LocalDensity.current

    var sheet by remember { mutableStateOf<TravelSheet?>(null) }
    var creatingTrip by remember { mutableStateOf(false) }
    var editingTrip by remember { mutableStateOf<TripEntity?>(null) }
    var picking by remember { mutableStateOf(false) }

    // 导出行程到系统日历：一个行程 = 一个覆盖整段日期的整天事件
    val exportScope = rememberCoroutineScope()
    var pendingExportTrip by remember { mutableStateOf<TripEntity?>(null) }
    val icsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val trip = pendingExportTrip
        pendingExportTrip = null
        if (uri != null && trip != null) {
            exportScope.launch {
                val ok = runCatching {
                    val stops = container.tripRepository.observeStops(trip.id).first()
                    val icsTrip = IcsTrip(
                        id = trip.id,
                        title = trip.title,
                        note = trip.note,
                        start = trip.startDate,
                        end = trip.endDate,
                        stops = stops.map { it.name }
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Ics.forTrip(icsTrip).toByteArray())
                    } ?: error("无法写入所选位置")
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) "已导出「${trip.title}」到日历文件" else "导出失败，请换一个位置再试",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 有弹层 / 选点态时，返回键先收起最上层，不要一下退出整个地图页
    val overlaysOpen = sheet != null || creatingTrip || editingTrip != null || picking
    BackHandler(enabled = overlaysOpen) {
        sheet = null
        creatingTrip = false
        editingTrip = null
        picking = false
    }
    // 系统侧滑 / 返回键：只接收「完成」这一次指令，进度全部忽略，页面一点都不跟手。
    // 注册预测式回调是为了告诉系统「这个返回由应用接管」，避免系统再补一层窗口级
    // 跟手预览；手势中途放弃则协程被取消，onBack 不会执行。
    // 收到指令后由 NavHost 播放 popExitTransition，整屏一次性渐进淡出。
    PredictiveBackHandler(enabled = !overlaysOpen) { progress ->
        progress.collect { /* 只吞进度，不做任何位移 */ }
        onBack()
    }

    val stopIds = plan.stops.map { it.id }
    LaunchedEffect(plan.trip?.id, stopIds, mapState.viewSize) {
        if (plan.stops.isNotEmpty()) {
            val gcj = plan.stops.map { Gcj02.fromWgs(it.lat, it.lon) }
            mapState.fit(
                points = gcj,
                topPx = with(density) { 184.dp.toPx() },
                bottomPx = with(density) { 248.dp.toPx() },
                sidePx = with(density) { 92.dp.toPx() }
            )
        } else {
            // 没有地点时给一个稳定的中国视野，不沿用上一趟行程的取景
            mapState.moveTo(34.8, 104.0, 4.8f)
        }
    }

    val summary = TravelSummaries.of(
        stayMinutes = plan.stops.map { it.stayMinutes },
        legMinutes = plan.legs.map { it.durationMinutes },
        distances = plan.legs.map { it.distanceMeters }
    )
    val meta = travelMetaText(
        stops = plan.stops.size,
        days = if (summary.approxDays > 0) "约 ${summary.approxDays} 天" else "",
        distanceMeters = summary.distanceMeters
    )

    // 行程被删掉时，站点 / 搜索 / 行程单弹层一起收起来，避免停在空弹层上
    LaunchedEffect(plan.trip?.id, sheet) {
        if (plan.trip == null) {
            when (sheet) {
                is TravelSheet.Stop, TravelSheet.Search, TravelSheet.Plan -> sheet = null
                else -> Unit
            }
        }
    }

    // 旅游地图页只是一个普通的全屏页面：进出过渡由 NavHost 的转场负责，
    // 自身不做跟手位移、不画「上一页」，也不依赖任何下层透出。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        TravelMap(
            plan = plan,
            color = tripColor,
            state = mapState,
            tiles = tileStore,
            picking = picking,
            bottomInset = 206.dp,
            onStopClick = { stop -> sheet = TravelSheet.Stop(StopTarget.Existing(stop.id), false) },
            onLegClick = { index ->
                val destination = plan.stops.getOrNull(index + 1)
                if (destination != null) {
                    sheet = TravelSheet.Stop(StopTarget.Existing(destination.id), true)
                }
            },
            onPickPoint = { latLon ->
                picking = false
                sheet = TravelSheet.Stop(StopTarget.MapPoint(latLon.lat, latLon.lon), false)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 渐变遮罩：只覆盖地图与路线内容，按钮/顶栏/行程条在渐变之后绘制，浮于渐变之上保持可见
        BottomFadeEdge(modifier = Modifier.align(Alignment.BottomCenter))
        TopFadeEdge(modifier = Modifier.align(Alignment.TopCenter))

        // 顶部：标题 + 地图库 / 添加 + 搜索条
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TripHeaderCard(
                    trip = plan.trip,
                    meta = meta,
                    color = tripColor,
                    modifier = Modifier.weight(1f),
                    onClick = { sheet = TravelSheet.Library }
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlassCircleButton(
                    icon = Icons.Default.List,
                    description = "我的行程",
                    onClick = { sheet = TravelSheet.Library },
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(tripColor)
                        .clickable {
                            if (plan.hasTrip) {
                                sheet = TravelSheet.Search
                            } else {
                                sheet = TravelSheet.Library
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加地点",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            SearchPill(
                hasTrip = plan.hasTrip,
                picking = picking,
                color = tripColor,
                onClick = {
                    if (picking) {
                        picking = false
                    } else if (plan.hasTrip) {
                        sheet = TravelSheet.Search
                    } else {
                        sheet = TravelSheet.Library
                    }
                },
                onPickOnMap = {
                    if (plan.hasTrip) picking = !picking else sheet = TravelSheet.Library
                }
            )
            AnimatedVisibility(visible = picking, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = "点按地图上任意位置，把它加为下一站",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 地图右侧的缩放 / 总览（空态时隐藏，避免压住居中的引导卡片）
        if (plan.hasStops) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassCircleButton(Icons.Default.Add, "放大") { mapState.zoomBy(1.6f) }
                GlassCircleButton(Icons.Default.Remove, "缩小") { mapState.zoomBy(1f / 1.6f) }
                GlassCircleButton(Icons.Default.Public, "显示全部地点") {
                    mapState.fit(
                        points = plan.stops.map { Gcj02.fromWgs(it.lat, it.lon) },
                        topPx = with(density) { 184.dp.toPx() },
                        bottomPx = with(density) { 248.dp.toPx() },
                        sidePx = with(density) { 92.dp.toPx() }
                    )
                }
            }
        }


        // 底部横向行程条：地点与交通交替，点任意一段去编辑
        if (plan.hasTrip) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 32.dp)
            ) {
                ItineraryStrip(
                    plan = plan,
                    color = tripColor,
                    meta = meta,
                    onOpenPlan = { sheet = TravelSheet.Plan },
                    onStopClick = { stop -> sheet = TravelSheet.Stop(StopTarget.Existing(stop.id), false) },
                    onLegClick = { index ->
                        val destination = plan.stops.getOrNull(index + 1)
                        if (destination != null) {
                            sheet = TravelSheet.Stop(StopTarget.Existing(destination.id), true)
                        }
                    },
                    onAdd = { sheet = TravelSheet.Search }
                )
            }
        }

        // 空态：没有行程 / 行程里还没有地点（首次读取完成后再显示，避免闪一下）
        if (ready && (!plan.hasTrip || plan.stops.isEmpty())) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 158.dp, bottom = 200.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyOverlay(
                    hasTrip = plan.hasTrip,
                    tripTitle = plan.trip?.title.orEmpty(),
                    color = tripColor,
                    onCreateTrip = { creatingTrip = true },
                    onOpenLibrary = { sheet = TravelSheet.Library },
                    onSearch = { sheet = TravelSheet.Search },
                    onPickOnMap = { picking = true },
                    onUseTemplate = { template ->
                        viewModel.createFromTemplate(template)
                        sheet = null
                    },
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                )
            }
        }
    }

    // ---- 弹层 ----
    when (val current = sheet) {
        TravelSheet.Library -> TripLibrarySheet(
            trips = trips,
            activeTripId = plan.trip?.id ?: 0L,
            routeEndpoint = prefs.routeEndpoint,
            onSaveRouteEndpoint = { prefs.routeEndpoint = it },
            onDismiss = { sheet = null },
            onSelect = {
                viewModel.selectTrip(it)
                sheet = null
            },
            onCreate = {
                sheet = null
                creatingTrip = true
            },
            onEdit = {
                sheet = null
                editingTrip = it
            },
            onDelete = { viewModel.deleteTrip(it.id) },
            onExport = { trip ->
                pendingExportTrip = trip
                icsLauncher.launch(Ics.tripFileNameOf(IcsTrip(id = trip.id, title = trip.title)))
            },
            onUseTemplate = {
                viewModel.createFromTemplate(it)
                sheet = null
            }
        )

        TravelSheet.Search -> PlaceSearchSheet(
            lastStop = plan.stops.lastOrNull(),
            onDismiss = { sheet = null },
            onPickPlace = { place ->
                sheet = TravelSheet.Stop(StopTarget.NewPlace(place), false)
            },
            onPickOnMap = {
                sheet = null
                picking = true
            },
            poiClient = poiClient
        )

        is TravelSheet.Stop -> {
            StopEditorSheet(
                target = current.target,
                plan = plan,
                color = tripColor,
                openTraffic = current.openTraffic,
                onDismiss = { sheet = null },
                onSave = { save ->
                    val existing = (current.target as? StopTarget.Existing)?.let { hit ->
                        plan.stops.firstOrNull { it.id == hit.stopId }
                    }
                    if (existing != null) {
                        viewModel.updateStop(
                            existing.copy(
                                name = save.name,
                                region = save.region,
                                stayMinutes = save.stayMinutes,
                                note = save.note
                            ),
                            save.arrival
                        )
                    } else {
                        viewModel.addStop(
                            place = TravelPlace(save.name, save.region, save.lat, save.lon),
                            stayMinutes = save.stayMinutes,
                            arrival = save.arrival
                        )
                    }
                    sheet = null
                },
                onDelete = {
                    val existing = (current.target as? StopTarget.Existing)?.let { hit ->
                        plan.stops.firstOrNull { it.id == hit.stopId }
                    }
                    if (existing != null) viewModel.deleteStop(existing)
                },
                planRoute = { mode, from, to -> viewModel.planRoute(mode, from, to) }
            )
        }

        TravelSheet.Plan -> TripPlanSheet(
            plan = plan,
            color = tripColor,
            onDismiss = { sheet = null },
            onEditStop = { stopId -> sheet = TravelSheet.Stop(StopTarget.Existing(stopId), false) },
            onEditLeg = { index ->
                val destination = plan.stops.getOrNull(index + 1)
                if (destination != null) {
                    sheet = TravelSheet.Stop(StopTarget.Existing(destination.id), true)
                }
            }
        )

        null -> Unit
    }

    if (creatingTrip) {
        TripEditDialog(
            initial = null,
            onDismiss = { creatingTrip = false },
            onConfirm = { title, emoji, colorHex ->
                viewModel.createTrip(title, emoji, colorHex)
                creatingTrip = false
            }
        )
    }
    editingTrip?.let { trip ->
        TripEditDialog(
            initial = trip,
            onDismiss = { editingTrip = null },
            onConfirm = { title, emoji, colorHex ->
                viewModel.updateTrip(trip.copy(title = title, emoji = emoji, colorHex = colorHex))
                editingTrip = null
            }
        )
    }
}

/**
 * 旅游页内部弹层状态。
 */
private sealed interface TravelSheet {

    /** 行程库 */
    data object Library : TravelSheet

    /** 地点搜索 */
    data object Search : TravelSheet

    /** 行程单 */
    data object Plan : TravelSheet

    /** 站点 / 交通编辑 */
    data class Stop(val target: StopTarget, val openTraffic: Boolean) : TravelSheet
}

/**
 * 顶部行程标题卡：emoji + 行程名 + 统计，点一下打开行程库。
 *
 * @param trip 当前行程
 * @param meta 统计文案
 * @param color 主题色
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun TripHeaderCard(
    trip: TripEntity?,
    meta: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.14f))
        ) {
            Text(trip?.emoji ?: "🏔", fontSize = 21.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "旅行规划",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trip?.title ?: "还没有行程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .size(15.dp)
                )
            }
            Text(
                text = if (trip == null) "点这里创建或选择一条行程" else meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 搜索条 / 选点提示条。
 *
 * @param hasTrip 是否有行程
 * @param picking 是否在选点
 * @param color 主题色
 * @param onClick 点击整条
 * @param onPickOnMap 点右侧图钉
 */
@Composable
private fun SearchPill(
    hasTrip: Boolean,
    picking: Boolean,
    color: Color,
    onClick: () -> Unit,
    onPickOnMap: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(
                width = 0.5.dp,
                color = if (picking) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                shape = RoundedCornerShape(23.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp)
    ) {
        Icon(
            imageVector = if (picking) Icons.Default.PinDrop else Icons.Default.Search,
            contentDescription = null,
            tint = if (picking) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = when {
                picking -> "点按地图选择位置 · 点这里取消"
                hasTrip -> "搜索地点，添加下一站"
                else -> "先创建一条行程"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (picking) color else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (picking) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "退出选点",
                tint = color,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(18.dp)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f))
                    .clickable(onClick = onPickOnMap)
            ) {
                Icon(
                    imageVector = Icons.Default.PinDrop,
                    contentDescription = "在地图上选点",
                    tint = color,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * 底部横向行程条：站点与交通交替排列。
 *
 * @param plan 当前行程
 * @param color 主题色
 * @param meta 统计文案
 * @param onOpenPlan 打开行程单
 * @param onStopClick 点击站点
 * @param onLegClick 点击某段交通（参数为起点站顺序）
 * @param onAdd 添加下一站
 */
@Composable
private fun ItineraryStrip(
    plan: TravelPlanUi,
    color: Color,
    meta: String,
    onOpenPlan: () -> Unit,
    onStopClick: (TripStopEntity) -> Unit,
    onLegClick: (Int) -> Unit,
    onAdd: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, fillAlpha = 0.93f) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "行程",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (plan.hasStops) meta else "点地点或连线可直接编辑",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenPlan)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "行程单",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(7.dp))
            if (plan.stops.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashedAddCard(text = "添加第一个地点", onClick = onAdd)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "也可以长按地图任意位置落点",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 1.dp)
                ) {
                    plan.stops.forEachIndexed { index, stop ->
                        item(key = "stop-${stop.id}") {
                            StopChip(
                                stop = stop,
                                index = index,
                                color = color,
                                onClick = { onStopClick(stop) }
                            )
                        }
                        if (index < plan.stops.size - 1) {
                            item(key = "leg-${stop.id}") {
                                LegChip(
                                    leg = plan.legAfter(index),
                                    color = color,
                                    onClick = { onLegClick(index) }
                                )
                            }
                        }
                    }
                    item(key = "add") {
                        Box(modifier = Modifier.padding(start = 6.dp)) {
                            DashedAddCard(text = "下一站", onClick = onAdd)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 行程条里的一个地点。
 *
 * @param stop 站点
 * @param index 顺序
 * @param color 主题色
 * @param onClick 点击
 */
@Composable
private fun StopChip(stop: TripStopEntity, index: Int, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 104.dp, max = 138.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color)
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = stop.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (stop.stayMinutes > 0) {
                "停留 ${TravelFormat.duration(stop.stayMinutes)}"
            } else {
                "路过"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 行程条里的一段交通：一条虚线把两侧地点连起来，中间是方式 + 时长。
 *
 * @param leg 交通
 * @param color 主题色
 * @param onClick 点击
 */
@Composable
private fun LegChip(
    leg: com.focuslist.app.data.local.entity.TripLegEntity?,
    color: Color,
    onClick: () -> Unit
) {
    val mode = TravelMode.of(leg?.mode)
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = color.copy(alpha = 0.32f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            Text(mode.emoji, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if ((leg?.durationMinutes ?: 0) > 0) {
                    TravelFormat.duration(leg?.durationMinutes ?: 0)
                } else {
                    "待定"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * 空态浮层：没有行程，或行程里还没有地点。
 *
 * @param hasTrip 是否有当前行程
 * @param tripTitle 当前行程标题
 * @param color 主题色
 * @param onCreateTrip 新建行程
 * @param onOpenLibrary 打开行程库
 * @param onSearch 搜索地点
 * @param onPickOnMap 地图选点
 * @param onUseTemplate 使用模板
 * @param modifier 修饰符
 */
@Composable
private fun EmptyOverlay(
    hasTrip: Boolean,
    tripTitle: String,
    color: Color,
    onCreateTrip: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSearch: () -> Unit,
    onPickOnMap: () -> Unit,
    onUseTemplate: (com.focuslist.app.util.TripTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
        cornerRadius = 26.dp,
        fillAlpha = 0.94f
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color.copy(alpha = 0.13f))
            ) {
                Icon(
                    imageVector = if (hasTrip) Icons.Default.Place else Icons.Default.TravelExplore,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasTrip) "「$tripTitle」还空着" else "把下一趟旅行画在地图上",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasTrip) {
                    "搜索一个目的地，或在地图上直接点选任意位置。"
                } else {
                    "搜索目的地、安排停留与交通方式，路线会自动连起来。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (hasTrip) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSearch,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("搜索地点")
                    }
                    TextButton(onClick = onPickOnMap) {
                        Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("地图选点")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCreateTrip,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("创建行程")
                    }
                    TextButton(onClick = onOpenLibrary) { Text("选择已有") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "或从模板开始",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TravelTemplates.all.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        row.forEach { template ->
                            val templateColor = parseThemeColor(template.colorHex)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(templateColor.copy(alpha = 0.10f))
                                    .border(1.dp, templateColor.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                                    .clickable { onUseTemplate(template) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                Text(template.emoji, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = template.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${template.stops.size} 站",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
