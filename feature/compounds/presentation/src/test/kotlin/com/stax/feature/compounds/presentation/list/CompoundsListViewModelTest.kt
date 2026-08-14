package com.stax.feature.compounds.presentation.list

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundDosesLeft
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.InventoryWarning
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CompoundsListViewModelTest {

    private lateinit var compounds: FakeCompoundRepository
    private lateinit var inventory: FakeInventoryRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        compounds = FakeCompoundRepository()
        inventory = FakeInventoryRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `All is selected by default and shows every compound`() = runTest {
        compounds.stored.value = listOf(compound(id = 1, name = "Retatrutide"), compound(id = 2, name = "NAD+"))

        val viewModel = viewModel()

        assertThat(viewModel.state.value.statusFilter).isEqualTo(CompoundStatusFilter.ALL)
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Retatrutide", "NAD+")
    }

    @Test
    fun `loading clears once the first emission arrives`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `Low stock keeps compounds under seven doses left`() = runTest {
        compounds.stored.value = listOf(
            compound(id = 1, name = "Retatrutide"),
            compound(id = 2, name = "NAD+"),
            compound(id = 3, name = "Tirzepatide"),
        )
        inventory.dosesLeft.value = listOf(
            dosesLeft(id = 1, doses = 6),
            dosesLeft(id = 2, doses = 7),
            dosesLeft(id = 3, doses = null),
        )

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnStatusFilterClick(CompoundStatusFilter.LOW_STOCK))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Retatrutide")
    }

    @Test
    fun `a compound without an active protocol is never low stock`() = runTest {
        compounds.stored.value = listOf(compound(id = 1, name = "Retatrutide"))
        inventory.dosesLeft.value = listOf(dosesLeft(id = 1, doses = null))

        val viewModel = viewModel()

        val item = viewModel.state.value.items.single()
        assertThat(item.isLowStock).isFalse()
        assertThat(item.dosesLeft).isNull()
    }

    @Test
    fun `Expiring soon keeps compounds whose effective expiry falls inside 28 days`() = runTest {
        compounds.stored.value = listOf(
            compound(id = 1, name = "Batch soon", batchExpiryDate = TODAY.plusDays(27)),
            compound(id = 2, name = "Batch later", batchExpiryDate = TODAY.plusDays(28)),
            compound(id = 3, name = "No expiry"),
        )

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnStatusFilterClick(CompoundStatusFilter.EXPIRING_SOON))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Batch soon")
    }

    @Test
    fun `effective expiry is the earlier of the batch and opened container dates`() = runTest {
        compounds.stored.value = listOf(
            compound(
                id = 1,
                name = "Retatrutide",
                batchExpiryDate = TODAY.plusDays(90),
                opened = openedContainer(predictedExpiryDate = TODAY.plusDays(10)),
            ),
        )

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnStatusFilterClick(CompoundStatusFilter.EXPIRING_SOON))

        val item = viewModel.state.value.items.single()
        assertThat(item.effectiveExpiry).isEqualTo(TODAY.plusDays(10))
        assertThat(item.isExpiringSoon).isTrue()
    }

    @Test
    fun `a user defined container expiry wins over the predicted one`() = runTest {
        compounds.stored.value = listOf(
            compound(
                id = 1,
                name = "Retatrutide",
                opened = openedContainer(
                    predictedExpiryDate = TODAY.plusDays(10),
                    userDefinedExpiryDate = TODAY.plusDays(60),
                ),
            ),
        )

        val viewModel = viewModel()

        assertThat(viewModel.state.value.items.single().effectiveExpiry).isEqualTo(TODAY.plusDays(60))
    }

    @Test
    fun `Category narrows to the selected categories and clears again on a second tap`() = runTest {
        compounds.stored.value = listOf(
            compound(id = 1, name = "Retatrutide", category = CompoundCategory.PEPTIDE),
            compound(id = 2, name = "NAD+", category = CompoundCategory.SUPPLEMENT),
            compound(id = 3, name = "Testosterone", category = CompoundCategory.HORMONE),
        )

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnCategoryToggle(CompoundCategory.PEPTIDE))
        viewModel.onAction(CompoundsListAction.OnCategoryToggle(CompoundCategory.HORMONE))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Retatrutide", "Testosterone")

        viewModel.onAction(CompoundsListAction.OnCategoryToggle(CompoundCategory.HORMONE))

        assertThat(viewModel.state.value.selectedCategories.toList()).containsExactly(CompoundCategory.PEPTIDE)
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Retatrutide")
    }

    @Test
    fun `Form narrows to the selected forms`() = runTest {
        compounds.stored.value = listOf(
            compound(id = 1, name = "Retatrutide", form = CompoundForm.INJECTABLE),
            compound(id = 2, name = "NAD+", form = CompoundForm.CAPSULE),
        )

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnFormToggle(CompoundForm.CAPSULE))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("NAD+")
    }

    @Test
    fun `Category and Form and Low stock and search all narrow together`() = runTest {
        compounds.stored.value = listOf(
            compound(id = 1, name = "Reta injectable", form = CompoundForm.INJECTABLE),
            compound(id = 2, name = "Reta capsule", form = CompoundForm.CAPSULE),
            compound(id = 3, name = "Reta hormone", category = CompoundCategory.HORMONE),
            compound(id = 4, name = "Other injectable", form = CompoundForm.INJECTABLE),
            compound(id = 5, name = "Reta stocked", form = CompoundForm.INJECTABLE),
        )
        inventory.dosesLeft.value = listOf(
            dosesLeft(id = 1, doses = 3),
            dosesLeft(id = 2, doses = 3),
            dosesLeft(id = 3, doses = 3),
            dosesLeft(id = 4, doses = 3),
            dosesLeft(id = 5, doses = 30),
        )

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnStatusFilterClick(CompoundStatusFilter.LOW_STOCK))
        viewModel.onAction(CompoundsListAction.OnCategoryToggle(CompoundCategory.PEPTIDE))
        viewModel.onAction(CompoundsListAction.OnFormToggle(CompoundForm.INJECTABLE))
        viewModel.onAction(CompoundsListAction.OnSearchQueryChange("reta"))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Reta injectable")
    }

    @Test
    fun `picking All again drops the Low stock constraint`() = runTest {
        compounds.stored.value = listOf(compound(id = 1, name = "Retatrutide"), compound(id = 2, name = "NAD+"))
        inventory.dosesLeft.value = listOf(dosesLeft(id = 1, doses = 3))

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnStatusFilterClick(CompoundStatusFilter.LOW_STOCK))
        viewModel.onAction(CompoundsListAction.OnStatusFilterClick(CompoundStatusFilter.ALL))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Retatrutide", "NAD+")
    }

    @Test
    fun `search matches a case insensitive substring of the name`() = runTest {
        compounds.stored.value = listOf(compound(id = 1, name = "Retatrutide"), compound(id = 2, name = "NAD+"))

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnSearchQueryChange("  TRUT "))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Retatrutide")

        viewModel.onAction(CompoundsListAction.OnSearchQueryChange(""))

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Retatrutide", "NAD+")
    }

    @Test
    fun `a filtered list reacts to a new repository emission`() = runTest {
        compounds.stored.value = listOf(compound(id = 1, name = "Retatrutide", category = CompoundCategory.PEPTIDE))

        val viewModel = viewModel()
        viewModel.onAction(CompoundsListAction.OnCategoryToggle(CompoundCategory.HORMONE))

        assertThat(viewModel.state.value.items).isEmpty()

        compounds.stored.value = compounds.stored.value +
            compound(id = 2, name = "Testosterone", category = CompoundCategory.HORMONE)

        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Testosterone")
    }

    @Test
    fun `the row carries the opened container remainder and the sealed container count`() = runTest {
        compounds.stored.value = listOf(
            compound(
                id = 1,
                name = "Retatrutide",
                numberOfContainers = 2,
                opened = openedContainer(remainingAmount = Quantity(Decimal.parse("8.5"), UnitCode.MG)),
            ),
            compound(id = 2, name = "NAD+", numberOfContainers = 3),
        )

        val viewModel = viewModel()

        val (opened, sealed) = viewModel.state.value.items
        assertThat(opened.remaining).isEqualTo("8.5 mg")
        assertThat(opened.sealedContainers).isEqualTo(2)
        assertThat(sealed.remaining).isNull()
        assertThat(sealed.sealedContainers).isEqualTo(3)
    }

    @Test
    fun `tapping a row navigates to its detail`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(CompoundsListAction.OnCompoundClick(compoundId = 42))

            assertThat(awaitItem()).isEqualTo(CompoundsListEvent.NavigateToCompoundDetail(42))
        }
    }

    @Test
    fun `the Add FAB navigates to Create Compound`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(CompoundsListAction.OnAddCompoundClick)

            assertThat(awaitItem()).isEqualTo(CompoundsListEvent.NavigateToCreateCompound)
        }
    }

    private fun viewModel() = CompoundsListViewModel(compounds, inventory, today = { TODAY })

    private class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow<List<CompoundSupply>>(emptyList())

        override fun observeAll(): Flow<List<CompoundSupply>> = stored

        override fun observeById(id: Long): Flow<CompoundSupply?> = throw NotImplementedError()

        override suspend fun create(compound: CompoundSupply): Result<Long, DataError.Local> =
            throw NotImplementedError()

        override suspend fun update(compound: CompoundSupply): EmptyResult<DataError.Local> =
            throw NotImplementedError()

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

    private class FakeInventoryRepository : InventoryRepository {
        val dosesLeft = MutableStateFlow<List<CompoundDosesLeft>>(emptyList())

        override fun observeWarnings(): Flow<List<InventoryWarning>> = throw NotImplementedError()

        override fun observeDosesLeftPerCompound(): Flow<List<CompoundDosesLeft>> = dosesLeft

        override fun observeRunOutDate(protocolId: Long): Flow<LocalDate?> = throw NotImplementedError()
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-08-14")
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

        fun dosesLeft(id: Long, doses: Int?) = CompoundDosesLeft(
            compoundSupplyId = id,
            compoundName = "",
            dosesLeft = doses,
            dosesPerActualInjection = null,
            daysLeft = null,
        )

        fun openedContainer(
            remainingAmount: Quantity = Quantity(Decimal.parse("5"), UnitCode.MG),
            predictedExpiryDate: LocalDate? = null,
            userDefinedExpiryDate: LocalDate? = null,
        ) = OpenedContainer(
            openedAt = EPOCH,
            remainingAmount = remainingAmount,
            expiryAfterOpeningDays = null,
            userDefinedExpiryDate = userDefinedExpiryDate,
            predictedExpiryDate = predictedExpiryDate,
        )

        @Suppress("LongParameterList")
        fun compound(
            id: Long,
            name: String,
            category: CompoundCategory = CompoundCategory.PEPTIDE,
            form: CompoundForm = CompoundForm.INJECTABLE,
            numberOfContainers: Int = 1,
            opened: OpenedContainer? = null,
            batchExpiryDate: LocalDate? = null,
        ) = CompoundSupply(
            id = id,
            name = name,
            category = category,
            form = form,
            containerType = ContainerType.VIAL,
            primaryUnit = UnitCode.MG,
            amountPerContainer = Quantity(Decimal.parse("10"), UnitCode.MG),
            concentration = null,
            numberOfContainers = numberOfContainers,
            currentOpened = opened,
            batchExpiryDate = batchExpiryDate,
            expiryAfterOpeningDays = null,
            storageLocation = StorageLocation.FRIDGE,
            batchNumber = null,
            supplier = null,
            notes = null,
            deletedAt = null,
            createdAt = EPOCH,
            updatedAt = EPOCH,
        )
    }
}
