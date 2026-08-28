package com.stax.feature.compounds.presentation.detail

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.stax.core.domain.AdministrationEvent
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundDosesLeft
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.DoseComponent
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.InventoryWarning
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.ScheduledDose
import com.stax.core.domain.ScheduledDoseStatus
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.AdministrationEventEdit
import com.stax.core.domain.repository.AdministrationEventRepository
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InventoryRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.ScheduledDoseRepository
import com.stax.feature.compounds.presentation.container.OpenedContainerSaveError
import com.stax.feature.compounds.presentation.container.OpenedContainerSheetAction
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CompoundDetailViewModelTest {

    private lateinit var compounds: FakeCompoundRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var protocols: FakeProtocolRepository
    private lateinit var scheduledDoses: FakeScheduledDoseRepository
    private lateinit var events: FakeAdministrationEventRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        compounds = FakeCompoundRepository()
        inventory = FakeInventoryRepository()
        protocols = FakeProtocolRepository()
        scheduledDoses = FakeScheduledDoseRepository()
        events = FakeAdministrationEventRepository()
        compounds.stored.value = compound()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- §4.3.2 Stat strip -------------------------------------------------

    @Test
    fun `stat strip takes doses and days left from the inventory aggregation`() = runTest {
        inventory.dosesLeft.value = listOf(dosesLeft(id = COMPOUND_ID, doses = 18, days = 63))

        val state = viewModel().state.value

        assertThat(state.stats.dosesLeft).isEqualTo(18)
        assertThat(state.stats.daysLeft).isEqualTo(63)
    }

    @Test
    fun `no active protocol leaves the supply figures unknown rather than zero`() = runTest {
        val state = viewModel().state.value

        assertThat(state.stats.dosesLeft).isNull()
        assertThat(state.stats.daysLeft).isNull()
    }

    @Test
    fun `expiry tile shows the container's when it falls before the batch's`() = runTest {
        compounds.stored.value = compound(
            batchExpiryDate = LocalDate.parse("2026-12-31"),
            opened = openedContainer(predictedExpiry = LocalDate.parse("2026-09-01")),
        )

        val expiry = viewModel().state.value.stats.expiry

        assertThat(expiry?.date).isEqualTo(LocalDate.parse("2026-09-01"))
        assertThat(expiry?.isContainerExpiry).isEqualTo(true)
    }

    @Test
    fun `expiry tile shows the batch's when it falls first`() = runTest {
        compounds.stored.value = compound(
            batchExpiryDate = LocalDate.parse("2026-09-01"),
            opened = openedContainer(predictedExpiry = LocalDate.parse("2026-12-31")),
        )

        val expiry = viewModel().state.value.stats.expiry

        assertThat(expiry?.date).isEqualTo(LocalDate.parse("2026-09-01"))
        assertThat(expiry?.isContainerExpiry).isEqualTo(false)
    }

    @Test
    fun `expiry tile is absent when the compound carries neither expiry`() = runTest {
        compounds.stored.value = compound(batchExpiryDate = null, opened = null)

        assertThat(viewModel().state.value.stats.expiry).isNull()
    }

    // --- §4.3.3 Opened container -------------------------------------------

    @Test
    fun `opened card reports how full the container is and how long it has been open`() = runTest {
        compounds.stored.value = compound(opened = openedContainer(remaining = "3.2", openedAgo = 12.days))

        val opened = viewModel().state.value.opened

        assertThat(opened?.remaining).isEqualTo("3.2")
        assertThat(opened?.capacity).isEqualTo("5")
        assertThat(opened?.fillFraction).isEqualTo(0.64f)
        assertThat(opened?.openedDaysAgo).isEqualTo(12)
    }

    // --- §4.3.4 Active protocols -------------------------------------------

    @Test
    fun `only active protocols are listed, each with its next pending dose`() = runTest {
        protocols.byCompound.value = listOf(
            protocol(id = 1, name = "Sema weekly titration"),
            protocol(id = 2, name = "Paused course", status = ProtocolStatus.PAUSED),
        )
        scheduledDoses.byProtocol[1] = MutableStateFlow(
            listOf(
                scheduledDose(id = 10, protocolId = 1, at = NOW + 3.days),
                scheduledDose(id = 11, protocolId = 1, at = NOW + 6.hours),
                scheduledDose(id = 12, protocolId = 1, at = NOW - 1.days, status = ScheduledDoseStatus.TAKEN),
            ),
        )

        val listed = viewModel().state.value.protocols

        assertThat(listed.map { it.name }).containsExactly("Sema weekly titration")
        assertThat(listed.single().nextDoseAt).isEqualTo(NOW + 6.hours)
        assertThat(listed.single().dose).isEqualTo("0.25 mg")
    }

    @Test
    fun `a protocol with nothing pending left reports no next dose`() = runTest {
        protocols.byCompound.value = listOf(protocol(id = 1, name = "Sema weekly titration"))
        scheduledDoses.byProtocol[1] = MutableStateFlow(
            listOf(scheduledDose(id = 10, protocolId = 1, at = NOW - 1.days, status = ScheduledDoseStatus.TAKEN)),
        )

        assertThat(viewModel().state.value.protocols.single().nextDoseAt).isNull()
    }

    // --- §4.3.6 – §4.3.8 History -------------------------------------------

    @Test
    fun `history pages in unfiltered and the badge counts Taken plus Partial all-time`() = runTest {
        events.history.value = listOf(
            historyEntry(id = 1, status = AdministrationEventStatus.TAKEN),
            historyEntry(id = 2, status = AdministrationEventStatus.PARTIAL),
            historyEntry(id = 3, status = AdministrationEventStatus.SKIPPED),
        )
        val viewModel = viewModel()

        assertThat(viewModel.history.asSnapshot().map { it.eventId }).containsExactly(1L, 2L, 3L)
        assertThat(viewModel.state.value.loggedDoseCount).isEqualTo(2)
    }

    @Test
    fun `a status chip re-queries the pages without moving the badge`() = runTest {
        events.history.value = listOf(
            historyEntry(id = 1, status = AdministrationEventStatus.TAKEN),
            historyEntry(id = 2, status = AdministrationEventStatus.PARTIAL),
            historyEntry(id = 3, status = AdministrationEventStatus.SKIPPED),
        )
        val viewModel = viewModel()

        viewModel.onAction(CompoundDetailAction.OnHistoryFilterClick(HistoryStatusFilter.SKIPPED))

        assertThat(viewModel.state.value.historyFilter).isEqualTo(HistoryStatusFilter.SKIPPED)
        assertThat(viewModel.history.asSnapshot().map { it.eventId }).containsExactly(3L)
        assertThat(viewModel.state.value.loggedDoseCount).isEqualTo(2)
    }

    @Test
    fun `a history row carries its dose, volume and site pre-rendered`() = runTest {
        events.history.value = listOf(historyEntry(id = 1, status = AdministrationEventStatus.TAKEN))

        val row = viewModel().history.asSnapshot().single()

        assertThat(row.dose).isEqualTo("0.25 mg")
        assertThat(row.volume).isEqualTo("0.1 ml")
        assertThat(row.siteName).isEqualTo("Abdomen R")
    }

    // --- §4.3.5 Notes ------------------------------------------------------

    @Test
    fun `Show more toggles the notes open and closed`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.isNotesExpanded).isFalse()
        viewModel.onAction(CompoundDetailAction.OnToggleNotes)
        assertThat(viewModel.state.value.isNotesExpanded).isTrue()
        viewModel.onAction(CompoundDetailAction.OnToggleNotes)
        assertThat(viewModel.state.value.isNotesExpanded).isFalse()
    }

    @Test
    fun `blank notes are no notes`() = runTest {
        compounds.stored.value = compound(notes = "   ")

        assertThat(viewModel().state.value.notes).isNull()
    }

    // --- Navigation (§4.3.1, §4.3.4, §4.3.8, §4.3.9) ------------------------

    @Test
    fun `every way out of the screen sends its own event`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(CompoundDetailAction.OnProtocolClick(protocolId = 7))
            assertThat(awaitItem()).isEqualTo(CompoundDetailEvent.NavigateToProtocol(7))

            viewModel.onAction(CompoundDetailAction.OnHistoryEntryClick(eventId = 42))
            assertThat(awaitItem()).isEqualTo(CompoundDetailEvent.NavigateToAdministrationEvent(42))

            viewModel.onAction(CompoundDetailAction.OnLogDoseClick)
            assertThat(awaitItem()).isEqualTo(CompoundDetailEvent.NavigateToLogDose(COMPOUND_ID))

            viewModel.onAction(CompoundDetailAction.OnAdjustClick)
            assertThat(awaitItem()).isEqualTo(CompoundDetailEvent.NavigateToEditCompound(COMPOUND_ID))

            viewModel.onAction(CompoundDetailAction.OnBackClick)
            assertThat(awaitItem()).isEqualTo(CompoundDetailEvent.NavigateBack)
        }
    }

    @Test
    fun `a compound that disappears leaves the screen rather than showing its last state`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            compounds.stored.value = null
            assertThat(awaitItem()).isEqualTo(CompoundDetailEvent.NavigateBack)
        }
    }

    // --- §4.5 The opened-container sheet ------------------------------------

    @Test
    fun `Edit opens the sheet on the stored container`() = runTest {
        compounds.stored.value = compound(opened = openedContainer(remaining = "3.2"))
        val viewModel = viewModel()

        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        val sheet = viewModel.state.value.openedSheet
        assertThat(sheet).isNotNull()
        assertThat(sheet?.isEdit).isEqualTo(true)
        assertThat(sheet?.remaining).isEqualTo("3.2")
        assertThat(sheet?.compoundName).isEqualTo("Semaglutide")
    }

    @Test
    fun `with nothing open the sheet is the Create Already Opened variant, defaulted full`() = runTest {
        compounds.stored.value = compound(opened = null)
        val viewModel = viewModel()

        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        val sheet = viewModel.state.value.openedSheet
        assertThat(sheet?.isEdit).isEqualTo(false)
        assertThat(sheet?.remaining).isEqualTo("5")
    }

    @Test
    fun `saving an edited remaining writes it and closes the sheet`() = runTest {
        compounds.stored.value = compound(opened = openedContainer(remaining = "3.2"))
        val viewModel = viewModel()
        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnRemainingChange("2.0")))
        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnSaveClick))

        assertThat(compounds.edited.single().value.toPlainString()).isEqualTo("2")
        assertThat(viewModel.state.value.openedSheet).isNull()
    }

    @Test
    fun `a remaining that is not a number at or above zero is refused in the sheet`() = runTest {
        compounds.stored.value = compound(opened = openedContainer(remaining = "3.2"))
        val viewModel = viewModel()
        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnRemainingChange("-1")))
        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnSaveClick))

        assertThat(viewModel.state.value.openedSheet?.hasRemainingError).isEqualTo(true)
        assertThat(compounds.edited).isEqualTo(emptyList<Quantity>())
    }

    @Test
    fun `saving an empty container closes it and offers the next one`() = runTest {
        compounds.stored.value = compound(opened = openedContainer(remaining = "3.2"), numberOfContainers = 2)
        val viewModel = viewModel()
        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnRemainingChange("0")))
        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnSaveClick))

        assertThat(compounds.closed).containsExactly(COMPOUND_ID)
        assertThat(viewModel.state.value.isDepletionPromptOpen).isTrue()

        viewModel.onAction(CompoundDetailAction.OnNaturalDepletionDecision(openNew = true))

        assertThat(compounds.openedNext).containsExactly(COMPOUND_ID)
        assertThat(viewModel.state.value.isDepletionPromptOpen).isFalse()
    }

    @Test
    fun `an emptied container with no stock left asks nothing`() = runTest {
        compounds.stored.value = compound(opened = openedContainer(remaining = "3.2"), numberOfContainers = 0)
        val viewModel = viewModel()
        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnRemainingChange("0")))
        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnSaveClick))

        assertThat(viewModel.state.value.isDepletionPromptOpen).isFalse()
    }

    @Test
    fun `a refused write is reported in the sheet, which stays open`() = runTest {
        compounds.stored.value = compound(opened = null)
        compounds.addError = DataError.Local.CONSTRAINT_VIOLATION
        val viewModel = viewModel()
        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnSaveClick))

        val sheet = viewModel.state.value.openedSheet
        assertThat(sheet).isNotNull()
        assertThat(sheet?.saveError).isEqualTo(OpenedContainerSaveError.NO_UNOPENED_STOCK)
        assertThat(sheet?.isSaving).isEqualTo(false)
    }

    @Test
    fun `Delete discards the container and says so`() = runTest {
        compounds.stored.value = compound(opened = openedContainer(remaining = "3.2"))
        val viewModel = viewModel()
        viewModel.onAction(CompoundDetailAction.OnOpenedContainerClick)

        viewModel.events.test {
            viewModel.onAction(sheetAction(OpenedContainerSheetAction.OnDeleteClick))
            assertThat(awaitItem()).isInstanceOf(CompoundDetailEvent.ShowMessage::class)
        }
        assertThat(compounds.closed).containsExactly(COMPOUND_ID)
        assertThat(viewModel.state.value.openedSheet).isNull()
    }

    // -----------------------------------------------------------------------

    private fun viewModel() = CompoundDetailViewModel(
        compoundRepository = compounds,
        inventoryRepository = inventory,
        protocolRepository = protocols,
        scheduledDoseRepository = scheduledDoses,
        administrationEventRepository = events,
        args = CompoundDetailArgs(COMPOUND_ID),
        now = { NOW },
        timeZone = TimeZone.UTC,
    )

    private fun sheetAction(action: OpenedContainerSheetAction) = CompoundDetailAction.OpenedContainerSheet(action)

    private class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow<CompoundSupply?>(null)
        val edited = mutableListOf<Quantity>()
        val added = mutableListOf<Quantity>()
        val closed = mutableListOf<Long>()
        val openedNext = mutableListOf<Long>()

        /** When set, `addOpenedContainer` fails with this error instead of writing. */
        var addError: DataError.Local? = null

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

        override suspend fun openContainer(id: Long): EmptyResult<DataError.Local> {
            openedNext += id
            return Result.Success(Unit)
        }

        override suspend fun closeContainer(id: Long, reason: String?): EmptyResult<DataError.Local> {
            closed += id
            return Result.Success(Unit)
        }

        override suspend fun addOpenedContainer(
            compoundSupplyId: Long,
            openedAt: Instant,
            remainingAmount: Quantity,
            expiryAfterOpeningDays: Int?,
            userDefinedExpiryDate: LocalDate?,
        ): EmptyResult<DataError.Local> {
            addError?.let { return Result.Error(it) }
            added += remainingAmount
            return Result.Success(Unit)
        }

        override suspend fun editOpenedContainer(
            compoundSupplyId: Long,
            openedAt: Instant?,
            remainingAmount: Quantity?,
            expiryAfterOpeningDays: Int?,
            userDefinedExpiryDate: LocalDate?,
        ): EmptyResult<DataError.Local> {
            remainingAmount?.let { edited += it }
            return Result.Success(Unit)
        }
    }

    private class FakeInventoryRepository : InventoryRepository {
        val dosesLeft = MutableStateFlow<List<CompoundDosesLeft>>(emptyList())

        override fun observeWarnings(): Flow<List<InventoryWarning>> = throw NotImplementedError()

        override fun observeDosesLeftPerCompound(): Flow<List<CompoundDosesLeft>> = dosesLeft

        override fun observeRunOutDate(protocolId: Long): Flow<LocalDate?> = throw NotImplementedError()
    }

    private class FakeProtocolRepository : ProtocolRepository {
        val byCompound = MutableStateFlow<List<Protocol>>(emptyList())

        override fun observeAll(): Flow<List<Protocol>> = throw NotImplementedError()

        override fun observeArchived(): Flow<List<Protocol>> = throw NotImplementedError()

        override fun observeById(id: Long): Flow<Protocol?> = throw NotImplementedError()

        override fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<List<Protocol>> = byCompound

        override suspend fun create(protocol: Protocol): Result<Long, DataError.Local> = throw NotImplementedError()

        override suspend fun update(protocol: Protocol): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun archive(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun duplicate(id: Long) = throw NotImplementedError()

        override suspend fun pause(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun resume(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun complete(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()
    }

    private class FakeScheduledDoseRepository : ScheduledDoseRepository {
        val byProtocol = mutableMapOf<Long, MutableStateFlow<List<ScheduledDose>>>()

        override fun observePending(date: LocalDate, zone: TimeZone): Flow<List<ScheduledDose>> =
            throw NotImplementedError()

        override fun observeNextPendingPerProtocol(): Flow<List<ScheduledDose>> = throw NotImplementedError()

        override fun observeForProtocol(protocolId: Long): Flow<List<ScheduledDose>> =
            byProtocol.getOrPut(protocolId) { MutableStateFlow(emptyList()) }

        override suspend fun snooze(id: Long, delta: Duration): EmptyResult<DataError.Local> =
            throw NotImplementedError()

        override suspend fun skip(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun markMissed(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()

        override suspend fun markTaken(id: Long, eventId: Long): EmptyResult<DataError.Local> =
            throw NotImplementedError()
    }

    private class FakeAdministrationEventRepository : AdministrationEventRepository {
        val history = MutableStateFlow<List<CompoundHistoryEntry>>(emptyList())

        /**
         * Stands in for the DAO's `WHERE status = :status`, so the chip is exercised the same way.
         *
         * The load states are spelled out because a bare `PagingData.from` leaves them alone: the
         * differ would stay on its initial `Loading` and `asSnapshot` would wait for it forever.
         */
        override fun pagedHistoryForCompound(
            compoundSupplyId: Long,
            status: AdministrationEventStatus?,
        ): Flow<PagingData<CompoundHistoryEntry>> = history.map { entries ->
            val loaded = LoadState.NotLoading(endOfPaginationReached = true)
            PagingData.from(
                data = entries.filter { status == null || it.status == status },
                sourceLoadStates = LoadStates(refresh = loaded, prepend = loaded, append = loaded),
            )
        }

        override fun observeLoggedDoseCount(compoundSupplyId: Long): Flow<Int> =
            history.map { entries -> entries.count { it.status != AdministrationEventStatus.SKIPPED } }

        override suspend fun log(
            event: AdministrationEvent,
            components: List<DoseComponent>,
        ): Result<Long, DataError.Local> = throw NotImplementedError()

        override suspend fun edit(id: Long, edits: AdministrationEventEdit): EmptyResult<DataError.Local> =
            throw NotImplementedError()

        override suspend fun delete(id: Long): EmptyResult<DataError.Local> = throw NotImplementedError()
    }

    private companion object {
        const val COMPOUND_ID = 1L
        val NOW: Instant = Instant.parse("2026-08-21T09:00:00Z")

        fun mg(value: String) = Quantity(Decimal.parse(value), UnitCode.MG)

        fun dosesLeft(id: Long, doses: Int?, days: Int?) = CompoundDosesLeft(
            compoundSupplyId = id,
            compoundName = "Semaglutide",
            dosesLeft = doses,
            dosesPerActualInjection = null,
            daysLeft = days,
        )

        fun openedContainer(
            remaining: String = "5",
            openedAgo: Duration = 0.days,
            predictedExpiry: LocalDate? = null,
        ) = OpenedContainer(
            openedAt = NOW - openedAgo,
            remainingAmount = mg(remaining),
            expiryAfterOpeningDays = 28,
            userDefinedExpiryDate = null,
            predictedExpiryDate = predictedExpiry,
        )

        fun compound(
            opened: OpenedContainer? = openedContainer(),
            batchExpiryDate: LocalDate? = null,
            numberOfContainers: Int = 1,
            notes: String? = "Pre-mixed with 2 mL BAC water.",
        ) = CompoundSupply(
            id = COMPOUND_ID,
            name = "Semaglutide",
            category = CompoundCategory.PEPTIDE,
            form = CompoundForm.INJECTABLE,
            containerType = ContainerType.VIAL,
            primaryUnit = UnitCode.MG,
            amountPerContainer = mg("5"),
            concentration = null,
            numberOfContainers = numberOfContainers,
            currentOpened = opened,
            batchExpiryDate = batchExpiryDate,
            expiryAfterOpeningDays = 28,
            storageLocation = StorageLocation.FRIDGE,
            batchNumber = null,
            supplier = null,
            notes = notes,
            deletedAt = null,
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun protocol(id: Long, name: String, status: ProtocolStatus = ProtocolStatus.ACTIVE) = Protocol(
            id = id,
            name = name,
            compoundSupplyId = COMPOUND_ID,
            plannedDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            route = Route.SUBCUTANEOUS,
            schedule = Schedule(
                type = ScheduleType.SPECIFIC_WEEKDAYS,
                interval = null,
                timesPerDay = null,
                selectedWeekdays = setOf(kotlinx.datetime.DayOfWeek.MONDAY),
                timesPerWeek = null,
                timesPerMonth = null,
            ),
            dosageTimes = emptyList(),
            escalation = null,
            protocolBreak = null,
            startDate = LocalDate.parse("2026-08-01"),
            endDate = null,
            reminderEnabled = false,
            reminderOffsetMinutes = 0,
            reminderBucket = null,
            injectionSiteRestriction = null,
            siteCooldownDays = null,
            notes = null,
            status = status,
            deletedAt = null,
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun scheduledDose(
            id: Long,
            protocolId: Long,
            at: Instant,
            status: ScheduledDoseStatus = ScheduledDoseStatus.PENDING,
        ) = ScheduledDose(
            id = id,
            protocolId = protocolId,
            compoundSupplyId = COMPOUND_ID,
            scheduledAt = at,
            hasTimeOfDay = true,
            plannedDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            route = Route.SUBCUTANEOUS,
            status = status,
            administrationEventId = null,
            createdAt = NOW,
        )

        fun historyEntry(id: Long, status: AdministrationEventStatus) = CompoundHistoryEntry(
            eventId = id,
            loggedAt = NOW - id.days,
            status = status,
            dose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            volume = Quantity(Decimal.parse("0.10"), UnitCode.ML),
            injectionSiteName = "Abdomen R",
        )
    }
}
