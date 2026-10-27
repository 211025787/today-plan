package com.focuslist.app.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import java.util.Calendar

/**
 * 日期选择对话框封装。
 *
 * @param initialMillis 初始选中日期时间戳；null 表示今天
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调，参数为所选日期 0 点时间戳
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogWrapper(initialMillis: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis ?: System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onConfirm)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = state)
    }
}

/**
 * 时间选择对话框封装。
 *
 * @param initialMillis 提供初始时分的时间戳；null 表示 09:00
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调，参数为（小时, 分钟）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogWrapper(initialMillis: Long?, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val cal = Calendar.getInstance()
    if (initialMillis != null) {
        cal.timeInMillis = initialMillis
    } else {
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
    }
    var hour by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = {
                hour = state.hour
                minute = state.minute
                onConfirm(state.hour, state.minute)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 将"日期 0 点 + 时分"合成完整时间戳。
 *
 * @param dayStart 当天 0 点时间戳
 * @param hour 小时（0-23）
 * @param minute 分钟（0-59）
 * @return 合成后的毫秒时间戳
 */
fun combineDayAndTime(dayStart: Long, hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dayStart
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}