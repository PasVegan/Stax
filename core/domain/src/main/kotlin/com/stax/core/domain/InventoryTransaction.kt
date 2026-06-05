package com.stax.core.domain

import kotlin.time.Instant

enum class InventoryTransactionType {
    INITIAL_STOCK,
    MANUAL,
    DOSE_DEDUCTION,
    CONTAINER_OPEN,
    CONTAINER_CLOSE,
}

data class InventoryTransaction(
    val id: Long,
    val compoundSupplyId: Long,
    val delta: Quantity,
    val type: InventoryTransactionType,
    val sourceEventId: Long?,
    val reason: String?,
    val at: Instant,
)
