package com.stax.core.domain

enum class UnitCode(val family: UnitFamily) {
    MCG(UnitFamily.MASS),
    MG(UnitFamily.MASS),
    G(UnitFamily.MASS),
    IU(UnitFamily.IU),
    ML(UnitFamily.VOLUME),
    CAPSULE(UnitFamily.COUNT),
    TABLET(UnitFamily.COUNT),
    SCOOP(UnitFamily.COUNT),
    DROP(UnitFamily.COUNT),
    ;

    /**
     * Converts [value] expressed in this unit to [target].
     *
     * Mass conversions are fully supported (1 g = 1 000 mg = 1 000 000 mcg).
     * Count units (CAPSULE, TABLET, SCOOP, DROP) are atomic — conversion always throws.
     * VOLUME (ML) and IU each have a single unit, so same-unit identity is the only
     * valid call and is handled by the [this] == [target] short-circuit.
     *
     * @throws IllegalArgumentException on cross-family or COUNT-within-COUNT conversion.
     */
    fun convertTo(target: UnitCode, value: Decimal): Decimal {
        if (this == target) return value
        require(family == target.family) {
            "Cannot convert between families: $family to ${target.family}"
        }
        return when (family) {
            UnitFamily.MASS -> value * mcgFactor() / target.mcgFactor()

            UnitFamily.COUNT -> throw IllegalArgumentException(
                "Count units are atomic: cannot convert $this to $target",
            )

            else -> value // unreachable: VOLUME and IU each have only one member
        }
    }

    private fun mcgFactor(): Decimal = when (this) {
        MCG -> Decimal.parse("1")
        MG -> Decimal.parse("1000")
        G -> Decimal.parse("1000000")
        else -> error("$this is not a mass unit")
    }
}
