package com.stax.feature.compounds.presentation.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.ContainerType
import com.stax.core.presentation.ObserveAsEvents
import com.stax.core.presentation.asString
import com.stax.feature.compounds.presentation.R
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

/**
 * Root of the Create / Edit Compound form (§10.1): holds the [CompoundFormViewModel] and turns its
 * events into the callbacks `:app` wired into the entry (§10.3).
 *
 * [args] is passed to the ViewModel rather than to the screen: which compound is being edited decides
 * what the ViewModel loads and how it saves, and none of that is the composable's business.
 */
@Suppress("FunctionName")
@Composable
fun CompoundFormRoot(
    args: CompoundFormArgs,
    onDone: () -> Unit,
    onReconstitute: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompoundFormViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events, key1 = onDone, key2 = onReconstitute) { event ->
        when (event) {
            CompoundFormEvent.Done -> onDone()
            is CompoundFormEvent.OpenReconstitutionHelper -> onReconstitute(event.compoundId)
            is CompoundFormEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }
        }
    }

    CompoundFormScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Create / Edit Compound (§4.4): a scrollable form under a transparent app bar, over a dock holding
 * Cancel and Save.
 *
 * Adaptive per §6.4.2: one column at Compact, two from Medium up — Basics + Stock + Storage on the
 * left, the opened container + Notes + the live stock preview on the right. The columns scroll
 * independently, which is what makes the split worth having: the right column stays put while the
 * user works down the left one.
 */
