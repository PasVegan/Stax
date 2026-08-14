package com.stax.notification.alarm

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.stax.core.domain.AppTheme
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.Result
import com.stax.core.domain.Settings
import com.stax.core.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExactAlarmPermissionMonitorTest {

    @Test
    fun `denied permission marks reminders degraded`() = runTest {
        val repository = FakeSettingsRepository(settings(exactAlarmDegraded = false))

        startMonitor(FakeExactAlarmPermission(granted = false), repository)

        assertThat(repository.stored.value.exactAlarmDegraded).isTrue()
    }

    @Test
    fun `granted permission clears the degraded flag`() = runTest {
        val repository = FakeSettingsRepository(settings(exactAlarmDegraded = true))

        startMonitor(FakeExactAlarmPermission(granted = true), repository)

        assertThat(repository.stored.value.exactAlarmDegraded).isFalse()
    }

    @Test
    fun `toggling the permission tracks every transition`() = runTest {
        val repository = FakeSettingsRepository(settings(exactAlarmDegraded = false))
        val permission = FakeExactAlarmPermission(granted = true)
        startMonitor(permission, repository)

        permission.granted.value = false
        runCurrent()
        assertThat(repository.stored.value.exactAlarmDegraded).isTrue()

        permission.granted.value = true
        runCurrent()
        assertThat(repository.stored.value.exactAlarmDegraded).isFalse()
    }

    @Test
    fun `an unchanged permission writes nothing`() = runTest {
        val repository = FakeSettingsRepository(settings(exactAlarmDegraded = false))

        startMonitor(FakeExactAlarmPermission(granted = true), repository)

        assertThat(repository.updates).isEqualTo(0)
    }

    @Test
    fun `the degraded flag is the only field touched`() = runTest {
        val repository = FakeSettingsRepository(settings(exactAlarmDegraded = false))
        val before = repository.stored.value

        startMonitor(FakeExactAlarmPermission(granted = false), repository)

        assertThat(repository.stored.value).isEqualTo(before.copy(exactAlarmDegraded = true))
    }

    /**
     * `sync()` collects until cancelled, so it runs on `backgroundScope` — `runTest` tears that down
     * at the end of the test instead of waiting on a job that never completes.
     */
    private fun TestScope.startMonitor(permission: ExactAlarmPermission, repository: SettingsRepository) {
        backgroundScope.launch { ExactAlarmPermissionMonitor(permission, repository).sync() }
        runCurrent()
    }

    private class FakeExactAlarmPermission(granted: Boolean) : ExactAlarmPermission {
        val granted = MutableStateFlow(granted)

        override fun isGranted(): Boolean = granted.value

        override fun observe(): Flow<Boolean> = granted
    }

    private class FakeSettingsRepository(initial: Settings) : SettingsRepository {
        val stored = MutableStateFlow(initial)
        var updates = 0
            private set

        override fun observe(): Flow<Settings> = stored

        override suspend fun update(settings: Settings): EmptyResult<DataError.Local> {
            updates++
            stored.value = settings
            return Result.Success(Unit)
        }
    }

    private fun settings(exactAlarmDegraded: Boolean) = Settings(
        theme = AppTheme.SYSTEM,
        dynamicColor = true,
        notificationStyle = NotificationStyle.NORMAL,
        timeZoneOverride = null,
        missedDoseWindowMinutes = 60,
        onboardingCompleted = true,
        exactAlarmDegraded = exactAlarmDegraded,
        defaultSiteCooldownDaysSC = 5,
        defaultSiteCooldownDaysIM = 7,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )
}
