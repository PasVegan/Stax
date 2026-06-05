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

    operator fun div(c: Concentration): Quantity {
        require(unit.family == c.amount.unit.family) {
            "Cannot divide $unit quantity by ${c.amount.unit}/${c.per.unit} concentration: " +
                "${unit.family} dose family does not match ${c.amount.unit.family} concentration family"
        }
        val amountValue = unit.convertTo(c.amount.unit, value)
        return Quantity((amountValue / c.amount.value) * c.per.value, c.per.unit)
    }

    override fun toString(): String = "${value.toPlainString()} ${unit.name.lowercase()}"
}
