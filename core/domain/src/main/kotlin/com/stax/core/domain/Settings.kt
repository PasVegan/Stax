package com.stax.core.domain

import kotlin.time.Instant

enum class AppTheme { SYSTEM, LIGHT, DARK }

enum class NotificationStyle { SILENT, NORMAL, PERSISTENT }

data class Settings(
    val id: Long = 1L,
    val theme: AppTheme,
    val dynamicColor: Boolean,
    val notificationStyle: NotificationStyle,
    val timeZoneOverride: String?,
    val missedDoseWindowMinutes: Int,
    val onboardingCompleted: Boolean,
    val exactAlarmDegraded: Boolean,
    val defaultSiteCooldownDaysSC: Int,
    val defaultSiteCooldownDaysIM: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
