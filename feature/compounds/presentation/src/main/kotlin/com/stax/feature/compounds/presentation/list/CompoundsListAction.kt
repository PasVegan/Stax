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

    /** Taps a compound row (§4.2.3). */
    data class OnCompoundClick(val compoundId: Long) : CompoundsListAction

    /** Taps the extended "Add" FAB (§4.2.5). */
    data object OnAddCompoundClick : CompoundsListAction
}
