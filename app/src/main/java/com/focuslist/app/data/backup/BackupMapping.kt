package com.focuslist.app.data.backup

import com.focuslist.app.data.local.entity.CategoryEntity
import com.focuslist.app.data.local.entity.FocusRecordEntity
import com.focuslist.app.data.local.entity.HabitCheckEntity
import com.focuslist.app.data.local.entity.HabitEntity
import com.focuslist.app.data.local.entity.NoteAttachmentEntity
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import com.focuslist.app.data.local.entity.SubTaskEntity
import com.focuslist.app.data.local.entity.TagEntity
import com.focuslist.app.data.local.entity.TaskEntity
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.data.local.entity.TripLegEntity
import com.focuslist.app.data.local.entity.TripStopEntity
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.util.PlanTemplate

/** 备份 DTO → Room 实体 的映射集合（导入时使用）。 */

fun BackupTask.toEntity(): TaskEntity = TaskEntity(
    id = id, title = title, note = note, categoryId = categoryId, tags = tags,
    priority = priority, dueDate = dueDate, hasTime = hasTime, reminderAt = reminderAt,
    repeatRule = repeatRule, estimatedPomos = estimatedPomos, completedPomos = completedPomos,
    isDone = isDone, doneAt = doneAt, deletedAt = deletedAt, sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt
)

fun BackupSubTask.toEntity(): SubTaskEntity =
    SubTaskEntity(id = id, taskId = taskId, title = title, isDone = isDone, sortOrder = sortOrder)

fun BackupFocusRecord.toEntity(): FocusRecordEntity = FocusRecordEntity(
    id = id, taskId = taskId, startAt = startAt, endAt = endAt,
    durationSec = durationSec, phase = phase, completed = completed
)

fun BackupHabit.toEntity(): HabitEntity = HabitEntity(
    id = id, name = name, colorHex = colorHex, icon = icon, reminderAt = reminderAt,
    archived = archived, sortOrder = sortOrder, createdAt = createdAt
)

fun BackupHabitCheck.toEntity(): HabitCheckEntity =
    HabitCheckEntity(id = id, habitId = habitId, date = date, createdAt = createdAt)

fun BackupCategory.toEntity(): CategoryEntity =
    CategoryEntity(id = id, name = name, colorHex = colorHex, sortOrder = sortOrder, isDefault = isDefault)

fun BackupTag.toEntity(): TagEntity =
    TagEntity(id = id, name = name, colorHex = colorHex, createdAt = createdAt)

fun BackupPlan.toEntity(): PlanEntity = PlanEntity(
    id = id, title = title, icon = icon, colorHex = colorHex,
    dailyGoalMinutes = dailyGoalMinutes, startDate = startDate, endDate = endDate,
    note = note, archived = archived, sortOrder = sortOrder,
    cardSize = cardSize, cardCols = cardCols, cardRows = cardRows,
    cardX = cardX, cardY = cardY, createdAt = createdAt
)

fun BackupPlanLog.toEntity(): PlanLogEntity =
    PlanLogEntity(id = id, planId = planId, date = date, accumulatedSec = accumulatedSec, updatedAt = updatedAt)

fun BackupNote.toEntity(): QuickNoteEntity =
    QuickNoteEntity(id = id, title = title, content = content, date = date, createdAt = createdAt, updatedAt = updatedAt)

fun BackupAttachment.toEntity(): NoteAttachmentEntity =
    NoteAttachmentEntity(id = id, noteId = noteId, type = type, uri = uri, createdAt = createdAt)

fun BackupTrip.toEntity(): TripEntity = TripEntity(
    id = id, title = title, emoji = emoji, colorHex = colorHex, note = note,
    startDate = startDate, endDate = endDate,
    cardSize = cardSize, cardCols = cardCols, cardRows = cardRows,
    cardX = cardX, cardY = cardY,
    createdAt = createdAt, updatedAt = updatedAt
)

fun BackupTripStop.toEntity(): TripStopEntity = TripStopEntity(
    id = id, tripId = tripId, sortIndex = sortIndex, name = name, region = region,
    lat = lat, lon = lon, stayMinutes = stayMinutes, note = note
)

