package com.stax.core.domain

data class Quantity(val value: Decimal, val unit: UnitCode) {

    operator fun plus(o: Quantity): Quantity {
        require(unit == o.unit) { "Cannot add quantities with different units: $unit and ${o.unit}" }
        return Quantity(value + o.value, unit)
    }

    operator fun minus(o: Quantity): Quantity {
        require(unit == o.unit) {
            "Cannot subtract quantities with different units: $unit and ${o.unit}"
        }
        return Quantity(value - o.value, unit)
    }

    operator fun times(scalar: Decimal): Quantity = Quantity(value * scalar, unit)

    override fun toString(): String = "${value.toPlainString()} ${unit.name.lowercase()}"
}
