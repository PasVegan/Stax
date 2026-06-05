package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private val Int.dec: Decimal get() = Decimal.parse(this.toString())
private val String.dec: Decimal get() = Decimal.parse(this)

data class QuantityDivisionCase(
    val dose: Quantity,
    val concentration: Concentration,
    val expected: Quantity?,
    val expectedErrorMessage: String? = null,
)

private fun q(value: String, unit: UnitCode): Quantity = Quantity(value.dec, unit)

private fun c(amountValue: String, amountUnit: UnitCode, perValue: String, perUnit: UnitCode): Concentration =
    Concentration(
        amount = q(amountValue, amountUnit),
        per = q(perValue, perUnit),
    )

private fun ok(dose: Quantity, concentration: Concentration, expected: Quantity): QuantityDivisionCase =
    QuantityDivisionCase(dose, concentration, expected)

private fun fail(dose: Quantity, concentration: Concentration, expectedErrorMessage: String): QuantityDivisionCase =
    QuantityDivisionCase(dose, concentration, expected = null, expectedErrorMessage)

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

    // div concentration

    @ParameterizedTest
    @MethodSource("divisionCases")
    fun `div concentration converts compatible amount units and returns per unit`(case: QuantityDivisionCase) {
        if (case.expectedErrorMessage != null) {
            val error = assertThrows<IllegalArgumentException> {
                case.dose / case.concentration
            }
            assertThat(error.message).isEqualTo(case.expectedErrorMessage)
            return
        }

        val result = case.dose / case.concentration

        requireNotNull(case.expected)
        assertThat(result.value.compareTo(case.expected.value)).isEqualTo(0)
        assertThat(result.unit).isEqualTo(case.expected.unit)
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

    companion object {
        @JvmStatic
        fun divisionCases(): Stream<QuantityDivisionCase> = Stream.of(
            ok(q("0.25", UnitCode.MG), c("2.5", UnitCode.MG, "1", UnitCode.ML), q("0.10", UnitCode.ML)),
            ok(q("1", UnitCode.IU), c("100", UnitCode.IU, "1", UnitCode.ML), q("0.01", UnitCode.ML)),
            ok(q("500", UnitCode.MCG), c("1", UnitCode.MG, "1", UnitCode.ML), q("0.5", UnitCode.ML)),
            ok(q("1", UnitCode.G), c("250", UnitCode.MG, "1", UnitCode.ML), q("4", UnitCode.ML)),
            ok(q("2500", UnitCode.MCG), c("2.5", UnitCode.MG, "1", UnitCode.ML), q("1", UnitCode.ML)),
            ok(q("10", UnitCode.MG), c("5", UnitCode.MG, "2", UnitCode.ML), q("4", UnitCode.ML)),
            ok(q("2", UnitCode.CAPSULE), c("1", UnitCode.CAPSULE, "1", UnitCode.ML), q("2", UnitCode.ML)),
            ok(q("3", UnitCode.TABLET), c("1", UnitCode.TABLET, "1", UnitCode.ML), q("3", UnitCode.ML)),
            ok(q("4", UnitCode.SCOOP), c("2", UnitCode.SCOOP, "1", UnitCode.ML), q("2", UnitCode.ML)),
            fail(
                q("1", UnitCode.MG),
                c("1", UnitCode.IU, "1", UnitCode.ML),
                "Cannot divide MG quantity by IU/ML concentration: " +
                    "MASS dose family does not match IU concentration family",
            ),
            fail(
                q("1", UnitCode.ML),
                c("1", UnitCode.MG, "1", UnitCode.ML),
                "Cannot divide ML quantity by MG/ML concentration: " +
                    "VOLUME dose family does not match MASS concentration family",
            ),
        )
    }
}
