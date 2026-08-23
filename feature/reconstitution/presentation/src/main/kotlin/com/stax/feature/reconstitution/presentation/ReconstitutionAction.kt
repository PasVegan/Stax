package com.stax.feature.reconstitution.presentation

import com.stax.core.domain.UnitCode

/** Everything the user can do in the Reconstitution Helper (§4.6). */
sealed interface ReconstitutionAction {

    /** §4.6.1: the leading `close`, and the back gesture that means the same thing. */
    data object OnCloseClick : ReconstitutionAction

    /** §4.6: the "Show calculation" row that unfolds Mix and the dose ladder. */
    data object OnToggleCalculation : ReconstitutionAction

    /** §4.6.4: the container tile, editable only in the standalone calculator. */
    data class OnContainerAmountChange(val value: String) : ReconstitutionAction
    data class OnContainerUnitSelected(val unit: UnitCode) : ReconstitutionAction

    /** §4.6.4: the two editable numbers the whole screen is derived from. */
    data class OnDiluentChange(val value: String) : ReconstitutionAction
    data class OnDesiredDoseChange(val value: String) : ReconstitutionAction
    data class OnDoseUnitSelected(val unit: UnitCode) : ReconstitutionAction

    /** §4.6.4 "Display": mL or insulin units. */
    data class OnDisplaySelected(val display: DoseDisplay) : ReconstitutionAction

    data class OnPickerClick(val picker: ReconstitutionPicker) : ReconstitutionAction
    data object OnPickerDismiss : ReconstitutionAction

    /** §4.6.7's dock. The write itself lands with M8-04. */
    data object OnSaveClick : ReconstitutionAction
}
