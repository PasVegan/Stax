package com.stax.core.domain

import java.math.BigDecimal
import java.math.MathContext

@JvmInline
value class Decimal(val raw: BigDecimal) : Comparable<Decimal> {
    operator fun plus(o: Decimal): Decimal = Decimal(raw.add(o.raw))
    operator fun minus(o: Decimal): Decimal = Decimal(raw.subtract(o.raw))
    operator fun times(o: Decimal): Decimal = Decimal(raw.multiply(o.raw))
    operator fun div(o: Decimal): Decimal = Decimal(raw.divide(o.raw, MATH))
    operator fun unaryMinus(): Decimal = Decimal(raw.negate())
    override operator fun compareTo(other: Decimal): Int = raw.compareTo(other.raw)
    fun toPlainString(): String = raw.stripTrailingZeros().toPlainString()

    companion object {
        val MATH: MathContext = MathContext.DECIMAL64 // HALF_EVEN, 16 digits
        fun parse(s: String): Decimal = Decimal(BigDecimal(s))
    }
}
