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
    val doseUnitOptions: ImmutableList<UnitCode> = MASS_UNITS,
    val openPicker: ReconstitutionPicker? = null,
    val isCalculationExpanded: Boolean = false,
    /** §4.6.2 "Draw to": the dose as a volume or as insulin units, per [display]. */
    val drawTo: String? = null,
    /** §4.6.6: how much active is in one millilitre, in [containerUnit]. */
    val concentration: String? = null,
    /** §4.6.6: whole doses of [desiredDose] one container yields. */
    val dosesPerContainer: Int? = null,
) {
    /** §4.6.7: nothing to save until the mix actually produces a concentration. */
    val canSave: Boolean get() = concentration != null
}

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
