package com.stax.feature.compounds.presentation.detail

import com.stax.core.presentation.UiText

/**
 * One-time effects of Compound Detail (§4.3, §10.1). The ViewModel names the destination;
 * `CompoundDetailRoot` hands it to the `:app` back stack (§10.3).
 */
sealed interface CompoundDetailEvent {

    data object NavigateBack : CompoundDetailEvent

    /** §4.3.4 sub-row → §4.8 Protocol Detail. */
    data class NavigateToProtocol(val protocolId: Long) : CompoundDetailEvent

    /** §4.3.8 row → §4.11 Administration Event detail. */
    data class NavigateToAdministrationEvent(val eventId: Long) : CompoundDetailEvent

    /** §4.3.9 dock → §4.10.2-b Log Dose, pre-selecting this compound. */
    data class NavigateToLogDose(val compoundId: Long) : CompoundDetailEvent

    /** §4.3.9 dock → the Edit Compound form, which is where stock is adjusted (§4.4.3). */
    data class NavigateToEditCompound(val compoundId: Long) : CompoundDetailEvent

    data class ShowError(val message: UiText) : CompoundDetailEvent

    /** §4.5.4: something happened and cannot be undone — the snackbar states it and offers nothing. */
    data class ShowMessage(val message: UiText) : CompoundDetailEvent
}
