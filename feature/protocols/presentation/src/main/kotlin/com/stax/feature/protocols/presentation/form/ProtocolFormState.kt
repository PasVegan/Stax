package com.stax.feature.protocols.presentation.form

import androidx.compose.runtime.Immutable
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.ContainerType
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.UnitCode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate

/**
 * A compound as the Compound card and the Compound picker show it (§4.9.3, §4.0.2): the name over
 * `{category} · {amountPerContainer}{unit} {containerType} · {concentration}`, pre-rendered by the
 * ViewModel so the screen never formats a `Quantity`.
 */
@Immutable
data class CompoundPickUi(
    val id: Long,
    val name: String,
    val category: CompoundCategory,
    val containerType: ContainerType,
    val amount: String,
    val amountUnit: UnitCode,
    val concentration: String? = null,
    val concentrationUnit: UnitCode? = null,
    val concentrationPerUnit: UnitCode? = null,
)

/**
 * §4.9.3's equivalence chip: what the planned dose comes to in volume, and — for a millilitre
 * concentration — in insulin units. Null whenever the compound has no concentration to derive it from.
 */
@Immutable
data class DoseEquivalenceUi(val volume: String, val volumeUnit: UnitCode, val insulinUnits: Int?)

/** One day of the 11b next-7-days strip: the date it stands for, and whether the schedule doses on it. */
@Immutable
data class PreviewDayUi(val date: LocalDate, val hasDose: Boolean, val isToday: Boolean)

/**
 * 11b's "Next 7 days · 2 doses" strip. It is the same horizon the save actually generates
 * (`SCHEDULE_HORIZON_DAYS`, §5.2), read through the domain schedule rule — so what it draws is what
 * Save will write, not a second implementation of the same rule.
 */
@Immutable
data class SchedulePreviewUi(val doseCount: Int, val days: ImmutableList<PreviewDayUi>)

/** §4.9.3's warning row: the batch runs out of shelf life before the protocol runs out of stock. */
@Immutable
data class ExpiryWarningUi(val batchExpiry: LocalDate, val runOut: LocalDate)

/** 11b's reorder row: how much more stock the protocol needs to reach its end date, and by when. */
@Immutable
data class ReorderHintUi(
    val containers: Int,
    val containerType: ContainerType,
    val orderBy: LocalDate,
    val coversUntil: LocalDate,
)

/**
 * §4.9.3's live Forecast & warnings card. Derived from the picked compound's stock and the form's own
 * schedule, so it updates as the user types — no save, no round trip.
 *
 * [daysLeft] and [runOutDate] are null when the stock outlives the forecast horizon: a 2×/month
 * protocol with a full tub does not run out inside any window worth walking, and "3 years left" is
 * not a number anyone acts on.
 */
@Immutable
data class ProtocolForecastUi(
    val dosesLeft: Int,
    val daysLeft: Int?,
    val runOutDate: LocalDate?,
    val expiryWarning: ExpiryWarningUi?,
    val reorder: ReorderHintUi?,
)

/**
 * UI state of the Create / Edit Protocol form (§4.9).
 *
 * Split like the compound form's (§4.4): [draft] is what the user typed and is the part that is
 * auto-saved, while everything around it — which sheet is open, what failed validation — is transient
 * screen state that is meaningless to restore.
 *
 * [errors] is empty until Save is tapped: a field the user has not finished typing is never marked
 * wrong. [scrollToError] is the first failing field, which the screen brings into view.
 */
@Immutable
data class ProtocolFormState(
    val draft: ProtocolFormDraft = ProtocolFormDraft(),
    /** Edit mode (§4.9.1/§4.9.2): warning banner, Lifecycle section, and Save updates instead of inserting. */
    val isEdit: Boolean = false,
    /** Onboarding step 3 (§4.14): the app bar reads "Create your first protocol · 3 of 3" + Skip. */
    val isOnboarding: Boolean = false,
    /** The stored protocol's name, for the Edit app bar's supporting line. */
    val editedProtocolName: String = "",
    /** The picked compound, as the §4.9.3 card shows it. Null until one is picked. */
    val compound: CompoundPickUi? = null,
    /** Every compound the picker can offer, already filtered by [pickerQuery] (§4.0.2). */
    val pickerCompounds: ImmutableList<CompoundPickUi> = persistentListOf(),
    /** §4.0.2 shows the search field only past five rows, counted before the query narrows them. */
    val isPickerSearchable: Boolean = false,
    val pickerQuery: String = "",
    /** The units the dose pill offers — the compound's own family, so a vial of mg never offers tablets. */
    val doseUnitOptions: ImmutableList<UnitCode> = persistentListOf(),
    val equivalence: DoseEquivalenceUi? = null,
    val preview: SchedulePreviewUi? = null,
    val forecast: ProtocolForecastUi? = null,
    /** §4.9.3's reminder card states the global notification style (§4.13.3), so it is read, not assumed. */
    val notificationStyle: NotificationStyle = NotificationStyle.NORMAL,
    val errors: ImmutableMap<ProtocolFormField, ProtocolFormError> = persistentMapOf(),
    val scrollToError: ProtocolFormField? = null,
    val openPicker: ProtocolFormPicker? = null,
    val openDateField: ProtocolDateField? = null,
    val isTimePickerOpen: Boolean = false,
    val isDiscardDialogOpen: Boolean = false,
    /** §4.9.6: Pause on a form with unsaved changes asks what to do with them first. */
    val isPauseDialogOpen: Boolean = false,
    /** §4.9.5: Archive is a soft-delete, and it still asks first. */
    val isArchiveDialogOpen: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    /** Whether the form differs from what it was loaded with — the discard dialog's trigger. */
    val isDirty: Boolean = false,
) {
    /**
     * §4.9.3: the reminder bucket chips replace the per-time alarms exactly when there is no time of
     * day to hang an alarm off — an empty `dosageTimes` with reminders on has to fire at *some* fixed
     * hour, and the buckets are it.
     */
    val isReminderBucketVisible: Boolean
        get() = draft.reminderEnabled && draft.dosageTimes.isEmpty()
}
