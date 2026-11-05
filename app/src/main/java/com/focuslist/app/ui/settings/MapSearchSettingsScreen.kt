package com.focuslist.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focuslist.app.AppContainer
import com.focuslist.app.data.prefs.TravelPrefs
import com.focuslist.app.ui.common.GlassCard

/**
 * 地图与搜索设置页：高德 Web 服务 Key 的配置与说明。
 *
 * Key 只存在本机偏好里，用于行程搜索时按名称查景点、酒店等具体地点。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 */
@Composable
fun MapSearchSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val travelPrefs = remember { TravelPrefs(context) }
    var amapKey by remember { mutableStateOf(travelPrefs.amapKey) }

    SettingsDetailScreen(
        title = "地图与搜索",
        subtitle = "高德 Key 与行程地点搜索",
        onBack = onBack
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "高德地图 Key",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (amapKey.isBlank()) "未配置" else "已配置",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (amapKey.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Text(
                    text = "配置后可以在行程里按名称搜索景点、酒店等具体地点；留空则只用内置目的地与地图点选。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                OutlinedTextField(
                    value = amapKey,
                    onValueChange = {
                        amapKey = it
                        travelPrefs.amapKey = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    placeholder = { Text("在高德开放平台申请的 Web 服务 Key") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                if (amapKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            amapKey = ""
                            travelPrefs.amapKey = ""
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("清空 Key")
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("这个 Key 会联网吗", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "只有「搜索具体地点」和「真实道路路线」会用这个 Key 请求高德服务；" +
                        "行程、站点与交通记录本身始终只保存在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
