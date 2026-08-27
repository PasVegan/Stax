package com.stax.feature.protocols.presentation.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxPickerEmptyState
import com.stax.core.design.system.StaxPickerRow
import com.stax.core.design.system.StaxPickerSheet
import com.stax.core.design.system.StaxShapes
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.ContainerType
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.core.presentation.ObserveAsEvents
import com.stax.core.presentation.asString
import com.stax.feature.protocols.presentation.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

/**
 * Root of the Create / Edit Protocol form (§10.1): holds the [ProtocolFormViewModel] and turns its
 * events into the callbacks `:app` wired into the entry (§10.3).
 *
 * [args] goes to the ViewModel rather than to the screen: which protocol is being edited decides
 * what it loads and how it saves, and none of that is the composable's business.
 */
@Suppress("FunctionName")
@Composable
fun ProtocolFormRoot(
    args: ProtocolFormArgs,
    onDone: () -> Unit,
    onCreateCompound: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProtocolFormViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events, key1 = onDone, key2 = onCreateCompound) { event ->
        when (event) {
            ProtocolFormEvent.Done -> onDone()
            ProtocolFormEvent.OpenCreateCompound -> onCreateCompound()
            is ProtocolFormEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }
        }
    }

    ProtocolFormScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Create / Edit Protocol (§4.9): a scrollable form under a transparent app bar, over a dock holding
 * Cancel and Save.
 *
 * Adaptive per §6.4.2: one column at Compact; two from Medium, with what the user fills in on the
 * left and what results from it on the right; and at Expanded the Forecast card leaves the right
 * column's scroll to sit pinned at its top, so it stays in sight while the left column is worked
 * through — which is the whole point of a forecast that updates as you type.
 */
