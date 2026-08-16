package com.stax.feature.compounds.presentation.form

import com.stax.core.presentation.UiText

/** One-time effects of the Create / Edit Compound form (§10.1). */
sealed interface CompoundFormEvent {

    /**
     * The form is done with — saved, discarded, or skipped (§4.4.4). Where "done" goes is the
     * caller's business: the Compounds list normally, onboarding step 3 during the first run.
     */
    data object Done : CompoundFormEvent

    /** §4.4.3: open the Reconstitution Helper (§4.6) for the compound being edited, if it exists yet. */
    data class OpenReconstitutionHelper(val compoundId: Long?) : CompoundFormEvent

    data class ShowError(val message: UiText) : CompoundFormEvent

    /** §4.5.4: something happened and cannot be undone — the snackbar states it and offers nothing. */
    data class ShowMessage(val message: UiText) : CompoundFormEvent
}
