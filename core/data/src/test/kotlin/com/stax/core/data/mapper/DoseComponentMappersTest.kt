package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.stax.core.database.DoseComponentEntity
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import org.junit.jupiter.api.Test

private fun dec(s: String): Decimal = Decimal.parse(s)

private fun entity(
    scheduledDoseId: Long? = 5L,
    protocolId: Long? = 10L,
    plannedDoseValue: Decimal? = dec("0.25"),
    plannedDoseUnit: UnitCode? = UnitCode.MG,
    concentrationAmountValue: Decimal? = dec("2.5"),
    concentrationAmountUnit: UnitCode? = UnitCode.MG,
    concentrationPerValue: Decimal? = dec("1"),
    concentrationPerUnit: UnitCode? = UnitCode.ML,
    notes: String? = null,
) = DoseComponentEntity(
    id = 20L,
    administrationEventId = 30L,
    scheduledDoseId = scheduledDoseId,
    protocolId = protocolId,
    compoundSupplyId = 1L,
    plannedDoseValue = plannedDoseValue,
    plannedDoseUnit = plannedDoseUnit,
    actualDoseValue = dec("0.25"),
    actualDoseUnit = UnitCode.MG,
    concentrationAmountValue = concentrationAmountValue,
    concentrationAmountUnit = concentrationAmountUnit,
    concentrationPerValue = concentrationPerValue,
    concentrationPerUnit = concentrationPerUnit,
    notes = notes,
    inventoryDeductedValue = dec("0.1"),
    inventoryDeductedUnit = UnitCode.ML,
)

class DoseComponentMappersTest {

    @Test
    fun `round-trip with all fields set`() {
        val e = entity()
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `round-trip manual log (null scheduledDoseId, protocolId, plannedDose)`() {
        val e = entity(
            scheduledDoseId = null,
            protocolId = null,
            plannedDoseValue = null,
            plannedDoseUnit = null,
        )
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `round-trip without concentration (unit-based form)`() {
        val e = entity(
            concentrationAmountValue = null,
            concentrationAmountUnit = null,
            concentrationPerValue = null,
            concentrationPerUnit = null,
        )
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `null concentration fields map to null domain concentrationAtLog`() {
        val domain = entity(
            concentrationAmountValue = null,
            concentrationAmountUnit = null,
            concentrationPerValue = null,
            concentrationPerUnit = null,
        ).toDomain()
        assertThat(domain.concentrationAtLog).isNull()
    }

    @Test
    fun `round-trip with notes`() {
        val e = entity(notes = "Injected left abdomen")
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }
}