@Suppress("FunctionName")
@Composable
fun CompoundFormScreen(
    state: CompoundFormState,
    onAction: (CompoundFormAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val focus = remember { CompoundFormFocus() }

    // §4.4.4: a failed Save takes the user to the first thing to fix. Focusing the field is what
    // scrolls it into view — Compose brings a newly focused field inside a scrollable into sight —
    // and leaves the cursor where the correction has to be typed.
    LaunchedEffect(state.scrollToError) {
        state.scrollToError?.let { field ->
            focus.of(field)?.requestFocus()
            onAction(CompoundFormAction.OnErrorScrollHandled)
        }
    }

    // The back gesture leaves the form, so it asks the same question the × does (§4.4.5).
    BackHandler { onAction(CompoundFormAction.OnCancelClick) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The app bar opens the pane and claims the status bar itself, drawing its (transparent)
            // container behind it so content scrolls under it as §4.4.1 asks (§2.3.6).
            .paneInsets(claimTop = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompoundFormTopBar(state = state, onAction = onAction)
            if (isTwoColumn()) {
                TwoColumnForm(state = state, onAction = onAction, focus = focus, modifier = Modifier.weight(1f))
            } else {
                SingleColumnForm(state = state, onAction = onAction, focus = focus, modifier = Modifier.weight(1f))
            }
            CompoundFormDock(isSaving = state.isSaving, onAction = onAction)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.isDiscardDialogOpen) DiscardChangesDialog(onAction = onAction)
    if (state.isDatePickerOpen) BatchExpiryDatePicker(onAction = onAction)
}

/**
 * §4.4.1: leading × that confirms a dirty discard, the mode's title, and — in onboarding step 2 only
 * — Skip in the trailing slot (§4.14). The container is transparent so the form scrolls under it;
 * the × keeps a `surface-container-low` fill of its own so it stays legible over whatever passes
 * beneath.
 */
@Suppress("FunctionName")
@Composable
private fun CompoundFormTopBar(state: CompoundFormState, onAction: (CompoundFormAction) -> Unit) {
    TopAppBar(
        title = { Text(text = state.title()) },
        navigationIcon = {
            Surface(
                shape = StaxShapes.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(start = FIELD_GAP),
            ) {
                IconButton(onClick = { onAction(CompoundFormAction.OnCancelClick) }) {
                    Icon(
                        painter = StaxIcons.Close,
                        contentDescription = stringResource(R.string.compound_form_close),
                    )
                }
            }
        },
        actions = {
            if (state.isOnboarding) {
                TextButton(onClick = { onAction(CompoundFormAction.OnSkipClick) }) {
                    Text(text = stringResource(R.string.compound_form_skip))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

/** §6.4.2 Compact: one column, sections in the order §4.4.3 lists them. */
@Suppress("FunctionName")
@Composable
private fun SingleColumnForm(
    state: CompoundFormState,
    onAction: (CompoundFormAction) -> Unit,
    focus: CompoundFormFocus,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_PADDING),
    ) {
        BasicsSection(state = state, onAction = onAction, focus = focus)
        StockSection(state = state, onAction = onAction, focus = focus)
        StorageSection(state = state, onAction = onAction)
        OpenedContainerSection(state = state, onAction = onAction)
        NotesSection(state = state, onAction = onAction)
        Box(modifier = Modifier.padding(bottom = SECTION_GAP))
    }
}

/**
 * §6.4.2 Medium / Expanded: the fields the user works through on the left, what results from them on
 * the right. Expanded gives the left column the extra width, which is where §6.4.2's "inputs wider"
 * goes — the unit pickers are inline at every width already, so nothing has to unwrap.
 */
@Suppress("FunctionName")
@Composable
private fun TwoColumnForm(
    state: CompoundFormState,
    onAction: (CompoundFormAction) -> Unit,
    focus: CompoundFormFocus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = SCREEN_PADDING),
        horizontalArrangement = Arrangement.spacedBy(SCREEN_PADDING),
    ) {
        Column(
            modifier = Modifier
                .weight(if (isExpandedWidth()) EXPANDED_LEFT_WEIGHT else EVEN_WEIGHT)
                .verticalScroll(rememberScrollState()),
        ) {
            BasicsSection(state = state, onAction = onAction, focus = focus)
            StockSection(state = state, onAction = onAction, focus = focus)
            StorageSection(state = state, onAction = onAction)
            Box(modifier = Modifier.padding(bottom = SECTION_GAP))
        }
        Column(
            modifier = Modifier
                .weight(if (isExpandedWidth()) EXPANDED_RIGHT_WEIGHT else EVEN_WEIGHT)
                .verticalScroll(rememberScrollState()),
        ) {
            OpenedContainerSection(state = state, onAction = onAction)
            NotesSection(state = state, onAction = onAction)
            state.forecast?.let { ForecastSection(forecast = it) }
            Box(modifier = Modifier.padding(bottom = SECTION_GAP))
        }
    }
}

/** §4.4: the bottom dock — Cancel, then the primary Save. Spans the whole form at every width. */
@Suppress("FunctionName")
@Composable
private fun CompoundFormDock(isSaving: Boolean, onAction: (CompoundFormAction) -> Unit) {
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
                TextButton(onClick = { onAction(CompoundFormAction.OnCancelClick) }) {
                    Text(text = stringResource(R.string.compound_form_cancel))
                }
                Button(
                    onClick = { onAction(CompoundFormAction.OnSaveClick) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                ) {
                    Icon(painter = StaxIcons.Check, contentDescription = null)
                    Text(
                        text = stringResource(R.string.compound_form_save),
                        modifier = Modifier.padding(start = FIELD_GAP),
                    )
                }
            }
        }
    }
}

/** §4.4.5: leaving a form with unsaved changes asks first. */
@Suppress("FunctionName")
@Composable
private fun DiscardChangesDialog(onAction: (CompoundFormAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(CompoundFormAction.Overlay.OnDiscardDismiss) },
        confirmButton = {
            TextButton(onClick = { onAction(CompoundFormAction.OnDiscardConfirm) }) {
                Text(text = stringResource(R.string.compound_form_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(CompoundFormAction.Overlay.OnDiscardDismiss) }) {
                Text(text = stringResource(R.string.compound_form_keep_editing))
            }
        },
        title = { Text(text = stringResource(R.string.compound_form_discard_title)) },
    )
}

/** §4.4.3: the batch expiry field opens the Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
private fun BatchExpiryDatePicker(onAction: (CompoundFormAction) -> Unit) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = { onAction(CompoundFormAction.Overlay.OnBatchExpiryDismiss) },
        confirmButton = {
            TextButton(
                onClick = {
                    // The picker reports UTC midnight of the chosen day; reading it back in any other
                    // zone would land on the day before for anyone west of Greenwich.
                    val date = pickerState.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                    onAction(CompoundFormAction.Overlay.OnBatchExpirySelected(date))
                },
            ) {
                Text(text = stringResource(R.string.compound_form_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(CompoundFormAction.Overlay.OnBatchExpiryDismiss) }) {
                Text(text = stringResource(R.string.compound_form_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** §4.4.1 / §4.14 step 2 — the three titles this one screen answers to. */
@Composable
private fun CompoundFormState.title(): String = when {
    isOnboarding -> stringResource(R.string.compound_form_title_onboarding)
    isEdit -> stringResource(R.string.compound_form_title_edit, editedCompoundName)
    else -> stringResource(R.string.compound_form_title_create)
}

/**
 * The focus targets §4.4.4 can send the user to. One per validatable *text* field — the picker rows
 * cannot fail validation, so they need none, and a requester that is never attached would throw when
 * asked to focus.
 */
@Stable
internal class CompoundFormFocus {
    val name = FocusRequester()
    val totalContainers = FocusRequester()
    val amountPerContainer = FocusRequester()
    val concentration = FocusRequester()

    fun of(field: CompoundFormField): FocusRequester? = when (field) {
        CompoundFormField.NAME -> name
        CompoundFormField.TOTAL_CONTAINERS -> totalContainers
        CompoundFormField.AMOUNT_PER_CONTAINER -> amountPerContainer
        CompoundFormField.CONCENTRATION -> concentration
        CompoundFormField.CONTAINER_TYPE -> null
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isTwoColumn(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isExpandedWidth(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

private const val EVEN_WEIGHT = 1f
private const val EXPANDED_LEFT_WEIGHT = 1.2f
private const val EXPANDED_RIGHT_WEIGHT = 1f

@Preview(name = "Create · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Create · Medium", showBackground = true, widthDp = 700, heightDp = 900)
@Preview(name = "Create · Expanded", showBackground = true, widthDp = 1000, heightDp = 900)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun CompoundFormScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            CompoundFormScreen(state = previewState(), onAction = {})
        }
    }
}

@Preview(name = "Validation · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun CompoundFormValidationPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            CompoundFormScreen(
                state = previewState().copy(
                    draft = previewState().draft.copy(name = ""),
                    errors = persistentMapOf(CompoundFormField.NAME to CompoundFormError.NAME_REQUIRED),
                ),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Edit · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun CompoundFormEditPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            CompoundFormScreen(
                state = previewState().copy(
                    isEdit = true,
                    editedCompoundName = "Semaglutide",
                    opened = OpenedContainerUi(
                        containerType = ContainerType.VIAL,
                        remaining = "3.2",
                        capacity = "5",
                        unit = "mg",
                        fillFraction = 0.64f,
                        openedDaysAgo = 12,
                    ),
                ),
                onAction = {},
            )
        }
    }
}

private fun previewState() = CompoundFormState(
    draft = CompoundFormDraft(
        name = "Retatrutide",
        totalContainers = "6",
        amountPerContainer = "10",
        concentrationAmount = "5",
        expiryAfterOpeningDays = "30",
    ),
    forecast = StockForecastUi(totalStock = "60 mg", containers = 6, volumePerContainer = "2 ml"),
)
