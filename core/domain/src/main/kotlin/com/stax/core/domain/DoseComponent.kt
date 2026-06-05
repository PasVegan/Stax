package com.stax.core.domain

data class DoseComponent(
    val id: Long,
    val administrationEventId: Long,
    val scheduledDoseId: Long?,
    val protocolId: Long?,
    val compoundSupplyId: Long,
    val plannedDose: Quantity?,
    val actualDose: Quantity,
    val concentrationAtLog: Concentration?,
    val notes: String?,
    val inventoryDeducted: Quantity,
)
