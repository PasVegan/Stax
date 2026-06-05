package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.stax.core.database.AppTheme as DbAppTheme
import com.stax.core.database.NotificationStyle as DbNotificationStyle
import com.stax.core.database.SettingsEntity
import kotlin.time.Instant
import org.junit.jupiter.api.Test

private val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

private fun entity(
    theme: DbAppTheme = DbAppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    notificationStyle: DbNotificationStyle = DbNotificationStyle.NORMAL,
    timeZoneOverride: String? = null,
) = SettingsEntity(
    id = 1L,
    theme = theme,
    dynamicColor = dynamicColor,
    notificationStyle = notificationStyle,
    timeZoneOverride = timeZoneOverride,
    missedDoseWindowMinutes = 60,
    onboardingCompleted = false,
    exactAlarmDegraded = false,
    defaultSiteCooldownDaysSC = 5,
    defaultSiteCooldownDaysIM = 7,
    createdAt = NOW,
    updatedAt = NOW,
)

class SettingsMappersTest {

    @Test
    fun `round-trip default settings`() {
        assertThat(entity().toDomain().toEntity()).isEqualTo(entity())
    }

    @Test
    fun `round-trip with timezone override`() {
        val e = entity(timeZoneOverride = "America/New_York")
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `round-trip dynamic color disabled`() {
        val e = entity(dynamicColor = false)
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `all theme values survive round-trip`() {
        DbAppTheme.values().forEach { theme ->
            val e = entity(theme = theme)
            assertThat(e.toDomain().toEntity()).isEqualTo(e)
        }
    }

    @Test
    fun `all notification style values survive round-trip`() {
        DbNotificationStyle.values().forEach { style ->
            val e = entity(notificationStyle = style)
            assertThat(e.toDomain().toEntity()).isEqualTo(e)
        }
    }
}
