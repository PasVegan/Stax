package com.stax.feature.compounds.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.ContainerType
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.core.presentation.ObserveAsEvents
import com.stax.core.presentation.asString
import com.stax.feature.compounds.presentation.R
import com.stax.feature.compounds.presentation.container.NaturalDepletionDialog
import com.stax.feature.compounds.presentation.container.OpenedContainerSheet
import com.stax.feature.compounds.presentation.form.OpenedContainerUi
import com.stax.feature.compounds.presentation.list.categoryLabel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Root of Compound Detail (§10.1): holds the [CompoundDetailViewModel] and turns its navigation
 * events into the callbacks `:app` wired into the entry (§10.3).
 *
 * [args] goes to the ViewModel rather than to the screen: which compound this is decides what is
 * loaded and what the sheet writes to, and none of that is the composable's business.
 */
@Suppress("FunctionName")
@Composable
fun CompoundDetailRoot(
    args: CompoundDetailArgs,
    onBack: () -> Unit,
    onProtocolClick: (Long) -> Unit,
    onLogDose: (Long) -> Unit,
    onEditCompound: (Long) -> Unit,
    onAdministrationEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompoundDetailViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events, key1 = onBack, key2 = onProtocolClick) { event ->
        when (event) {
            CompoundDetailEvent.NavigateBack -> onBack()
            is CompoundDetailEvent.NavigateToProtocol -> onProtocolClick(event.protocolId)
            is CompoundDetailEvent.NavigateToLogDose -> onLogDose(event.compoundId)
            is CompoundDetailEvent.NavigateToEditCompound -> onEditCompound(event.compoundId)
            is CompoundDetailEvent.NavigateToAdministrationEvent -> onAdministrationEventClick(event.eventId)
            is CompoundDetailEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }

            is CompoundDetailEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }
        }
    }

    CompoundDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Compound Detail (§4.3): the stat strip, the opened container, the active protocols, the notes and
 * the dose history, over a sticky dock holding Log dose and Adjust.
 *
 * This is the **detail pane** of the Compounds list-detail Scene (§6.4.2), so its width is the pane's
 * and the internal layout is decided from that width rather than from the window's: at Expanded the
 * pane is what is left after a `400dp` list pane and the navigation rail, which on an `840dp` window
 * is under `350dp` — narrower than a Compact phone. Two columns are worth having once the pane can
 * actually carry them ([TWO_COLUMN_MIN_WIDTH]), and below that the same content is one scroll, which
 * is the layout §6.4.2 gives Compact anyway.
 */
