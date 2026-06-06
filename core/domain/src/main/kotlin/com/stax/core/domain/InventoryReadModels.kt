package com.stax.core.domain

import kotlinx.datetime.LocalDate

data class CompoundDosesLeft(
    val compoundSupplyId: Long,
    val compoundName: String,
    val dosesLeft: Int?,
    val dosesPerActualInjection: Quantity?,
    val daysLeft: Int?,
)

sealed interface InventoryWarning {
    val compoundSupplyId: Long
    val compoundName: String

    data class LowStock(
        override val compoundSupplyId: Long,
        override val compoundName: String,
        val dosesLeft: Int,
        val reorderBefore: LocalDate?,
    ) : InventoryWarning

    data class OpenedContainerExpiring(
        override val compoundSupplyId: Long,
        override val compoundName: String,
        val expiryDate: LocalDate,
        val daysUntilExpiry: Int,
    ) : InventoryWarning

    data class ProtocolNeedsMore(
        override val compoundSupplyId: Long,
        override val compoundName: String,
        val protocolId: Long,
        val required: Quantity,
        val available: Quantity,
    ) : InventoryWarning

    data class BatchExpiresBeforeRunOut(
        override val compoundSupplyId: Long,
        override val compoundName: String,
        val batchExpiryDate: LocalDate,
        val runOutDate: LocalDate,
    ) : InventoryWarning
}
