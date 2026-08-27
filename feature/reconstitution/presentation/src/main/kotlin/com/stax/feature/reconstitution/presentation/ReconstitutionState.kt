package com.stax.feature.reconstitution.presentation

import com.stax.core.domain.UnitCode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * §4.6.4 "Display": the unit the drawn dose is stated in.
 *
 * Insulin units are the U-100 standard — 100 units to the millilitre — which is what every insulin
 * syringe is graduated in regardless of its capacity (§4.6.2).
 */
enum class DoseDisplay { MILLILITRES, INSULIN_UNITS }

/**
 * §4.6.2's syringe sizes, in the order the size badge cycles them.
 *
 * An insulin syringe is graduated in the U-100 standard's units — 100 to the millilitre — so U-30,
 * U-50 and U-100 are 0.3, 0.5 and 1 mL of capacity under three different numbers. A regular syringe
 * is graduated in millilitres and simply says so.
 *
 * [minorCount] is how many intervals the graduation cuts the barrel into, not how many units it
 * counts: a U-100 barrel with a hundred ticks on it is a grey smear, so it ticks every two units and
 * numbers every fifty. [scaleMax] is the number at the far end — units on an insulin barrel,
 * millilitres on a regular one — and every [labelEvery] tick carries the figure it has reached.
 */
enum class SyringeSize(
    val isInsulin: Boolean,
    /** Capacity in millilitres, as the string [com.stax.core.domain.Decimal] parses for the fill. */
    val capacityMl: String,
    val scaleMax: Int,
    val minorCount: Int,
    val majorEvery: Int,
    val labelEvery: Int,
) {
    U30(isInsulin = true, capacityMl = "0.3", scaleMax = 30, minorCount = 30, majorEvery = 5, labelEvery = 10),
    U50(isInsulin = true, capacityMl = "0.5", scaleMax = 50, minorCount = 50, majorEvery = 5, labelEvery = 10),
    U100(isInsulin = true, capacityMl = "1", scaleMax = 100, minorCount = 50, majorEvery = 5, labelEvery = 25),
    ML2(isInsulin = false, capacityMl = "2", scaleMax = 2, minorCount = 20, majorEvery = 5, labelEvery = 10),
    ML3(isInsulin = false, capacityMl = "3", scaleMax = 3, minorCount = 30, majorEvery = 5, labelEvery = 10),
    ML5(isInsulin = false, capacityMl = "5", scaleMax = 5, minorCount = 25, majorEvery = 5, labelEvery = 5),
    ;

    /** §4.6.2: the badge cycles the six in order and wraps. */
    fun next(): SyringeSize = entries[(ordinal + 1) % entries.size]

    /**
     * The figure printed under tick [tick]. Every size divides evenly at its own [labelEvery], so the
     * numbers on the barrel are whole ones — "0 50 100", never "0 33.3 66.7".
     */
    fun graduationLabel(tick: Int): String = (scaleMax * tick / minorCount).toString()
}

/** The menus §4.6.4's tiles open. Only one is up at a time, so the open one is a single value. */
enum class ReconstitutionPicker { CONTAINER_UNIT, DOSE_UNIT, DISPLAY }

/**
 * UI state of the Reconstitution Helper (§4.6).
 *
 * The three inputs stay **strings**: they are what is in the fields, half-typed decimal points and
 * all, and turning them into [com.stax.core.domain.Decimal] is the ViewModel's job on every keystroke
 * (§2.3.1). Everything below them is derived from that pass and pre-rendered — the screen never
 * divides a quantity (§3.0.1).
 *
 * [compoundName] is null in the standalone calculator, which §4.4.3's Helper button opens from the
 * Create form when there is no compound to pre-select yet. That is also the only case where
 * [isContainerEditable] is true: with a stored compound the container is read-only (§4.6.4).
 *
 * [isCalculationExpanded] is state rather than a `remember`: whether §4.6's progressive disclosure is
 * unfolded is app state, and it survives the pane being resized underneath it.
 */
