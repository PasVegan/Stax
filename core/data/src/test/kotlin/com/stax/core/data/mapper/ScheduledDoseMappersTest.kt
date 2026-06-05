package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import com.stax.core.database.Route as DbRoute
import com.stax.core.database.ScheduledDoseStatus as DbScheduledDoseStatus

private val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
private val TODAY: LocalDate = LocalDate(2023, 11, 14)
private fun dec(s: String): Decimal = Decimal.parse(s)

private fun entity(
    hasTimeOfDay: Boolean = true,
    originalLocalTime: LocalTime? = LocalTime(8, 0),
    administrationEventId: Long? = null,
    status: DbScheduledDoseStatus = DbScheduledDoseStatus.PENDING,
) = ScheduledDoseEntity(
    id = 5L,
    protocolId = 10L,
    compoundSupplyId = 1L,
    scheduledAt = NOW,
    hasTimeOfDay = hasTimeOfDay,
    plannedDoseValue = dec("0.25"),
    plannedDoseUnit = UnitCode.MG,
    route = DbRoute.SUBCUTANEOUS,
    status = status,
    administrationEventId = administrationEventId,
    originalLocalDate = TODAY,
    originalLocalTime = originalLocalTime,
    originalZone = "Europe/Paris",
    createdAt = NOW,
)

class ScheduledDoseMappersTest {

    @Test
    fun `round-trip pending dose with time of day`() {
        val e = entity()
        val roundTripped = e.toDomain().toEntity(e.originalLocalDate, e.originalLocalTime, e.originalZone)
        assertThat(roundTripped).isEqualTo(e)
    }

    @Test
    fun `round-trip dose without time of day`() {
        val e = entity(hasTimeOfDay = false, originalLocalTime = null)
        val roundTripped = e.toDomain().toEntity(e.originalLocalDate, e.originalLocalTime, e.originalZone)
        assertThat(roundTripped).isEqualTo(e)
    }

    @Test
    fun `round-trip taken dose with administrationEventId`() {
        val e = entity(status = DbScheduledDoseStatus.TAKEN, administrationEventId = 99L)
        val roundTripped = e.toDomain().toEntity(e.originalLocalDate, e.originalLocalTime, e.originalZone)
        assertThat(roundTripped).isEqualTo(e)
    }

    @Test
    fun `all status values survive round-trip`() {
        DbScheduledDoseStatus.values().forEach { status ->
            val e = entity(status = status)
            val roundTripped = e.toDomain().toEntity(e.originalLocalDate, e.originalLocalTime, e.originalZone)
            assertThat(roundTripped).isEqualTo(e)
        }
    }
}
