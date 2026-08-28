package com.stax.feature.protocols.presentation.form

import com.stax.core.domain.BodyRegion
import com.stax.core.domain.ReminderBucket
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Everything the user can do on the Create / Edit Protocol form (§4.9). */
sealed interface ProtocolFormAction {

    /** The fields that carry a typed or toggled value. */
    sealed interface Edit : ProtocolFormAction {
        data class OnDoseChange(val value: String) : Edit

        /** The count the selected schedule chip owns — "every N days", "N×/week", … (§4.9.3). */
        data class OnScheduleCountChange(val value: String) : Edit
        data class OnNotesChange(val value: String) : Edit
        data class OnReminderToggle(val enabled: Boolean) : Edit
    }

    /** The choices: chips, segments and the things a picker returns. */
    sealed interface Pick : ProtocolFormAction {
        data class OnCompoundSelected(val compoundId: Long) : Pick
        data class OnRouteSelected(val route: Route) : Pick
        data class OnDoseUnitSelected(val unit: UnitCode) : Pick
        data class OnScheduleTypeSelected(val type: ScheduleType) : Pick

        /** The 7-day circle picker toggles one weekday at a time (§4.9.3). */
        data class OnWeekdayToggled(val day: DayOfWeek) : Pick
        data class OnReminderBucketSelected(val bucket: ReminderBucket) : Pick

        /** Null clears the restriction back to "No restriction" (§4.9.3). */
        data class OnBodyRegionSelected(val region: BodyRegion?) : Pick
    }

    /** Times of day (§4.9.3): the "Add time" pill adds one, tapping a pill removes it. */
    data object OnAddTimeClick : ProtocolFormAction
    data class OnTimeSelected(val time: LocalTime) : ProtocolFormAction
    data object OnTimePickerDismiss : ProtocolFormAction
    data class OnTimeRemoved(val time: LocalTime) : ProtocolFormAction

    /**
     * The transient surfaces: the two pickers, the date fields and the prompts. Grouped because they
     * are all the same kind of statement — "this opened" or "this closed".
     */
    sealed interface Overlay : ProtocolFormAction {
        data class OnPickerOpen(val picker: ProtocolFormPicker) : Overlay
        data object OnPickerDismiss : Overlay
        data class OnPickerQueryChange(val query: String) : Overlay

        data class OnDateFieldClick(val field: ProtocolDateField) : Overlay
        data class OnDateSelected(val date: LocalDate?) : Overlay
        data object OnDatePickerDismiss : Overlay

        data object OnDiscardDismiss : Overlay
        data object OnArchiveDismiss : Overlay
    }

    /** §4.0.2's empty state: there is nothing to pick, so the sheet offers the way to make one. */
    data object OnAddCompoundClick : ProtocolFormAction

    /** Consumed once the screen has brought the first failing field into view. */
    data object OnErrorScrollHandled : ProtocolFormAction

    data object OnSaveClick : ProtocolFormAction

    /** §4.9.5 Lifecycle, Edit mode only. */
    data object OnPauseClick : ProtocolFormAction
    data object OnDuplicateClick : ProtocolFormAction
    data object OnArchiveClick : ProtocolFormAction
    data object OnArchiveConfirm : ProtocolFormAction

    /** The app bar's leading icon and Cancel, plus the back gesture — all confirm first when dirty. */
    data object OnCancelClick : ProtocolFormAction
    data object OnDiscardConfirm : ProtocolFormAction

    /** Onboarding step 3 only (§4.14): leaves the step without saving. */
    data object OnSkipClick : ProtocolFormAction
}
