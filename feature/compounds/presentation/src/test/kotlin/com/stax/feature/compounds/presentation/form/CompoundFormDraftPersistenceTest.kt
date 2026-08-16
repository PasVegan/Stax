package com.stax.feature.compounds.presentation.form

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
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
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * §4.4.5's auto-saved draft, taken through the round trip that makes it worth having: the form is
 * edited, the `SavedStateHandle` is saved and restored as the platform does on process death, and a
 * fresh ViewModel is built over what came back.
 *
 * On Robolectric because the draft serializes into a real `Bundle` — which is also what makes this
 * a genuine check of the draft's shape, `LocalDate` and touched-field set included, rather than of
 * an in-memory copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompoundFormDraftPersistenceTest {

    private lateinit var compounds: FakeCompoundRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        compounds = FakeCompoundRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the draft survives process death`() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(savedStateHandle = handle)

        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("Retatrutide"))
        viewModel.onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("10"))
        viewModel.onAction(CompoundFormAction.Overlay.OnBatchExpirySelected(LocalDate.parse("2027-07-29")))

        val restored = viewModel(savedStateHandle = handle.afterProcessDeath())

        assertThat(restored.state.value.draft.name).isEqualTo("Retatrutide")
        assertThat(restored.state.value.draft.amountPerContainer).isEqualTo("10")
        assertThat(restored.state.value.draft.batchExpiryDate).isEqualTo(LocalDate.parse("2027-07-29"))
        assertThat(restored.state.value.isDirty).isTrue()
    }

    @Test
    fun `a touched field stays protected from smart defaults across process death`() {
        val handle = SavedStateHandle()
        viewModel(savedStateHandle = handle)
            .onAction(CompoundFormAction.Edit.OnAmountPerContainerChange("12"))

        val restored = viewModel(savedStateHandle = handle.afterProcessDeath())
        restored.onAction(CompoundFormAction.Pick.OnFormSelected(CompoundForm.LIQUID))

        assertThat(restored.state.value.draft.amountPerContainer).isEqualTo("12")
    }

    @Test
    fun `a restored draft beats the stored compound in Edit mode`() {
        compounds.stored.value = compound()
        val handle = SavedStateHandle()

        viewModel(compoundId = COMPOUND_ID, savedStateHandle = handle)
            .onAction(CompoundFormAction.Edit.OnNameChange("Semaglutide (new batch)"))

        val restored = viewModel(compoundId = COMPOUND_ID, savedStateHandle = handle.afterProcessDeath())

        assertThat(restored.state.value.draft.name).isEqualTo("Semaglutide (new batch)")
        assertThat(restored.state.value.editedCompoundName).isEqualTo("Semaglutide")
        assertThat(restored.state.value.isDirty).isTrue()
    }

    @Test
    fun `finishing the form drops the draft, so the next Create opens clean`() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(savedStateHandle = handle)

        viewModel.onAction(CompoundFormAction.Edit.OnNameChange("Retatrutide"))
        viewModel.onAction(CompoundFormAction.OnDiscardConfirm)

        val next = viewModel(savedStateHandle = handle.afterProcessDeath())

        assertThat(next.state.value.draft.name).isEqualTo("")
        assertThat(next.state.value.isDirty).isFalse()
    }

    /**
     * What the platform does around process death: every registered `SavedStateProvider` is asked to
     * write itself out, and the handle is rebuilt from the result. `@RestrictTo(LIBRARY_GROUP)` on
     * both halves is aimed at production code — a test is the one place that has to stand in for the
     * framework.
     */
    @Suppress("RestrictedApi")
    private fun SavedStateHandle.afterProcessDeath(): SavedStateHandle =
        SavedStateHandle.createHandle(savedStateProvider().saveState(), null)

    private fun viewModel(compoundId: Long? = null, savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        CompoundFormViewModel(
            savedStateHandle = savedStateHandle,
            compoundRepository = compounds,
            args = CompoundFormArgs(compoundId = compoundId),
            now = { NOW },
            timeZone = TimeZone.UTC,
        )

    private class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow<CompoundSupply?>(null)

        override fun observeAll(): Flow<List<CompoundSupply>> = stored.map { listOfNotNull(it) }

        override fun observeById(id: Long): Flow<CompoundSupply?> = stored

        override suspend fun create(compound: CompoundSupply): Result<Long, DataError.Local> =
            Result.Success(COMPOUND_ID)

        override suspend fun update(
            compound: CompoundSupply,
            capOpenedContainer: Boolean,
        ): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun archive(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun duplicate(id: Long): Result<Long, DataError.Local> = throw NotImplementedError()

        override suspend fun openContainer(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun closeContainer(id: Long, reason: String?): EmptyResult<DataError.Local> =
            throw NotImplementedError()

        override suspend fun addOpenedContainer(
            compoundSupplyId: Long,
            openedAt: Instant,
            remainingAmount: Quantity,
            expiryAfterOpeningDays: Int?,
            userDefinedExpiryDate: LocalDate?,
        ): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun editOpenedContainer(
            compoundSupplyId: Long,
            openedAt: Instant?,
            remainingAmount: Quantity?,
            expiryAfterOpeningDays: Int?,
            userDefinedExpiryDate: LocalDate?,
        ): EmptyResult<DataError.Local> = throw NotImplementedError()
    }

    private companion object {
        const val COMPOUND_ID = 7L
        val NOW: Instant = Instant.parse("2026-08-15T09:00:00Z")

        fun compound() = CompoundSupply(
            id = COMPOUND_ID,
            name = "Semaglutide",
            category = CompoundCategory.PEPTIDE,
            form = CompoundForm.INJECTABLE,
            containerType = ContainerType.VIAL,
            primaryUnit = UnitCode.MG,
            amountPerContainer = Quantity(Decimal.parse("5"), UnitCode.MG),
            concentration = null,
            numberOfContainers = 2,
            currentOpened = null,
            batchExpiryDate = null,
            expiryAfterOpeningDays = 28,
            storageLocation = StorageLocation.FRIDGE,
            batchNumber = null,
            supplier = null,
            notes = null,
            deletedAt = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }
}
