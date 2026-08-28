package com.stax.feature.protocols.presentation.list

/** Everything the user can do on the Protocols list (§4.7). */
sealed interface ProtocolsListAction {

    /** Taps one of the four mutually exclusive Active / Paused / Completed / Archived chips (§4.7.2). */
    data class OnFilterClick(val filter: ProtocolFilter) : ProtocolsListAction

    /** Taps the app bar's leading `search` icon, which opens the search overlay (§4.7.1, §4.0.1). */
    data object OnSearchClick : ProtocolsListAction

    /** Leaves the search overlay through its `arrow_back` icon or a back gesture (§4.0.1). */
    data object OnSearchDismiss : ProtocolsListAction

    /** Types into — or clears — the search overlay's text field (§4.0.1). */
    data class OnSearchQueryChange(val query: String) : ProtocolsListAction

    /** Taps a protocol card — opens its detail (§4.7.3). */
    data class OnProtocolClick(val protocolId: Long) : ProtocolsListAction

    /** Taps the extended "New protocol" FAB, or the empty state's CTA (§4.7.5, §7). */
    data object OnCreateProtocolClick : ProtocolsListAction
}
