package com.stax.feature.protocols.presentation.detail

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.stax.core.domain.AppTheme
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Escalation
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolBreak
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.Settings
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.AdministrationEventRepository
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InventoryRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.SettingsRepository
import com.stax.feature.protocols.presentation.list.ProtocolPill
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
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolDetailViewModelTest {

    private lateinit var protocols: FakeProtocolRepository
    private lateinit var compounds: FakeCompoundRepository
    private lateinit var inventory: FakeInventoryRepository
    private lateinit var events: FakeAdministrationEventRepository
    private lateinit var settings: FakeSettingsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        protocols = FakeProtocolRepository()
        compounds = FakeCompoundRepository()
        inventory = FakeInventoryRepository()
        events = FakeAdministrationEventRepository()
        settings = FakeSettingsRepository()
        protocols.stored.value = protocol()
        compounds.stored.value = compound()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // §4.8.1 – §4.8.8 reading
    // -----------------------------------------------------------------------

    @Test
    fun `the app bar names the protocol, its status and its compound`() = runTest {
        val state = viewModel().state.value

        assertThat(state.name).isEqualTo("Sema weekly titration")
        assertThat(state.pill).isEqualTo(ProtocolPill.ACTIVE)
        assertThat(state.compoundName).isEqualTo("Semaglutide")
    }

    @Test
    fun `an in-break protocol stays Active but reports the break`() = runTest {
        protocols.stored.value = protocol(protocolBreak = ProtocolBreak(daysOn = 1, daysOff = 6))

        // Day 3 of a 1-on / 6-off cycle that started on TODAY - 2 days: inside the off window.
        val state = viewModel(today = TODAY).state.value

        assertThat(state.pill).isEqualTo(ProtocolPill.IN_BREAK)
    }

    @Test
    fun `the schedule card carries the parts, not a sentence`() = runTest {
        val schedule = viewModel().state.value.schedule

        assertThat(schedule).isNotNull()
        assertThat(schedule?.scheduleType).isEqualTo(ScheduleType.SPECIFIC_WEEKDAYS)
        assertThat(schedule?.weekdays?.map { it.name }).isEqualTo(listOf("MONDAY", "THURSDAY"))
        assertThat(schedule?.dosageTimes?.toList()).isEqualTo(listOf(LocalTime(20, 0)))
        assertThat(schedule?.startDate).isEqualTo(START_DATE)
        assertThat(schedule?.endDate).isNull()
    }

    @Test
    fun `reminders off leave no offset to render`() = runTest {
        protocols.stored.value = protocol(reminderEnabled = false)

        assertThat(viewModel().state.value.schedule?.reminderOffsetMinutes).isNull()
    }

    @Test
    fun `the titration row reads the target in the start dose's unit`() = runTest {
        protocols.stored.value = protocol(
            escalation = Escalation(
                startDose = Quantity(Decimal.parse("250"), UnitCode.MCG),
                targetDose = Quantity(Decimal.parse("1"), UnitCode.MG),
                increaseAmount = Quantity(Decimal.parse("250"), UnitCode.MCG),
                increaseEvery = EscalationIncreaseEvery.EVERY_X_WEEKS,
                increaseEveryValue = 4,
                maxDose = null,
                stopAtTarget = true,
            ),
        )

        val titration = viewModel().state.value.schedule?.titration

        assertThat(titration?.startDose).isEqualTo("250")
        assertThat(titration?.targetDose).isEqualTo("1000 mcg")
        assertThat(titration?.increaseEveryValue).isEqualTo(4)
    }

    @Test
    fun `the linked compound row states the dose, its volume and the concentration`() = runTest {
        val compound = viewModel().state.value.compound

        assertThat(compound?.name).isEqualTo("Semaglutide")
        assertThat(compound?.dose).isEqualTo("0.25 mg")
        assertThat(compound?.volume).isEqualTo("0.1 ml")
        assertThat(compound?.concentration).isEqualTo("2.5")
    }

    @Test
    fun `an archived compound leaves the card empty rather than a placeholder row`() = runTest {
        compounds.stored.value = null

        val state = viewModel().state.value

        assertThat(state.compound).isNull()
        assertThat(state.compoundName).isNull()
    }

    @Test
    fun `doses remaining divides the whole stock by one dose`() = runTest {
        // 2 vials × 5 mg = 10 mg of stock, drawn 0.25 mg at a time.
        assertThat(viewModel().state.value.forecast.dosesRemaining).isEqualTo(40)
    }

    @Test
    fun `the run-out date is the inventory aggregation's, not a second walk of the schedule`() = runTest {
        inventory.runOut.value = LocalDate.parse("2026-07-28")

        assertThat(viewModel().state.value.forecast.runOutDate).isEqualTo(LocalDate.parse("2026-07-28"))
    }

    @Test
    fun `an open-ended protocol requires nothing by its end`() = runTest {
        assertThat(viewModel().state.value.forecast.requiredUntilEnd).isNull()
    }

    @Test
    fun `an ended protocol states what the remaining doses come to`() = runTest {
        // Mondays and Thursdays from TODAY (a Monday) through the Thursday of the week after:
        // 4 doses of 0.25 mg.
        protocols.stored.value = protocol(endDate = TODAY.plusDays(10))

        assertThat(viewModel().state.value.forecast.requiredUntilEnd).isEqualTo("1 mg")
    }

    // -----------------------------------------------------------------------
    // §4.8.5's warning row — the acceptance condition
    // -----------------------------------------------------------------------

    @Test
    fun `a batch expiring before the run-out raises the warning row`() = runTest {
        inventory.runOut.value = LocalDate.parse("2026-07-28")
        compounds.stored.value = compound(batchExpiryDate = LocalDate.parse("2026-07-14"))

        assertThat(viewModel().state.value.forecast.batchExpiry).isEqualTo(LocalDate.parse("2026-07-14"))
    }

    @Test
    fun `a batch outliving the run-out raises nothing`() = runTest {
        inventory.runOut.value = LocalDate.parse("2026-07-28")
        compounds.stored.value = compound(batchExpiryDate = LocalDate.parse("2026-09-01"))

        assertThat(viewModel().state.value.forecast.batchExpiry).isNull()
    }

    @Test
    fun `without a run-out date there is nothing for a batch expiry to be before`() = runTest {
        inventory.runOut.value = null
        compounds.stored.value = compound(batchExpiryDate = LocalDate.parse("2026-07-14"))

        assertThat(viewModel().state.value.forecast.batchExpiry).isNull()
    }

    // -----------------------------------------------------------------------
    // §4.8.6 site restrictions
    // -----------------------------------------------------------------------

    @Test
    fun `the rotation rule falls back to the Settings default for the route`() = runTest {
        protocols.stored.value = protocol(siteCooldownDays = null, route = Route.INTRAMUSCULAR)

        assertThat(viewModel().state.value.sites?.cooldownDays).isEqualTo(7)
    }

    @Test
    fun `a per-protocol cooldown wins over the default`() = runTest {
        protocols.stored.value = protocol(siteCooldownDays = 3)

        assertThat(viewModel().state.value.sites?.cooldownDays).isEqualTo(3)
    }

    // -----------------------------------------------------------------------
    // §4.8.2 quick actions + §4.8.9 dock
    // -----------------------------------------------------------------------

    @Test
    fun `the one chip pauses a running protocol and resumes a paused one`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolDetailAction.OnPauseClick)
        assertThat(protocols.paused).containsExactly(PROTOCOL_ID)

        protocols.stored.value = protocol(status = ProtocolStatus.PAUSED)
        assertThat(viewModel.state.value.isPaused).isTrue()

        viewModel.onAction(ProtocolDetailAction.OnPauseClick)
        assertThat(protocols.resumed).containsExactly(PROTOCOL_ID)
    }

    @Test
    fun `Duplicate writes a copy and says so`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ProtocolDetailAction.OnDuplicateClick)

            assertThat(awaitItem()).isInstanceOf(ProtocolDetailEvent.ShowMessage::class)
            assertThat(protocols.duplicated).containsExactly(PROTOCOL_ID)
        }
    }

    @Test
    fun `a failed write is reported rather than silently dropped`() = runTest {
        protocols.fails = true
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ProtocolDetailAction.OnPauseClick)

            assertThat(awaitItem()).isInstanceOf(ProtocolDetailEvent.ShowError::class)
        }
    }

    @Test
    fun `Archive confirms first, then leaves — the row it showed is soft-deleted`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolDetailAction.OnArchiveClick)
        assertThat(viewModel.state.value.isArchiveDialogOpen).isTrue()

        viewModel.events.test {
            viewModel.onAction(ProtocolDetailAction.OnArchiveConfirm)

            assertThat(viewModel.state.value.isArchiveDialogOpen).isFalse()
            assertThat(protocols.archivedIds).containsExactly(PROTOCOL_ID)
            assertThat(awaitItem()).isEqualTo(ProtocolDetailEvent.NavigateBack)
        }
    }

    @Test
    fun `dismissing the confirmation writes nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ProtocolDetailAction.OnArchiveClick)
        viewModel.onAction(ProtocolDetailAction.OnArchiveDismiss)

        assertThat(viewModel.state.value.isArchiveDialogOpen).isFalse()
        assertThat(protocols.archivedIds).isEqualTo(emptyList<Long>())
    }

    @Test
    fun `the dock's Log dose carries this protocol, and the compound row its compound`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ProtocolDetailAction.OnLogDoseClick)
            assertThat(awaitItem()).isEqualTo(ProtocolDetailEvent.NavigateToLogDose(PROTOCOL_ID))

            viewModel.onAction(ProtocolDetailAction.OnCompoundClick)
            assertThat(awaitItem()).isEqualTo(ProtocolDetailEvent.NavigateToCompound(COMPOUND_ID))
        }
    }

    @Test
    fun `a protocol that is gone takes the screen with it`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            protocols.stored.value = null

            assertThat(awaitItem()).isEqualTo(ProtocolDetailEvent.NavigateBack)
        }
    }

    @Test
    fun `the notes unfold in place`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.isNotesExpanded).isFalse()
        viewModel.onAction(ProtocolDetailAction.OnToggleNotes)
        assertThat(viewModel.state.value.isNotesExpanded).isTrue()
    }

    @Test
    fun `blank notes read as none at all`() = runTest {
        protocols.stored.value = protocol(notes = "   ")

        assertThat(viewModel().state.value.notes).isNull()
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun viewModel(today: LocalDate = TODAY) = ProtocolDetailViewModel(
        protocolRepository = protocols,
        compoundRepository = compounds,
        inventoryRepository = inventory,
        administrationEventRepository = events,
        settingsRepository = settings,
        args = ProtocolDetailArgs(PROTOCOL_ID),
        today = { today },
    )

    private fun LocalDate.plusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)

    private fun protocol(
        status: ProtocolStatus = ProtocolStatus.ACTIVE,
        escalation: Escalation? = null,
        protocolBreak: ProtocolBreak? = null,
        endDate: LocalDate? = null,
        reminderEnabled: Boolean = true,
        siteCooldownDays: Int? = null,
        route: Route = Route.SUBCUTANEOUS,
        notes: String? = "Titrating slowly.",
    ) = Protocol(
        id = PROTOCOL_ID,
        name = "Sema weekly titration",
        compoundSupplyId = COMPOUND_ID,
        plannedDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
        route = route,
        schedule = Schedule(
            type = ScheduleType.SPECIFIC_WEEKDAYS,
            interval = null,
            timesPerDay = null,
            selectedWeekdays = setOf(kotlinx.datetime.DayOfWeek.MONDAY, kotlinx.datetime.DayOfWeek.THURSDAY),
            timesPerWeek = null,
            timesPerMonth = null,
        ),
        dosageTimes = listOf(LocalTime(20, 0)),
        escalation = escalation,
        protocolBreak = protocolBreak,
        startDate = START_DATE,
        endDate = endDate,
        reminderEnabled = reminderEnabled,
        reminderOffsetMinutes = 10,
        reminderBucket = null,
        injectionSiteRestriction = null,
        siteCooldownDays = siteCooldownDays,
        notes = notes,
        status = status,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun compound(batchExpiryDate: LocalDate? = null) = CompoundSupply(
        id = COMPOUND_ID,
        name = "Semaglutide",
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainer = Quantity(Decimal.parse("5"), UnitCode.MG),
        numberOfContainers = 2,
        concentration = Concentration(
            amount = Quantity(Decimal.parse("2.5"), UnitCode.MG),
            per = Quantity(Decimal.parse("1"), UnitCode.ML),
        ),
        batchExpiryDate = batchExpiryDate,
        storageLocation = StorageLocation.FRIDGE,
        batchNumber = null,
        supplier = null,
        expiryAfterOpeningDays = null,
        notes = null,
        currentOpened = null,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private class FakeProtocolRepository : ProtocolRepository {
        val stored = MutableStateFlow<Protocol?>(null)

        val archivedIds = mutableListOf<Long>()
        val duplicated = mutableListOf<Long>()
        val paused = mutableListOf<Long>()
        val resumed = mutableListOf<Long>()

        var fails: Boolean = false

        override fun observeById(id: Long): Flow<Protocol?> = stored

        override fun observeAll(): Flow<List<Protocol>> = throw NotImplementedError()

        override fun observeArchived(): Flow<List<Protocol>> = throw NotImplementedError()

        override fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<List<Protocol>> =
            throw NotImplementedError()

        override suspend fun create(protocol: Protocol) = throw NotImplementedError()

        override suspend fun update(protocol: Protocol) = throw NotImplementedError()

        override suspend fun archive(id: Long) = record(archivedIds, id)

        override suspend fun duplicate(id: Long): Result<Long, DataError.Local> =
            when (val result = record(duplicated, id)) {
                is Result.Error -> Result.Error(result.error)
                is Result.Success -> Result.Success(id)
            }

        override suspend fun pause(id: Long) = record(paused, id)

        override suspend fun resume(id: Long) = record(resumed, id)

        override suspend fun complete(id: Long) = throw NotImplementedError()

        private fun record(into: MutableList<Long>, id: Long): EmptyResult<DataError.Local> {
            into += id
            return if (fails) Result.Error(DataError.Local.UNKNOWN) else Result.Success(Unit)
        }
    }

    private class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow<CompoundSupply?>(null)

        override fun observeById(id: Long): Flow<CompoundSupply?> = stored

        override fun observeAll(): Flow<List<CompoundSupply>> = throw NotImplementedError()

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

    private class FakeInventoryRepository : InventoryRepository {
        val runOut = MutableStateFlow<LocalDate?>(null)

        override fun observeRunOutDate(protocolId: Long): Flow<LocalDate?> = runOut

        override fun observeWarnings() = throw NotImplementedError()

        override fun observeDosesLeftPerCompound() = throw NotImplementedError()
    }

    private class FakeAdministrationEventRepository : AdministrationEventRepository {
        val loggedDoseCount = MutableStateFlow(0)

        override fun observeLoggedDoseCountForProtocol(protocolId: Long): Flow<Int> = loggedDoseCount

        val history = MutableStateFlow<List<CompoundHistoryEntry>>(emptyList())

        /**
         * The rows §4.8.7 renders. Built eagerly by the ViewModel, so it has to answer even in the
         * tests that never look at it.
         */
        override fun pagedHistoryForProtocol(protocolId: Long): Flow<PagingData<CompoundHistoryEntry>> =
            history.map { entries ->
                val loaded = LoadState.NotLoading(endOfPaginationReached = true)
                PagingData.from(
                    data = entries,
                    sourceLoadStates = LoadStates(refresh = loaded, prepend = loaded, append = loaded),
                )
            }

        override fun pagedHistoryForCompound(
            compoundSupplyId: Long,
            status: com.stax.core.domain.AdministrationEventStatus?,
        ) = throw NotImplementedError()

        override fun observeLoggedDoseCount(compoundSupplyId: Long) = throw NotImplementedError()

        override suspend fun log(
            event: com.stax.core.domain.AdministrationEvent,
            components: List<com.stax.core.domain.DoseComponent>,
        ) = throw NotImplementedError()

        override suspend fun edit(id: Long, edits: com.stax.core.domain.repository.AdministrationEventEdit) =
            throw NotImplementedError()

        override fun observeSiteUsesBetween(from: Instant, until: Instant) = throw NotImplementedError()

        override fun observeSiteDoses(injectionSiteId: Long) = throw NotImplementedError()

        override suspend fun delete(id: Long) = throw NotImplementedError()
    }

    private class FakeSettingsRepository : SettingsRepository {
        val stored = MutableStateFlow(
            Settings(
                theme = AppTheme.SYSTEM,
                dynamicColor = true,
                notificationStyle = NotificationStyle.NORMAL,
                timeZoneOverride = null,
                missedDoseWindowMinutes = 120,
                onboardingCompleted = true,
                exactAlarmDegraded = false,
                defaultSiteCooldownDaysSC = 5,
                defaultSiteCooldownDaysIM = 7,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )

        override fun observe(): Flow<Settings> = stored.map { it }

        override suspend fun update(settings: Settings) = throw NotImplementedError()
    }

    private companion object {
        const val PROTOCOL_ID = 7L
        const val COMPOUND_ID = 42L

        /** A Monday, so the Mon/Thu schedule doses on it. */
        val TODAY: LocalDate = LocalDate.parse("2026-06-01")
        val START_DATE: LocalDate = LocalDate.parse("2026-05-30")
        val NOW: Instant = Instant.parse("2026-06-01T20:00:00Z")
    }
}
