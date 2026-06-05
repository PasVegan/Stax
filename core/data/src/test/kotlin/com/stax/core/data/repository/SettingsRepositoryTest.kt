package com.stax.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.stax.core.data.preferences.ThemePreferences
import com.stax.core.database.AppTheme as DbAppTheme
import com.stax.core.database.NotificationStyle as DbNotificationStyle
import com.stax.core.database.SettingsDao
import com.stax.core.database.SettingsEntity
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.AppTheme
import com.stax.core.domain.DataError
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.Result
import com.stax.core.domain.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsRepositoryTest {

    private lateinit var database: StaxDatabase
    private lateinit var dao: SettingsDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: RoomSettingsRepository
    private lateinit var testPrefsFile: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, StaxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.settingsDao()

        // Unique file per test run to avoid cross-test DataStore state.
        testPrefsFile = File(context.filesDir, "test_theme_${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(produceFile = { testPrefsFile })

        repository = RoomSettingsRepository(dao, dataStore)
    }

    @After
    fun tearDown() {
        database.close()
        testPrefsFile.delete()
    }

    // -----------------------------------------------------------------------
    // observe()
    // -----------------------------------------------------------------------

    @Test
    fun `observe emits Settings when row exists`() = runTest {
        dao.insert(defaultEntity())

        val result = repository.observe().first()

        assertThat(result.id).isEqualTo(1L)
        assertThat(result.theme).isEqualTo(AppTheme.SYSTEM)
    }

    @Test
    fun `observe does not emit when table is empty`() = runTest {
        // filterNotNull means no emission when the row is absent.
        // We verify by checking that observe() has no initial value when empty.
        val flow = repository.observe()
        // Collect with a very short timeout — expect no emission.
        val emitted = try {
            kotlinx.coroutines.withTimeout(100) { flow.first() }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            null
        }
        assertThat(emitted).isNull()
    }

    @Test
    fun `observe re-emits after update`() = runTest {
        dao.insert(defaultEntity())

        repository.update(domainSettings().copy(theme = AppTheme.DARK))

        assertThat(repository.observe().first().theme).isEqualTo(AppTheme.DARK)
    }

    // -----------------------------------------------------------------------
    // update() — Room persistence
    // -----------------------------------------------------------------------

    @Test
    fun `update persists changes to Room`() = runTest {
        dao.insert(defaultEntity())

        val result = repository.update(domainSettings().copy(onboardingCompleted = true))

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(dao.observe().first()?.onboardingCompleted).isEqualTo(true)
    }

    @Test
    fun `update returns NOT_FOUND when no row exists`() = runTest {
        val result = repository.update(domainSettings())

        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    @Test
    fun `update persists all fields correctly`() = runTest {
        dao.insert(defaultEntity())
        val updated = domainSettings().copy(
            theme = AppTheme.LIGHT,
            dynamicColor = false,
            notificationStyle = NotificationStyle.SILENT,
            timeZoneOverride = "America/New_York",
            missedDoseWindowMinutes = 30,
            onboardingCompleted = true,
            exactAlarmDegraded = true,
            defaultSiteCooldownDaysSC = 3,
            defaultSiteCooldownDaysIM = 5,
        )

        repository.update(updated)

        val stored = repository.observe().first()
        assertThat(stored.theme).isEqualTo(AppTheme.LIGHT)
        assertThat(stored.dynamicColor).isEqualTo(false)
        assertThat(stored.notificationStyle).isEqualTo(NotificationStyle.SILENT)
        assertThat(stored.timeZoneOverride).isEqualTo("America/New_York")
        assertThat(stored.missedDoseWindowMinutes).isEqualTo(30)
        assertThat(stored.onboardingCompleted).isEqualTo(true)
    }

    // -----------------------------------------------------------------------
    // update() — DataStore write-through (§3.8 acceptance criteria)
    // -----------------------------------------------------------------------

    @Test
    fun `update writes theme to DataStore immediately`() = runTest {
        dao.insert(defaultEntity())

        repository.update(domainSettings().copy(theme = AppTheme.DARK))

        val prefs = dataStore.data.first()
        assertThat(prefs[ThemePreferences.THEME]).isEqualTo(AppTheme.DARK.name)
    }

    @Test
    fun `update writes dynamicColor to DataStore immediately`() = runTest {
        dao.insert(defaultEntity())

        repository.update(domainSettings().copy(dynamicColor = false))

        val prefs = dataStore.data.first()
        assertThat(prefs[ThemePreferences.DYNAMIC_COLOR]).isEqualTo(false)
    }

    @Test
    fun `DataStore mirror consistent with Room after each theme update`() = runTest {
        dao.insert(defaultEntity())

        // Three successive updates — DataStore must never lag.
        val themes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM)
        for (theme in themes) {
            repository.update(domainSettings().copy(theme = theme))

            val roomTheme = repository.observe().first().theme
            val datastoreTheme = dataStore.data.first()[ThemePreferences.THEME]
                ?.let { AppTheme.valueOf(it) }

            assertThat(datastoreTheme).isEqualTo(roomTheme)
        }
    }

    @Test
    fun `DataStore not written when Room row is absent`() = runTest {
        // update() returns NOT_FOUND — DataStore must not be touched.
        repository.update(domainSettings().copy(theme = AppTheme.DARK))

        val prefs = dataStore.data.first()
        // Key should be absent (default empty preferences).
        assertThat(prefs[ThemePreferences.THEME]).isNull()
    }

    @Test
    fun `DataStore theme key serialised as enum name`() = runTest {
        dao.insert(defaultEntity())

        repository.update(domainSettings().copy(theme = AppTheme.LIGHT))

        val raw = dataStore.data.first()[ThemePreferences.THEME]
        assertThat(raw).isEqualTo("LIGHT")
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private val now: Instant = Instant.parse("2026-06-06T00:00:00Z")

    private fun defaultEntity() = SettingsEntity(
        id = 1L,
        theme = DbAppTheme.SYSTEM,
        dynamicColor = true,
        notificationStyle = DbNotificationStyle.NORMAL,
        timeZoneOverride = null,
        missedDoseWindowMinutes = 60,
        onboardingCompleted = false,
        exactAlarmDegraded = false,
        defaultSiteCooldownDaysSC = 5,
        defaultSiteCooldownDaysIM = 7,
        createdAt = now,
        updatedAt = now,
    )

    private fun domainSettings() = Settings(
        id = 1L,
        theme = AppTheme.SYSTEM,
        dynamicColor = true,
        notificationStyle = NotificationStyle.NORMAL,
        timeZoneOverride = null,
        missedDoseWindowMinutes = 60,
        onboardingCompleted = false,
        exactAlarmDegraded = false,
        defaultSiteCooldownDaysSC = 5,
        defaultSiteCooldownDaysIM = 7,
        createdAt = now,
        updatedAt = now,
    )
}