fun BackupTripLeg.toEntity(): TripLegEntity = TripLegEntity(
    id = id, tripId = tripId, sortIndex = sortIndex, mode = mode,
    durationMinutes = durationMinutes, fromStation = fromStation, toStation = toStation,
    serviceNo = serviceNo, lines = lines, distanceMeters = distanceMeters, polyline = polyline
)

/** 备份的偏好设置 → 应用内设置对象 */
fun BackupSettings.toTimerSettings(): TimerSettings = TimerSettings(
    focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes,
    longBreakMinutes = longBreakMinutes,
    longBreakInterval = longBreakInterval,
    autoStartBreak = autoStartBreak,
    autoStartNextPomo = autoStartNextPomo,
    soundEnabled = soundEnabled,
    vibrateEnabled = vibrateEnabled,
    chimeType = chimeType,
    alertSoundSec = alertSoundSec,
    alertVibrateSec = alertVibrateSec,
    capsuleEnabled = capsuleEnabled,
    capsuleColorHex = capsuleColorHex,
    capsuleAlpha = capsuleAlpha,
    capsuleWidthDp = capsuleWidthDp,
    capsuleRowHeightDp = capsuleRowHeightDp,
    capsuleTextColorHex = capsuleTextColorHex,
    capsuleTextAlpha = capsuleTextAlpha,
    capsuleProgressColorHex = capsuleProgressColorHex,
    capsuleProgressAlpha = capsuleProgressAlpha,
    capsuleX = capsuleX,
    capsuleY = capsuleY,
    strictMode = strictMode,
    themeMode = themeMode,
    themeColor = themeColor,
    whiteNoiseType = whiteNoiseType,
    planReachGoalSound = planReachGoalSound,
    planReachGoalVibrate = planReachGoalVibrate,
    dailyGoalMinutes = dailyGoalMinutes
)

/** 应用内设置对象 → 备份的偏好设置 */
fun TimerSettings.toBackupSettings(): BackupSettings = BackupSettings(
    focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes,
    longBreakMinutes = longBreakMinutes,
    longBreakInterval = longBreakInterval,
    autoStartBreak = autoStartBreak,
    autoStartNextPomo = autoStartNextPomo,
    soundEnabled = soundEnabled,
    vibrateEnabled = vibrateEnabled,
    chimeType = chimeType,
    alertSoundSec = alertSoundSec,
    alertVibrateSec = alertVibrateSec,
    capsuleEnabled = capsuleEnabled,
    capsuleColorHex = capsuleColorHex,
    capsuleAlpha = capsuleAlpha,
    capsuleWidthDp = capsuleWidthDp,
    capsuleRowHeightDp = capsuleRowHeightDp,
    capsuleTextColorHex = capsuleTextColorHex,
    capsuleTextAlpha = capsuleTextAlpha,
    capsuleProgressColorHex = capsuleProgressColorHex,
    capsuleProgressAlpha = capsuleProgressAlpha,
    capsuleX = capsuleX,
    capsuleY = capsuleY,
    strictMode = strictMode,
    themeMode = themeMode,
    themeColor = themeColor,
    whiteNoiseType = whiteNoiseType,
    planReachGoalSound = planReachGoalSound,
    planReachGoalVibrate = planReachGoalVibrate,
    dailyGoalMinutes = dailyGoalMinutes
)

/** 备份的模板 → 应用内模板 */
fun BackupTemplate.toPlanTemplate(): PlanTemplate = PlanTemplate(
    id = id,
    title = title,
    icon = icon,
    dailyMinutes = dailyMinutes,
    durationDays = durationDays,
    colorHex = colorHex,
    useCount = useCount,
    createdAt = createdAt
)

/** 应用内模板 → 备份的模板 */
fun PlanTemplate.toBackupTemplate(): BackupTemplate = BackupTemplate(
    id = id,
    title = title,
    icon = icon,
    dailyMinutes = dailyMinutes,
    durationDays = durationDays,
    colorHex = colorHex,
    useCount = useCount,
    createdAt = createdAt
)
