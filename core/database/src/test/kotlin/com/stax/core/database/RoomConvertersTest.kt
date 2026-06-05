package com.stax.core.database

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import com.stax.core.domain.UnitFamily
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class RoomConvertersTest {

    private val converters = RoomConverters()

    @Test
    fun `Instant round trips through epoch millis`() {
        val value = Instant.parse("2026-06-06T12:34:56.789Z")
        val stored = converters.instantToLong(value)

        assertThat(stored).isEqualTo(1_780_749_296_789L)
        assertThat(converters.longToInstant(stored)).isEqualTo(value)
    }

    @Test
    fun `LocalDate round trips through ISO string`() {
        val value = LocalDate.parse("2026-06-06")
        val stored = converters.localDateToString(value)

        assertThat(stored).isEqualTo("2026-06-06")
        assertThat(converters.stringToLocalDate(stored)).isEqualTo(value)
    }

    @Test
    fun `LocalTime round trips through ISO string`() {
        val value = LocalTime.parse("08:30:15")
        val stored = converters.localTimeToString(value)

        assertThat(stored).isEqualTo("08:30:15")
        assertThat(converters.stringToLocalTime(stored)).isEqualTo(value)
    }

    @Test
    fun `Decimal round trips through canonical plain string`() {
        val value = Decimal.parse("0.250")
        val stored = converters.decimalToString(value)
        val roundTrip = converters.stringToDecimal(stored)

        assertThat(stored).isEqualTo("0.25")
        assertThat(roundTrip?.compareTo(Decimal.parse("0.25"))).isEqualTo(0)
        assertThat(roundTrip?.toPlainString()).isEqualTo("0.25")
    }

    @Test
    fun `UnitCode round trips through stable name`() {
        val value = UnitCode.MG
        val stored = converters.unitCodeToString(value)

        assertThat(stored).isEqualTo("MG")
        assertThat(converters.stringToUnitCode(stored)).isEqualTo(value)
    }

    @Test
    fun `UnitFamily round trips through stable name`() {
        val value = UnitFamily.MASS
        val stored = converters.unitFamilyToString(value)

        assertThat(stored).isEqualTo("MASS")
        assertThat(converters.stringToUnitFamily(stored)).isEqualTo(value)
    }

    @Test
    fun `CompoundCategory round trips through stable name`() {
        val value = CompoundCategory.PEPTIDE
        val stored = converters.compoundCategoryToString(value)

        assertThat(stored).isEqualTo("PEPTIDE")
        assertThat(converters.stringToCompoundCategory(stored)).isEqualTo(value)
    }

    @Test
    fun `CompoundForm round trips through stable name`() {
        val value = CompoundForm.INJECTABLE
        val stored = converters.compoundFormToString(value)

        assertThat(stored).isEqualTo("INJECTABLE")
        assertThat(converters.stringToCompoundForm(stored)).isEqualTo(value)
    }

    @Test
    fun `ContainerType round trips through stable name`() {
        val value = ContainerType.VIAL
        val stored = converters.containerTypeToString(value)

        assertThat(stored).isEqualTo("VIAL")
        assertThat(converters.stringToContainerType(stored)).isEqualTo(value)
    }

    @Test
    fun `StorageLocation round trips through stable name`() {
        val value = StorageLocation.FRIDGE
        val stored = converters.storageLocationToString(value)

        assertThat(stored).isEqualTo("FRIDGE")
        assertThat(converters.stringToStorageLocation(stored)).isEqualTo(value)
    }

    @Test
    fun `nullable values round trip as null`() {
        assertThat(converters.instantToLong(null)).isEqualTo(null)
        assertThat(converters.longToInstant(null)).isEqualTo(null)
        assertThat(converters.localDateToString(null)).isEqualTo(null)
        assertThat(converters.stringToLocalDate(null)).isEqualTo(null)
        assertThat(converters.localTimeToString(null)).isEqualTo(null)
        assertThat(converters.stringToLocalTime(null)).isEqualTo(null)
        assertThat(converters.decimalToString(null)).isEqualTo(null)
        assertThat(converters.stringToDecimal(null)).isEqualTo(null)
        assertThat(converters.unitCodeToString(null)).isEqualTo(null)
        assertThat(converters.stringToUnitCode(null)).isEqualTo(null)
        assertThat(converters.unitFamilyToString(null)).isEqualTo(null)
        assertThat(converters.stringToUnitFamily(null)).isEqualTo(null)
        assertThat(converters.compoundCategoryToString(null)).isEqualTo(null)
        assertThat(converters.stringToCompoundCategory(null)).isEqualTo(null)
        assertThat(converters.compoundFormToString(null)).isEqualTo(null)
        assertThat(converters.stringToCompoundForm(null)).isEqualTo(null)
        assertThat(converters.containerTypeToString(null)).isEqualTo(null)
        assertThat(converters.stringToContainerType(null)).isEqualTo(null)
        assertThat(converters.storageLocationToString(null)).isEqualTo(null)
        assertThat(converters.stringToStorageLocation(null)).isEqualTo(null)
    }
}