@Suppress("FunctionName")
@Composable
fun CompoundDetailScreen(
    state: CompoundDetailState,
    onAction: (CompoundDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // The pane opens with its own app bar, so the status bar is the bar's to claim and draw
            // its container behind (§2.3.6). The pane still takes the sides and the bottom.
            .paneInsets(claimTop = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompoundDetailTopBar(state = state, onAction = onAction)
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                if (maxWidth >= TWO_COLUMN_MIN_WIDTH) {
                    TwoColumnContent(state = state, onAction = onAction)
                } else {
                    SingleColumnContent(state = state, onAction = onAction)
                }
            }
            CompoundDetailDock(onAction = onAction)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // §4.5, hosted as a mode of this screen rather than as a destination (§10.3).
    state.openedSheet?.let { sheet ->
        OpenedContainerSheet(
            state = sheet,
            onAction = { onAction(CompoundDetailAction.OpenedContainerSheet(it)) },
        )
    }
    if (state.isDepletionPromptOpen) {
        NaturalDepletionDialog(
            onOpenNew = { onAction(CompoundDetailAction.OnNaturalDepletionDecision(openNew = true)) },
            onLeaveClosed = { onAction(CompoundDetailAction.OnNaturalDepletionDecision(openNew = false)) },
        )
    }
}

/** §4.3.1: leading `arrow_back`, the compound's name as the headline, its category underneath. */
@Suppress("FunctionName")
@Composable
private fun CompoundDetailTopBar(state: CompoundDetailState, onAction: (CompoundDetailAction) -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(text = state.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                state.category?.let { category ->
                    Text(
                        text = categoryLabel(category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { onAction(CompoundDetailAction.OnBackClick) }) {
                Icon(
                    painter = StaxIcons.ArrowBack,
                    contentDescription = stringResource(R.string.compound_detail_back),
                )
            }
        },
    )
}

/**
 * Compact and Medium (§6.4.2): one column, everything in one `LazyColumn`.
 *
 * The history rows are items of that same list rather than a list of their own — a lazy list nested in
 * a scrolling parent has no height to measure against, and the point of §4.3.8's lazy loading is
 * exactly that these rows are not all composed at once.
 */
@Suppress("FunctionName")
@Composable
private fun SingleColumnContent(state: CompoundDetailState, onAction: (CompoundDetailAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        item(key = "stats") { StatStrip(stats = state.stats) }
        item(key = "opened") { OpenedContainerCard(opened = state.opened, onAction = onAction) }
        item(key = "protocols") { ActiveProtocolsCard(protocols = state.protocols, onAction = onAction) }
        item(key = "notes") {
            NotesCard(notes = state.notes, isExpanded = state.isNotesExpanded, onAction = onAction)
        }
        historySection(state = state, onAction = onAction)
    }
}

/**
 * Expanded (§6.4.2): the stat strip spans the pane, the cards take the left column and the history
 * takes the right one. The two scroll independently — which is the whole reason the split is worth
 * having, since the cards stay put while the user works down the history.
 */
@Suppress("FunctionName")
@Composable
private fun TwoColumnContent(state: CompoundDetailState, onAction: (CompoundDetailAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        StatStrip(stats = state.stats, modifier = Modifier.padding(top = SCREEN_PADDING))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SCREEN_PADDING),
        ) {
            Column(
                modifier = Modifier
                    .weight(LEFT_COLUMN_WEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                OpenedContainerCard(opened = state.opened, onAction = onAction)
                ActiveProtocolsCard(protocols = state.protocols, onAction = onAction)
                NotesCard(notes = state.notes, isExpanded = state.isNotesExpanded, onAction = onAction)
                Box(modifier = Modifier.padding(bottom = SCREEN_PADDING))
            }
            LazyColumn(
                modifier = Modifier.weight(RIGHT_COLUMN_WEIGHT),
                contentPadding = PaddingValues(bottom = SCREEN_PADDING),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                historySection(state = state, onAction = onAction)
            }
        }
    }
}

/** §4.3.6–§4.3.8, as list items so both layouts can place them in whichever list they own. */
private fun LazyListScope.historySection(state: CompoundDetailState, onAction: (CompoundDetailAction) -> Unit) {
    item(key = "history-header") { HistoryHeader(loggedDoseCount = state.loggedDoseCount) }
    item(key = "history-filters") {
        HistoryFilterRow(selected = state.historyFilter, onAction = onAction)
    }
    if (state.history.isEmpty() && !state.isLoading) {
        item(key = "history-empty") {
            Text(
                text = stringResource(
                    if (state.historyFilter == HistoryStatusFilter.ALL) {
                        R.string.compound_detail_history_empty
                    } else {
                        R.string.compound_detail_history_empty_filtered
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(items = state.history, key = { it.eventId }) { entry ->
        HistoryRow(entry = entry, onAction = onAction)
    }
}

/** §4.3.9: the sticky dock — Log dose, then Adjust. Spans the detail pane, never the list pane. */
@Suppress("FunctionName")
@Composable
private fun CompoundDetailDock(onAction: (CompoundDetailAction) -> Unit) {
    Column {
        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SCREEN_PADDING, vertical = CARD_GAP),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onAction(CompoundDetailAction.OnLogDoseClick) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painter = StaxIcons.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.compound_detail_log_dose),
                        modifier = Modifier.padding(start = DOCK_ICON_GAP),
                    )
                }
                FilledTonalButton(onClick = { onAction(CompoundDetailAction.OnAdjustClick) }) {
                    Icon(painter = StaxIcons.Inventory2, contentDescription = null)
                    Text(
                        text = stringResource(R.string.compound_detail_adjust),
                        modifier = Modifier.padding(start = DOCK_ICON_GAP),
                    )
                }
            }
        }
    }
}

