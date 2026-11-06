package com.focuslist.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
 * Key 的保存是**显式的**：默认只读展示已保存的值，点「编辑」才进入输入态，
 * 点「确定」才真正写入本机偏好，并立刻回显「✓ 已保存」——不会边打字边悄悄存。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 */
@Composable
fun MapSearchSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val travelPrefs = remember { TravelPrefs(context) }
    // savedKey 是「已经落盘的那一份」，draft 只是编辑中的草稿，两者分开才谈得上取消 / 确定
    var savedKey by remember { mutableStateOf(travelPrefs.amapKey) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(savedKey) }
    // 刚保存过：给一条持续可见的回执，用户能确认「确实存了」
    var savedJustNow by remember { mutableStateOf(false) }

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
                        text = if (editing) {
                            "编辑中"
                        } else if (savedKey.isBlank()) {
                            "未配置"
                        } else {
                            "已配置"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (editing) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = "配置后可以在行程里按名称搜索景点、酒店等具体地点；留空则只用内置目的地与地图点选。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            savedJustNow = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        placeholder = { Text("在高德开放平台申请的 Web 服务 Key") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = "改完点「确定」才会保存；留空并确定可清除已保存的 Key。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        TextButton(onClick = {
                            draft = savedKey
                            editing = false
                            savedJustNow = false
                        }) { Text("取消") }
                        Button(
                            onClick = {
                                val next = draft.trim()
                                travelPrefs.amapKey = next
                                savedKey = next
                                draft = next
                                editing = false
                                savedJustNow = true
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(if (draft.isBlank() && savedKey.isNotBlank()) "清空并保存" else "确定")
                        }
                    }
                } else {
                    // 只读态：把保存结果明确写出来，Key 本身打码，避免整串暴露
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (savedKey.isBlank()) "当前：未保存任何 Key" else "当前：${maskKey(savedKey)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (savedJustNow) {
                                Text(
                                    text = "✓ 已保存，下次搜索立即生效",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else {
                                Text(
                                    text = if (savedKey.isBlank()) "点「编辑」填一个 Key" else "已保存于本机设置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        TextButton(onClick = {
                            draft = savedKey
                            savedJustNow = false
                            editing = true
                        }) { Text("编辑") }
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

/**
 * Key 打码：只留后 4 位便于核对是不是刚填的那一个，中间一律用圆点。
 *
 * @param key 原始 Key
 * @return 打码后的展示串
 */
private fun maskKey(key: String): String {
    if (key.length <= 4) return "•".repeat(key.length)
    return "•".repeat(8) + key.takeLast(4)
}
