package com.stax.core.data.mapper

import com.stax.core.database.DoseComponentEntity
import com.stax.core.domain.Concentration
import com.stax.core.domain.DoseComponent
import com.stax.core.domain.Quantity

// ---------------------------------------------------------------------------
// DoseComponentEntity ↔ DoseComponent
// ---------------------------------------------------------------------------

fun DoseComponentEntity.toDomain(): DoseComponent =
    DoseComponent(
        id = id,
        administrationEventId = administrationEventId,
        scheduledDoseId = scheduledDoseId,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        plannedDose = if (plannedDoseValue != null) Quantity(plannedDoseValue!!, plannedDoseUnit!!) else null,
        actualDose = Quantity(actualDoseValue, actualDoseUnit),
        concentrationAtLog = buildConcentration(
            concentrationAmountValue,
            concentrationAmountUnit,
            concentrationPerValue,
            concentrationPerUnit,
        ),
        notes = notes,
        inventoryDeducted = Quantity(inventoryDeductedValue, inventoryDeductedUnit),
    )

fun DoseComponent.toEntity(): DoseComponentEntity =
    DoseComponentEntity(
        id = id,
        administrationEventId = administrationEventId,
        scheduledDoseId = scheduledDoseId,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = plannedDose?.value,
        plannedDoseUnit = plannedDose?.unit,
        actualDoseValue = actualDose.value,
        actualDoseUnit = actualDose.unit,
        concentrationAmountValue = concentrationAtLog?.amount?.value,
        concentrationAmountUnit = concentrationAtLog?.amount?.unit,
        concentrationPerValue = concentrationAtLog?.per?.value,
        concentrationPerUnit = concentrationAtLog?.per?.unit,
        notes = notes,
        inventoryDeductedValue = inventoryDeducted.value,
        inventoryDeductedUnit = inventoryDeducted.unit,
    )

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private fun buildConcentration(
    amountValue: com.stax.core.domain.Decimal?,
    amountUnit: com.stax.core.domain.UnitCode?,
    perValue: com.stax.core.domain.Decimal?,
    perUnit: com.stax.core.domain.UnitCode?,
): Concentration? {
    if (amountValue == null || amountUnit == null || perValue == null || perUnit == null) return null
    return Concentration(
        amount = Quantity(amountValue, amountUnit),
        per = Quantity(perValue, perUnit),
    )
}
