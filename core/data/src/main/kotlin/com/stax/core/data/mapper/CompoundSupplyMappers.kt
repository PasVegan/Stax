package com.stax.core.data.mapper

import com.stax.core.database.CompoundSupplyEntity
import com.stax.core.database.OpenedContainerEntity
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Quantity

// ---------------------------------------------------------------------------
// CompoundSupplyEntity ↔ CompoundSupply
// ---------------------------------------------------------------------------

/**
 * Maps a [CompoundSupplyEntity] to [CompoundSupply].
 *
 * [opened] is the companion row from the `opened_container` table, or null when no
 * container is currently open. Callers (repositories) must JOIN / query this row.
 */
fun CompoundSupplyEntity.toDomain(opened: OpenedContainerEntity? = null): CompoundSupply = CompoundSupply(
    id = id,
    name = name,
    category = category.toDomain(),
    form = form.toDomain(),
    containerType = containerType.toDomain(),
    primaryUnit = primaryUnit,
    amountPerContainer = Quantity(amountPerContainerValue, amountPerContainerUnit),
    concentration = buildConcentration(
        concentrationAmountValue,
        concentrationAmountUnit,
        concentrationPerValue,
        concentrationPerUnit,
    ),
    numberOfContainers = numberOfContainers,
    currentOpened = opened?.toDomain(),
    batchExpiryDate = batchExpiryDate,
    expiryAfterOpeningDays = expiryAfterOpeningDays,
    storageLocation = storageLocation.toDomain(),
    batchNumber = batchNumber,
    supplier = supplier,
    notes = notes,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps [CompoundSupply] back to [CompoundSupplyEntity]. `currentOpened` is omitted — it lives in a separate table. */
fun CompoundSupply.toEntity(): CompoundSupplyEntity = CompoundSupplyEntity(
    id = id,
    name = name,
    category = category.toEntity(),
    form = form.toEntity(),
    containerType = containerType.toEntity(),
    primaryUnit = primaryUnit,
    amountPerContainerValue = amountPerContainer.value,
    amountPerContainerUnit = amountPerContainer.unit,
    concentrationAmountValue = concentration?.amount?.value,
    concentrationAmountUnit = concentration?.amount?.unit,
    concentrationPerValue = concentration?.per?.value,
    concentrationPerUnit = concentration?.per?.unit,
    numberOfContainers = numberOfContainers,
    batchExpiryDate = batchExpiryDate,
    expiryAfterOpeningDays = expiryAfterOpeningDays,
    storageLocation = storageLocation.toEntity(),
    batchNumber = batchNumber,
    supplier = supplier,
    notes = notes,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---------------------------------------------------------------------------
// OpenedContainerEntity ↔ OpenedContainer
// ---------------------------------------------------------------------------

fun OpenedContainerEntity.toDomain(): OpenedContainer = OpenedContainer(
    openedAt = openedAt,
    remainingAmount = Quantity(remainingAmountValue, remainingAmountUnit),
    expiryAfterOpeningDays = expiryAfterOpeningDays,
    userDefinedExpiryDate = userDefinedExpiryDate,
    predictedExpiryDate = predictedExpiryDate,
)

/**
 * Maps [OpenedContainer] back to [OpenedContainerEntity].
 *
 * [id] and [compoundSupplyId] are DB-only fields not carried in the domain model.
 * Pass the original entity's values (or 0 / the parent compound id for inserts).
 */
fun OpenedContainer.toEntity(id: Long, compoundSupplyId: Long): OpenedContainerEntity = OpenedContainerEntity(
    id = id,
    compoundSupplyId = compoundSupplyId,
    openedAt = openedAt,
    remainingAmountValue = remainingAmount.value,
    remainingAmountUnit = remainingAmount.unit,
    expiryAfterOpeningDays = expiryAfterOpeningDays,
    userDefinedExpiryDate = userDefinedExpiryDate,
    predictedExpiryDate = predictedExpiryDate,
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
