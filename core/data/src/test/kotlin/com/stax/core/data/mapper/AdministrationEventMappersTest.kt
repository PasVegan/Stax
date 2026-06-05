package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.stax.core.database.AdministrationEventEntity
import com.stax.core.database.DoseComponentEntity
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import com.stax.core.database.AdministrationEventStatus as DbAdministrationEventStatus
import com.stax.core.database.Route as DbRoute

private val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
private fun dec(s: String): Decimal = Decimal.parse(s)

private fun eventEntity(injectionSiteId: Long? = 3L, notes: String? = null) = AdministrationEventEntity(
    id = 30L,
    loggedAt = NOW,
    route = DbRoute.SUBCUTANEOUS,
    status = DbAdministrationEventStatus.TAKEN,
    injectionSiteId = injectionSiteId,
    notes = notes,
    createdAt = NOW,
    updatedAt = NOW,
)

private fun componentEntity(eventId: Long = 30L) = DoseComponentEntity(
    id = 20L,
    administrationEventId = eventId,
    scheduledDoseId = 5L,
    protocolId = 10L,
    compoundSupplyId = 1L,
    plannedDoseValue = dec("0.25"),
    plannedDoseUnit = UnitCode.MG,
    actualDoseValue = dec("0.25"),
    actualDoseUnit = UnitCode.MG,
    concentrationAmountValue = dec("2.5"),
    concentrationAmountUnit = UnitCode.MG,
    concentrationPerValue = dec("1"),
    concentrationPerUnit = UnitCode.ML,
    notes = null,
    inventoryDeductedValue = dec("0.1"),
    inventoryDeductedUnit = UnitCode.ML,
)

class AdministrationEventMappersTest {

    @Test
    fun `round-trip event entity (no components)`() {
        val entity = eventEntity()
        assertThat(entity.toDomain(emptyList()).toEntity()).isEqualTo(entity)
    }

    @Test
    fun `round-trip event without injection site or notes`() {
        val entity = eventEntity(injectionSiteId = null, notes = null)
        assertThat(entity.toDomain(emptyList()).toEntity()).isEqualTo(entity)
    }

    @Test
    fun `toComponentEntities round-trips component rows`() {
        val compEntity = componentEntity()
        val domain = eventEntity().toDomain(listOf(compEntity.toDomain()))
        val roundTripped = domain.toComponentEntities()
        assertThat(roundTripped).isEqualTo(listOf(compEntity))
    }

    @Test
    fun `all administration event status values survive round-trip`() {
        DbAdministrationEventStatus.values().forEach { status ->
            val entity = eventEntity().copy(status = status)
            assertThat(entity.toDomain(emptyList()).toEntity()).isEqualTo(entity)
        }
    }

    @Test
    fun `toDomain preserves component count`() {
        val comps = listOf(componentEntity(), componentEntity(eventId = 30L).copy(id = 21L))
        val domain = eventEntity().toDomain(comps.map { it.toDomain() })
        assertThat(domain.components.size).isEqualTo(2)
    }
}