data class ReconstitutionState(
    val compoundName: String? = null,
    val containerAmount: String = "",
    val containerUnit: UnitCode = UnitCode.MG,
    val isContainerEditable: Boolean = true,
    val diluent: String = "",
    val desiredDose: String = "",
    val doseUnit: UnitCode = UnitCode.MG,
    val display: DoseDisplay = DoseDisplay.INSULIN_UNITS,
    /** §4.6.2: the syringe drawn under "Draw to", cycled by the size badge. */
    val syringeSize: SyringeSize = SyringeSize.U100,
    val doseUnitOptions: ImmutableList<UnitCode> = MASS_UNITS,
    val openPicker: ReconstitutionPicker? = null,
    val isCalculationExpanded: Boolean = false,
    /** §4.6.3: the drawn dose stated in every unit that names it, or null until the mix produces one. */
    val equivalence: DoseEquivalence? = null,
    /** §4.6.5: the dose rungs, computed off the typed dose. Empty until there is one to compute from. */
    val ladder: ImmutableList<DoseRung> = persistentListOf(),
    /** §4.6.2: how far up [syringeSize]'s barrel the dose reaches, `0f`..`1f`. */
    val syringeFill: Float = 0f,
    /** §4.6.6: how much active is in one millilitre, in [containerUnit]. */
    val concentration: String? = null,
    /** §4.6.6: whole doses of [desiredDose] one container yields. */
    val dosesPerContainer: Int? = null,
    /** §4.6.7: the write is in flight, so the dock takes no second tap. */
    val isSaving: Boolean = false,
) {
    /** §4.6.2 "Draw to": the dose as a volume or as insulin units, per [display]. */
    val drawTo: String? get() = when (display) {
        DoseDisplay.MILLILITRES -> equivalence?.volume
        DoseDisplay.INSULIN_UNITS -> equivalence?.units
    }

    /** §4.6.7: nothing to save until the mix actually produces a concentration. */
    val canSave: Boolean get() = concentration != null && !isSaving
}

/**
 * §4.6.3's chips: one dose, said three ways.
 *
 * [mass] is the dose as it was asked for, in [ReconstitutionState.doseUnit]; [volume] is what that
 * comes to in the syringe, and [units] the same volume on the U-100 graduation. They are one
 * computation apart, so they arrive together or not at all — a volume without its mass is a chip row
 * with a hole in it.
 */
data class DoseEquivalence(val mass: String, val volume: String, val units: String)

/**
 * One rung of §4.6.5's ladder.
 *
 * [dose] is both the figure on the pill and the string tapping it types into §4.6.4's Desired dose —
 * the ladder is a shortcut for that field, not a second source of truth. [equivalent] is what the
 * rung comes to under §4.6.4's Display ("10" units, "0.10" mL), and is null while the mix has no
 * concentration to convert through.
 */
data class DoseRung(val dose: String, val equivalent: String?, val isSelected: Boolean)

/**
 * The units a reconstituted container can be measured in — the standalone calculator's container
 * picker (§4.6.4).
 *
 * Reconstitution dissolves a solid into a volume, so the container is a mass or an IU count and never
 * a tablet: [UnitCode.ML] and the count units are left out because a compound already measured in
 * them has nothing to reconstitute (§4.6).
 */
val RECONSTITUTABLE_UNITS: ImmutableList<UnitCode> =
    persistentListOf(UnitCode.MCG, UnitCode.MG, UnitCode.G, UnitCode.IU)

/** The dose picker offers only the container's own family — a dose in IU of a mg vial is not a dose. */
val MASS_UNITS: ImmutableList<UnitCode> = persistentListOf(UnitCode.MCG, UnitCode.MG, UnitCode.G)
val IU_UNITS: ImmutableList<UnitCode> = persistentListOf(UnitCode.IU)
