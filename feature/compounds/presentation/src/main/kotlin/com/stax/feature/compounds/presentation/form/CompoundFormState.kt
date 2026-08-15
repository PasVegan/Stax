package com.stax.feature.compounds.presentation.form

import androidx.compose.runtime.Immutable
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.ContainerType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * The opened container summary card of §4.4.3, laid out like the Compound Detail opened vial card
 * (§4.3.3): "3.2 / 5.0 mg remaining" over a progress track, with how long ago it was opened.
 *
 * Pre-rendered by the ViewModel — the screen never divides quantities. [fillFraction] is display
 * geometry for the progress track, not dose math, which is why it is the one `Float` here (§3.0.1).
 */
@Immutable
data class OpenedContainerUi(
    val containerType: ContainerType,
    val remaining: String,
    val capacity: String,
    val unit: String,
    val fillFraction: Float,
    val openedDaysAgo: Int,
)

/**
 * The live stock preview of the wide layouts (§6.4.2 Create / Edit Compound, right column).
 *
 * Derived from the form's own fields alone — no repository, no protocol — so it updates as the user
 * types. Null whenever the numbers it needs are missing or half-typed.
 */
@Immutable
data class StockForecastUi(
    val totalStock: String,
    val containers: Int,
    /** Volume one container makes up to at the entered concentration, e.g. "2 ml". */
    val volumePerContainer: String?,
)

/**
 * The §4.4.4 Edit-case prompt: the new container size no longer holds what is left in the opened one.
 *
 * Both amounts arrive already carrying their unit ("3.2 mg"), because the same edit that shrank the
 * container may also have changed the unit — "3.2 / 2 mg" would then be two different measurements
 * printed as one.
 */
@Immutable
data class ContainerShrinkPromptUi(val remaining: String, val newAmount: String)

/** §4.4.4: what the user chose to do about a container that shrank below its opened contents. */
enum class ContainerShrinkDecision {
    /** Leave the opened container as it is — remaining above the new size is allowed. */
    KEEP,

    /** Clamp the remaining amount to the new size and book the difference in the ledger. */
    CAP,

    /** Put the amount back to what it was and save nothing. */
    CANCEL,
}

/**
 * UI state of the Create / Edit Compound form (§4.4).
 *
 * Split in two on purpose: [draft] is what the user typed and is the part that is auto-saved
 * (§4.4.5), while everything around it — which menu is open, what failed validation, whether a
 * dialog is up — is transient screen state that is meaningless to restore.
 *
 * [errors] is empty until Save is tapped: §4.4.4 validates on Save, not on every keystroke, so a
 * field the user has not finished typing is never marked wrong. [scrollToError] is the first failing
 * field, which the screen scrolls into view and focuses.
 */
@Immutable
data class CompoundFormState(
    val draft: CompoundFormDraft = CompoundFormDraft(),
    /** Edit mode (§4.4.1): the app bar reads "Editing {name}" and Save updates instead of inserting. */
    val isEdit: Boolean = false,
    /** Onboarding step 2 (§4.14): the app bar reads "Add your first compound · 2 of 3" + Skip. */
    val isOnboarding: Boolean = false,
    /** The compound's stored name, for the Edit app bar title — not [CompoundFormDraft.name], which the user is editing. */
    val editedCompoundName: String = "",
    val opened: OpenedContainerUi? = null,
    val forecast: StockForecastUi? = null,
    val errors: ImmutableMap<CompoundFormField, CompoundFormError> = persistentMapOf(),
    val scrollToError: CompoundFormField? = null,
    val openPicker: CompoundFormPicker? = null,
    val isDatePickerOpen: Boolean = false,
    /**
     * The opened-container sheet the §4.4.3 CTA asks for. The sheet itself is §4.5's and arrives with
     * M7-06 — this form only records that the user asked for it.
     */
    val isOpenedContainerSheetOpen: Boolean = false,
    val isDiscardDialogOpen: Boolean = false,
    /** §4.4.4 Edit case: non-null while Save is waiting to be told what to do about the opened container. */
    val shrinkPrompt: ContainerShrinkPromptUi? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    /** Whether the form differs from what it was loaded with — the discard dialog's trigger (§4.4.5). */
    val isDirty: Boolean = false,
) {
    /**
     * §4.4.3: concentration is required for an injectable that is not an ampoule. An ampoule arrives
     * pre-mixed, so there is nothing for the user to tell us; a vial of lyophilized powder has no
     * usable dose until its concentration is known.
     */
    val isConcentrationRequired: Boolean
        get() = draft.form == CompoundForm.INJECTABLE && draft.containerType != ContainerType.AMPOULE
}