@Suppress("FunctionName")
@Composable
fun ProtocolFormScreen(
    state: ProtocolFormState,
    onAction: (ProtocolFormAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // The back gesture leaves the form, so it asks the same question the app bar's leading icon does.
    BackHandler { onAction(ProtocolFormAction.OnCancelClick) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The app bar opens the pane and claims the status bar itself, drawing its (transparent)
            // container behind it so content scrolls under it (§2.3.6).
            .paneInsets(claimTop = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProtocolFormTopBar(state = state, onAction = onAction)
            if (isTwoColumn()) {
                TwoColumnForm(state = state, onAction = onAction, modifier = Modifier.weight(1f))
            } else {
                SingleColumnForm(state = state, onAction = onAction, modifier = Modifier.weight(1f))
            }
            ProtocolFormDock(state = state, onAction = onAction)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (state.openPicker) {
        ProtocolFormPicker.COMPOUND -> CompoundPickerSheet(state = state, onAction = onAction)
        ProtocolFormPicker.BODY_REGION -> BodyRegionPickerSheet(state = state, onAction = onAction)
        // The dose unit is a dropdown anchored to its own pill (§4.9.3), not a sheet.
        ProtocolFormPicker.DOSE_UNIT, null -> Unit
    }
    state.openDateField?.let { field -> DurationDatePicker(state = state, field = field, onAction = onAction) }
    if (state.isTimePickerOpen) DosageTimePicker(onAction = onAction)
    if (state.isDiscardDialogOpen) DiscardChangesDialog(onAction = onAction)
    if (state.isArchiveDialogOpen) ArchiveDialog(onAction = onAction)
}

/**
 * §4.9.1: Create opens with `close`, Edit with `arrow_back` over the protocol's name, and onboarding
 * step 3 puts Skip in the trailing slot (§4.14). The container is transparent so the form scrolls
 * under it; the leading icon keeps a fill of its own so it stays legible over whatever passes beneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
private fun ProtocolFormTopBar(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(text = state.title())
                if (state.isEdit && state.editedProtocolName.isNotBlank()) {
                    Text(
                        text = state.editedProtocolName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            Surface(
                shape = StaxShapes.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(start = FIELD_GAP),
            ) {
                IconButton(onClick = { onAction(ProtocolFormAction.OnCancelClick) }) {
                    Icon(
                        painter = if (state.isEdit) StaxIcons.ArrowBack else StaxIcons.Close,
                        contentDescription = stringResource(R.string.protocol_form_close),
                    )
                }
            }
        },
        actions = {
            if (state.isOnboarding) {
                TextButton(onClick = { onAction(ProtocolFormAction.OnSkipClick) }) {
                    Text(text = stringResource(R.string.protocol_form_skip))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

/** §6.4.2 Compact: one column, sections in the order §4.9.3 lists them. */
@Suppress("FunctionName")
@Composable
private fun SingleColumnForm(
    state: ProtocolFormState,
    onAction: (ProtocolFormAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_PADDING),
    ) {
        if (state.isEdit) EditWarningBanner()
        InputSections(state = state, onAction = onAction)
        ResultSections(state = state, onAction = onAction)
        ForecastSection(state = state)
        if (state.isEdit) LifecycleSection(onAction = onAction)
        Box(modifier = Modifier.padding(bottom = SECTION_GAP))
    }
}

/**
 * §6.4.2 Medium / Expanded: the fields the user works through on the left, what follows from them on
 * the right. The columns scroll independently, which is what makes the split worth having.
 *
 * At Expanded the Forecast card is lifted out of the right column's scroll and pinned to its top —
 * §6.4.2's "sticky inset" — so a card that changes with every keystroke on the left is never scrolled
 * out of sight by work done on the right.
 */
@Suppress("FunctionName")
@Composable
private fun TwoColumnForm(
    state: ProtocolFormState,
    onAction: (ProtocolFormAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isForecastPinned = isExpandedWidth()
    Column(modifier = modifier.padding(horizontal = SCREEN_PADDING)) {
        if (state.isEdit) {
            Column { EditWarningBanner() }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SCREEN_PADDING),
        ) {
            Column(
                modifier = Modifier
                    .weight(COLUMN_WEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                InputSections(state = state, onAction = onAction)
                Box(modifier = Modifier.padding(bottom = SECTION_GAP))
            }
            Column(modifier = Modifier.weight(COLUMN_WEIGHT)) {
                if (isForecastPinned) ForecastSection(state = state)
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ResultSections(state = state, onAction = onAction)
                    if (!isForecastPinned) ForecastSection(state = state)
                    if (state.isEdit) LifecycleSection(onAction = onAction)
                    Box(modifier = Modifier.padding(bottom = SECTION_GAP))
                }
            }
        }
    }
}

/** §6.4.2's left column: Compound + Route + Planned dose + Schedule + Times of day + Duration. */
@Suppress("FunctionName")
@Composable
private fun ColumnScope.InputSections(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    CompoundSection(state = state, onAction = onAction)
    RouteSection(state = state, onAction = onAction)
    PlannedDoseSection(state = state, onAction = onAction)
    ScheduleSection(state = state, onAction = onAction)
    state.preview?.let { SchedulePreview(preview = it) }
    DurationSection(state = state, onAction = onAction)
}

/** §6.4.2's right column, forecast aside: Reminder + Site restriction + Notes. */
@Suppress("FunctionName")
@Composable
private fun ColumnScope.ResultSections(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    ReminderSection(state = state, onAction = onAction)
    SiteRestrictionSection(state = state, onAction = onAction)
    NotesSection(state = state, onAction = onAction)
}

/** §4.9.4: Cancel, then the primary Save. Spans the whole form at every width. */
@Suppress("FunctionName")
@Composable
private fun ProtocolFormDock(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    Column {
        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SCREEN_PADDING, vertical = FIELD_GAP),
                horizontalArrangement = Arrangement.spacedBy(SCREEN_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onAction(ProtocolFormAction.OnCancelClick) }) {
                    Text(text = stringResource(R.string.protocol_form_cancel))
                }
                Button(
                    onClick = { onAction(ProtocolFormAction.OnSaveClick) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Icon(painter = StaxIcons.Check, contentDescription = null)
                    Text(
                        text = stringResource(
                            if (state.isEdit) R.string.protocol_form_save_changes else R.string.protocol_form_save,
                        ),
                        modifier = Modifier.padding(start = FIELD_GAP),
                    )
                }
            }
        }
    }
}

/**
 * §4.9.3's Compound picker, on the reusable §4.0.2 sheet. With no compounds to offer it becomes the
 * pattern's empty state, whose CTA is the only useful thing left to do — go and create one (§4.4).
 */
@Suppress("FunctionName")
@Composable
private fun CompoundPickerSheet(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    StaxPickerSheet(
        title = stringResource(R.string.protocol_form_picker_compound),
        onDismissRequest = { onAction(ProtocolFormAction.Overlay.OnPickerDismiss) },
        query = state.pickerQuery,
        onQueryChange = if (state.isPickerSearchable) {
            { query -> onAction(ProtocolFormAction.Overlay.OnPickerQueryChange(query)) }
        } else {
            null
        },
        searchPlaceholder = stringResource(R.string.protocol_form_picker_search),
        closeContentDescription = stringResource(R.string.protocol_form_picker_close),
        clearContentDescription = stringResource(R.string.protocol_form_picker_clear),
    ) {
        if (state.pickerCompounds.isEmpty()) {
            item {
                Column {
                    StaxPickerEmptyState(
                        message = stringResource(R.string.protocol_form_picker_empty),
                        ctaLabel = stringResource(R.string.protocol_form_picker_add_compound),
                        onCtaClick = { onAction(ProtocolFormAction.OnAddCompoundClick) },
                    )
                }
            }
        }
        items(state.pickerCompounds, key = { it.id }) { compound ->
            StaxPickerRow(
                name = compound.name,
                onClick = { onAction(ProtocolFormAction.Pick.OnCompoundSelected(compound.id)) },
                icon = StaxIcons.Colorize,
                supporting = compoundMeta(compound),
                isSelected = compound.id == state.draft.compoundSupplyId,
            )
        }
    }
}

