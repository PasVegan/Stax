package com.stax.feature.protocols.presentation.detail

/** Everything the user can do on Protocol Detail (§4.8). */
sealed interface ProtocolDetailAction {

    /** §4.8.1: the leading `arrow_back`, and the back gesture that means the same thing. */
    data object OnBackClick : ProtocolDetailAction

    /** §4.8.2: one chip that pauses a running protocol and resumes a paused one. */
    data object OnPauseClick : ProtocolDetailAction
    data object OnEditClick : ProtocolDetailAction
    data object OnDuplicateClick : ProtocolDetailAction

    /** §4.8.4: the linked compound sub-row → §4.3 Compound Detail. */
    data object OnCompoundClick : ProtocolDetailAction

    /** §4.8.8: "Show more" / "Show less" unfolds the notes in place. */
    data object OnToggleNotes : ProtocolDetailAction

    /** §4.8.7: a history row → §4.11 Administration Event detail. */
    data class OnHistoryEntryClick(val eventId: Long) : ProtocolDetailAction

    /** §4.8.9 dock: Log dose → §4.10.2-c, prefilled with this protocol. */
    data object OnLogDoseClick : ProtocolDetailAction

    /** §4.8.9 dock: Archive asks first, because it takes the protocol off every list (§5.5). */
    data object OnArchiveClick : ProtocolDetailAction
    data object OnArchiveConfirm : ProtocolDetailAction
    data object OnArchiveDismiss : ProtocolDetailAction
}
