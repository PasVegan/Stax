package com.stax.core.data.mapper

import com.stax.core.database.SettingsEntity
import com.stax.core.domain.Settings

// ---------------------------------------------------------------------------
// SettingsEntity ↔ Settings
// ---------------------------------------------------------------------------

fun SettingsEntity.toDomain(): Settings =
    Settings(
        id = id,
        theme = theme.toDomain(),
        dynamicColor = dynamicColor,
        notificationStyle = notificationStyle.toDomain(),
        timeZoneOverride = timeZoneOverride,
        missedDoseWindowMinutes = missedDoseWindowMinutes,
        onboardingCompleted = onboardingCompleted,
        exactAlarmDegraded = exactAlarmDegraded,
        defaultSiteCooldownDaysSC = defaultSiteCooldownDaysSC,
        defaultSiteCooldownDaysIM = defaultSiteCooldownDaysIM,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Settings.toEntity(): SettingsEntity =
    SettingsEntity(
        id = id,
        theme = theme.toEntity(),
        dynamicColor = dynamicColor,
        notificationStyle = notificationStyle.toEntity(),
        timeZoneOverride = timeZoneOverride,
        missedDoseWindowMinutes = missedDoseWindowMinutes,
        onboardingCompleted = onboardingCompleted,
        exactAlarmDegraded = exactAlarmDegraded,
        defaultSiteCooldownDaysSC = defaultSiteCooldownDaysSC,
        defaultSiteCooldownDaysIM = defaultSiteCooldownDaysIM,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
