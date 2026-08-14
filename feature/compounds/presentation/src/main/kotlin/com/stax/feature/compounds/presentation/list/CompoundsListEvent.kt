package com.stax.feature.compounds.presentation.list

/**
 * One-time effects of the Compounds list (§4.2, §10.1). The ViewModel names the destination;
 * `CompoundsListRoot` (M7-02) hands it to the `:app` back stack.
 */
sealed interface CompoundsListEvent {

    /** Row tap → Compound Detail (§4.2.3, §4.3). */
    data class NavigateToCompoundDetail(val compoundId: Long) : CompoundsListEvent

    /** FAB tap → Create Compound (§4.2.5, §4.4). */
    data object NavigateToCreateCompound : CompoundsListEvent
}
