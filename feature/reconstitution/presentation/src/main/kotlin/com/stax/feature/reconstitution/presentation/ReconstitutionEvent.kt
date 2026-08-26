package com.stax.feature.reconstitution.presentation

import com.stax.core.domain.Concentration
import com.stax.core.presentation.UiText

/**
 * One-time effects of the Reconstitution Helper (§4.6, §10.1). `ReconstitutionRoot` hands them to the
 * callbacks `:app` wired into the entry (§10.3).
 */
sealed interface ReconstitutionEvent {

    /** §4.6.1's `close`, and the compound the helper was opened on disappearing underneath it. */
    data object NavigateBack : ReconstitutionEvent

    /**
     * §4.6.7: the mix has been written to the compound, and the caller gets it back.
     *
     * The concentration travels with the event because "return to caller" means the Create / Edit
     * Compound form comes back with its field filled — and in the standalone calculator there is no
     * compound row for the form to read it from, only this.
     */
    data class Saved(val concentration: Concentration) : ReconstitutionEvent

    /** The write failed; the dock stays put and the snackbar says why. */
    data class ShowError(val message: UiText) : ReconstitutionEvent
}
