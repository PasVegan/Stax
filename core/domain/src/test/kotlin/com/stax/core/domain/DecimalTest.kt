package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.math.MathContext

class DecimalTest {

    // parse

    @Test
    fun `parse creates Decimal from string`() {
        assertThat(Decimal.parse("0.25").toPlainString()).isEqualTo("0.25")
    }

    @Test
    fun `parse integer string`() {
        assertThat(Decimal.parse("100").toPlainString()).isEqualTo("100")
    }

    // toPlainString — trailing zero strip

    @Test
    fun `toPlainString strips trailing zeros`() {
        assertThat(Decimal.parse("0.250").toPlainString()).isEqualTo("0.25")
    }

    @Test
    fun `toPlainString strips all fractional trailing zeros`() {
        assertThat(Decimal.parse("1.000").toPlainString()).isEqualTo("1")
    }

    @Test
    fun `toPlainString no trailing zeros unchanged`() {
        assertThat(Decimal.parse("0.25").toPlainString()).isEqualTo("0.25")
    }

    // plus — acceptance criterion

    @Test
    fun `plus 0_25 + 0_1 equals 0_35`() {
        val result = Decimal.parse("0.25") + Decimal.parse("0.1")
        assertThat(result).isEqualTo(Decimal.parse("0.35"))
    }

    @Test
    fun `plus is commutative`() {
        val a = Decimal.parse("1.5")
        val b = Decimal.parse("2.3")
        assertThat((a + b).toPlainString()).isEqualTo((b + a).toPlainString())
    }

    // minus

    @Test
    fun `minus subtracts correctly`() {
        val result = Decimal.parse("1.00") - Decimal.parse("0.25")
        assertThat(result.compareTo(Decimal.parse("0.75"))).isEqualTo(0)
    }

    // times — use compareTo: multiply accumulates scale, BigDecimal.equals checks scale

    @Test
    fun `times multiplies correctly`() {
        val result = Decimal.parse("2.5") * Decimal.parse("4.0")
        assertThat(result.compareTo(Decimal.parse("10"))).isEqualTo(0)
    }

    @Test
    fun `times result numeric value`() {
        assertThat((Decimal.parse("0.5") * Decimal.parse("0.5")).toPlainString()).isEqualTo("0.25")
    }

    // div — acceptance criterion

    @Test
    fun `div 1_0 by 3_0 does not throw`() {
        val result = Decimal.parse("1.0") / Decimal.parse("3.0")
        assertThat(result.raw.precision()).isEqualTo(16)
    }

    @Test
    fun `div 1_0 by 3_0 returns DECIMAL64 HALF_EVEN result`() {
        val result = Decimal.parse("1.0") / Decimal.parse("3.0")
        val expected = java.math.BigDecimal("1.0").divide(
            java.math.BigDecimal("3.0"),
            MathContext.DECIMAL64,
        )
        assertThat(result.raw.compareTo(expected)).isEqualTo(0)
    }

    @Test
    fun `div exact quotient`() {
        val result = Decimal.parse("1.0") / Decimal.parse("4.0")
        assertThat(result.compareTo(Decimal.parse("0.25"))).isEqualTo(0)
    }

    // compareTo

    @Test
    fun `compareTo less than`() {
        assertThat(Decimal.parse("0.1") < Decimal.parse("0.2")).isTrue()
    }

    @Test
    fun `compareTo equal`() {
        assertThat(Decimal.parse("1.0").compareTo(Decimal.parse("1.0"))).isEqualTo(0)
    }

    @Test
    fun `compareTo greater than`() {
        assertThat(Decimal.parse("0.3") > Decimal.parse("0.1")).isTrue()
    }

    @Test
    fun `compareTo treats 1_0 and 1_00 as equal`() {
        assertThat(Decimal.parse("1.0").compareTo(Decimal.parse("1.00"))).isEqualTo(0)
    }

    // MATH constant

    @Test
    fun `MATH is DECIMAL64`() {
        assertThat(Decimal.MATH).isEqualTo(MathContext.DECIMAL64)
    }
}
