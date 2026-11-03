package com.focuslist.app.ui.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.data.local.entity.TripStopEntity
import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelFormat
import com.focuslist.app.util.TravelGeo
import com.focuslist.app.util.TravelPlace
import com.focuslist.app.util.TravelPlaces
import kotlinx.coroutines.delay

/**
 * 地点搜索弹层：内置目的地即时搜索 + 地图点选入口。
 *
 * 默认聚焦输入框并弹出键盘；空关键词时展示热门与「离上一站最近」，
 * 找不到就一键切到地图点选，任意坐标都能落点。
 *
 * @param lastStop 上一站，用于显示距离与「附近」推荐
 * @param onDismiss 关闭回调
 * @param onPickPlace 选中一个内置地点
 * @param onPickOnMap 切到地图点选
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchSheet(
    lastStop: TripStopEntity?,
    onDismiss: () -> Unit,
    onPickPlace: (TravelPlace) -> Unit,
    onPickOnMap: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val results = remember(query) { TravelPlaces.search(query, 30) }
    val nearby = remember(lastStop?.id, lastStop?.lat, lastStop?.lon) {
        if (lastStop == null) {
            emptyList()
        } else {
            val origin = LatLon(lastStop.lat, lastStop.lon)
            TravelPlaces.all.asSequence()
                .filter { it.name != lastStop.name }
                .map { it to TravelGeo.distanceMeters(origin, LatLon(it.lat, it.lon)) }
                .sortedBy { it.second }
                .take(6)
                .toList()
        }
    }

    LaunchedEffect(Unit) {
        delay(320)
        // 弹层动画期间焦点节点可能还没挂上，失败就交给用户点一下输入框
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Text(
                text = "搜索地点",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "选一个目的地，接着安排停留和交通",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("城市、古镇、景区，如：成都 / 乌镇 / 京都") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (query.isBlank()) {
                    item {
                        TravelSectionTitle("热门目的地")
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(TravelPlaces.popular().chunked(3)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { place ->
                                Box(modifier = Modifier.weight(1f)) {
                                    TravelChip(
                                        text = place.name,
                                        selected = false,
                                        color = MaterialTheme.colorScheme.primary,
                                        onClick = { onPickPlace(place) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (nearby.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            TravelSectionTitle("离「${lastStop?.name}」最近")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(nearby.chunked(2)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (place, distance) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                            .clickable { onPickPlace(place) }
                                            .padding(horizontal = 10.dp, vertical = 9.dp)
                                    ) {
                                        Text(place.kind.emoji, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = place.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = TravelFormat.distance(distance.toLong()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                repeat(2 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SearchTipCard()
                    }
                } else {
                    if (results.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("没有找到「$query」", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "内置目的地里没有这个名字，可以在下面切到地图上直接点选。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(results, key = { it.name }) { place ->
                            PlaceRow(
                                place = place,
                                lastStop = lastStop,
                                onClick = { onPickPlace(place) }
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        SearchTipCard()
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onPickOnMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("在地图上点选位置")
                }
            }
        }
    }
}

/**
 * 单个搜索结果行。
 *
 * @param place 地点
 * @param lastStop 上一站（用于距离）
 * @param onClick 点击回调
 */
@Composable
private fun PlaceRow(place: TravelPlace, lastStop: TripStopEntity?, onClick: () -> Unit) {
    val distance = remember(place, lastStop?.id) {
        lastStop?.let {
            TravelGeo.distanceMeters(LatLon(it.lat, it.lon), LatLon(place.lat, place.lon))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        ) {
            Text(place.kind.emoji, fontSize = 17.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${place.region} · ${place.kind.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (distance != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = TravelFormat.distance(distance.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 搜索弹层底部提示。
 */
@Composable
private fun SearchTipCard() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("💡", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "清单里没有？点「地图选点」，或在地图上长按任意位置直接落点。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
