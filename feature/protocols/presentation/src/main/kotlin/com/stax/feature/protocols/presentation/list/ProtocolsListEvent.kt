package com.stax.feature.protocols.presentation.list

import com.stax.core.presentation.UiText

/**
 * One-time effects of the Protocols list (§4.7, §10.1). The ViewModel names the destination;
 * `ProtocolsListRoot` hands it to the `:app` back stack.
 */
sealed interface ProtocolsListEvent {

    /** Card tap → Protocol Detail (§4.7.3, §4.8). */
    data class NavigateToProtocolDetail(val protocolId: Long) : ProtocolsListEvent

    /** FAB tap → Create Protocol (§4.7.5, §4.9). */
    data object NavigateToCreateProtocol : ProtocolsListEvent

    /**
     * A multi-select action failed part-way (§4.7.4). Success is silent — the list updating under
     * the user is the confirmation — but a write that did not land has to say so, or the card simply
     * stays put with no explanation.
     */
    data class ShowError(val message: UiText) : ProtocolsListEvent
}
