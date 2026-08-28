package com.stax.feature.protocols.presentation.list

/**
 * One-time effects of the Protocols list (§4.7, §10.1). The ViewModel names the destination;
 * `ProtocolsListRoot` hands it to the `:app` back stack.
 */
sealed interface ProtocolsListEvent {

    /** Card tap → Protocol Detail (§4.7.3, §4.8). */
    data class NavigateToProtocolDetail(val protocolId: Long) : ProtocolsListEvent

    /** FAB tap → Create Protocol (§4.7.5, §4.9). */
    data object NavigateToCreateProtocol : ProtocolsListEvent
}
