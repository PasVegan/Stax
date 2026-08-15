package com.stax.feature.compounds.presentation.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.CompoundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CompoundFormViewModelTest {

    private lateinit var compounds: FakeCompoundRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        compounds = FakeCompoundRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Smart defaults (§4.4.3)
    // -----------------------------------------------------------------------

    @Test
    fun `picking a Form fills its container type, unit and amount`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.CAPSULE))

        val draft = viewModel.state.value.draft
        assertThat(draft.containerType).isEqualTo(ContainerType.BOTTLE)
        assertThat(draft.primaryUnit).isEqualTo(UnitCode.CAPSULE)
        assertThat(draft.amountPerContainer).isEqualTo("60")
    }

    @Test
    fun `a smart default never overwrites what the user typed`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("12"))
        viewModel.onAction(CompoundFormAction.Pick.OnContainerTypeSelected(ContainerType.AMPOULE))
        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.TABLET))

        val draft = viewModel.state.value.draft
        assertThat(draft.amountPerContainer).isEqualTo("12")
        assertThat(draft.containerType).isEqualTo(ContainerType.AMPOULE)
    }

    @Test
    fun `a unit the new form does not offer is replaced even when the user picked it`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(CompoundFormAction.Pick.OnPrimaryUnitSelected(UnitCode.ML))
        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.TABLET))

        assertThat(viewModel.state.value.draft.primaryUnit).isEqualTo(UnitCode.TABLET)
    }

    /** A tablet's strength is per tablet; "mg/mL" on a blister is not a unit (§4.4.3). */
    @Test
    fun `the concentration denominator follows the Form`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.draft.concentrationPerUnit).isEqualTo(UnitCode.ML)

        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.TABLET))
        assertThat(viewModel.state.value.draft.concentrationPerUnit).isEqualTo(UnitCode.TABLET)

        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.CAPSULE))
        assertThat(viewModel.state.value.draft.concentrationPerUnit).isEqualTo(UnitCode.CAPSULE)

        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.POWDER))
        assertThat(viewModel.state.value.draft.concentrationPerUnit).isEqualTo(UnitCode.G)
    }

    @Test
    fun `a concentration the user picked survives a Form that still offers it`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(
            CompoundFormAction.Pick.OnConcentrationUnitSelected(ConcentrationUnits(UnitCode.IU, UnitCode.ML)),
        )
        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.LIQUID))

        assertThat(viewModel.state.value.draft.concentrationUnit).isEqualTo(UnitCode.IU)
        assertThat(viewModel.state.value.draft.concentrationPerUnit).isEqualTo(UnitCode.ML)
    }

    @Test
    fun `a concentration the new Form cannot express is replaced even when the user picked it`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(
            CompoundFormAction.Pick.OnConcentrationUnitSelected(ConcentrationUnits(UnitCode.IU, UnitCode.ML)),
        )
        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.TABLET))

        assertThat(viewModel.state.value.draft.concentrationPerUnit).isEqualTo(UnitCode.TABLET)
    }

    @Test
    fun `Save stores the concentration against the unit the Form asked for`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.TABLET))
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("Anastrozole"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("1"))

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(compounds.created?.concentration?.per)
            .isEqualTo(Quantity(Decimal.parse("1"), UnitCode.TABLET))
    }

    // -----------------------------------------------------------------------
    // Validation (§4.4.4)
    // -----------------------------------------------------------------------

    @Test
    fun `an empty name blocks Save and is the field scrolled to`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("5"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("2"))

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        val state = viewModel.state.value
        assertThat(state.errors[CompoundFormField.NAME]).isEqualTo(CompoundFormError.NAME_REQUIRED)
        assertThat(state.scrollToError).isEqualTo(CompoundFormField.NAME)
        assertThat(compounds.created).isNull()
    }

    @Test
    fun `an injectable that is not an ampoule requires a concentration`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("Retatrutide"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("10"))

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[CompoundFormField.CONCENTRATION])
            .isEqualTo(CompoundFormError.CONCENTRATION_REQUIRED)
    }

    @Test
    fun `an ampoule arrives pre-mixed, so it saves without a concentration`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("Testosterone"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("250"))
        viewModel.onAction(CompoundFormAction.Pick.OnContainerTypeSelected(ContainerType.AMPOULE))

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(compounds.created?.concentration).isNull()
    }

    @Test
    fun `an amount of zero is rejected`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("NAD+"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("0"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("5"))

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[CompoundFormField.AMOUNT_PER_CONTAINER])
            .isEqualTo(CompoundFormError.AMOUNT_NOT_POSITIVE)
    }

    @Test
    fun `a container count that is not a whole number is rejected`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("NAD+"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("5"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("5"))
        viewModel.onAction(CompoundFormAction.Edit.OnTotalContainersChange("2.5"))

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[CompoundFormField.TOTAL_CONTAINERS])
            .isEqualTo(CompoundFormError.CONTAINERS_INVALID)
    }

    @Test
    fun `editing a rejected field clears its error`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[CompoundFormField.NAME]).isNotNull()

        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("R"))

        assertThat(viewModel.state.value.errors[CompoundFormField.NAME]).isNull()
    }

    // -----------------------------------------------------------------------
    // Save (§4.4.4)
    // -----------------------------------------------------------------------

    @Test
    fun `Create stores the whole form and closes`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("  Retatrutide  "))
        viewModel.onAction(CompoundFormAction.Edit.OnTotalContainersChange("6"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("10"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("5"))
        viewModel.onAction(CompoundFormAction.Edit.OnExpiryAfterOpeningDaysChange("30"))
        viewModel.onAction(CompoundFormAction.Pick.OnStorageLocationSelected(StorageLocation.FREEZER))

        viewModel.events.test {
            viewModel.onAction(CompoundFormAction.OnSaveClick)

            assertThat(awaitItem()).isEqualTo(CompoundFormEvent.Done)
        }

        val created = requireNotNull(compounds.created)
        assertThat(created.name).isEqualTo("Retatrutide")
        assertThat(created.numberOfContainers).isEqualTo(6)
        assertThat(created.amountPerContainer).isEqualTo(Quantity(Decimal.parse("10"), UnitCode.MG))
        assertThat(created.concentration).isEqualTo(
            Concentration(
                amount = Quantity(Decimal.parse("5"), UnitCode.MG),
                per = Quantity(Decimal.parse("1"), UnitCode.ML),
            ),
        )
        assertThat(created.expiryAfterOpeningDays).isEqualTo(30)
        assertThat(created.storageLocation).isEqualTo(StorageLocation.FREEZER)
        assertThat(created.currentOpened).isNull()
    }

    @Test
    fun `blank optional text is stored as null, not as an empty string`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("NAD+"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("5"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("5"))
        viewModel.onAction(CompoundFormAction.Edit.OnSupplierChange("   "))

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(compounds.created?.supplier).isNull()
        assertThat(compounds.created?.batchNumber).isNull()
        assertThat(compounds.created?.notes).isNull()
    }

    /**
     * §4.4.4's worked example: total owned `3` with one opened container stores `2` unopened, and the
     * two together still describe the three containers the user is holding.
     */
    @Test
    fun `Edit stores the total-owned count minus the opened container`() = runTest {
        compounds.stored.value = compound(
            numberOfContainers = 2,
            opened = openedContainer(remaining = Quantity(Decimal.parse("5"), UnitCode.MG)),
        )

        val viewModel = viewModel(compoundId = COMPOUND_ID)

        // The field shows the total owned, which is the stored unopened count plus the opened one.
        assertThat(viewModel.state.value.draft.totalContainers).isEqualTo("3")

        viewModel.onAction(CompoundFormAction.OnSaveClick)

        val updated = requireNotNull(compounds.updated)
        assertThat(updated.numberOfContainers).isEqualTo(2)
        assertThat(updated.currentOpened?.remainingAmount).isEqualTo(Quantity(Decimal.parse("5"), UnitCode.MG))
    }

    @Test
    fun `raising the total owned to 4 with one opened stores 3 unopened`() = runTest {
        compounds.stored.value = compound(numberOfContainers = 2, opened = openedContainer())

        val viewModel = viewModel(compoundId = COMPOUND_ID)
        viewModel.onAction(CompoundFormAction.Edit.OnTotalContainersChange("4"))
        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(compounds.updated?.numberOfContainers).isEqualTo(3)
    }

    @Test
    fun `a total of zero is rejected while a container is open`() = runTest {
        compounds.stored.value = compound(numberOfContainers = 2, opened = openedContainer())

        val viewModel = viewModel(compoundId = COMPOUND_ID)
        viewModel.onAction(CompoundFormAction.Edit.OnTotalContainersChange("0"))
        viewModel.onAction(CompoundFormAction.OnSaveClick)

        assertThat(viewModel.state.value.errors[CompoundFormField.TOTAL_CONTAINERS])
            .isEqualTo(CompoundFormError.CONTAINERS_BELOW_OPENED)
        assertThat(compounds.updated).isNull()
    }

    @Test
    fun `a failed write reports the error and leaves the form open`() = runTest {
        compounds.createError = DataError.Local.DISK_FULL

        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("NAD+"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("5"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("5"))

        viewModel.events.test {
            viewModel.onAction(CompoundFormAction.OnSaveClick)

            assertThat(awaitItem()).isInstanceOfShowError()
        }
        assertThat(viewModel.state.value.isSaving).isFalse()
    }

    // -----------------------------------------------------------------------
    // Edit mode (§4.4.1)
    // -----------------------------------------------------------------------

    @Test
    fun `Edit loads the compound into the form`() = runTest {
        compounds.stored.value = compound(numberOfContainers = 2, opened = openedContainer())

        val viewModel = viewModel(compoundId = COMPOUND_ID)

        val state = viewModel.state.value
        assertThat(state.isEdit).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.editedCompoundName).isEqualTo("Semaglutide")
        assertThat(state.draft.name).isEqualTo("Semaglutide")
        assertThat(state.draft.amountPerContainer).isEqualTo("5")
        assertThat(state.opened?.openedDaysAgo).isEqualTo(12)
        assertThat(state.isDirty).isFalse()
    }

    @Test
    fun `a compound that is gone reports it and closes`() = runTest {
        val viewModel = viewModel(compoundId = COMPOUND_ID)

        viewModel.events.test {
            assertThat(awaitItem()).isInstanceOfShowError()
            assertThat(awaitItem()).isEqualTo(CompoundFormEvent.Done)
        }
    }

    // -----------------------------------------------------------------------
    // Discard + draft (§4.4.5)
    // -----------------------------------------------------------------------

    @Test
    fun `closing an untouched form leaves without asking`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(CompoundFormAction.OnCancelClick)

            assertThat(awaitItem()).isEqualTo(CompoundFormEvent.Done)
        }
        assertThat(viewModel.state.value.isDiscardDialogOpen).isFalse()
    }

    @Test
    fun `closing a changed form confirms first`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("R"))

        viewModel.events.test {
            viewModel.onAction(CompoundFormAction.OnCancelClick)

            expectNoEvents()
            assertThat(viewModel.state.value.isDiscardDialogOpen).isTrue()

            viewModel.onAction(CompoundFormAction.OnDiscardConfirm)

            assertThat(awaitItem()).isEqualTo(CompoundFormEvent.Done)
        }
    }

    @Test
    fun `typing then deleting back to the loaded value is not a change`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("R"))
        assertThat(viewModel.state.value.isDirty).isTrue()

        viewModel.onAction(CompoundFormAction.Edit.OnNameChange(""))
        assertThat(viewModel.state.value.isDirty).isFalse()
    }

    // The draft's round trip through the `SavedStateHandle` needs a real `Bundle` to serialize into,
    // so it lives in `CompoundFormDraftPersistenceTest` on Robolectric.

    // -----------------------------------------------------------------------
    // Live stock preview (§6.4.2)
    // -----------------------------------------------------------------------

    @Test
    fun `the preview totals the containers and works out the volume they make up to`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnTotalContainersChange("6"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("10"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("5"))

        val forecast = requireNotNull(viewModel.state.value.forecast)
        assertThat(forecast.totalStock).isEqualTo("60 mg")
        assertThat(forecast.containers).isEqualTo(6)
        assertThat(forecast.volumePerContainer).isEqualTo("2 ml")
    }

    @Test
    fun `the preview counts only what is left in the opened container`() = runTest {
        compounds.stored.value = compound(
            numberOfContainers = 2,
            opened = openedContainer(remaining = Quantity(Decimal.parse("1.5"), UnitCode.MG)),
        )

        val viewModel = viewModel(compoundId = COMPOUND_ID)

        // Two sealed 5 mg vials plus 1.5 mg left in the opened one.
        assertThat(viewModel.state.value.forecast?.totalStock).isEqualTo("11.5 mg")
    }

    /**
     * "…once mixed" is reconstitution talk. Dividing a tablet count by a per-tablet strength answers
     * a question nobody asked, so the line is simply absent for a Form that is not mixed or poured.
     */
    @Test
    fun `the preview leaves out the mixed volume when the concentration is not per volume`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.TABLET))
        viewModel.onAction(CompoundFormAction.Edit.OnTotalContainersChange("2"))
        viewModel.onAction(CompoundFormAction.Edit.OnConcentrationChange("1"))

        val forecast = requireNotNull(viewModel.state.value.forecast)
        assertThat(forecast.totalStock).isEqualTo("60 tablet")
        assertThat(forecast.volumePerContainer).isNull()
    }

    @Test
    fun `an amount that is not yet a number has no preview rather than a wrong one`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("."))

        assertThat(viewModel.state.value.forecast).isNull()
    }

    // -----------------------------------------------------------------------
    // Onboarding (§4.14 step 2)
    // -----------------------------------------------------------------------

    @Test
    fun `Skip ends the step without saving and without asking`() = runTest {
        val viewModel = viewModel(isOnboarding = true)
        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("Retatrutide"))

        viewModel.events.test {
            viewModel.onAction(CompoundFormAction.OnSkipClick)

            assertThat(awaitItem()).isEqualTo(CompoundFormEvent.Done)
        }
        assertThat(compounds.created).isNull()
    }

    @Test
    fun `the Helper button opens the Reconstitution Helper for the compound being edited`() = runTest {
        compounds.stored.value = compound()
        val viewModel = viewModel(compoundId = COMPOUND_ID)

        viewModel.events.test {
            viewModel.onAction(CompoundFormAction.OnReconstitutionHelperClick)

            assertThat(awaitItem()).isEqualTo(CompoundFormEvent.OpenReconstitutionHelper(COMPOUND_ID))
        }
    }

    // -----------------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------------

    private fun viewModel(
        compoundId: Long? = null,
        isOnboarding: Boolean = false,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = CompoundFormViewModel(
        savedStateHandle = savedStateHandle,
        compoundRepository = compounds,
        args = CompoundFormArgs(compoundId = compoundId, isOnboarding = isOnboarding),
        now = { NOW },
        timeZone = TimeZone.UTC,
    )

    private fun assertk.Assert<CompoundFormEvent>.isInstanceOfShowError() =
        given { assertThat(it is CompoundFormEvent.ShowError).isTrue() }

    private class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow<CompoundSupply?>(null)
        var created: CompoundSupply? = null
        var updated: CompoundSupply? = null

        /** When set, `create` fails with this instead of storing. */
        var createError: DataError.Local? = null

        override fun observeAll(): Flow<List<CompoundSupply>> = stored.map { listOfNotNull(it) }

        override fun observeById(id: Long): Flow<CompoundSupply?> = stored

        override suspend fun create(compound: CompoundSupply): Result<Long, DataError.Local> {
            createError?.let { return Result.Error(it) }
            created = compound
            return Result.Success(COMPOUND_ID)
        }

        override suspend fun update(
            compound: CompoundSupply,
            capOpenedContainer: Boolean,
        ): EmptyResult<DataError.Local> {
            updated = compound
            return Result.Success(Unit)
        }

        override suspend fun archive(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun duplicate(id: Long): Result<Long, DataError.Local> = throw NotImplementedError()

        override suspend fun openContainer(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun closeContainer(id: Long, reason: String?): EmptyResult<DataError.Local> =
            throw NotImplementedError()

        override suspend fun editOpenedContainer(
            compoundSupplyId: Long,
            remainingAmount: Quantity?,
            expiryAfterOpeningDays: Int?,
            userDefinedExpiryDate: LocalDate?,
        ): EmptyResult<DataError.Local> = throw NotImplementedError()
    }

    private companion object {
        const val COMPOUND_ID = 7L
        val NOW: Instant = Instant.parse("2026-08-15T09:00:00Z")
        val OPENED_AT: Instant = Instant.parse("2026-08-03T09:00:00Z")

        fun openedContainer(remaining: Quantity = Quantity(Decimal.parse("5"), UnitCode.MG)) = OpenedContainer(
            openedAt = OPENED_AT,
            remainingAmount = remaining,
            expiryAfterOpeningDays = null,
            userDefinedExpiryDate = null,
            predictedExpiryDate = null,
        )

        fun compound(numberOfContainers: Int = 2, opened: OpenedContainer? = null) = CompoundSupply(
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
            numberOfContainers = numberOfContainers,
            currentOpened = opened,
            batchExpiryDate = null,
            expiryAfterOpeningDays = 28,
            storageLocation = StorageLocation.FRIDGE,
            batchNumber = null,
            supplier = null,
            notes = null,
            deletedAt = null,
            createdAt = OPENED_AT,
            updatedAt = OPENED_AT,
        )
    }
}
