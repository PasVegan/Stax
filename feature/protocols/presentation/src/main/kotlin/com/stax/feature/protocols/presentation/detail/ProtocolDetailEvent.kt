package com.stax.feature.protocols.presentation.detail

import com.stax.core.presentation.UiText

/**
 * One-time effects of Protocol Detail (§4.8, §10.1). The ViewModel names the destination;
 * `ProtocolDetailRoot` hands it to the `:app` back stack (§10.3).
 */
sealed interface ProtocolDetailEvent {

    data object NavigateBack : ProtocolDetailEvent

    /** §4.8.2's Edit chip → the Edit Protocol form (§4.9). */
    data class NavigateToEditProtocol(val protocolId: Long) : ProtocolDetailEvent

    /** §4.8.4's sub-row → §4.3 Compound Detail. */
    data class NavigateToCompound(val compoundId: Long) : ProtocolDetailEvent

    /** §4.8.9 dock → §4.10.2-c Log Dose, carrying this protocol as the context to prefill from. */
    data class NavigateToLogDose(val protocolId: Long) : ProtocolDetailEvent

    /** §4.8.7 row → §4.11 Administration Event detail. */
    data class NavigateToAdministrationEvent(val eventId: Long) : ProtocolDetailEvent

    data class ShowError(val message: UiText) : ProtocolDetailEvent

    /** §4.8.2's Duplicate: the copy is made elsewhere on the list, so the screen says it happened. */
    data class ShowMessage(val message: UiText) : ProtocolDetailEvent
}
