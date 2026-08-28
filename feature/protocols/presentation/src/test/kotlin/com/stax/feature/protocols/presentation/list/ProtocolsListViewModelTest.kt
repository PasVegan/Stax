package com.stax.feature.protocols.presentation.list

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.ContainerType
import com.stax.core.domain.Decimal
import com.stax.core.domain.Escalation
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolBreak
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.ScheduledDose
import com.stax.core.domain.ScheduledDoseStatus
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.ScheduledDoseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolsListViewModelTest {

    private lateinit var protocols: FakeProtocolRepository
    private lateinit var compounds: FakeCompoundRepository
    private lateinit var doses: FakeScheduledDoseRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        protocols = FakeProtocolRepository()
        compounds = FakeCompoundRepository()
        doses = FakeScheduledDoseRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // §4.7.2 tab definitions
    // -----------------------------------------------------------------------

    @Test
    fun `Active is selected by default and keeps only active protocols`() = runTest {
        protocols.live.value = listOf(
            protocol(id = 1, name = "Sema"),
            protocol(id = 2, name = "Test Cyp", status = ProtocolStatus.PAUSED),
            protocol(id = 3, name = "Vit D", status = ProtocolStatus.COMPLETED),
        )

        val viewModel = viewModel()

        assertThat(viewModel.state.value.filter).isEqualTo(ProtocolFilter.ACTIVE)
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Sema")
    }

    @Test
    fun `Paused and Completed each keep their own status`() = runTest {
        protocols.live.value = listOf(
            protocol(id = 1, name = "Sema"),
            protocol(id = 2, name = "Test Cyp", status = ProtocolStatus.PAUSED),
            protocol(id = 3, name = "Vit D", status = ProtocolStatus.COMPLETED),
        )
        val viewModel = viewModel()

        viewModel.onAction(ProtocolsListAction.OnFilterClick(ProtocolFilter.PAUSED))
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Test Cyp")

        viewModel.onAction(ProtocolsListAction.OnFilterClick(ProtocolFilter.COMPLETED))
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Vit D")
    }

    @Test
    fun `Active keeps a protocol that is inside its break`() = runTest {
        // Day 60 of a 56-on / 28-off cycle: in break, and still Active (§3.2).
        protocols.live.value = listOf(protocol(id = 1, name = "BPC-157", protocolBreak = ProtocolBreak(56, 28)))

        val viewModel = viewModel(today = START_DATE.plusDays(60))

        val item = viewModel.state.value.items.single()
        assertThat(item.isInBreak).isTrue()
        assertThat(item.pill).isEqualTo(ProtocolPill.IN_BREAK)
    }

    @Test
    fun `Archived reads deletedAt and never leaks into the other three tabs`() = runTest {
        // Soft-deleted but still Active — §4.7.2 archives it on `deletedAt` alone.
        protocols.archived.value = listOf(
            protocol(id = 9, name = "Old ramp", deletedAt = NOW, status = ProtocolStatus.ACTIVE),
        )
        val viewModel = viewModel()

        ProtocolFilter.entries.filter { it != ProtocolFilter.ARCHIVED }.forEach { filter ->
            viewModel.onAction(ProtocolsListAction.OnFilterClick(filter))
            assertThat(viewModel.state.value.items.map { it.name }).containsExactly()
        }

        viewModel.onAction(ProtocolsListAction.OnFilterClick(ProtocolFilter.ARCHIVED))
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Old ramp")
    }

    // -----------------------------------------------------------------------
    // §4.7.3 card contents
    // -----------------------------------------------------------------------

    @Test
    fun `the card carries the compound name, the current dose and the next pending dose`() = runTest {
        protocols.live.value = listOf(protocol(id = 1, name = "Sema"))
        compounds.stored.value = listOf(compound(id = COMPOUND_ID, name = "Semaglutide"))
        doses.next.value = listOf(scheduledDose(protocolId = 1, at = NOW))

        val item = viewModel().state.value.items.single()

        assertThat(item.compoundName).isEqualTo("Semaglutide")
        assertThat(item.dose).isEqualTo("0.5 mg")
        assertThat(item.nextDoseAt).isEqualTo(NOW)
        assertThat(item.nextDoseHasTime).isTrue()
    }

    @Test
    fun `a protocol with no generated dose left reports none`() = runTest {
        protocols.live.value = listOf(protocol(id = 1, name = "Sema", status = ProtocolStatus.PAUSED))
        val viewModel = viewModel()

        viewModel.onAction(ProtocolsListAction.OnFilterClick(ProtocolFilter.PAUSED))

        assertThat(viewModel.state.value.items.single().nextDoseAt).isNull()
    }

    @Test
    fun `a compound archived out from under a protocol drops the name rather than faking one`() = runTest {
        protocols.archived.value = listOf(protocol(id = 1, name = "Old ramp", deletedAt = NOW))
        val viewModel = viewModel()

        viewModel.onAction(ProtocolsListAction.OnFilterClick(ProtocolFilter.ARCHIVED))

        assertThat(viewModel.state.value.items.single().compoundName).isNull()
    }

    @Test
    fun `a protocol without escalation has no titration bar`() = runTest {
        protocols.live.value = listOf(protocol(id = 1, name = "Sema"))

        assertThat(viewModel().state.value.items.single().titration).isNull()
    }

    @Test
    fun `the titration bar reads the dose the protocol is on today`() = runTest {
        protocols.live.value = listOf(
            protocol(
                id = 1,
                name = "Sema",
                escalation = escalation(increaseEvery = EscalationIncreaseEvery.EVERY_X_WEEKS, every = 2),
            ),
        )

        // Two weeks in: one step of +0.25 off the 0.25 start, a quarter of the way to 1 mg.
        val titration = viewModel(today = START_DATE.plusDays(14)).state.value.items.single().titration

        assertThat(titration?.current).isEqualTo("0.5")
        assertThat(titration?.target).isEqualTo("1 mg")
        assertThat(titration?.progress).isEqualTo(0.5f)
    }

    @Test
    fun `the titration bar never fills past its target`() = runTest {
        protocols.live.value = listOf(
            protocol(
                id = 1,
                name = "Sema",
                escalation = escalation(increaseEvery = EscalationIncreaseEvery.EVERY_X_WEEKS, every = 2),
            ),
        )

        val titration = viewModel(today = START_DATE.plusDays(365)).state.value.items.single().titration

        assertThat(titration?.progress).isEqualTo(1f)
    }

    // -----------------------------------------------------------------------
    // §4.7.3 / §4.7.5 navigation
    // -----------------------------------------------------------------------

    @Test
    fun `tapping a card opens its detail`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ProtocolsListAction.OnProtocolClick(7))
            assertThat(awaitItem()).isEqualTo(ProtocolsListEvent.NavigateToProtocolDetail(7))
        }
    }

    @Test
    fun `tapping the FAB opens Create Protocol`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ProtocolsListAction.OnCreateProtocolClick)
            assertThat(awaitItem()).isEqualTo(ProtocolsListEvent.NavigateToCreateProtocol)
        }
    }

    // -----------------------------------------------------------------------
    // §4.0.1 search overlay
    // -----------------------------------------------------------------------

    @Test
    fun `the query narrows the active tab rather than searching around it`() = runTest {
        protocols.live.value = listOf(
            protocol(id = 1, name = "Sema weekly"),
            protocol(id = 2, name = "Sema starter", status = ProtocolStatus.PAUSED),
            protocol(id = 3, name = "Tirzepatide ramp"),
        )
        val viewModel = viewModel()

        viewModel.onAction(ProtocolsListAction.OnSearchClick)
        viewModel.onAction(ProtocolsListAction.OnSearchQueryChange("sema"))

        assertThat(viewModel.state.value.isSearchOpen).isTrue()
        // "Sema starter" is Paused, so the Active tab keeps it out even though the name matches.
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Sema weekly")
    }

    @Test
    fun `leaving the overlay drops the query with it`() = runTest {
        protocols.live.value = listOf(protocol(id = 1, name = "Sema"), protocol(id = 2, name = "Tirz"))
        val viewModel = viewModel()
        viewModel.onAction(ProtocolsListAction.OnSearchClick)
        viewModel.onAction(ProtocolsListAction.OnSearchQueryChange("sema"))

        viewModel.onAction(ProtocolsListAction.OnSearchDismiss)

        assertThat(viewModel.state.value.isSearchOpen).isFalse()
        assertThat(viewModel.state.value.searchQuery).isEqualTo("")
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Sema", "Tirz")
    }

    // -----------------------------------------------------------------------
    // §7 empty states
    // -----------------------------------------------------------------------

    @Test
    fun `hasAnyProtocol separates an empty tab from an empty app`() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.state.value.hasAnyProtocol).isFalse()
        assertThat(viewModel.state.value.isLoading).isFalse()

        // Nothing Active, but the app is not empty — an archived protocol counts.
        protocols.archived.value = listOf(protocol(id = 1, name = "Old ramp", deletedAt = NOW))

        assertThat(viewModel.state.value.items).containsExactly()
        assertThat(viewModel.state.value.hasAnyProtocol).isTrue()
    }

    @Test
    fun `a filter survives a data emission`() = runTest {
        protocols.live.value = listOf(protocol(id = 1, name = "Sema", status = ProtocolStatus.PAUSED))
        val viewModel = viewModel()
        viewModel.onAction(ProtocolsListAction.OnFilterClick(ProtocolFilter.PAUSED))

        protocols.live.value += protocol(id = 2, name = "Test Cyp", status = ProtocolStatus.PAUSED)

        assertThat(viewModel.state.value.filter).isEqualTo(ProtocolFilter.PAUSED)
        assertThat(viewModel.state.value.items.map { it.name }).containsExactly("Sema", "Test Cyp")
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private fun viewModel(today: LocalDate = START_DATE) =
        ProtocolsListViewModel(protocols, compounds, doses, today = { today })

    private fun LocalDate.plusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)

    private fun protocol(
        id: Long,
        name: String,
        status: ProtocolStatus = ProtocolStatus.ACTIVE,
        deletedAt: Instant? = null,
        escalation: Escalation? = null,
        protocolBreak: ProtocolBreak? = null,
    ) = Protocol(
        id = id,
        name = name,
        compoundSupplyId = COMPOUND_ID,
        plannedDose = Quantity(Decimal.parse("0.5"), UnitCode.MG),
        route = Route.SUBCUTANEOUS,
        schedule = Schedule(ScheduleType.DAILY, null, null, null, null, null),
        dosageTimes = listOf(LocalTime(20, 0)),
        escalation = escalation,
        protocolBreak = protocolBreak,
        startDate = START_DATE,
        endDate = null,
        reminderEnabled = false,
        reminderOffsetMinutes = 0,
        reminderBucket = null,
        injectionSiteRestriction = null,
        siteCooldownDays = null,
        notes = null,
        status = status,
        deletedAt = deletedAt,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun escalation(increaseEvery: EscalationIncreaseEvery, every: Int) = Escalation(
        startDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
        targetDose = Quantity(Decimal.parse("1"), UnitCode.MG),
        increaseAmount = Quantity(Decimal.parse("0.25"), UnitCode.MG),
        increaseEvery = increaseEvery,
        increaseEveryValue = every,
        maxDose = null,
        stopAtTarget = true,
    )

    private fun compound(id: Long, name: String) = CompoundSupply(
        id = id,
        name = name,
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainer = Quantity(Decimal.parse("5"), UnitCode.MG),
        numberOfContainers = 1,
        concentration = null,
        batchExpiryDate = null,
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

    private fun scheduledDose(protocolId: Long, at: Instant) = ScheduledDose(
        id = protocolId,
        protocolId = protocolId,
        compoundSupplyId = COMPOUND_ID,
        scheduledAt = at,
        hasTimeOfDay = true,
        plannedDose = Quantity(Decimal.parse("0.5"), UnitCode.MG),
        route = Route.SUBCUTANEOUS,
        status = ScheduledDoseStatus.PENDING,
        administrationEventId = null,
        createdAt = NOW,
    )

    private class FakeProtocolRepository : ProtocolRepository {
        val live = MutableStateFlow<List<Protocol>>(emptyList())
        val archived = MutableStateFlow<List<Protocol>>(emptyList())

        override fun observeAll(): Flow<List<Protocol>> = live

        override fun observeArchived(): Flow<List<Protocol>> = archived

        override fun observeById(id: Long): Flow<Protocol?> = throw NotImplementedError()

        override fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<List<Protocol>> =
            throw NotImplementedError()

        override suspend fun create(protocol: Protocol) = throw NotImplementedError()

        override suspend fun update(protocol: Protocol) = throw NotImplementedError()

        override suspend fun archive(id: Long) = throw NotImplementedError()

        override suspend fun duplicate(id: Long) = throw NotImplementedError()

        override suspend fun pause(id: Long) = throw NotImplementedError()

        override suspend fun resume(id: Long) = throw NotImplementedError()

        override suspend fun complete(id: Long) = throw NotImplementedError()
    }

    private class FakeCompoundRepository : CompoundRepository {
        val stored = MutableStateFlow<List<CompoundSupply>>(emptyList())

        override fun observeAll(): Flow<List<CompoundSupply>> = stored

        override fun observeById(id: Long): Flow<CompoundSupply?> = throw NotImplementedError()

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

    private class FakeScheduledDoseRepository : ScheduledDoseRepository {
        val next = MutableStateFlow<List<ScheduledDose>>(emptyList())

        override fun observeNextPendingPerProtocol(): Flow<List<ScheduledDose>> = next

        override fun observePending(date: LocalDate, zone: TimeZone): Flow<List<ScheduledDose>> =
            throw NotImplementedError()

        override fun observeForProtocol(protocolId: Long): Flow<List<ScheduledDose>> = throw NotImplementedError()

        override suspend fun snooze(id: Long, delta: Duration) = throw NotImplementedError()

        override suspend fun skip(id: Long) = throw NotImplementedError()

        override suspend fun markMissed(id: Long) = throw NotImplementedError()

        override suspend fun markTaken(id: Long, eventId: Long) = throw NotImplementedError()
    }

    private companion object {
        const val COMPOUND_ID = 42L
        val START_DATE: LocalDate = LocalDate.parse("2026-06-01")
        val NOW: Instant = Instant.parse("2026-06-01T20:00:00Z")
    }
}
