package com.stax.feature.reconstitution.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
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
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.CompoundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * §4.6: the helper is one derivation run on every keystroke, so most of these drive an action and read
 * the state back. The numbers are the worked example of §4.6 / `19 · Reconstitution Helper`: a 5 mg
 * vial in 2 mL of diluent is 2.5 mg/mL, and 0.25 mg of it is 0.10 mL — 10 insulin units — with 20
 * doses to the vial.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconstitutionViewModelTest {

    private lateinit var compounds: FakeCompoundRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        compounds = FakeCompoundRepository()
        compounds.stored.value = compound()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fills the container from the compound and leaves it read-only`() = runTest {
        val viewModel = viewModel()

        val state = viewModel.state.value
        assertThat(state.compoundName).isEqualTo("Semaglutide")
        assertThat(state.containerAmount).isEqualTo("5")
        assertThat(state.containerUnit).isEqualTo(UnitCode.MG)
        assertThat(state.isContainerEditable).isFalse()
        assertThat(state.doseUnit).isEqualTo(UnitCode.MG)
    }

    /** §4.6: "most reconstitution events use the saved concentration" — 5 mg at 2.5 mg/mL is 2 mL. */
    @Test
    fun `seeds the diluent from the stored concentration`() = runTest {
        compounds.stored.value = compound(concentration = concentrationOf("2.5"))

        val viewModel = viewModel()

        assertThat(viewModel.state.value.diluent).isEqualTo("2")
        assertThat(viewModel.state.value.concentration).isEqualTo("2.5")
    }

    @Test
    fun `leaves the diluent blank when the compound has no concentration`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.diluent).isEqualTo("")
        assertThat(viewModel.state.value.concentration).isNull()
    }

    @Test
    fun `computes concentration, draw-to and doses per container`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ReconstitutionAction.OnDiluentChange("2"))
        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("0.25"))

        val state = viewModel.state.value
        assertThat(state.concentration).isEqualTo("2.5")
        assertThat(state.dosesPerContainer).isEqualTo(20)
        assertThat(state.drawTo).isEqualTo("10")
    }

    /** The acceptance of M8-01: editing the diluent moves every derived figure with it. */
    @Test
    fun `re-derives everything on a diluent edit`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("0.25"))

        viewModel.onAction(ReconstitutionAction.OnDiluentChange("2"))
        assertThat(viewModel.state.value.drawTo).isEqualTo("10")

        viewModel.onAction(ReconstitutionAction.OnDiluentChange("1"))
        val state = viewModel.state.value
        assertThat(state.concentration).isEqualTo("5")
        assertThat(state.drawTo).isEqualTo("5")
        // Half the volume dissolves the same 5 mg, so the vial still holds the same 20 doses.
        assertThat(state.dosesPerContainer).isEqualTo(20)
    }

    @Test
    fun `re-derives everything on a desired-dose edit`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ReconstitutionAction.OnDiluentChange("2"))

        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("0.5"))
        assertThat(viewModel.state.value.drawTo).isEqualTo("20")
        assertThat(viewModel.state.value.dosesPerContainer).isEqualTo(10)

        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("1"))
        assertThat(viewModel.state.value.drawTo).isEqualTo("40")
        assertThat(viewModel.state.value.dosesPerContainer).isEqualTo(5)
    }

    /** §4.6.4's Display tile restates the same volume; it never changes the mix. */
    @Test
    fun `restates the drawn dose in millilitres`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ReconstitutionAction.OnDiluentChange("2"))
        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("0.25"))

        viewModel.onAction(ReconstitutionAction.OnDisplaySelected(DoseDisplay.MILLILITRES))

        assertThat(viewModel.state.value.drawTo).isEqualTo("0.10")
        assertThat(viewModel.state.value.concentration).isEqualTo("2.5")
        assertThat(viewModel.state.value.openPicker).isNull()
    }

    /** A dose in mcg of a mg vial is the same dose: 250 mcg of 2.5 mg/mL is still 10 units. */
    @Test
    fun `converts a desired dose given in another unit of the same family`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ReconstitutionAction.OnDiluentChange("2"))
        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("250"))

        viewModel.onAction(ReconstitutionAction.OnDoseUnitSelected(UnitCode.MCG))

        assertThat(viewModel.state.value.drawTo).isEqualTo("10")
        assertThat(viewModel.state.value.dosesPerContainer).isEqualTo(20)
    }

    @Test
    fun `holds the results back until the inputs are numbers`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("0.25"))

        // Blank, half-typed and zero are all "still typing", not an error to report.
        listOf("", ".", "0").forEach { diluent ->
            viewModel.onAction(ReconstitutionAction.OnDiluentChange(diluent))
            val state = viewModel.state.value
            assertThat(state.concentration).isNull()
            assertThat(state.drawTo).isNull()
            assertThat(state.canSave).isFalse()
        }
    }

    /** §4.6.7's dock has nothing to set until the mix produces a concentration. */
    @Test
    fun `can save once the mix yields a concentration`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.canSave).isFalse()

        viewModel.onAction(ReconstitutionAction.OnDiluentChange("2"))

        assertThat(viewModel.state.value.canSave).isTrue()
    }

    /** The standalone calculator of §4.4.3: no compound, so the container is typed. */
    @Test
    fun `computes from a typed container in the standalone calculator`() = runTest {
        val viewModel = viewModel(compoundId = null)

        assertThat(viewModel.state.value.isContainerEditable).isTrue()
        viewModel.onAction(ReconstitutionAction.OnContainerAmountChange("10"))
        viewModel.onAction(ReconstitutionAction.OnDiluentChange("2"))
        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("0.5"))

        assertThat(viewModel.state.value.concentration).isEqualTo("5")
        assertThat(viewModel.state.value.dosesPerContainer).isEqualTo(20)
    }

    /** A container unit from another family takes the desired dose with it — the mix cannot mix them. */
    @Test
    fun `moves the desired dose to the container's family`() = runTest {
        val viewModel = viewModel(compoundId = null)
        viewModel.onAction(ReconstitutionAction.OnDesiredDoseChange("0.25"))

        viewModel.onAction(ReconstitutionAction.OnContainerUnitSelected(UnitCode.IU))

        assertThat(viewModel.state.value.doseUnit).isEqualTo(UnitCode.IU)
        assertThat(viewModel.state.value.doseUnitOptions).isEqualTo(IU_UNITS)
    }

    /** A typed edit is not thrown away when the observed compound re-emits (§4.6). */
    @Test
    fun `keeps what the user typed when the compound row changes underneath`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ReconstitutionAction.OnDiluentChange("3"))

        compounds.stored.value = compound(name = "Semaglutide (renamed)")

        assertThat(viewModel.state.value.diluent).isEqualTo("3")
        assertThat(viewModel.state.value.compoundName).isEqualTo("Semaglutide (renamed)")
    }

    @Test
    fun `leaves when the compound is gone`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            compounds.stored.value = null
            assertThat(awaitItem()).isEqualTo(ReconstitutionEvent.NavigateBack)
        }
    }

    @Test
    fun `closing the helper navigates back`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ReconstitutionAction.OnCloseClick)
            assertThat(awaitItem()).isEqualTo(ReconstitutionEvent.NavigateBack)
        }
    }

    @Test
    fun `unfolds the calculation`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ReconstitutionAction.OnToggleCalculation)
        assertThat(viewModel.state.value.isCalculationExpanded).isTrue()

        viewModel.onAction(ReconstitutionAction.OnToggleCalculation)
        assertThat(viewModel.state.value.isCalculationExpanded).isFalse()
    }

    private fun viewModel(compoundId: Long? = COMPOUND_ID) =
        ReconstitutionViewModel(compounds, ReconstitutionArgs(compoundId))

    private fun compound(name: String = "Semaglutide", concentration: Concentration? = null) = CompoundSupply(
        id = COMPOUND_ID,
        name = name,
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainer = Quantity(Decimal.parse("5"), UnitCode.MG),
        concentration = concentration,
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

    private fun concentrationOf(perMl: String) = Concentration(
        amount = Quantity(Decimal.parse(perMl), UnitCode.MG),
        per = Quantity(Decimal.parse("1"), UnitCode.ML),
    )

    private class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow<CompoundSupply?>(null)

        override fun observeAll(): Flow<List<CompoundSupply>> = throw NotImplementedError()

        override fun observeById(id: Long): Flow<CompoundSupply?> = stored

        override suspend fun create(compound: CompoundSupply): Result<Long, DataError.Local> =
            throw NotImplementedError()

        override suspend fun update(
            compound: CompoundSupply,
            capOpenedContainer: Boolean,
        ): EmptyResult<DataError.Local> = throw NotImplementedError()

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
        val NOW: Instant = Clock.System.now()
    }
}
