package com.stax.feature.compounds.presentation.list

import com.stax.core.presentation.UiText

/**
 * One-time effects of the Compounds list (§4.2, §10.1). The ViewModel names the destination;
 * `CompoundsListRoot` (M7-02) hands it to the `:app` back stack.
 */
sealed interface CompoundsListEvent {

    /** Row tap → Compound Detail (§4.2.3, §4.3). */
    data class NavigateToCompoundDetail(val compoundId: Long) : CompoundsListEvent

    /** FAB tap → Create Compound (§4.2.5, §4.4). */
    data object NavigateToCreateCompound : CompoundsListEvent

    /**
     * A multi-select action failed part-way (§4.2.4). Success is silent — §4.2.4 rules out the undo
     * snackbar, and the list updating under the user is the confirmation — but a write that did not
     * land has to say so, or the row simply stays put with no explanation.
     */
    data class ShowError(val message: UiText) : CompoundsListEvent
}
