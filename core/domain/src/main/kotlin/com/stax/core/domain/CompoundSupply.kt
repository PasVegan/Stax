package com.stax.core.domain

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

enum class CompoundCategory { PEPTIDE, SUPPLEMENT, HORMONE, MEDICATION }

enum class CompoundForm { INJECTABLE, CAPSULE, TABLET, POWDER, LIQUID, TOPICAL }

enum class ContainerType { VIAL, BOTTLE, BLISTER, PACKET, TUB, AMPOULE }

enum class StorageLocation { FRIDGE, ROOM_TEMP, FREEZER }

data class OpenedContainer(
    val openedAt: Instant,
    val remainingAmount: Quantity,
    val expiryAfterOpeningDays: Int?,
    val userDefinedExpiryDate: LocalDate?,
    val predictedExpiryDate: LocalDate?,
)

data class CompoundSupply(
    val id: Long,
    val name: String,
    val category: CompoundCategory,
    val form: CompoundForm,
    val containerType: ContainerType,
    val primaryUnit: UnitCode,
    val amountPerContainer: Quantity,
    val concentration: Concentration?,
    val numberOfContainers: Int,
    val currentOpened: OpenedContainer?,
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
