package com.stax.feature.compounds.presentation.container

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.stax.core.design.system.StaxAdaptiveSheet
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.ContainerType
import com.stax.core.domain.UnitCode
import com.stax.feature.compounds.presentation.R
import com.stax.feature.compounds.presentation.form.FormPickerField
import com.stax.feature.compounds.presentation.form.FormTextField
import com.stax.feature.compounds.presentation.form.containerTypeLabel
import com.stax.feature.compounds.presentation.form.formatLong
import com.stax.feature.compounds.presentation.form.unitLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The opened-container sheet of §4.5, in both variants: Edit Opened Container, and — without the
 * Delete button — Create Already Opened.
 *
 * Adaptive through [StaxAdaptiveSheet] (§6.4.2): full-width bottom sheet at Compact, clamped to
 * `560dp` at Medium, an end-edge `420dp` side sheet at Expanded. The content is one scrolling column
 * either way, because that is the reflow §6.4.2 asks of these sheets at every width.
 *
 * Stateless: every tap leaves as an [OpenedContainerSheetAction] for the screen that owns the sheet
 * to act on (§10.1).
 */
@Suppress("FunctionName")
@Composable
fun OpenedContainerSheet(
    state: OpenedContainerSheetState,
    onAction: (OpenedContainerSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    StaxAdaptiveSheet(
        onDismissRequest = { onAction(OpenedContainerSheetAction.OnDismiss) },
        modifier = modifier,
    ) {
        OpenedContainerSheetContent(state = state, onAction = onAction)
    }
    state.openDatePicker?.let { field ->
        SheetDatePicker(
            initialDate = if (field == OpenedContainerDateField.OPENED) state.openedDate else state.expiryDate,
            onAction = onAction,
        )
    }
}

/**
 * The sheet's body, separate from the surface it is presented on.
 *
 * A modal sheet is a window of its own, which no `@Preview` renders — so the previews below take
 * this and the app takes the sheet, and the thing being looked at is the same either way.
 */
@Suppress("FunctionName")
@Composable
private fun OpenedContainerSheetContent(
    state: OpenedContainerSheetState,
    onAction: (OpenedContainerSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = SHEET_PADDING)) {
        // Only the fields scroll, so Delete and Save stay where they are. At Expanded the side sheet
        // is as tall as a landscape phone — 411dp — and the fields alone are taller than that, which
        // put the primary action of the sheet below the fold until it was pulled out of the scroll.
        // `fill = false` keeps the bottom sheet sized to its content when there is room to spare.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            SheetHeader(state = state, onAction = onAction)
            OpenedDateField(state = state, onAction = onAction)
            RemainingField(state = state, onAction = onAction)
            ExpiryField(state = state, onAction = onAction)
        }
        state.saveError?.let { SaveError(error = it) }
        SheetActions(state = state, onAction = onAction)
    }
}

/**
 * Why the last Save did not go through, stated in the sheet.
 *
 * Not a snackbar: the sheet is its own window and the screen's `SnackbarHost` draws behind it, so a
 * failure reported that way is a failure reported where nobody is looking.
 */
