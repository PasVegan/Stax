package com.stax.feature.protocols.presentation.list

/** Everything the user can do on the Protocols list (§4.7). */
sealed interface ProtocolsListAction {

    /** Taps one of the four mutually exclusive Active / Paused / Completed / Archived chips (§4.7.2). */
    data class OnFilterClick(val filter: ProtocolFilter) : ProtocolsListAction

    /** Taps a protocol card — opens its detail (§4.7.3). */
    data class OnProtocolClick(val protocolId: Long) : ProtocolsListAction

    /** Taps the extended "New protocol" FAB, or the empty state's CTA (§4.7.5, §7). */
    data object OnCreateProtocolClick : ProtocolsListAction
}
