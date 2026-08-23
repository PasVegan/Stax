package com.stax.feature.reconstitution.presentation

/**
 * One-time effects of the Reconstitution Helper (§4.6, §10.1). `ReconstitutionRoot` hands them to the
 * callbacks `:app` wired into the entry (§10.3).
 */
sealed interface ReconstitutionEvent {

    /** §4.6.1's `close`, and §4.6.7's "return to caller" once M8-04 has written the concentration. */
    data object NavigateBack : ReconstitutionEvent
}
