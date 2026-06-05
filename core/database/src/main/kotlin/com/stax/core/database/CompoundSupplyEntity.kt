package com.stax.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Entity(
    tableName = "compound_supply",
    indices = [
        Index(value = ["deletedAt"]),
        Index(value = ["category", "form", "deletedAt"]),
        Index(value = ["name"]),
        Index(value = ["batchExpiryDate"]),
    ],
)
data class CompoundSupplyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: CompoundCategory,
    val form: CompoundForm,
    val containerType: ContainerType,
    val primaryUnit: UnitCode,
    val amountPerContainerValue: Decimal,
    val amountPerContainerUnit: UnitCode,
    val concentrationAmountValue: Decimal?,
    val concentrationAmountUnit: UnitCode?,
    val concentrationPerValue: Decimal?,
    val concentrationPerUnit: UnitCode?,
    val numberOfContainers: Int,
    val batchExpiryDate: LocalDate?,
    val expiryAfterOpeningDays: Int?,
    val storageLocation: StorageLocation,
    val batchNumber: String?,
    val supplier: String?,
    val notes: String?,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class CompoundCategory {
    PEPTIDE,
    SUPPLEMENT,
    HORMONE,
    MEDICATION,
}

enum class CompoundForm {
    INJECTABLE,
    CAPSULE,
    TABLET,
    POWDER,
    LIQUID,
    TOPICAL,
}

enum class ContainerType {
    VIAL,
    BOTTLE,
    BLISTER,
    PACKET,
    TUB,
    AMPOULE,
}

enum class StorageLocation {
    FRIDGE,
    ROOM_TEMP,
    FREEZER,
}
