package com.stax.feature.settings.presentation.settings

import app.cash.turbine.test
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var repository: FakeSettingsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeSettingsRepository(settings(exactAlarmDegraded = false))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state mirrors the persisted degraded flag`() = runTest {
        repository.stored.value = settings(exactAlarmDegraded = true)

        val viewModel = SettingsViewModel(repository)

        assertThat(viewModel.state.value.exactAlarmDegraded).isTrue()
    }

    @Test
    fun `no warning while exact alarms are allowed`() = runTest {
        val viewModel = SettingsViewModel(repository)

        assertThat(viewModel.state.value.exactAlarmDegraded).isFalse()
    }

    @Test
    fun `revoking the permission raises the warning on an open screen`() = runTest {
        val viewModel = SettingsViewModel(repository)

        repository.stored.value = settings(exactAlarmDegraded = true)

        assertThat(viewModel.state.value.exactAlarmDegraded).isTrue()
    }

    @Test
    fun `granting the permission clears the warning on an open screen`() = runTest {
        repository.stored.value = settings(exactAlarmDegraded = true)
        val viewModel = SettingsViewModel(repository)

        repository.stored.value = settings(exactAlarmDegraded = false)

        assertThat(viewModel.state.value.exactAlarmDegraded).isFalse()
    }

    @Test
    fun `the enable CTA opens the system alarms settings`() = runTest {
        val viewModel = SettingsViewModel(repository)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnEnableExactRemindersClick)

            assertThat(awaitItem()).isEqualTo(SettingsEvent.OpenExactAlarmSettings)
        }
    }

    private class FakeSettingsRepository(initial: Settings) : SettingsRepository {
        val stored = MutableStateFlow(initial)

        override fun observe(): Flow<Settings> = stored

        override suspend fun update(settings: Settings): EmptyResult<DataError.Local> {
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
