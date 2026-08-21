package com.stax.core.database

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** `AdministrationEventDao.observeHistoryForCompound` — the query behind §4.3.8's history list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompoundHistoryDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var compoundDao: CompoundSupplyDao
    private lateinit var eventDao: AdministrationEventDao
    private lateinit var componentDao: DoseComponentDao
    private lateinit var injectionSiteDao: InjectionSiteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        compoundDao = database.compoundSupplyDao()
        eventDao = database.administrationEventDao()
        componentDao = database.doseComponentDao()
        injectionSiteDao = database.injectionSiteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `returns this compound's rows newest first`() = runTest {
        val compoundId = compoundDao.insert(compound())
        val oldest = logDose(compoundId, at = "2026-06-01T08:00:00Z")
        val newest = logDose(compoundId, at = "2026-06-08T08:00:00Z")
        val middle = logDose(compoundId, at = "2026-06-04T08:00:00Z")

        val rows = eventDao.observeHistoryForCompound(compoundId).first()

        assertThat(rows.map { it.eventId }).containsExactly(newest, middle, oldest)
    }

    @Test
    fun `excludes doses of other compounds`() = runTest {
        val compoundId = compoundDao.insert(compound(name = "Semaglutide"))
        val otherId = compoundDao.insert(compound(name = "BPC-157"))
        val mine = logDose(compoundId, at = "2026-06-01T08:00:00Z")
        logDose(otherId, at = "2026-06-02T08:00:00Z")

        val rows = eventDao.observeHistoryForCompound(compoundId).first()

        assertThat(rows.map { it.eventId }).containsExactly(mine)
    }

    @Test
    fun `an event logging two compounds appears in both histories, with its own dose`() = runTest {
        val first = compoundDao.insert(compound(name = "Semaglutide"))
        val second = compoundDao.insert(compound(name = "BPC-157"))
        val eventId = eventDao.insert(administrationEvent())
        componentDao.insert(
            doseComponent(eventId = eventId, compoundSupplyId = first, actualDose = Decimal.parse("0.25")),
        )
        componentDao.insert(
            doseComponent(eventId = eventId, compoundSupplyId = second, actualDose = Decimal.parse("0.5")),
        )

        assertThat(eventDao.observeHistoryForCompound(first).first().single().actualDoseValue)
            .isEqualTo(Decimal.parse("0.25"))
        assertThat(eventDao.observeHistoryForCompound(second).first().single().actualDoseValue)
            .isEqualTo(Decimal.parse("0.5"))
    }

    @Test
    fun `joins the injection site name and tolerates its absence`() = runTest {
        val compoundId = compoundDao.insert(compound())
        val siteId = injectionSiteDao.insert(injectionSite(name = "Abdomen R"))
        logDose(compoundId, at = "2026-06-02T08:00:00Z", injectionSiteId = siteId)
        logDose(compoundId, at = "2026-06-01T08:00:00Z", injectionSiteId = null)

        val rows = eventDao.observeHistoryForCompound(compoundId).first()

        assertThat(rows.first().injectionSiteName).isEqualTo("Abdomen R")
        assertThat(rows.last().injectionSiteName).isNull()
    }

    @Test
    fun `a compound with no logged dose has an empty history`() = runTest {
        val compoundId = compoundDao.insert(compound())

        assertThat(eventDao.observeHistoryForCompound(compoundId).first()).isEmpty()
    }

    private suspend fun logDose(
        compoundSupplyId: Long,
        at: String,
        status: AdministrationEventStatus = AdministrationEventStatus.TAKEN,
        injectionSiteId: Long? = null,
    ): Long {
        val eventId = eventDao.insert(
            administrationEvent(loggedAt = Instant.parse(at), status = status, injectionSiteId = injectionSiteId),
        )
        componentDao.insert(doseComponent(eventId = eventId, compoundSupplyId = compoundSupplyId))
        return eventId
    }

    private fun administrationEvent(
        loggedAt: Instant = Instant.parse("2026-06-06T08:00:00Z"),
        status: AdministrationEventStatus = AdministrationEventStatus.TAKEN,
        injectionSiteId: Long? = null,
    ): AdministrationEventEntity = AdministrationEventEntity(
        id = 0,
        loggedAt = loggedAt,
        route = Route.SUBCUTANEOUS,
        status = status,
        injectionSiteId = injectionSiteId,
        notes = null,
        createdAt = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-06T00:00:00Z"),
    )

    private fun doseComponent(
        eventId: Long,
        compoundSupplyId: Long,
        actualDose: Decimal = Decimal.parse("0.25"),
    ): DoseComponentEntity = DoseComponentEntity(
        id = 0,
        administrationEventId = eventId,
        scheduledDoseId = null,
        protocolId = null,
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = actualDose,
        plannedDoseUnit = UnitCode.MG,
        actualDoseValue = actualDose,
        actualDoseUnit = UnitCode.MG,
        concentrationAmountValue = Decimal.parse("2.5"),
        concentrationAmountUnit = UnitCode.MG,
        concentrationPerValue = Decimal.parse("1"),
        concentrationPerUnit = UnitCode.ML,
        notes = null,
        inventoryDeductedValue = Decimal.parse("0.1"),
        inventoryDeductedUnit = UnitCode.ML,
    )

    private fun injectionSite(name: String): InjectionSiteEntity = InjectionSiteEntity(
        id = 0,
        name = name,
        bodyRegion = BodyRegion.ABDOMEN,
        side = InjectionSide.RIGHT,
        sublocation = null,
        lastUsedAt = null,
        avoidUntil = null,
        notes = null,
        isAvailable = true,
    )

    private fun compound(name: String = "Semaglutide"): CompoundSupplyEntity = CompoundSupplyEntity(
        id = 0,
        name = name,
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainerValue = Decimal.parse("5"),
        amountPerContainerUnit = UnitCode.MG,
        concentrationAmountValue = Decimal.parse("2.5"),
        concentrationAmountUnit = UnitCode.MG,
        concentrationPerValue = Decimal.parse("1"),
        concentrationPerUnit = UnitCode.ML,
        numberOfContainers = 1,
        batchExpiryDate = null,
        expiryAfterOpeningDays = null,
        storageLocation = StorageLocation.FRIDGE,
        batchNumber = null,
        supplier = null,
        notes = null,
        deletedAt = null,
        createdAt = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-06T00:00:00Z"),
    )
}
