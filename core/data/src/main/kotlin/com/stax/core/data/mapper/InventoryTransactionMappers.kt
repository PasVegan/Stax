package com.stax.core.data.mapper

import com.stax.core.database.InventoryTransactionEntity
import com.stax.core.domain.InventoryTransaction
import com.stax.core.domain.Quantity

// ---------------------------------------------------------------------------
// InventoryTransactionEntity ↔ InventoryTransaction
// ---------------------------------------------------------------------------

fun InventoryTransactionEntity.toDomain(): InventoryTransaction = InventoryTransaction(
    id = id,
    compoundSupplyId = compoundSupplyId,
    delta = Quantity(deltaValue, deltaUnit),
    type = type.toDomain(),
    sourceEventId = sourceEventId,
    reason = reason,
    at = at,
)

fun InventoryTransaction.toEntity(): InventoryTransactionEntity = InventoryTransactionEntity(
    id = id,
    compoundSupplyId = compoundSupplyId,
    deltaValue = delta.value,
    deltaUnit = delta.unit,
    type = type.toEntity(),
    sourceEventId = sourceEventId,
    reason = reason,
    at = at,
)
