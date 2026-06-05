package com.stax.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Entity(
    tableName = "opened_container",
    foreignKeys = [
        ForeignKey(
            entity = CompoundSupplyEntity::class,
            parentColumns = ["id"],
            childColumns = ["compoundSupplyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["compoundSupplyId"], unique = true),
    ],
)
data class OpenedContainerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val compoundSupplyId: Long,
    val openedAt: Instant,
    val remainingAmountValue: Decimal,
    val remainingAmountUnit: UnitCode,
    val expiryAfterOpeningDays: Int?,
    val userDefinedExpiryDate: LocalDate?,
    val predictedExpiryDate: LocalDate?,
)
