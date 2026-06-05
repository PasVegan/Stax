package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private val Int.dec: Decimal get() = Decimal.parse(this.toString())
private val String.dec: Decimal get() = Decimal.parse(this)

class QuantityTest {

    // plus — acceptance criterion

    @Test
    fun `plus same unit adds values`() {
        val result = Quantity("0.25".dec, UnitCode.MG) + Quantity("0.1".dec, UnitCode.MG)
        assertThat(result).isEqualTo(Quantity("0.35".dec, UnitCode.MG))
    }

    @Test
    fun `plus different units throws`() {
        assertThrows<IllegalArgumentException> {
            Quantity(1.dec, UnitCode.MG) + Quantity(1.dec, UnitCode.MCG)
        }
    }

    @Test
    fun `plus preserves unit`() {
        val result = Quantity(1.dec, UnitCode.ML) + Quantity(1.dec, UnitCode.ML)
        assertThat(result.unit).isEqualTo(UnitCode.ML)
    }

    // minus

    @Test
    fun `minus same unit subtracts values`() {
        val result = Quantity("1.00".dec, UnitCode.MG) - Quantity("0.25".dec, UnitCode.MG)
        assertThat(result.value.compareTo("0.75".dec)).isEqualTo(0)
        assertThat(result.unit).isEqualTo(UnitCode.MG)
    }

    @Test
    fun `minus different units throws`() {
        assertThrows<IllegalArgumentException> {
            Quantity(1.dec, UnitCode.G) - Quantity(1.dec, UnitCode.MG)
        }
    }

    // times

    @Test
    fun `times scales value by scalar`() {
        val result = Quantity("2.5".dec, UnitCode.MG) * "4".dec
        assertThat(result.value.compareTo("10".dec)).isEqualTo(0)
        assertThat(result.unit).isEqualTo(UnitCode.MG)
    }

    @Test
    fun `times by zero returns zero quantity`() {
        val result = Quantity("5".dec, UnitCode.MCG) * 0.dec
        assertThat(result.value.compareTo(0.dec)).isEqualTo(0)
    }

    // toString

    @Test
    fun `toString formats value and unit lowercase`() {
        assertThat(Quantity("2.5".dec, UnitCode.MG).toString()).isEqualTo("2.5 mg")
    }

    @Test
    fun `toString strips trailing zeros via toPlainString`() {
        assertThat(Quantity("0.250".dec, UnitCode.MCG).toString()).isEqualTo("0.25 mcg")
    }

    @Test
    fun `toString for each unit uses lowercase name`() {
        assertThat(Quantity(1.dec, UnitCode.CAPSULE).toString()).isEqualTo("1 capsule")
        assertThat(Quantity(1.dec, UnitCode.TABLET).toString()).isEqualTo("1 tablet")
        assertThat(Quantity(1.dec, UnitCode.ML).toString()).isEqualTo("1 ml")
        assertThat(Quantity(1.dec, UnitCode.IU).toString()).isEqualTo("1 iu")
        assertThat(Quantity(1.dec, UnitCode.G).toString()).isEqualTo("1 g")
        assertThat(Quantity(1.dec, UnitCode.SCOOP).toString()).isEqualTo("1 scoop")
        assertThat(Quantity(1.dec, UnitCode.DROP).toString()).isEqualTo("1 drop")
    }

    // structural equality (data class)

    @Test
    fun `same value and unit are equal`() {
        assertThat(Quantity("1.5".dec, UnitCode.MG)).isEqualTo(Quantity("1.5".dec, UnitCode.MG))
    }

    @Test
    fun `different unit not equal`() {
        val a = Quantity(1.dec, UnitCode.MG)
        val b = Quantity(1.dec, UnitCode.MCG)
        assertThat(a == b).isEqualTo(false)
    }

    @Test
    fun `different value not equal`() {
        val a = Quantity("1.0".dec, UnitCode.MG)
        val b = Quantity("2.0".dec, UnitCode.MG)
        assertThat(a == b).isEqualTo(false)
    }
}
