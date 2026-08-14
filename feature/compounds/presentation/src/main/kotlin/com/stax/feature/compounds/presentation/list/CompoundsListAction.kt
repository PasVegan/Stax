package com.stax.feature.compounds.presentation.list

import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm

/** Everything the user can do on the Compounds list (§4.2). */
sealed interface CompoundsListAction {

    /** Taps one of the mutually exclusive All / Low stock / Expiring soon chips (§4.2.2). */
    data class OnStatusFilterClick(val filter: CompoundStatusFilter) : CompoundsListAction

    /** Toggles one item of the Category multi-select menu (§4.2.2). */
    data class OnCategoryToggle(val category: CompoundCategory) : CompoundsListAction

    /** Toggles one item of the Form multi-select menu (§4.2.2). */
    data class OnFormToggle(val form: CompoundForm) : CompoundsListAction

    /** Taps the Category or Form chip, which opens its multi-select menu (§4.2.2). */
    data class OnFilterMenuOpen(val menu: CompoundFilterMenu) : CompoundsListAction

    /** Dismisses whichever filter menu is open (§4.2.2). */
    data object OnFilterMenuDismiss : CompoundsListAction

    /** Taps the app bar's leading `search` icon, which opens the search overlay (§4.2.1, §4.0.1). */
    data object OnSearchClick : CompoundsListAction

    /** Leaves the search overlay through its `arrow_back` icon or a back gesture (§4.0.1). */
    data object OnSearchDismiss : CompoundsListAction

    /** Types into — or clears — the search overlay's text field (§4.0.1). */
    data class OnSearchQueryChange(val query: String) : CompoundsListAction

    /** Taps a compound row — opens its detail, or toggles it while multi-select is on (§4.2.3). */
    data class OnCompoundClick(val compoundId: Long) : CompoundsListAction

    /** Taps the extended "Add" FAB (§4.2.5). */
    data object OnAddCompoundClick : CompoundsListAction

    /**
     * Everything multi-select mode adds (§4.2.4). Grouped because the mode is a mode: the ViewModel
     * dispatches the whole family to one handler, and the screen only ever raises these while the
     * contextual bar and dock are the ones on screen.
     */
    sealed interface Selection : CompoundsListAction {

        /** Long-presses a compound row, which enters multi-select mode (§4.2.3, §4.2.4). */
        data class OnLongPress(val compoundId: Long) : Selection

        /** Leaves multi-select through the contextual bar's `close` icon or a back gesture. */
        data object OnDismiss : Selection

        /** Taps the bottom dock's Duplicate button. */
        data object OnDuplicate : Selection

        /** Taps the bottom dock's Archive button, which asks for confirmation first. */
        data object OnArchiveClick : Selection

        /** Confirms the archive dialog. */
        data object OnArchiveConfirm : Selection

        /** Dismisses the archive dialog without archiving anything. */
        data object OnArchiveDismiss : Selection
    }
}
