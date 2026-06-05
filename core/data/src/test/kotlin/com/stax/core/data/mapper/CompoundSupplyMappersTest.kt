package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.stax.core.database.CompoundSupplyEntity
import com.stax.core.database.OpenedContainerEntity
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import com.stax.core.database.CompoundCategory as DbCompoundCategory
import com.stax.core.database.CompoundForm as DbCompoundForm
import com.stax.core.database.ContainerType as DbContainerType
import com.stax.core.database.StorageLocation as DbStorageLocation

private val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
private val TODAY: LocalDate = LocalDate(2023, 11, 14)
private fun dec(s: String): Decimal = Decimal.parse(s)

class CompoundSupplyMappersTest {

    private fun compoundEntity(
        concentrationAmountValue: Decimal? = null,
        concentrationAmountUnit: UnitCode? = null,
        concentrationPerValue: Decimal? = null,
        concentrationPerUnit: UnitCode? = null,
    ) = CompoundSupplyEntity(
        id = 1L,
        name = "BPC-157",
        category = DbCompoundCategory.PEPTIDE,
        form = DbCompoundForm.INJECTABLE,
        containerType = DbContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainerValue = dec("5"),
        amountPerContainerUnit = UnitCode.MG,
        concentrationAmountValue = concentrationAmountValue,
        concentrationAmountUnit = concentrationAmountUnit,
        concentrationPerValue = concentrationPerValue,
        concentrationPerUnit = concentrationPerUnit,
        numberOfContainers = 3,
        batchExpiryDate = TODAY,
        expiryAfterOpeningDays = 30,
        storageLocation = DbStorageLocation.FRIDGE,
        batchNumber = "BN-001",
        supplier = "Peptide Sciences",
        notes = "Store cold",
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun openedEntity() = OpenedContainerEntity(
        id = 7L,
        compoundSupplyId = 1L,
        openedAt = NOW,
        remainingAmountValue = dec("4.5"),
        remainingAmountUnit = UnitCode.MG,
        expiryAfterOpeningDays = 30,
        userDefinedExpiryDate = null,
        predictedExpiryDate = TODAY,
    )

    // -----------------------------------------------------------------------
    // CompoundSupplyEntity ↔ CompoundSupply
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip without concentration and without opened container`() {
        val entity = compoundEntity()
        val roundTripped = entity.toDomain(null).toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    @Test
    fun `round-trip with concentration`() {
        val entity = compoundEntity(
            concentrationAmountValue = dec("2.5"),
            concentrationAmountUnit = UnitCode.MG,
            concentrationPerValue = dec("1"),
            concentrationPerUnit = UnitCode.ML,
        )
        val roundTripped = entity.toDomain(null).toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    @Test
    fun `toDomain maps concentration correctly`() {
        val entity = compoundEntity(
            concentrationAmountValue = dec("2.5"),
            concentrationAmountUnit = UnitCode.MG,
            concentrationPerValue = dec("1"),
            concentrationPerUnit = UnitCode.ML,
        )
        val domain = entity.toDomain()
        assertThat(domain.concentration!!.amount.value).isEqualTo(dec("2.5"))
        assertThat(domain.concentration!!.per.unit).isEqualTo(UnitCode.ML)
    }

    @Test
    fun `toDomain with null concentration fields yields null concentration`() {
        val domain = compoundEntity().toDomain()
        assertThat(domain.concentration).isNull()
    }

    // -----------------------------------------------------------------------
    // OpenedContainerEntity ↔ OpenedContainer
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip opened container`() {
        val entity = openedEntity()
        val roundTripped = entity.toDomain().toEntity(id = entity.id, compoundSupplyId = entity.compoundSupplyId)
        assertThat(roundTripped).isEqualTo(entity)
    }

    @Test
    fun `round-trip opened container with user-defined expiry`() {
        val entity = openedEntity().copy(userDefinedExpiryDate = TODAY, predictedExpiryDate = null)
        val roundTripped = entity.toDomain().toEntity(id = entity.id, compoundSupplyId = entity.compoundSupplyId)
        assertThat(roundTripped).isEqualTo(entity)
    }

    @Test
    fun `compound with opened container has currentOpened set`() {
        val compoundEntity = compoundEntity()
        val openedEntity = openedEntity()
        val domain = compoundEntity.toDomain(openedEntity)
        assertThat(domain.currentOpened!!.remainingAmount.value).isEqualTo(dec("4.5"))
    }
}
