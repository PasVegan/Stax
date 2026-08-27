package com.stax.feature.protocols.presentation.form

import com.stax.core.presentation.UiText

/** One-time effects of the Create / Edit Protocol form (§10.1). */
sealed interface ProtocolFormEvent {

    /**
     * The form is done with — saved, discarded, skipped, or ended through the Lifecycle section
     * (§4.9.5). Where "done" goes is the caller's business: the Protocols list normally, the end of
     * onboarding during the first run (§4.14 step 3).
     */
    data object Done : ProtocolFormEvent

    /** §4.0.2's empty picker: there are no compounds yet, so the sheet offers Create Compound (§4.4). */
    data object OpenCreateCompound : ProtocolFormEvent

    data class ShowError(val message: UiText) : ProtocolFormEvent
}
