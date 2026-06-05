package com.stax.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlin.time.Instant

@Entity(
    tableName = "inventory_transaction",
    foreignKeys = [
        ForeignKey(
            entity = CompoundSupplyEntity::class,
            parentColumns = ["id"],
            childColumns = ["compoundSupplyId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AdministrationEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceEventId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["compoundSupplyId", "at"]),
        Index(value = ["sourceEventId"]),
    ],
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val compoundSupplyId: Long,
    val deltaValue: Decimal,
    val deltaUnit: UnitCode,
    val type: InventoryTransactionType,
    val sourceEventId: Long?,
    val reason: String?,
    val at: Instant,
)

enum class InventoryTransactionType {
    INITIAL_STOCK,
    MANUAL,
    DOSE_DEDUCTION,
    CONTAINER_OPEN,
    CONTAINER_CLOSE,
}