/**
 * The pane width at which §6.4.2's two-column detail layout starts paying for itself: below it the
 * `0.55 / 0.45` split leaves the history rows under `300dp`, which wraps every one of them onto three
 * lines. Measured against the pane, not the window — see [CompoundDetailScreen].
 */
private val TWO_COLUMN_MIN_WIDTH = 720.dp

/** §6.4.2: cards left, history right. */
private const val LEFT_COLUMN_WEIGHT = 0.55f
private const val RIGHT_COLUMN_WEIGHT = 0.45f

private val DOCK_ICON_GAP = 8.dp

@Preview(name = "Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium detail pane", showBackground = true, widthDp = 540, heightDp = 841)
@Preview(name = "Expanded detail pane", showBackground = true, widthDp = 880, heightDp = 900)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun CompoundDetailScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            CompoundDetailScreen(state = previewState(), onAction = {})
        }
    }
}

@Preview(name = "Nothing open · no protocol", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun CompoundDetailScreenEmptyPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            CompoundDetailScreen(
                state = previewState().copy(
                    stats = CompoundStatsUi(dosesLeft = null, daysLeft = null, expiry = null),
                    opened = null,
                    protocols = persistentListOf(),
                    notes = null,
                    loggedDoseCount = 0,
                    history = persistentListOf(),
                ),
                onAction = {},
            )
        }
    }
}

private fun previewState(): CompoundDetailState = CompoundDetailState(
    name = "Semaglutide",
    category = CompoundCategory.PEPTIDE,
    stats = CompoundStatsUi(
        dosesLeft = 18,
        daysLeft = 63,
        expiry = ExpiryStatUi(LocalDate.parse("2026-07-14"), isContainerExpiry = false),
    ),
    opened = OpenedContainerUi(
        containerType = ContainerType.VIAL,
        remaining = "3.2",
        capacity = "5.0",
        unit = "mg",
        fillFraction = 0.64f,
        openedDaysAgo = 12,
    ),
    protocols = persistentListOf(
        ActiveProtocolUi(
            id = 1,
            name = "Sema weekly titration",
            scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
            scheduleValue = null,
            weekdays = persistentListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dose = "0.25 mg",
            route = Route.SUBCUTANEOUS,
            nextDoseAt = PREVIEW_NOW + 6.hours,
            nextDoseHasTime = true,
        ),
    ),
    notes = "Pre-mixed with 2 mL BAC water. Reconstituted May 14 — keep refrigerated and use " +
        "within 28 days of opening.",
    loggedDoseCount = 24,
    history = previewHistory(),
    isLoading = false,
)

private fun previewHistory() = persistentListOf(
    HistoryEntryUi(
        eventId = 1,
        loggedAt = PREVIEW_NOW,
        status = AdministrationEventStatus.TAKEN,
        dose = "0.25 mg",
        volume = "0.10 ml",
        siteName = "Abdomen R",
    ),
    HistoryEntryUi(
        eventId = 2,
        loggedAt = PREVIEW_NOW - 7.days,
        status = AdministrationEventStatus.PARTIAL,
        dose = "0.25 mg",
        volume = "0.10 ml",
        siteName = "Abdomen R",
    ),
    HistoryEntryUi(
        eventId = 3,
        loggedAt = PREVIEW_NOW - 14.days,
        status = AdministrationEventStatus.SKIPPED,
        dose = "0.25 mg",
        volume = "0.10 ml",
        siteName = null,
    ),
)

/** Previews render outside a clock the test can move, so their "now" is simply the real one. */
private val PREVIEW_NOW = Clock.System.now()