/**
 * §4.9.3's Body region picker, on the same §4.0.2 sheet. "No restriction" is the first row rather
 * than a clear button: it is a choice like any other, and it is the one the field starts on.
 *
 * No search field — nine fixed rows fit on one screen, and a search box over them is furniture.
 */
@Suppress("FunctionName")
@Composable
private fun BodyRegionPickerSheet(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    StaxPickerSheet(
        title = stringResource(R.string.protocol_form_picker_region),
        onDismissRequest = { onAction(ProtocolFormAction.Overlay.OnPickerDismiss) },
        closeContentDescription = stringResource(R.string.protocol_form_picker_close),
    ) {
        item {
            StaxPickerRow(
                name = stringResource(R.string.protocol_form_site_none),
                onClick = { onAction(ProtocolFormAction.Pick.OnBodyRegionSelected(null)) },
                icon = StaxIcons.Block,
                isSelected = state.draft.siteRestriction == null,
            )
        }
        items(BodyRegion.entries, key = { it.name }) { region ->
            StaxPickerRow(
                name = bodyRegionName(region),
                onClick = { onAction(ProtocolFormAction.Pick.OnBodyRegionSelected(region)) },
                icon = StaxIcons.PersonPinCircle,
                isSelected = state.draft.siteRestriction == region,
            )
        }
    }
}

/**
 * §4.9.3 Duration: both boxes open the Material date picker, seeded with whatever the box holds.
 *
 * The End box's Clear is what makes a protocol open-ended again, so it is offered only there — a
 * Start the user could clear would leave the schedule with no day 0 to count from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
private fun DurationDatePicker(
    state: ProtocolFormState,
    field: ProtocolDateField,
    onAction: (ProtocolFormAction) -> Unit,
) {
    val selected = when (field) {
        ProtocolDateField.START -> state.draft.startDate
        ProtocolDateField.END -> state.draft.endDate
    }
    val pickerState = rememberDatePickerState(
        // The picker speaks UTC milliseconds; anything else would land a day off for half the world.
        initialSelectedDateMillis = selected?.atStartOfDayInUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = { onAction(ProtocolFormAction.Overlay.OnDatePickerDismiss) },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = pickerState.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                    onAction(ProtocolFormAction.Overlay.OnDateSelected(date))
                },
            ) {
                Text(text = stringResource(R.string.protocol_form_date_confirm))
            }
        },
        dismissButton = {
            Row {
                if (field == ProtocolDateField.END) {
                    TextButton(onClick = { onAction(ProtocolFormAction.Overlay.OnDateSelected(null)) }) {
                        Text(text = stringResource(R.string.protocol_form_date_clear))
                    }
                }
                TextButton(onClick = { onAction(ProtocolFormAction.Overlay.OnDatePickerDismiss) }) {
                    Text(text = stringResource(R.string.protocol_form_cancel))
                }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** §4.9.3 Times of day: "Add time" opens the Material time picker, in the device's own clock format. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
private fun DosageTimePicker(onAction: (ProtocolFormAction) -> Unit) {
    val pickerState = rememberTimePickerState()
    TimePickerDialog(
        onDismissRequest = { onAction(ProtocolFormAction.OnTimePickerDismiss) },
        title = { Text(text = stringResource(R.string.protocol_form_time_picker_title)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val time = LocalTime(pickerState.hour, pickerState.minute)
                    onAction(ProtocolFormAction.OnTimeSelected(time))
                },
            ) {
                Text(text = stringResource(R.string.protocol_form_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ProtocolFormAction.OnTimePickerDismiss) }) {
                Text(text = stringResource(R.string.protocol_form_cancel))
            }
        },
    ) {
        TimePicker(state = pickerState)
    }
}

/** §4.4.5's rule, which §4.9 inherits: leaving a form with unsaved changes asks first. */
@Suppress("FunctionName")
@Composable
private fun DiscardChangesDialog(onAction: (ProtocolFormAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ProtocolFormAction.Overlay.OnDiscardDismiss) },
        confirmButton = {
            TextButton(onClick = { onAction(ProtocolFormAction.OnDiscardConfirm) }) {
                Text(text = stringResource(R.string.protocol_form_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ProtocolFormAction.Overlay.OnDiscardDismiss) }) {
                Text(text = stringResource(R.string.protocol_form_keep_editing))
            }
        },
        title = { Text(text = stringResource(R.string.protocol_form_discard_title)) },
    )
}

