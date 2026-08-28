package com.stax.feature.protocols.presentation.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.stax.core.domain.AppTheme
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.ReminderBucket
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.Settings
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * One case per rule of §4.9, over one shared pair of fakes. Like the compound form's suite, it grows
 * with the form it covers rather than splitting and duplicating the harness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class ProtocolFormViewModelTest {

    private lateinit var protocols: FakeProtocolRepository
    private lateinit var compounds: FakeCompoundRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        protocols = FakeProtocolRepository()
        compounds = FakeCompoundRepository()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // -----------------------------------------------------------------------
    // Save (§4.9.7) — the acceptance criteria
    // -----------------------------------------------------------------------

    @Test
    fun `Save on Create inserts through create, which is what generates the 7-day Pending horizon`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)

        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        val created = protocols.created
        assertThat(created).isNotNull()
        assertThat(protocols.updated).isNull()
        assertThat(created?.compoundSupplyId).isEqualTo(COMPOUND_ID)
        assertThat(created?.plannedDose).isEqualTo(Quantity(Decimal.parse("0.25"), UnitCode.MG))
        assertThat(created?.startDate).isEqualTo(TODAY)
        assertThat(created?.status).isEqualTo(ProtocolStatus.ACTIVE)
    }

    @Test
    fun `Save on Edit updates through update, which is what runs the pending-regen scope rule`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))

        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.5"))
        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        val updated = protocols.updated
        assertThat(protocols.created).isNull()
        assertThat(updated?.id).isEqualTo(PROTOCOL_ID)
        assertThat(updated?.plannedDose).isEqualTo(Quantity(Decimal.parse("0.5"), UnitCode.MG))
    }

    @Test
    fun `a saved form reports Done`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)

        viewModel.events.test {
            viewModel.onAction(ProtocolFormAction.OnSaveClick)
            assertThat(awaitItem()).isEqualTo(ProtocolFormEvent.Done)
        }
    }

    @Test
    fun `a failed write keeps the form open and says what went wrong`() = runTest {
        protocols.createError = DataError.Local.DISK_FULL
        val viewModel = viewModel()
        fillRequiredFields(viewModel)

        viewModel.events.test {
            viewModel.onAction(ProtocolFormAction.OnSaveClick)
            assertThat(awaitItem()).isInstanceOf(ProtocolFormEvent.ShowError::class)
        }
        assertThat(viewModel.state.value.isSaving).isFalse()
    }

    /**
     * §4.9.3 has no control for escalation or a protocol break, so editing a titrating protocol
     * through this form has to leave both exactly as it found them.
     */
    @Test
    fun `an edit carries through the fields the form has no control for`() = runTest {
        val stored = storedProtocol().copy(siteCooldownDays = 3, status = ProtocolStatus.PAUSED)
        protocols.stored.value = stored
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))

        viewModel.onAction(ProtocolFormAction.Edit.OnNotesChange("ramping slowly"))
        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        val updated = protocols.updated
        assertThat(updated?.escalation).isEqualTo(stored.escalation)
        assertThat(updated?.protocolBreak).isEqualTo(stored.protocolBreak)
        assertThat(updated?.siteCooldownDays).isEqualTo(3)
        assertThat(updated?.status).isEqualTo(ProtocolStatus.PAUSED)
        assertThat(updated?.name).isEqualTo(stored.name)
        assertThat(updated?.notes).isEqualTo("ramping slowly")
    }

    /** §4.9.3 has no name field, so a created protocol takes the name of what it doses (§4.7.3). */
    @Test
    fun `a created protocol is named after its compound`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)

        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        assertThat(protocols.created?.name).isEqualTo("Semaglutide")
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    @Test
    fun `Save with nothing filled in rejects the required fields and scrolls to the first`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        val state = viewModel.state.value
        assertThat(state.errors[ProtocolFormField.COMPOUND]).isEqualTo(ProtocolFormError.COMPOUND_REQUIRED)
        assertThat(state.errors[ProtocolFormField.DOSE]).isEqualTo(ProtocolFormError.DOSE_NOT_POSITIVE)
        assertThat(state.scrollToError).isEqualTo(ProtocolFormField.COMPOUND)
        assertThat(protocols.created).isNull()
    }

    @Test
    fun `a weekday schedule with no day selected is rejected`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)
        viewModel.onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.SPECIFIC_WEEKDAYS))

        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[ProtocolFormField.WEEKDAYS])
            .isEqualTo(ProtocolFormError.WEEKDAYS_REQUIRED)
        assertThat(protocols.created).isNull()
    }

    @Test
    fun `an end date on or before the start is rejected`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)
        viewModel.onAction(ProtocolFormAction.Overlay.OnDateFieldClick(ProtocolDateField.END))
        viewModel.onAction(ProtocolFormAction.Overlay.OnDateSelected(TODAY))

        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[ProtocolFormField.END_DATE])
            .isEqualTo(ProtocolFormError.END_DATE_NOT_AFTER_START)
    }

    @Test
    fun `a schedule count below one is rejected`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)
        viewModel.onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.EVERY_X_DAYS))
        viewModel.onAction(ProtocolFormAction.Edit.OnScheduleCountChange("0"))

        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[ProtocolFormField.SCHEDULE_COUNT])
            .isEqualTo(ProtocolFormError.SCHEDULE_COUNT_INVALID)
    }

    @Test
    fun `editing a rejected field clears its error`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ProtocolFormAction.OnSaveClick)

        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.25"))

        assertThat(viewModel.state.value.errors[ProtocolFormField.DOSE]).isNull()
    }

    // -----------------------------------------------------------------------
    // Picking a compound (§4.9.3)
    // -----------------------------------------------------------------------

    @Test
    fun `picking a compound defaults the route and offers its own unit family`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolFormAction.Pick.OnCompoundSelected(COMPOUND_ID))

        val state = viewModel.state.value
        assertThat(state.draft.route).isEqualTo(Route.SUBCUTANEOUS)
        assertThat(state.doseUnitOptions).containsExactly(UnitCode.MG, UnitCode.MCG, UnitCode.G)
        assertThat(state.compound?.name).isEqualTo("Semaglutide")
    }

    @Test
    fun `a route the user picked survives the next compound`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ProtocolFormAction.Pick.OnRouteSelected(Route.INTRAMUSCULAR))

        viewModel.onAction(ProtocolFormAction.Pick.OnCompoundSelected(COMPOUND_ID))

        assertThat(viewModel.state.value.draft.route).isEqualTo(Route.INTRAMUSCULAR)
    }

    @Test
    fun `the concentration turns the dose into a volume and insulin units`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ProtocolFormAction.Pick.OnCompoundSelected(COMPOUND_ID))

        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.25"))

        val equivalence = viewModel.state.value.equivalence
        assertThat(equivalence?.volume).isEqualTo("0.10")
        assertThat(equivalence?.volumeUnit).isEqualTo(UnitCode.ML)
        assertThat(equivalence?.insulinUnits).isEqualTo(10)
    }

    // -----------------------------------------------------------------------
    // Preview + forecast (§4.9.3, 11b)
    // -----------------------------------------------------------------------

    @Test
    fun `the preview counts the doses the same horizon will generate`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.EVERY_X_DAYS))
        viewModel.onAction(ProtocolFormAction.Edit.OnScheduleCountChange("3"))

        val preview = viewModel.state.value.preview
        // Days 0, 3 and 6 of a 7-day horizon.
        assertThat(preview?.doseCount).isEqualTo(3)
        assertThat(preview?.days?.count { it.hasDose }).isEqualTo(3)
        assertThat(preview?.days?.first()?.isToday).isEqualTo(true)
    }

    @Test
    fun `the forecast divides the whole stock by one dose and walks the schedule to the run-out`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)

        val forecast = viewModel.state.value.forecast
        // Two sealed 5 mg vials plus 5 mg still open = 15 mg, at 0.25 mg a day.
        assertThat(forecast?.dosesLeft).isEqualTo(60)
        assertThat(forecast?.daysLeft).isEqualTo(59)
        assertThat(forecast?.runOutDate).isEqualTo(LocalDate(2026, 4, 1))
    }

    @Test
    fun `a batch that expires before the run-out raises the warning row`() = runTest {
        compounds.stored.value = compound().copy(batchExpiryDate = LocalDate(2026, 3, 1))
        val viewModel = viewModel()
        fillRequiredFields(viewModel)

        val warning = viewModel.state.value.forecast?.expiryWarning
        assertThat(warning?.batchExpiry).isEqualTo(LocalDate(2026, 3, 1))
        assertThat(warning?.runOut).isEqualTo(LocalDate(2026, 4, 1))
    }

    @Test
    fun `an end date the stock cannot reach raises the reorder row`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)
        viewModel.onAction(ProtocolFormAction.Overlay.OnDateFieldClick(ProtocolDateField.END))
        viewModel.onAction(ProtocolFormAction.Overlay.OnDateSelected(LocalDate(2026, 4, 30)))

        val reorder = viewModel.state.value.forecast?.reorder
        // 30 days past the run-out at 0.25 mg = 7.5 mg, which is two more 5 mg vials.
        assertThat(reorder?.containers).isEqualTo(2)
        assertThat(reorder?.containerType).isEqualTo(ContainerType.VIAL)
        assertThat(reorder?.coversUntil).isEqualTo(LocalDate(2026, 4, 30))
    }

    @Test
    fun `an open-ended protocol has nothing to reorder against`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)

        assertThat(viewModel.state.value.forecast?.reorder).isNull()
    }

    @Test
    fun `there is no forecast until a compound and a dose are both in place`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolFormAction.Pick.OnCompoundSelected(COMPOUND_ID))

        assertThat(viewModel.state.value.forecast).isNull()
    }

    // -----------------------------------------------------------------------
    // Times of day, pickers and dirt (§4.9.3, §4.4.5's rule)
    // -----------------------------------------------------------------------

    @Test
    fun `dosage times are kept sorted and de-duplicated`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolFormAction.OnTimeSelected(LocalTime(20, 0)))
        viewModel.onAction(ProtocolFormAction.OnTimeSelected(LocalTime(8, 0)))
        viewModel.onAction(ProtocolFormAction.OnTimeSelected(LocalTime(20, 0)))

        assertThat(viewModel.state.value.draft.dosageTimes)
            .containsExactly(LocalTime(8, 0), LocalTime(20, 0))
    }

    @Test
    fun `tapping a time pill removes it`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ProtocolFormAction.OnTimeSelected(LocalTime(8, 0)))

        viewModel.onAction(ProtocolFormAction.OnTimeRemoved(LocalTime(8, 0)))

        assertThat(viewModel.state.value.draft.dosageTimes).isEmpty()
    }

    /** §3.2: the bucket is only what an alarm hangs off when there is no time of day to use. */
    @Test
    fun `the reminder bucket is stored only while there is no time of day`() = runTest {
        val viewModel = viewModel()
        fillRequiredFields(viewModel)
        viewModel.onAction(ProtocolFormAction.Pick.OnReminderBucketSelected(ReminderBucket.EVENING))

        viewModel.onAction(ProtocolFormAction.OnSaveClick)
        assertThat(protocols.created?.reminderBucket).isEqualTo(ReminderBucket.EVENING)

        protocols.created = null
        viewModel.onAction(ProtocolFormAction.OnTimeSelected(LocalTime(20, 0)))
        viewModel.onAction(ProtocolFormAction.OnSaveClick)
        assertThat(protocols.created?.reminderBucket).isNull()
    }

    @Test
    fun `weekday circles toggle on and off`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolFormAction.Pick.OnWeekdayToggled(DayOfWeek.MONDAY))
        viewModel.onAction(ProtocolFormAction.Pick.OnWeekdayToggled(DayOfWeek.THURSDAY))
        viewModel.onAction(ProtocolFormAction.Pick.OnWeekdayToggled(DayOfWeek.MONDAY))

        assertThat(viewModel.state.value.draft.weekdays).containsOnly(DayOfWeek.THURSDAY)
    }

    /** Each chip keeps its own count, so flipping between them does not overwrite the other's number. */
    @Test
    fun `switching schedule chips keeps each chip's own count`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.EVERY_X_DAYS))
        viewModel.onAction(ProtocolFormAction.Edit.OnScheduleCountChange("5"))
        viewModel.onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.X_TIMES_PER_WEEK))
        viewModel.onAction(ProtocolFormAction.Edit.OnScheduleCountChange("2"))
        viewModel.onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.EVERY_X_DAYS))

        assertThat(viewModel.state.value.draft.scheduleCount()).isEqualTo("5")
    }

    @Test
    fun `an untouched form closes without asking`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ProtocolFormAction.OnCancelClick)
            assertThat(awaitItem()).isEqualTo(ProtocolFormEvent.Done)
        }
    }

    @Test
    fun `a changed form asks before it closes`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.25"))

        viewModel.onAction(ProtocolFormAction.OnCancelClick)

        assertThat(viewModel.state.value.isDirty).isTrue()
        assertThat(viewModel.state.value.isDiscardDialogOpen).isTrue()
    }

    @Test
    fun `the picker's search field appears only past five compounds`() = runTest {
        compounds.all.value = List(SIX) { compound().copy(id = it + 1L, name = "Compound $it") }
        val viewModel = viewModel()

        assertThat(viewModel.state.value.isPickerSearchable).isTrue()

        viewModel.onAction(ProtocolFormAction.Overlay.OnPickerQueryChange("Compound 3"))
        assertThat(viewModel.state.value.pickerCompounds.map { it.name }).containsExactly("Compound 3")
    }

    // -----------------------------------------------------------------------
    // Lifecycle (§4.9.5)
    // -----------------------------------------------------------------------

    @Test
    fun `Pause pauses the stored protocol and leaves the form`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))

        viewModel.events.test {
            viewModel.onAction(ProtocolFormAction.OnPauseClick)
            assertThat(awaitItem()).isEqualTo(ProtocolFormEvent.Done)
        }
        assertThat(protocols.paused).isEqualTo(PROTOCOL_ID)
    }

    // §4.9.6 — the three answers to "Save changes before pausing?", one DB state each.

    @Test
    fun `Pause on a changed form asks before it decides what happens to the edits`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.5"))

        viewModel.onAction(ProtocolFormAction.OnPauseClick)

        assertThat(viewModel.state.value.isPauseDialogOpen).isTrue()
        assertThat(protocols.paused).isNull()
        assertThat(protocols.updated).isNull()
    }

    /**
     * Save + Pause is one `update` carrying `status = Paused`, not an update followed by a pause: the
     * regen §5.4 runs inside it then finds a paused protocol and generates nothing (§5.2).
     */
    @Test
    fun `Save + Pause writes the edits and the paused status in the same update`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.5"))
        viewModel.onAction(ProtocolFormAction.OnPauseClick)

        viewModel.events.test {
            viewModel.onAction(ProtocolFormAction.OnPauseSaveConfirm)
            assertThat(awaitItem()).isEqualTo(ProtocolFormEvent.Done)
        }

        val updated = protocols.updated
        assertThat(updated?.id).isEqualTo(PROTOCOL_ID)
        assertThat(updated?.status).isEqualTo(ProtocolStatus.PAUSED)
        assertThat(updated?.plannedDose).isEqualTo(Quantity(Decimal.parse("0.5"), UnitCode.MG))
        assertThat(protocols.paused).isNull()
        assertThat(viewModel.state.value.isPauseDialogOpen).isFalse()
    }

    @Test
    fun `Pause without saving pauses the stored protocol and leaves the edits unwritten`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.5"))
        viewModel.onAction(ProtocolFormAction.OnPauseClick)

        viewModel.events.test {
            viewModel.onAction(ProtocolFormAction.OnPauseDiscardConfirm)
            assertThat(awaitItem()).isEqualTo(ProtocolFormEvent.Done)
        }

        assertThat(protocols.paused).isEqualTo(PROTOCOL_ID)
        assertThat(protocols.updated).isNull()
    }

    @Test
    fun `Cancel leaves the protocol running and the edits on screen`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.5"))
        viewModel.onAction(ProtocolFormAction.OnPauseClick)

        viewModel.onAction(ProtocolFormAction.Overlay.OnPauseDismiss)

        assertThat(viewModel.state.value.isPauseDialogOpen).isFalse()
        assertThat(viewModel.state.value.draft.doseAmount).isEqualTo("0.5")
        assertThat(protocols.paused).isNull()
        assertThat(protocols.updated).isNull()
    }

    /** Save + Pause is still a save, so a form it cannot write stays open with its fields marked. */
    @Test
    fun `Save + Pause on an invalid form rejects the fields instead of pausing`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange(""))
        viewModel.onAction(ProtocolFormAction.OnPauseClick)

        viewModel.onAction(ProtocolFormAction.OnPauseSaveConfirm)

        assertThat(viewModel.state.value.errors[ProtocolFormField.DOSE])
            .isEqualTo(ProtocolFormError.DOSE_NOT_POSITIVE)
        assertThat(protocols.updated).isNull()
        assertThat(protocols.paused).isNull()
    }

    @Test
    fun `Duplicate creates a copy of what is on screen, Active and suffixed`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.5"))

        viewModel.onAction(ProtocolFormAction.OnDuplicateClick)

        val created = protocols.created
        assertThat(created?.id).isEqualTo(0L)
        assertThat(created?.name).isEqualTo("Sema weekly titration (copy)")
        assertThat(created?.status).isEqualTo(ProtocolStatus.ACTIVE)
        assertThat(created?.plannedDose).isEqualTo(Quantity(Decimal.parse("0.5"), UnitCode.MG))
    }

    @Test
    fun `Archive asks first, then soft-deletes`() = runTest {
        protocols.stored.value = storedProtocol()
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))

        viewModel.onAction(ProtocolFormAction.OnArchiveClick)
        assertThat(viewModel.state.value.isArchiveDialogOpen).isTrue()
        assertThat(protocols.archived).isNull()

        viewModel.onAction(ProtocolFormAction.OnArchiveConfirm)
        assertThat(protocols.archived).isEqualTo(PROTOCOL_ID)
    }

    // -----------------------------------------------------------------------
    // Edit-mode loading
    // -----------------------------------------------------------------------

    @Test
    fun `Edit loads the stored protocol into the form`() = runTest {
        protocols.stored.value = storedProtocol()

        val state = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID)).state.value

        assertThat(state.isEdit).isTrue()
        assertThat(state.editedProtocolName).isEqualTo("Sema weekly titration")
        assertThat(state.draft.doseAmount).isEqualTo("0.25")
        assertThat(state.draft.weekdays).containsOnly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        assertThat(state.draft.dosageTimes).containsExactly(LocalTime(20, 0))
        assertThat(state.isDirty).isFalse()
    }

    @Test
    fun `a protocol that is not there says so and leaves`() = runTest {
        protocols.stored.value = null
        val viewModel = viewModel(args = ProtocolFormArgs(protocolId = PROTOCOL_ID))

        viewModel.events.test {
            assertThat(awaitItem()).isInstanceOf(ProtocolFormEvent.ShowError::class)
            assertThat(awaitItem()).isEqualTo(ProtocolFormEvent.Done)
        }
    }

    // -----------------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------------

    private fun viewModel(args: ProtocolFormArgs = ProtocolFormArgs(), saved: SavedStateHandle = SavedStateHandle()) =
        ProtocolFormViewModel(
            savedStateHandle = saved,
            protocolRepository = protocols,
            compoundRepository = compounds,
            settingsRepository = FakeSettingsRepository(),
            args = args,
            now = { NOW },
            timeZone = TimeZone.UTC,
        )

    /** The three §4.9.3 fields Save requires; the start date is already today by default. */
    private fun fillRequiredFields(viewModel: ProtocolFormViewModel) {
        viewModel.onAction(ProtocolFormAction.Pick.OnCompoundSelected(COMPOUND_ID))
        viewModel.onAction(ProtocolFormAction.Edit.OnDoseChange("0.25"))
    }

    private fun compound() = CompoundSupply(
        id = COMPOUND_ID,
        name = "Semaglutide",
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainer = Quantity(Decimal.parse("5"), UnitCode.MG),
        concentration = Concentration(
            amount = Quantity(Decimal.parse("2.5"), UnitCode.MG),
            per = Quantity(Decimal.parse("1"), UnitCode.ML),
        ),
        numberOfContainers = 2,
        currentOpened = OpenedContainer(
            openedAt = NOW,
            remainingAmount = Quantity(Decimal.parse("5"), UnitCode.MG),
            expiryAfterOpeningDays = null,
            userDefinedExpiryDate = null,
            predictedExpiryDate = null,
        ),
        batchExpiryDate = null,
        expiryAfterOpeningDays = null,
        storageLocation = StorageLocation.FRIDGE,
        batchNumber = null,
        supplier = null,
        notes = null,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun storedProtocol() = Protocol(
        id = PROTOCOL_ID,
        name = "Sema weekly titration",
        compoundSupplyId = COMPOUND_ID,
        plannedDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
        route = Route.SUBCUTANEOUS,
        schedule = Schedule(
            type = ScheduleType.SPECIFIC_WEEKDAYS,
            interval = null,
            timesPerDay = null,
            selectedWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            timesPerWeek = null,
            timesPerMonth = null,
        ),
        dosageTimes = listOf(LocalTime(20, 0)),
        escalation = null,
        protocolBreak = null,
        startDate = TODAY,
        endDate = null,
        reminderEnabled = true,
        reminderOffsetMinutes = 0,
        reminderBucket = null,
        injectionSiteRestriction = null,
        siteCooldownDays = null,
        notes = null,
        status = ProtocolStatus.ACTIVE,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private inner class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow(compound())
        val all = MutableStateFlow<List<CompoundSupply>?>(null)

        override fun observeAll(): Flow<List<CompoundSupply>> = all.map { override -> override ?: listOf(stored.value) }

        override fun observeById(id: Long): Flow<CompoundSupply?> = stored

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
        val stored = MutableStateFlow<Protocol?>(null)
        var created: Protocol? = null
        var updated: Protocol? = null
        var paused: Long? = null
        var archived: Long? = null

        /** When set, `create` fails with this instead of storing. */
        var createError: DataError.Local? = null

        override fun observeAll(): Flow<List<Protocol>> = stored.map { listOfNotNull(it) }

        override fun observeById(id: Long): Flow<Protocol?> = stored

        override fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<List<Protocol>> =
            stored.map { listOfNotNull(it) }

        override suspend fun create(protocol: Protocol): Result<Long, DataError.Local> {
            createError?.let { return Result.Error(it) }
            created = protocol
            return Result.Success(PROTOCOL_ID)
        }

        override suspend fun update(protocol: Protocol): EmptyResult<DataError.Local> {
            updated = protocol
            return Result.Success(Unit)
        }

        override suspend fun archive(id: Long): EmptyResult<DataError.Local> {
            archived = id
            return Result.Success(Unit)
        }

        override suspend fun pause(id: Long): EmptyResult<DataError.Local> {
            paused = id
            return Result.Success(Unit)
        }

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
        const val COMPOUND_ID = 7L
        const val PROTOCOL_ID = 42L
        const val SIX = 6

        /** 2026-02-01T00:00:00Z, so `TODAY` is unambiguous in UTC. */
        val NOW: Instant = Instant.fromEpochSeconds(1_769_904_000)
        val TODAY = LocalDate(2026, 2, 1)
    }
}
