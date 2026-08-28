package com.stax.feature.protocols.presentation.form

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.stax.core.domain.AppTheme
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.Protocol
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.Settings
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * §4.4.5's auto-saved draft, which §4.9 inherits, taken through the round trip that makes it worth
 * having: the form is edited, the `SavedStateHandle` is saved and restored as the platform does on
 * process death, and a fresh ViewModel is built over what came back.
 *
 * On Robolectric because the draft serializes into a real `Bundle` — which is what makes this a
 * genuine check of the draft's shape, weekday set and dosage times included, rather than of an
 * in-memory copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtocolFormDraftPersistenceTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the draft survives process death`() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(savedStateHandle = handle)

        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.75"))
        viewModel.onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.SPECIFIC_WEEKDAYS))
        viewModel.onAction(ProtocolFormAction.Pick.OnWeekdayToggled(DayOfWeek.THURSDAY))
        viewModel.onAction(ProtocolFormAction.OnTimeSelected(LocalTime(20, 0)))

        val restored = viewModel(savedStateHandle = handle.afterProcessDeath()).state.value

        assertThat(restored.draft.doseAmount).isEqualTo("0.75")
        assertThat(restored.draft.scheduleType).isEqualTo(ScheduleType.SPECIFIC_WEEKDAYS)
        assertThat(restored.draft.weekdays).contains(DayOfWeek.THURSDAY)
        assertThat(restored.draft.dosageTimes).contains(LocalTime(20, 0))
        assertThat(restored.draft.startDate).isEqualTo(TODAY)
        // The restored draft carries unsaved edits, so the form is dirty before a keystroke.
        assertThat(restored.isDirty).isTrue()
    }

    /** A finished form drops its draft, or the next Create would open on the last one abandoned. */
    @Test
    fun `the draft is dropped once the form is done with`() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(savedStateHandle = handle)
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.75"))

        viewModel.onAction(ProtocolFormAction.OnDiscardConfirm)

        val restored = viewModel(savedStateHandle = handle.afterProcessDeath()).state.value
        assertThat(restored.draft.doseAmount).isEqualTo("")
        assertThat(restored.isDirty).isFalse()
    }

    private fun SavedStateHandle.afterProcessDeath(): SavedStateHandle =
        SavedStateHandle.createHandle(savedStateProvider().saveState(), null)

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = ProtocolFormViewModel(
        savedStateHandle = savedStateHandle,
        protocolRepository = FakeProtocolRepository(),
        compoundRepository = FakeCompoundRepository(),
        settingsRepository = FakeSettingsRepository(),
        args = ProtocolFormArgs(),
        now = { NOW },
        timeZone = TimeZone.UTC,
    )

    private class FakeCompoundRepository : CompoundRepository {
        override fun observeAll(): Flow<List<CompoundSupply>> = flowOf(emptyList())

        override fun observeById(id: Long): Flow<CompoundSupply?> = flowOf(null)

        override suspend fun create(compound: CompoundSupply) = throw NotImplementedError()

        override suspend fun update(compound: CompoundSupply, capOpenedContainer: Boolean) = throw NotImplementedError()

        override suspend fun archive(id: Long) = throw NotImplementedError()

        override suspend fun duplicate(id: Long) = throw NotImplementedError()

        override suspend fun openContainer(id: Long) = throw NotImplementedError()

        override suspend fun addOpenedContainer(
            compoundSupplyId: Long,
            openedAt: Instant,
            remainingAmount: Quantity,
            expiryAfterOpeningDays: Int?,
            userDefinedExpiryDate: LocalDate?,
        ) = throw NotImplementedError()

        override suspend fun closeContainer(id: Long, reason: String?) = throw NotImplementedError()

        override suspend fun editOpenedContainer(
            compoundSupplyId: Long,
            openedAt: Instant?,
            remainingAmount: Quantity?,
            expiryAfterOpeningDays: Int?,
            userDefinedExpiryDate: LocalDate?,
        ) = throw NotImplementedError()
    }

    private class FakeProtocolRepository : ProtocolRepository {
        override fun observeAll(): Flow<List<Protocol>> = flowOf(emptyList())

        override fun observeArchived(): Flow<List<Protocol>> = flowOf(emptyList())

        override fun observeById(id: Long): Flow<Protocol?> = flowOf(null)

        override fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<List<Protocol>> = flowOf(emptyList())

        override suspend fun create(protocol: Protocol): Result<Long, DataError.Local> = Result.Success(1L)

        override suspend fun update(protocol: Protocol): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun archive(id: Long) = throw NotImplementedError()

        override suspend fun duplicate(id: Long) = throw NotImplementedError()

        override suspend fun pause(id: Long) = throw NotImplementedError()

        override suspend fun resume(id: Long) = throw NotImplementedError()

        override suspend fun complete(id: Long) = throw NotImplementedError()
    }

    private class FakeSettingsRepository : SettingsRepository {
        override fun observe(): Flow<Settings> = flowOf(
            Settings(
                theme = AppTheme.SYSTEM,
                dynamicColor = true,
                notificationStyle = NotificationStyle.NORMAL,
                timeZoneOverride = null,
                missedDoseWindowMinutes = 60,
                onboardingCompleted = true,
                exactAlarmDegraded = false,
                defaultSiteCooldownDaysSC = 5,
                defaultSiteCooldownDaysIM = 7,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )

        override suspend fun update(settings: Settings) = throw NotImplementedError()
    }

    private companion object {
        /** 2026-02-01T00:00:00Z. */
        val NOW: Instant = Instant.fromEpochSeconds(1_769_904_000)
        val TODAY = LocalDate(2026, 2, 1)
    }
}
