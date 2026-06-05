package com.stax.core.database

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var dao: SettingsDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.settingsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert with id 1 stores entity and observe returns it`() = runTest {
        val settings = defaultSettings()

        dao.insert(settings)

        assertThat(dao.observe().first()).isEqualTo(settings)
    }

    @Test
    fun `insert with id other than 1 throws IllegalArgumentException`() = runTest {
        val error = try {
            dao.insert(defaultSettings().copy(id = 2L))
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertThat(error).isNotNull()
    }

    @Test
    fun `insert with id 0 throws IllegalArgumentException`() = runTest {
        val error = try {
            dao.insert(defaultSettings().copy(id = 0L))
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertThat(error).isNotNull()
    }

    @Test
    fun `observe returns null-emitting flow when table is empty`() = runTest {
        assertThat(dao.observe().first()).isNull()
    }

    @Test
    fun `update modifies existing row`() = runTest {
        dao.insert(defaultSettings())

        dao.update(defaultSettings().copy(theme = AppTheme.DARK, dynamicColor = false))

        val stored = dao.observe().first()
        assertThat(stored?.theme).isEqualTo(AppTheme.DARK)
        assertThat(stored?.dynamicColor).isEqualTo(false)
    }

    @Test
    fun `all AppTheme values round-trip through Room`() = runTest {
        dao.insert(defaultSettings())
        for (theme in AppTheme.entries) {
            dao.update(defaultSettings().copy(theme = theme))
            assertThat(dao.observe().first()?.theme).isEqualTo(theme)
        }
    }

    @Test
    fun `all NotificationStyle values round-trip through Room`() = runTest {
        dao.insert(defaultSettings())
        for (style in NotificationStyle.entries) {
            dao.update(defaultSettings().copy(notificationStyle = style))
            assertThat(dao.observe().first()?.notificationStyle).isEqualTo(style)
        }
    }

    @Test
    fun `nullable timeZoneOverride stored and retrieved`() = runTest {
        dao.insert(defaultSettings().copy(timeZoneOverride = "Europe/Paris"))

        assertThat(dao.observe().first()?.timeZoneOverride).isEqualTo("Europe/Paris")

        dao.update(defaultSettings().copy(timeZoneOverride = null))

        assertThat(dao.observe().first()?.timeZoneOverride).isNull()
    }

    @Test
    fun `observe emits updated value after update`() = runTest {
        dao.insert(defaultSettings())
        assertThat(dao.observe().first()?.onboardingCompleted).isEqualTo(false)

        dao.update(defaultSettings().copy(onboardingCompleted = true))

        assertThat(dao.observe().first()?.onboardingCompleted).isEqualTo(true)
    }

    private fun defaultSettings(
        id: Long = 1L,
        theme: AppTheme = AppTheme.SYSTEM,
        dynamicColor: Boolean = true,
        notificationStyle: NotificationStyle = NotificationStyle.NORMAL,
        timeZoneOverride: String? = null,
        missedDoseWindowMinutes: Int = 60,
        onboardingCompleted: Boolean = false,
        exactAlarmDegraded: Boolean = false,
        defaultSiteCooldownDaysSC: Int = 5,
        defaultSiteCooldownDaysIM: Int = 7,
        createdAt: Instant = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt: Instant = createdAt,
    ): SettingsEntity = SettingsEntity(
        id = id,
        theme = theme,
        dynamicColor = dynamicColor,
        notificationStyle = notificationStyle,
        timeZoneOverride = timeZoneOverride,
        missedDoseWindowMinutes = missedDoseWindowMinutes,
        onboardingCompleted = onboardingCompleted,
        exactAlarmDegraded = exactAlarmDegraded,
        defaultSiteCooldownDaysSC = defaultSiteCooldownDaysSC,
        defaultSiteCooldownDaysIM = defaultSiteCooldownDaysIM,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
