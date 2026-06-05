package com.stax.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
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

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class NotificationStyle {
    SILENT,
    NORMAL,
    PERSISTENT,
}
