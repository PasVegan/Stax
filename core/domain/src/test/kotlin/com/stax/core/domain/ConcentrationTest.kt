package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

private val String.dec: Decimal get() = Decimal.parse(this)

class ConcentrationTest {

    private val twoPointFiveMgPerMl = Concentration(
        amount = Quantity("2.5".dec, UnitCode.MG),
        per = Quantity("1".dec, UnitCode.ML),
    )

    // toString — acceptance criterion

    @Test
    fun `toString returns amount slash per format`() {
        assertThat(twoPointFiveMgPerMl.toString()).isEqualTo("2.5 mg / 1 ml")
    }

    @Test
    fun `toString with capsule per`() {
        val c = Concentration(
            amount = Quantity("50".dec, UnitCode.MG),
            per = Quantity("1".dec, UnitCode.CAPSULE),
        )
        assertThat(c.toString()).isEqualTo("50 mg / 1 capsule")
    }

    @Test
    fun `toString strips trailing zeros`() {
        val c = Concentration(
            amount = Quantity("2.50".dec, UnitCode.MG),
            per = Quantity("1.0".dec, UnitCode.ML),
        )
        assertThat(c.toString()).isEqualTo("2.5 mg / 1 ml")
    }

    // structural equality

    @Test
    fun `same amount and per are equal`() {
        val a = Concentration(
            amount = Quantity("2.5".dec, UnitCode.MG),
            per = Quantity("1".dec, UnitCode.ML),
        )
        val b = Concentration(
            amount = Quantity("2.5".dec, UnitCode.MG),
            per = Quantity("1".dec, UnitCode.ML),
        )
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `different amount not equal`() {
        val a = Concentration(
            amount = Quantity("2.5".dec, UnitCode.MG),
            per = Quantity("1".dec, UnitCode.ML),
        )
        val b = Concentration(
            amount = Quantity("5".dec, UnitCode.MG),
            per = Quantity("1".dec, UnitCode.ML),
        )
        assertThat(a == b).isEqualTo(false)
    }

    @Test
    fun `different per not equal`() {
        val a = Concentration(
            amount = Quantity("2.5".dec, UnitCode.MG),
            per = Quantity("1".dec, UnitCode.ML),
        )
        val b = Concentration(
            amount = Quantity("2.5".dec, UnitCode.MG),
            per = Quantity("2".dec, UnitCode.ML),
        )
        assertThat(a == b).isEqualTo(false)
    }
}
