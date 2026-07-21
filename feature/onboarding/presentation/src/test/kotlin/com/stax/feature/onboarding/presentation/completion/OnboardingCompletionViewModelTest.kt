package com.stax.feature.onboarding.presentation.completion

import assertk.assertThat
import assertk.assertions.isEqualTo
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
class OnboardingCompletionViewModelTest {

    private lateinit var repository: FakeSettingsRepository
    private lateinit var viewModel: OnboardingCompletionViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeSettingsRepository(settings(onboardingCompleted = false))
        viewModel = OnboardingCompletionViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `complete marks onboarding done`() = runTest {
        viewModel.complete()

        assertThat(repository.stored.value.onboardingCompleted).isTrue()
    }

    @Test
    fun `complete changes nothing else`() = runTest {
        val before = repository.stored.value

        viewModel.complete()

        assertThat(repository.stored.value).isEqualTo(before.copy(onboardingCompleted = true))
    }

    private class FakeSettingsRepository(initial: Settings) : SettingsRepository {
        val stored = MutableStateFlow(initial)

        override fun observe(): Flow<Settings> = stored

        override suspend fun update(settings: Settings): EmptyResult<DataError.Local> {
            stored.value = settings
            return Result.Success(Unit)
        }
    }

    private fun settings(onboardingCompleted: Boolean) = Settings(
        theme = AppTheme.SYSTEM,
        dynamicColor = true,
        notificationStyle = NotificationStyle.NORMAL,
        timeZoneOverride = null,
        missedDoseWindowMinutes = 120,
        onboardingCompleted = onboardingCompleted,
        exactAlarmDegraded = false,
        defaultSiteCooldownDaysSC = 7,
        defaultSiteCooldownDaysIM = 14,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )
}
