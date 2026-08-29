package com.stax.core.data.repository

import androidx.paging.testing.asSnapshot
import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.stax.core.database.AppTheme
import com.stax.core.database.BodyRegion
import com.stax.core.database.CompoundCategory
import com.stax.core.database.CompoundForm
import com.stax.core.database.CompoundSupplyEntity
import com.stax.core.database.ContainerType
import com.stax.core.database.InjectionSide
import com.stax.core.database.InjectionSiteEntity
import com.stax.core.database.InventoryTransactionEntity
import com.stax.core.database.InventoryTransactionType
import com.stax.core.database.NotificationStyle
import com.stax.core.database.OpenedContainerEntity
import com.stax.core.database.ProtocolEntity
import com.stax.core.database.ReminderBucket
import com.stax.core.database.Route
import com.stax.core.database.ScheduleEmbed
import com.stax.core.database.ScheduleType
import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.database.SettingsEntity
import com.stax.core.database.StaxDatabase
import com.stax.core.database.StorageLocation
import com.stax.core.domain.AdministrationEvent
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.DoseComponent
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.AdministrationEventEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import com.stax.core.domain.AdministrationEventStatus as DomainAdministrationEventStatus
import com.stax.core.domain.Route as DomainRoute

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdministrationEventRepositoryTest {

    private lateinit var database: StaxDatabase
    private lateinit var repository: RoomAdministrationEventRepository

    private var compoundId: Long = 0L
    private var protocolId: Long = 0L
    private var scheduledDoseId: Long = 0L
    private var injectionSiteId: Long = 0L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        repository = RoomAdministrationEventRepository(
            database = database,
            eventDao = database.administrationEventDao(),
            componentDao = database.doseComponentDao(),
            compoundDao = database.compoundSupplyDao(),
            openedContainerDao = database.openedContainerDao(),
            inventoryDao = database.inventoryTransactionDao(),
            scheduledDoseDao = database.scheduledDoseDao(),
            injectionSiteDao = database.injectionSiteDao(),
            protocolDao = database.protocolDao(),
            settingsDao = database.settingsDao(),
        )

        database.settingsDao().insert(settings())
        compoundId = database.compoundSupplyDao().insert(compound())
        database.openedContainerDao().insert(openedContainer(compoundId))
        database.inventoryTransactionDao().insert(initialStock(compoundId))
        protocolId = database.protocolDao().insert(protocol(compoundId))
        scheduledDoseId = database.scheduledDoseDao().insertOrIgnore(scheduledDose(protocolId, compoundId))
        injectionSiteId = database.injectionSiteDao().insert(injectionSite())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `log captures concentration deducts inventory links dose and cools site`() = runTest {
        val result = repository.log(event(), listOf(component(actualDoseValue = "0.5")))

        assertThat(result).isInstanceOf(Result.Success::class)
        val eventId = (result as Result.Success).data
        val storedComponent = database.doseComponentDao().getByAdministrationEventId(eventId).single()
        assertThat(storedComponent.concentrationAmountValue).isEqualTo(Decimal.parse("2.5"))
        assertThat(storedComponent.concentrationPerUnit).isEqualTo(UnitCode.ML)
        assertThat(storedComponent.inventoryDeductedValue).isEqualTo(Decimal.parse("0.2"))
        assertThat(storedComponent.inventoryDeductedUnit).isEqualTo(UnitCode.ML)

        val opened = database.openedContainerDao().getByCompoundSupplyId(compoundId)!!
        assertThat(opened.remainingAmountValue).isEqualTo(Decimal.parse("0.8"))

        val scheduled = database.scheduledDoseDao().getById(scheduledDoseId)!!
        assertThat(scheduled.status).isEqualTo(ScheduledDoseStatus.TAKEN)
        assertThat(scheduled.administrationEventId).isEqualTo(eventId)

        val site = database.injectionSiteDao().getById(injectionSiteId)!!
        assertThat(site.lastUsedAt).isEqualTo(LOGGED_AT)
        assertThat(site.avoidUntil).isEqualTo(LOGGED_AT + 3.days)

        assertLedgerBalanced()
    }

    @Test
    fun `edit appends reversal and new deduction keeping ledger balanced`() = runTest {
        val eventId = (repository.log(event(), listOf(component(actualDoseValue = "0.5"))) as Result.Success).data

        val result = repository.edit(
            eventId,
            AdministrationEventEdit(
                loggedAt = LOGGED_AT,
                route = DomainRoute.SUBCUTANEOUS,
                status = DomainAdministrationEventStatus.TAKEN,
                injectionSiteId = injectionSiteId,
                notes = "Adjusted",
                components = listOf(component(actualDoseValue = "1.0")),
            ),
        )

        assertThat(result).isInstanceOf(Result.Success::class)
        val storedEvent = database.administrationEventDao().getById(eventId)!!
        assertThat(storedEvent.notes).isEqualTo("Adjusted")
        val components = database.doseComponentDao().getByAdministrationEventId(eventId)
        assertThat(components).hasSize(1)
        assertThat(components.single().inventoryDeductedValue).isEqualTo(Decimal.parse("0.4"))
        assertThat(database.openedContainerDao().getByCompoundSupplyId(compoundId)!!.remainingAmountValue)
            .isEqualTo(Decimal.parse("0.6"))

        val transactions = database.inventoryTransactionDao().observeByCompound(compoundId).first()
        assertThat(transactions.map { it.deltaValue.toPlainString() })
            .containsExactlyInAnyOrder("1", "-0.2", "0.2", "-0.4")
        assertLedgerBalanced()
    }

    @Test
    fun `delete reverses inventory resets scheduled dose and clears site cooldown`() = runTest {
        val eventId = (repository.log(event(), listOf(component(actualDoseValue = "0.5"))) as Result.Success).data

        val result = repository.delete(eventId)

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(database.administrationEventDao().getById(eventId)).isNull()
        assertThat(database.doseComponentDao().getByAdministrationEventId(eventId)).hasSize(0)
        val scheduled = database.scheduledDoseDao().getById(scheduledDoseId)!!
        assertThat(scheduled.status).isEqualTo(ScheduledDoseStatus.PENDING)
        assertThat(scheduled.administrationEventId).isNull()
        val site = database.injectionSiteDao().getById(injectionSiteId)!!
        assertThat(site.lastUsedAt).isNull()
        assertThat(site.avoidUntil).isNull()
        assertLedgerBalanced()
    }

    @Test
    fun `log returns CONSTRAINT_VIOLATION when opened inventory is insufficient`() = runTest {
        val result = repository.log(event(), listOf(component(actualDoseValue = "3.0")))

        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.CONSTRAINT_VIOLATION)
        assertLedgerBalanced()
    }

    @Test
    fun `pagedHistoryForCompound pages newest first and derives volume from the logged concentration`() = runTest {
        repository.log(event(), listOf(component(actualDoseValue = "0.25")))
        repository.log(
            event().copy(loggedAt = LOGGED_AT + 1.days),
            // A scheduled dose can only be logged once (§3.4), so the later dose is a manual one.
            listOf(component(actualDoseValue = "0.5").copy(scheduledDoseId = null)),
        )

        val history = repository.pagedHistoryForCompound(compoundId, status = null).asSnapshot()

        assertThat(history.map { it.dose.value.toPlainString() }).containsExactly("0.5", "0.25")
        // 2.5 mg/mL snapshotted at log time (§3.5): 0.5 mg is 0.2 mL, 0.25 mg is 0.1 mL.
        assertThat(history.map { it.volume?.value?.toPlainString() }).containsExactly("0.2", "0.1")
        assertThat(history.first().injectionSiteName).isEqualTo("Left abdomen")
        assertThat(history.first().status).isEqualTo(DomainAdministrationEventStatus.TAKEN)
    }

    @Test
    fun `pagedHistoryForCompound narrows to the status the chip picked`() = runTest {
        repository.log(event(), listOf(component(actualDoseValue = "0.25")))
        repository.log(
            event(status = DomainAdministrationEventStatus.SKIPPED).copy(loggedAt = LOGGED_AT + 1.days),
            listOf(component(actualDoseValue = "0.5").copy(scheduledDoseId = null)),
        )

        val skipped = repository
            .pagedHistoryForCompound(compoundId, DomainAdministrationEventStatus.SKIPPED)
            .asSnapshot()

        assertThat(skipped.map { it.dose.value.toPlainString() }).containsExactly("0.5")
    }

    @Test
    fun `pagedHistoryForCompound leaves volume null when nothing was logged to divide by`() = runTest {
        database.compoundSupplyDao().update(
            database.compoundSupplyDao().getById(compoundId)!!.copy(
                concentrationAmountValue = null,
                concentrationAmountUnit = null,
                concentrationPerValue = null,
                concentrationPerUnit = null,
            ),
        )

        repository.log(event(status = DomainAdministrationEventStatus.SKIPPED), listOf(component("0.25")))

        assertThat(repository.pagedHistoryForCompound(compoundId, status = null).asSnapshot().single().volume)
            .isNull()
    }

    @Test
    fun `observeLoggedDoseCount counts Taken plus Partial and ignores Skipped`() = runTest {
        repository.log(event(), listOf(component(actualDoseValue = "0.25")))
        repository.log(
            event(status = DomainAdministrationEventStatus.PARTIAL).copy(loggedAt = LOGGED_AT + 1.days),
            listOf(component(actualDoseValue = "0.1").copy(scheduledDoseId = null)),
        )
        repository.log(
            event(status = DomainAdministrationEventStatus.SKIPPED).copy(loggedAt = LOGGED_AT + 2.days),
            listOf(component(actualDoseValue = "0.25").copy(scheduledDoseId = null)),
        )

        assertThat(repository.observeLoggedDoseCount(compoundId).first()).isEqualTo(2)
    }

    @Test
    fun `observeSiteUsesBetween returns site-bearing doses inside the range only`() = runTest {
        repository.log(event(), listOf(component(actualDoseValue = "0.1")))
        repository.log(
            event().copy(loggedAt = LOGGED_AT + 1.days),
            listOf(component(actualDoseValue = "0.1").copy(scheduledDoseId = null)),
        )
        // No site: an oral dose is not a use of anything.
        repository.log(
            event().copy(loggedAt = LOGGED_AT + 2.days, injectionSiteId = null),
            listOf(component(actualDoseValue = "0.1").copy(scheduledDoseId = null)),
        )

        val uses = repository.observeSiteUsesBetween(LOGGED_AT, LOGGED_AT + 2.days).first()

        assertThat(uses.map { it.loggedAt }).containsExactly(LOGGED_AT + 1.days, LOGGED_AT)
        assertThat(uses.map { it.injectionSiteId }).containsOnly(injectionSiteId)
        assertThat(uses.map { it.route }).containsOnly(DomainRoute.SUBCUTANEOUS)
    }

    private suspend fun assertLedgerBalanced() {
        val compound = database.compoundSupplyDao().getById(compoundId)!!
        val opened = database.openedContainerDao().getByCompoundSupplyId(compoundId)
        val ledger = database.inventoryTransactionDao().observeByCompound(compoundId).first()
            .fold(Decimal.parse("0")) { total, transaction -> total + transaction.deltaValue }
        val closedStock = compound.amountPerContainerValue * Decimal.parse(compound.numberOfContainers.toString())
        val openedStock = opened?.remainingAmountValue ?: Decimal.parse("0")
        assertThat(ledger.toPlainString()).isEqualTo((closedStock + openedStock).toPlainString())
    }

    private fun event(
        status: DomainAdministrationEventStatus = DomainAdministrationEventStatus.TAKEN,
    ): AdministrationEvent = AdministrationEvent(
        id = 0,
        loggedAt = LOGGED_AT,
        route = DomainRoute.SUBCUTANEOUS,
        status = status,
        injectionSiteId = injectionSiteId,
        notes = null,
        components = emptyList(),
        createdAt = LOGGED_AT,
        updatedAt = LOGGED_AT,
    )

    private fun component(actualDoseValue: String): DoseComponent = DoseComponent(
        id = 0,
        administrationEventId = 0,
        scheduledDoseId = scheduledDoseId,
        protocolId = protocolId,
        compoundSupplyId = compoundId,
        plannedDose = Quantity(Decimal.parse("0.5"), UnitCode.MG),
        actualDose = Quantity(Decimal.parse(actualDoseValue), UnitCode.MG),
        concentrationAtLog = null,
        notes = null,
        inventoryDeducted = Quantity(Decimal.parse("0"), UnitCode.ML),
    )

    private fun compound(): CompoundSupplyEntity = CompoundSupplyEntity(
        id = 0,
        name = "Semaglutide",
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainerValue = Decimal.parse("1"),
        amountPerContainerUnit = UnitCode.ML,
        concentrationAmountValue = Decimal.parse("2.5"),
        concentrationAmountUnit = UnitCode.MG,
        concentrationPerValue = Decimal.parse("1"),
        concentrationPerUnit = UnitCode.ML,
        numberOfContainers = 0,
        batchExpiryDate = LocalDate.parse("2026-12-31"),
        expiryAfterOpeningDays = 28,
        storageLocation = StorageLocation.FRIDGE,
        batchNumber = "BATCH-1",
        supplier = null,
        notes = null,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun openedContainer(compoundSupplyId: Long): OpenedContainerEntity = OpenedContainerEntity(
        id = 0,
        compoundSupplyId = compoundSupplyId,
        openedAt = NOW,
        remainingAmountValue = Decimal.parse("1"),
        remainingAmountUnit = UnitCode.ML,
        expiryAfterOpeningDays = 28,
        userDefinedExpiryDate = null,
        predictedExpiryDate = LocalDate.parse("2026-07-04"),
    )

    private fun initialStock(compoundSupplyId: Long): InventoryTransactionEntity = InventoryTransactionEntity(
        id = 0,
        compoundSupplyId = compoundSupplyId,
        deltaValue = Decimal.parse("1"),
        deltaUnit = UnitCode.ML,
        type = InventoryTransactionType.INITIAL_STOCK,
        sourceEventId = null,
        reason = null,
        at = NOW,
    )

    private fun protocol(compoundSupplyId: Long): ProtocolEntity = ProtocolEntity(
        id = 0,
        name = "Protocol",
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = Decimal.parse("0.5"),
        plannedDoseUnit = UnitCode.MG,
        route = Route.SUBCUTANEOUS,
        schedule = ScheduleEmbed(
            type = ScheduleType.DAILY,
            interval = null,
            timesPerDay = null,
            timesPerWeek = null,
            timesPerMonth = null,
        ),
        selectedWeekdaysBitmask = 0,
        escalation = null,
        protocolBreak = null,
        startDate = LocalDate.parse("2026-06-06"),
        endDate = null,
        reminderEnabled = false,
        reminderOffsetMinutes = 0,
        reminderBucket = ReminderBucket.MORNING,
        injectionSiteRestriction = BodyRegion.ABDOMEN,
        notes = null,
        status = com.stax.core.database.ProtocolStatus.ACTIVE,
        siteCooldownDays = 3,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun scheduledDose(protocolId: Long, compoundSupplyId: Long): ScheduledDoseEntity = ScheduledDoseEntity(
        id = 0,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        scheduledAt = LOGGED_AT,
        hasTimeOfDay = true,
        plannedDoseValue = Decimal.parse("0.5"),
        plannedDoseUnit = UnitCode.MG,
        route = Route.SUBCUTANEOUS,
        status = ScheduledDoseStatus.PENDING,
        administrationEventId = null,
        originalLocalDate = LocalDate.parse("2026-06-06"),
        originalLocalTime = LocalTime.parse("08:00"),
        originalZone = "Europe/Paris",
        createdAt = NOW,
    )

    private fun injectionSite(): InjectionSiteEntity = InjectionSiteEntity(
        id = 0,
        name = "Left abdomen",
        bodyRegion = BodyRegion.ABDOMEN,
        side = InjectionSide.LEFT,
        sublocation = null,
        lastUsedAt = null,
        avoidUntil = null,
        notes = null,
        isAvailable = true,
    )

    private fun settings(): SettingsEntity = SettingsEntity(
        id = 1,
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
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-06T00:00:00Z")
        val LOGGED_AT: Instant = Instant.parse("2026-06-06T08:00:00Z")
    }
}
