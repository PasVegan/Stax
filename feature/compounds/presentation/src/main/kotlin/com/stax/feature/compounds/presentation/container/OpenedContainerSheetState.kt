package com.stax.feature.compounds.presentation.container

import androidx.compose.runtime.Immutable
import com.stax.core.domain.ContainerType
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate

/**
 * UI state of the opened-container sheet (§4.5), in both its variants.
 *
 * One state for both because they are one sheet: §4.5 describes Create Already Opened as the Edit
 * sheet "minus Delete", so [isEdit] is the whole difference between them.
 *
 * The sheet is not a destination — it lives in the state of whatever screen opened it (§10.3), which
 * is the Create / Edit Compound form today and Compound Detail from M7-07. That screen owns the
 * writes; this is only what the sheet shows and what the user has typed into it.
 *
 * Everything here is either raw user text ([remaining]) or already-derived display values: the
 * "12 days ago" under the opened date and the "28 days after opening" under the expiry are counted
 * by the owner, not by the composable.
 */
@Immutable
data class OpenedContainerSheetState(
    /** §4.5.4: the Edit variant carries Delete. Create Already Opened is the same sheet without it. */
    val isEdit: Boolean,
    val containerType: ContainerType,
    /** §4.5.2's subtitle is "{compound} · {amount} {unit} {container type}"; these are its parts. */
    val compoundName: String,
    val containerAmount: String,
    val unit: UnitCode,
    val openedDate: LocalDate,
    val openedDaysAgo: Int,
    /** Raw text, like the form's numeric fields: "3." is nothing a `Decimal` can be (§4.4.3). */
    val remaining: String,
    val expiryDate: LocalDate? = null,
    /**
     * §4.5.3: the expiry is still the one `expiryAfterOpeningDays` implies rather than one the user
     * set by hand. It stays auto until they tap the field's `edit`, after which the manual date wins
     * (§3.1.1) and follows nothing.
     */
    val isExpiryAuto: Boolean = true,
    /** "N days after opening" — null while there is no expiry to count to. */
    val expiryDaysAfterOpening: Int? = null,
    val openDatePicker: OpenedContainerDateField? = null,
    /** §4.5.3: what was typed into Remaining is not a number at or above zero. */
    val hasRemainingError: Boolean = false,
    /**
     * Why the last Save did not go through.
     *
     * It belongs to the sheet rather than to a snackbar because a modal sheet is a window of its own
     * and the screen's `SnackbarHost` is behind it — a save that failed would say so out of sight.
     */
    val saveError: OpenedContainerSaveError? = null,
    val isSaving: Boolean = false,
)

/** The two date fields of §4.5.3. At most one picker is open, so the open one is a nullable enum. */
enum class OpenedContainerDateField { OPENED, EXPIRY }

/** Why §4.5.5's write was refused. The sheet owns the wording; whoever owns the sheet owns the rule. */
enum class OpenedContainerSaveError {
    /**
     * There is no sealed container left to open (§5.3). Reached from Create Already Opened on a
     * compound whose stock is all already open or gone.
     */
    NO_UNOPENED_STOCK,

    /** The write itself failed — a full disk, a row that vanished under us. */
    WRITE_FAILED,
}

/** Everything the user can do on the opened-container sheet (§4.5). */
sealed interface OpenedContainerSheetAction {

    /** The `close` icon (§4.5.2), the scrim, and the back gesture — all the same statement. */
    data object OnDismiss : OpenedContainerSheetAction

    data class OnDateFieldClick(val field: OpenedContainerDateField) : OpenedContainerSheetAction
    data class OnDateSelected(val date: LocalDate?) : OpenedContainerSheetAction
    data object OnDatePickerDismiss : OpenedContainerSheetAction

    data class OnRemainingChange(val value: String) : OpenedContainerSheetAction

    /** §4.5.4 Edit variant only: the lost / discarded path. */
    data object OnDeleteClick : OpenedContainerSheetAction

    data object OnSaveClick : OpenedContainerSheetAction
}
