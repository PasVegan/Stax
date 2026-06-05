package com.stax.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode

@Entity(
    tableName = "dose_component",
    foreignKeys = [
        ForeignKey(
            entity = AdministrationEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["administrationEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ScheduledDoseEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduledDoseId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ProtocolEntity::class,
            parentColumns = ["id"],
            childColumns = ["protocolId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = CompoundSupplyEntity::class,
            parentColumns = ["id"],
            childColumns = ["compoundSupplyId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["administrationEventId"]),
        Index(value = ["compoundSupplyId"]),
        Index(value = ["protocolId"]),
        Index(value = ["scheduledDoseId"], unique = true),
    ],
)
data class DoseComponentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val administrationEventId: Long,
    val scheduledDoseId: Long?,
    val protocolId: Long?,
    val compoundSupplyId: Long,
    val plannedDoseValue: Decimal?,
    val plannedDoseUnit: UnitCode?,
    val actualDoseValue: Decimal,
    val actualDoseUnit: UnitCode,
    val concentrationAmountValue: Decimal?,
    val concentrationAmountUnit: UnitCode?,
    val concentrationPerValue: Decimal?,
    val concentrationPerUnit: UnitCode?,
    val notes: String?,
    val inventoryDeductedValue: Decimal,
    val inventoryDeductedUnit: UnitCode,
)