/** §4.9.5: Archive takes the protocol off every list the user looks at, so it asks first (§5.5). */
@Suppress("FunctionName")
@Composable
private fun ArchiveDialog(onAction: (ProtocolFormAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(ProtocolFormAction.Overlay.OnArchiveDismiss) },
        confirmButton = {
            TextButton(onClick = { onAction(ProtocolFormAction.OnArchiveConfirm) }) {
                Text(text = stringResource(R.string.protocol_form_archive))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ProtocolFormAction.Overlay.OnArchiveDismiss) }) {
                Text(text = stringResource(R.string.protocol_form_cancel))
            }
        },
        title = { Text(text = stringResource(R.string.protocol_form_archive_title)) },
        text = { Text(text = stringResource(R.string.protocol_form_archive_supporting)) },
    )
}

/** §4.9.1 / §4.14 step 3 — the three titles this one screen answers to. */
@Composable
private fun ProtocolFormState.title(): String = when {
    isOnboarding -> stringResource(R.string.protocol_form_title_onboarding)
    isEdit -> stringResource(R.string.protocol_form_title_edit)
    else -> stringResource(R.string.protocol_form_title_create)
}

private fun LocalDate.atStartOfDayInUtcMillis(): Long =
    kotlinx.datetime.LocalDateTime(this, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds()

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isTwoColumn(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isExpandedWidth(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** §6.4.2 splits the form evenly: neither column has the longer fields, so neither earns the width. */
private const val COLUMN_WEIGHT = 1f

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Create · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Create · Medium", showBackground = true, widthDp = 700, heightDp = 900)
@Preview(name = "Create · Expanded", showBackground = true, widthDp = 1000, heightDp = 900)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ProtocolFormScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface { ProtocolFormScreen(state = previewState(), onAction = {}) }
    }
}

@Preview(name = "Edit · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Edit · Expanded", showBackground = true, widthDp = 1000, heightDp = 900)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ProtocolFormEditPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ProtocolFormScreen(
                state = previewState().copy(isEdit = true, editedProtocolName = "Sema weekly titration"),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Validation · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ProtocolFormValidationPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ProtocolFormScreen(
                state = previewState().copy(
                    compound = null,
                    draft = previewState().draft.copy(doseAmount = ""),
                    errors = persistentMapOf(
                        ProtocolFormField.COMPOUND to ProtocolFormError.COMPOUND_REQUIRED,
                        ProtocolFormField.DOSE to ProtocolFormError.DOSE_NOT_POSITIVE,
                    ),
                ),
                onAction = {},
            )
        }
    }
}

private val previewToday = LocalDate(2026, 5, 26)

private fun previewState(): ProtocolFormState {
    val compound = CompoundPickUi(
        id = 1,
        name = "Semaglutide",
        category = CompoundCategory.PEPTIDE,
        containerType = ContainerType.VIAL,
        amount = "5",
        amountUnit = UnitCode.MG,
        concentration = "2.5",
        concentrationUnit = UnitCode.MG,
        concentrationPerUnit = UnitCode.ML,
    )
    return ProtocolFormState(
        draft = ProtocolFormDraft(
            compoundSupplyId = 1,
            doseAmount = "0.25",
            scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dosageTimes = listOf(LocalTime(20, 0)),
            startDate = previewToday,
            endDate = previewToday.plus(90, DateTimeUnit.DAY),
        ),
        compound = compound,
        pickerCompounds = persistentListOf(compound),
        doseUnitOptions = persistentListOf(UnitCode.MG, UnitCode.MCG),
        equivalence = DoseEquivalenceUi(volume = "0.10", volumeUnit = UnitCode.ML, insulinUnits = 10),
        preview = SchedulePreviewUi(
            doseCount = 2,
            days = List(7) { offset ->
                val date = previewToday.plus(offset, DateTimeUnit.DAY)
                PreviewDayUi(date = date, hasDose = offset == 0 || offset == 3, isToday = offset == 0)
            }.toImmutableList(),
        ),
        forecast = ProtocolForecastUi(
            dosesLeft = 18,
            daysLeft = 63,
            runOutDate = LocalDate(2026, 7, 28),
            expiryWarning = ExpiryWarningUi(
                batchExpiry = LocalDate(2026, 7, 14),
                runOut = LocalDate(2026, 7, 28),
            ),
            reorder = ReorderHintUi(
                containers = 1,
                containerType = ContainerType.VIAL,
                orderBy = LocalDate(2026, 7, 21),
                coversUntil = LocalDate(2026, 8, 24),
            ),
        ),
    )
}