@Suppress("FunctionName")
@Composable
private fun SaveError(error: OpenedContainerSaveError) {
    Row(
        modifier = Modifier.padding(top = SHEET_PADDING),
        horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = StaxIcons.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            text = stringResource(
                when (error) {
                    OpenedContainerSaveError.NO_UNOPENED_STOCK -> R.string.container_sheet_error_no_stock
                    OpenedContainerSaveError.WRITE_FAILED -> R.string.container_sheet_error_write_failed
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** §4.5.2: the title and what compound it is about, with `close` on the end. */
@Suppress("FunctionName")
@Composable
private fun SheetHeader(state: OpenedContainerSheetState, onAction: (OpenedContainerSheetAction) -> Unit) {
    Row(modifier = Modifier.padding(bottom = FIELD_GAP), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (state.isEdit) R.string.container_sheet_title_edit else R.string.container_sheet_title_add,
                    containerTypeLabel(state.containerType),
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    R.string.container_sheet_subtitle,
                    state.compoundName,
                    state.containerAmount,
                    unitLabel(state.unit),
                    containerTypeLabel(state.containerType),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onAction(OpenedContainerSheetAction.OnDismiss) }) {
            Icon(
                painter = StaxIcons.Close,
                contentDescription = stringResource(R.string.container_sheet_close),
            )
        }
    }
}

/** §4.5.3 field 1: when the container was opened, and how long ago that was. */
@Suppress("FunctionName")
@Composable
private fun OpenedDateField(state: OpenedContainerSheetState, onAction: (OpenedContainerSheetAction) -> Unit) {
    FormPickerField(
        label = stringResource(R.string.container_sheet_opened_date),
        value = state.openedDate.formatLong(),
        icon = StaxIcons.Today,
        trailingIcon = StaxIcons.Edit,
        onClick = { onAction(OpenedContainerSheetAction.OnDateFieldClick(OpenedContainerDateField.OPENED)) },
        supporting = pluralStringResource(
            R.plurals.container_sheet_days_ago,
            state.openedDaysAgo,
            state.openedDaysAgo,
        ),
    )
    Box(modifier = Modifier.size(FIELD_GAP))
}

/** §4.5.3 field 2: what is left in the container, in the compound's own unit. */
@Suppress("FunctionName")
@Composable
private fun RemainingField(state: OpenedContainerSheetState, onAction: (OpenedContainerSheetAction) -> Unit) {
    FormTextField(
        label = stringResource(R.string.container_sheet_remaining),
        value = state.remaining,
        onValueChange = { onAction(OpenedContainerSheetAction.OnRemainingChange(it)) },
        icon = StaxIcons.Straighten,
        error = stringResource(R.string.container_sheet_remaining_error).takeIf { state.hasRemainingError },
        keyboardType = KeyboardType.Decimal,
        // The unit is the compound's, not a choice: this sheet edits one container of a compound
        // that has already settled what it is measured in (§4.5.3).
        suffix = { Text(text = unitLabel(state.unit), style = MaterialTheme.typography.bodyMedium) },
    )
    Box(modifier = Modifier.size(FIELD_GAP))
}

/**
 * §4.5.3 field 3: the container's own expiry.
 *
 * Auto until the user sets one: with `expiryAfterOpeningDays` on the compound the date is
 * `openedDate + days` and moves whenever the opened date does, which is what the greyed "auto"
 * marker says. Setting a date by hand ends that — a manual override wins and then follows nothing
 * (§3.1.1).
 */
@Suppress("FunctionName")
@Composable
private fun ExpiryField(state: OpenedContainerSheetState, onAction: (OpenedContainerSheetAction) -> Unit) {
    FormPickerField(
        label = stringResource(R.string.container_sheet_expiry),
        value = state.expiryDate?.formatLong() ?: stringResource(R.string.container_sheet_expiry_unset),
        icon = StaxIcons.EventBusy,
        trailingIcon = StaxIcons.Edit,
        onClick = { onAction(OpenedContainerSheetAction.OnDateFieldClick(OpenedContainerDateField.EXPIRY)) },
        isOptional = true,
        supporting = state.expiryDaysAfterOpening?.let { days ->
            if (state.isExpiryAuto) {
                pluralStringResource(R.plurals.container_sheet_expiry_auto, days, days)
            } else {
                pluralStringResource(R.plurals.container_sheet_expiry_manual, days, days)
            }
        },
    )
}

/** §4.5.4: Delete beside Save on the Edit variant; Save alone on Create Already Opened. */
@Suppress("FunctionName")
@Composable
private fun SheetActions(state: OpenedContainerSheetState, onAction: (OpenedContainerSheetAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SHEET_PADDING, bottom = SHEET_PADDING),
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
    ) {
        if (state.isEdit) {
            FilledTonalButton(
                onClick = { onAction(OpenedContainerSheetAction.OnDeleteClick) },
                enabled = !state.isSaving,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(painter = StaxIcons.Delete, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
                Text(
                    text = stringResource(R.string.container_sheet_delete),
                    modifier = Modifier.padding(start = LABEL_GAP),
                )
            }
        }
        Button(
            onClick = { onAction(OpenedContainerSheetAction.OnSaveClick) },
            modifier = Modifier.weight(1f),
            enabled = !state.isSaving,
        ) {
            Icon(painter = StaxIcons.Check, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
            Text(
                text = stringResource(R.string.container_sheet_save),
                modifier = Modifier.padding(start = LABEL_GAP),
            )
        }
    }
}

/**
 * The Material date picker behind both date fields of §4.5.3.
 *
 * It opens as a calendar where there is room for one and as a text field where there is not: the
 * calendar needs about `500dp` of height, and a phone in landscape — the very orientation the
 * Expanded side sheet is for — has `411dp`. Below that the grid overlaps its own header and action
 * row, so the picker's other display mode is the only one that can be used at all.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Suppress("FunctionName")
@Composable
private fun SheetDatePicker(initialDate: LocalDate?, onAction: (OpenedContainerSheetAction) -> Unit) {
    val hasRoomForCalendar = currentWindowAdaptiveInfoV2().windowSizeClass
        .isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val pickerState = rememberDatePickerState(
        // The picker counts in UTC milliseconds, so the day the user is looking at has to be handed
        // over as that same UTC midnight or it opens on the day before.
        initialSelectedDateMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        initialDisplayMode = if (hasRoomForCalendar) DisplayMode.Picker else DisplayMode.Input,
    )
    DatePickerDialog(
        onDismissRequest = { onAction(OpenedContainerSheetAction.OnDatePickerDismiss) },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = pickerState.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                    onAction(OpenedContainerSheetAction.OnDateSelected(date))
                },
            ) {
                Text(text = stringResource(R.string.compound_form_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(OpenedContainerSheetAction.OnDatePickerDismiss) }) {
                Text(text = stringResource(R.string.compound_form_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/**
 * §4.5.5: saving a container with nothing left in it empties it for good, and the question that
 * follows is whether to open the next one now.
 *
 * Only raised while unopened stock remains — with nothing left to open there is no question — and
 * "Open new" is the default action, as it is for the same prompt after a dose empties a container
 * (§5.3).
 */
@Suppress("FunctionName")
@Composable
fun NaturalDepletionDialog(onOpenNew: () -> Unit, onLeaveClosed: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLeaveClosed,
        title = { Text(text = stringResource(R.string.container_depleted_title)) },
        text = { Text(text = stringResource(R.string.container_depleted_body)) },
        confirmButton = {
            TextButton(onClick = onOpenNew) {
                Text(text = stringResource(R.string.container_depleted_open_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onLeaveClosed) {
                Text(text = stringResource(R.string.container_depleted_leave_closed))
            }
        },
    )
}

private val SHEET_PADDING = 16.dp
private val FIELD_GAP = 8.dp
private val LABEL_GAP = 4.dp
private val ICON_SMALL = 18.dp

/** The widths §6.4.2 gives the sheet: Compact takes the window, Medium `560dp`, Expanded `420dp`. */
@Preview(name = "Edit · Compact", showBackground = true, widthDp = 411)
@Preview(name = "Edit · Medium", showBackground = true, widthDp = 560)
@Preview(name = "Edit · Expanded side sheet", showBackground = true, widthDp = 420)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun OpenedContainerSheetEditPreview() {
    StaxTheme(dynamicColor = false) {
        Surface { OpenedContainerSheetContent(state = previewState(), onAction = {}) }
    }
}

@Preview(name = "Add · Compact", showBackground = true, widthDp = 411)
@Preview(name = "Add · Expanded side sheet", showBackground = true, widthDp = 420)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun OpenedContainerSheetAddPreview() {
    StaxTheme(dynamicColor = false) {
        Surface { OpenedContainerSheetContent(state = previewState().copy(isEdit = false), onAction = {}) }
    }
}

@Preview(name = "Remaining rejected · Compact", showBackground = true, widthDp = 411)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun OpenedContainerSheetErrorPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            OpenedContainerSheetContent(
                state = previewState().copy(remaining = "-1", hasRemainingError = true),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Depleted · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun NaturalDepletionDialogPreview() {
    StaxTheme(dynamicColor = false) {
        Surface { NaturalDepletionDialog(onOpenNew = {}, onLeaveClosed = {}) }
    }
}

private fun previewState() = OpenedContainerSheetState(
    isEdit = true,
    containerType = ContainerType.VIAL,
    compoundName = "Semaglutide",
    containerAmount = "5",
    unit = UnitCode.MG,
    openedDate = LocalDate.parse("2026-05-14"),
    openedDaysAgo = 12,
    remaining = "3.2",
    expiryDate = LocalDate.parse("2026-06-11"),
    expiryDaysAfterOpening = 28,
)
