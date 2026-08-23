package com.stax.feature.compounds.presentation.detail

import com.stax.feature.compounds.presentation.container.OpenedContainerSheetAction

/** Everything the user can do on Compound Detail (§4.3). */
sealed interface CompoundDetailAction {

    /** §4.3.1: the leading `arrow_back`, and the back gesture that means the same thing. */
    data object OnBackClick : CompoundDetailAction

    /** §4.3.3: the opened vial card's "Edit", which opens the §4.5 sheet on the stored container. */
    data object OnOpenedContainerClick : CompoundDetailAction

    /** §4.3.4: a protocol sub-row → §4.8 Protocol Detail. */
    data class OnProtocolClick(val protocolId: Long) : CompoundDetailAction

    /** §4.3.5: "Show more" / "Show less" unfolds the notes in place. */
    data object OnToggleNotes : CompoundDetailAction

    data class OnHistoryFilterClick(val filter: HistoryStatusFilter) : CompoundDetailAction

    /** §4.3.8: a history row → §4.11 Administration Event detail. */
    data class OnHistoryEntryClick(val eventId: Long) : CompoundDetailAction

    /** §4.3.9 dock: Log dose → §4.10.2-b, Adjust → the Edit Compound form. */
    data object OnLogDoseClick : CompoundDetailAction
    data object OnAdjustClick : CompoundDetailAction

    /** The §4.5 sheet, hosted here as a mode of this screen rather than as a destination (§10.3). */
    data class OpenedContainerSheet(val action: OpenedContainerSheetAction) : CompoundDetailAction

    /** §4.5.5: an emptied container asks whether to open the next one. */
    data class OnNaturalDepletionDecision(val openNew: Boolean) : CompoundDetailAction
}
