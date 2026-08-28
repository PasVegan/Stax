package com.stax.feature.protocols.presentation.list

/** Everything the user can do on the Protocols list (§4.7). */
sealed interface ProtocolsListAction {

    /** Taps one of the four mutually exclusive Active / Paused / Completed / Archived chips (§4.7.2). */
    data class OnFilterClick(val filter: ProtocolFilter) : ProtocolsListAction

    /** Taps the app bar's leading `search` icon, which opens the search overlay (§4.7.1, §4.0.1). */
    data object OnSearchClick : ProtocolsListAction

    /** Leaves the search overlay through its `arrow_back` icon or a back gesture (§4.0.1). */
    data object OnSearchDismiss : ProtocolsListAction

    /** Types into — or clears — the search overlay's text field (§4.0.1). */
    data class OnSearchQueryChange(val query: String) : ProtocolsListAction

    /** Taps a protocol card — opens its detail, or toggles it while multi-select is on (§4.7.3). */
    data class OnProtocolClick(val protocolId: Long) : ProtocolsListAction

    /** Taps the extended "New protocol" FAB, or the empty state's CTA (§4.7.5, §7). */
    data object OnCreateProtocolClick : ProtocolsListAction

    /**
     * Everything multi-select mode adds (§4.7.4). Grouped because the mode is a mode: the ViewModel
     * dispatches the whole family to one handler, and the screen only ever raises these while the
     * contextual bar and dock are the ones on screen.
     */
    sealed interface Selection : ProtocolsListAction {

        /** Long-presses a protocol card, which enters multi-select mode (§4.7.3, §4.7.4). */
        data class OnLongPress(val protocolId: Long) : Selection

        /** Leaves multi-select through the contextual bar's `close` icon or a back gesture. */
        data object OnDismiss : Selection

        /** Opens the contextual bar's `more_vert` overflow. */
        data object OnMenuClick : Selection

        /** Dismisses the overflow without picking anything. */
        data object OnMenuDismiss : Selection

        /** Overflow "Select all" — every card the active tab is showing. */
        data object OnSelectAll : Selection

        /** Overflow "Invert" — swaps selected and unselected within the active tab. */
        data object OnInvert : Selection

        /** Taps the dock's Archive button, which asks for confirmation first. */
        data object OnArchiveClick : Selection

        /** Dismisses the archive dialog without archiving anything. */
        data object OnArchiveDismiss : Selection

        /**
         * The dock actions that write (§4.7.4). Separate from the bookkeeping above because they
         * share one path in the ViewModel: run over the compatible part of the selection, then leave
         * the mode.
         */
        sealed interface Batch : Selection {

            /** Taps the dock's Pause button. Applies to the selected Active protocols only. */
            data object OnPause : Batch

            /** Taps the dock's Resume button. Applies to the selected Paused protocols only. */
            data object OnResume : Batch

            /** Taps the dock's Complete button. Applies to whatever is not already Completed. */
            data object OnComplete : Batch

            /** Taps the dock's Duplicate button. */
            data object OnDuplicate : Batch

            /** Confirms the archive dialog. */
            data object OnArchiveConfirm : Batch
        }
    }
}
