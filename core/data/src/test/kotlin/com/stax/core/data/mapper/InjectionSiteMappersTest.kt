package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.stax.core.database.InjectionSiteEntity
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import com.stax.core.database.BodyRegion as DbBodyRegion
import com.stax.core.database.InjectionSide as DbInjectionSide
import com.stax.core.database.Sublocation as DbSublocation

private val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

private fun entity(
    side: DbInjectionSide = DbInjectionSide.LEFT,
    sublocation: DbSublocation? = DbSublocation.UPPER,
    lastUsedAt: Instant? = NOW,
    avoidUntil: Instant? = null,
    notes: String? = "Small bruise",
    isAvailable: Boolean = true,
) = InjectionSiteEntity(
    id = 2L,
    name = "Left abdomen upper",
    bodyRegion = DbBodyRegion.ABDOMEN,
    side = side,
    sublocation = sublocation,
    lastUsedAt = lastUsedAt,
    avoidUntil = avoidUntil,
    notes = notes,
    isAvailable = isAvailable,
)

class InjectionSiteMappersTest {

    @Test
    fun `round-trip with all optional fields set`() {
        assertThat(entity().toDomain().toEntity()).isEqualTo(entity())
    }

    @Test
    fun `round-trip with all optional fields null`() {
        val e = entity(sublocation = null, lastUsedAt = null, avoidUntil = null, notes = null)
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `round-trip unavailable site`() {
        val e = entity(isAvailable = false, avoidUntil = NOW)
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `all body regions survive round-trip`() {
        DbBodyRegion.values().forEach { region ->
            val e = entity().copy(bodyRegion = region)
            assertThat(e.toDomain().toEntity()).isEqualTo(e)
        }
    }

    @Test
    fun `all sides survive round-trip`() {
        DbInjectionSide.values().forEach { side ->
            val e = entity(side = side)
            assertThat(e.toDomain().toEntity()).isEqualTo(e)
        }
    }

    @Test
    fun `all sublocations survive round-trip`() {
        DbSublocation.values().forEach { sub ->
            val e = entity(sublocation = sub)
            assertThat(e.toDomain().toEntity()).isEqualTo(e)
        }
    }
}
