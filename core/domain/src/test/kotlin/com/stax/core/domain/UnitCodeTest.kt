package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

private val Int.dec: Decimal get() = Decimal.parse(this.toString())
private val String.dec: Decimal get() = Decimal.parse(this)

class UnitCodeTest {

    // family property

    @Test
    fun `MCG family is MASS`() = assertThat(UnitCode.MCG.family).isEqualTo(UnitFamily.MASS)

    @Test
    fun `MG family is MASS`() = assertThat(UnitCode.MG.family).isEqualTo(UnitFamily.MASS)

    @Test
    fun `G family is MASS`() = assertThat(UnitCode.G.family).isEqualTo(UnitFamily.MASS)

    @Test
    fun `IU family is IU`() = assertThat(UnitCode.IU.family).isEqualTo(UnitFamily.IU)

    @Test
    fun `ML family is VOLUME`() = assertThat(UnitCode.ML.family).isEqualTo(UnitFamily.VOLUME)

    @ParameterizedTest
    @EnumSource(UnitCode::class, names = ["CAPSULE", "TABLET", "SCOOP", "DROP"])
    fun `count units family is COUNT`(unit: UnitCode) {
        assertThat(unit.family).isEqualTo(UnitFamily.COUNT)
    }

    // convertTo — identity

    @ParameterizedTest
    @EnumSource(UnitCode::class)
    fun `convertTo same unit returns value unchanged`(unit: UnitCode) {
        assertThat(unit.convertTo(unit, "1.5".dec)).isEqualTo("1.5".dec)
    }

    // convertTo — mass conversions (acceptance criterion)

    @Test
    fun `MG to MCG 1mg equals 1000mcg`() {
        val result = UnitCode.MG.convertTo(UnitCode.MCG, 1.dec)
        assertThat(result.compareTo("1000".dec)).isEqualTo(0)
    }

    @Test
    fun `G to MG 1g equals 1000mg`() {
        val result = UnitCode.G.convertTo(UnitCode.MG, 1.dec)
        assertThat(result.compareTo("1000".dec)).isEqualTo(0)
    }

    @Test
    fun `G to MCG 1g equals 1000000mcg`() {
        val result = UnitCode.G.convertTo(UnitCode.MCG, 1.dec)
        assertThat(result.compareTo("1000000".dec)).isEqualTo(0)
    }

    @Test
    fun `MCG to MG 1000mcg equals 1mg`() {
        val result = UnitCode.MCG.convertTo(UnitCode.MG, "1000".dec)
        assertThat(result.compareTo(1.dec)).isEqualTo(0)
    }

    @Test
    fun `MCG to G 1000000mcg equals 1g`() {
        val result = UnitCode.MCG.convertTo(UnitCode.G, "1000000".dec)
        assertThat(result.compareTo(1.dec)).isEqualTo(0)
    }

    @Test
    fun `MG to G 500mg equals 0_5g`() {
        val result = UnitCode.MG.convertTo(UnitCode.G, "500".dec)
        assertThat(result.compareTo("0.5".dec)).isEqualTo(0)
    }

    // convertTo — COUNT throws (acceptance criterion)

    @Test
    fun `CAPSULE to TABLET throws`() {
        assertThrows<IllegalArgumentException> {
            UnitCode.CAPSULE.convertTo(UnitCode.TABLET, 1.dec)
        }
    }

    @Test
    fun `TABLET to SCOOP throws`() {
        assertThrows<IllegalArgumentException> {
            UnitCode.TABLET.convertTo(UnitCode.SCOOP, 1.dec)
        }
    }

    @Test
    fun `SCOOP to DROP throws`() {
        assertThrows<IllegalArgumentException> {
            UnitCode.SCOOP.convertTo(UnitCode.DROP, 1.dec)
        }
    }

    // convertTo — cross-family throws

    @Test
    fun `MCG to ML throws cross-family`() {
        assertThrows<IllegalArgumentException> {
            UnitCode.MCG.convertTo(UnitCode.ML, 1.dec)
        }
    }

    @Test
    fun `MG to IU throws cross-family`() {
        assertThrows<IllegalArgumentException> {
            UnitCode.MG.convertTo(UnitCode.IU, 1.dec)
        }
    }

    @Test
    fun `ML to CAPSULE throws cross-family`() {
        assertThrows<IllegalArgumentException> {
            UnitCode.ML.convertTo(UnitCode.CAPSULE, 1.dec)
        }
    }

    @Test
    fun `IU to MG throws cross-family`() {
        assertThrows<IllegalArgumentException> {
            UnitCode.IU.convertTo(UnitCode.MG, 1.dec)
        }
    }
}
