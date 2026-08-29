package com.stax.feature.protocols.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.core.presentation.ObserveAsEvents
import com.stax.core.presentation.asString
import com.stax.feature.protocols.presentation.R
import com.stax.feature.protocols.presentation.list.ProtocolPill
import com.stax.feature.protocols.presentation.list.labelRes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Root of Protocol Detail (§10.1): holds the [ProtocolDetailViewModel] and turns its navigation
 * events into the callbacks `:app` wired into the entry (§10.3).
 *
 * [args] goes to the ViewModel rather than to the screen: which protocol this is decides what is
 * loaded and what Pause / Archive write to, none of which is the composable's business.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
fun ProtocolDetailRoot(
    args: ProtocolDetailArgs,
    onBack: () -> Unit,
    onEditProtocol: (Long) -> Unit,
    onCompoundClick: (Long) -> Unit,
    onLogDose: (Long) -> Unit,
    onAdministrationEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProtocolDetailViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history = viewModel.history.collectAsLazyPagingItems()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events, key1 = onBack, key2 = onCompoundClick) { event ->
        when (event) {
            ProtocolDetailEvent.NavigateBack -> onBack()
            is ProtocolDetailEvent.NavigateToEditProtocol -> onEditProtocol(event.protocolId)
            is ProtocolDetailEvent.NavigateToCompound -> onCompoundClick(event.compoundId)
            is ProtocolDetailEvent.NavigateToLogDose -> onLogDose(event.protocolId)
            is ProtocolDetailEvent.NavigateToAdministrationEvent -> onAdministrationEventClick(event.eventId)
            is ProtocolDetailEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }

            is ProtocolDetailEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }
        }
    }

    ProtocolDetailScreen(
        state = state,
        history = history,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Protocol Detail (§4.8): the quick action chips, the Schedule, Linked compound, Inventory forecast,
 * Site restrictions, Dose history and Notes cards, over a sticky dock holding Log dose and Archive.
 *
 * This is the **detail pane** of the Protocols list-detail Scene (§6.4.2), so the two-column switch
 * is measured against the pane's own width and not the window's — at the Expanded lower bound the
 * pane is what is left after a `400dp` list pane and the navigation rail, which is narrower than a
 * Compact phone and has no business being split (the same [TWO_COLUMN_MIN_WIDTH] rule §4.3 uses).
 *
 * [history] arrives separately from [state] because it is paged (§4.8.7).
 */
@Suppress("FunctionName")
@Composable
fun ProtocolDetailScreen(
    state: ProtocolDetailState,
    history: LazyPagingItems<ProtocolHistoryEntryUi>,
    onAction: (ProtocolDetailAction) -> Unit,
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
            ProtocolDetailTopBar(state = state, onAction = onAction)
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                if (maxWidth >= TWO_COLUMN_MIN_WIDTH) {
                    TwoColumnContent(state = state, history = history, onAction = onAction)
                } else {
                    SingleColumnContent(state = state, history = history, onAction = onAction)
                }
            }
            ProtocolDetailDock(onAction = onAction)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.isArchiveDialogOpen) {
        ArchiveDialog(
            onConfirm = { onAction(ProtocolDetailAction.OnArchiveConfirm) },
            onDismiss = { onAction(ProtocolDetailAction.OnArchiveDismiss) },
        )
    }
}

/** §4.8.1: leading `arrow_back`, the protocol's name, then "{status} · {compound}" underneath. */
@Suppress("FunctionName")
@Composable
private fun ProtocolDetailTopBar(state: ProtocolDetailState, onAction: (ProtocolDetailAction) -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(text = state.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                Text(
                    // A protocol outlives an archived compound (§4.7.2); the line then states the
                    // status alone rather than leaving a separator with nothing after it.
                    text = state.compoundName
                        ?.let { stringResource(R.string.protocol_detail_supporting, statusLabel(state.pill), it) }
                        ?: statusLabel(state.pill),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { onAction(ProtocolDetailAction.OnBackClick) }) {
                Icon(
                    painter = StaxIcons.ArrowBack,
                    contentDescription = stringResource(R.string.protocol_detail_back),
                )
            }
        },
    )
}

/**
 * §4.8.2: Pause / Edit / Duplicate as three outlined pills.
 *
 * They scroll rather than wrap: three chips fit a Compact phone, but not the `360dp` detail pane a
 * Medium window gives this screen (§6.4.2), and a chip half off the edge reads as a layout bug.
 */
@Suppress("FunctionName")
@Composable
private fun QuickActionChips(
    isPaused: Boolean,
    onAction: (ProtocolDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
        item(key = "pause") {
            QuickActionChip(
                icon = if (isPaused) StaxIcons.PlayArrow else StaxIcons.Pause,
                label = stringResource(
                    if (isPaused) R.string.protocol_detail_resume else R.string.protocol_detail_pause,
                ),
                onClick = { onAction(ProtocolDetailAction.OnPauseClick) },
            )
        }
        item(key = "edit") {
            QuickActionChip(
                icon = StaxIcons.Edit,
                label = stringResource(R.string.protocol_detail_edit),
                onClick = { onAction(ProtocolDetailAction.OnEditClick) },
            )
        }
        item(key = "duplicate") {
            QuickActionChip(
                icon = StaxIcons.AddCircle,
                label = stringResource(R.string.protocol_detail_duplicate),
                onClick = { onAction(ProtocolDetailAction.OnDuplicateClick) },
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun QuickActionChip(icon: Painter, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(painter = icon, contentDescription = null)
        Text(text = label, modifier = Modifier.padding(start = ICON_GAP), maxLines = 1)
    }
}

/** Compact and Medium (§6.4.2): one column, every card an item of the same `LazyColumn`. */
@Suppress("FunctionName")
@Composable
private fun SingleColumnContent(
    state: ProtocolDetailState,
    history: LazyPagingItems<ProtocolHistoryEntryUi>,
    onAction: (ProtocolDetailAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        item(key = "chips") { QuickActionChips(isPaused = state.isPaused, onAction = onAction) }
        state.schedule?.let { schedule -> item(key = "schedule") { ScheduleCard(schedule = schedule) } }
        item(key = "compound") { LinkedCompoundCard(compound = state.compound, onAction = onAction) }
        item(key = "forecast") { ForecastCard(forecast = state.forecast) }
        state.sites?.let { sites -> item(key = "sites") { SiteRestrictionsCard(sites = sites) } }
        historySection(state = state, history = history, onAction = onAction)
        item(key = "notes") {
            NotesCard(notes = state.notes, isExpanded = state.isNotesExpanded, onAction = onAction)
        }
    }
}

/**
 * Expanded (§6.4.2): the chips span the pane; Schedule, Linked compound, Site restrictions and Notes
 * take the left column, and the forecast + history take the right one.
 *
 * The two scroll independently — which is the point of the split, since the schedule stays put while
 * the user works down the history.
 */
@Suppress("FunctionName")
@Composable
private fun TwoColumnContent(
    state: ProtocolDetailState,
    history: LazyPagingItems<ProtocolHistoryEntryUi>,
    onAction: (ProtocolDetailAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        QuickActionChips(
            isPaused = state.isPaused,
            onAction = onAction,
            modifier = Modifier.padding(top = SCREEN_PADDING),
        )
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
                state.schedule?.let { ScheduleCard(schedule = it) }
                LinkedCompoundCard(compound = state.compound, onAction = onAction)
                state.sites?.let { SiteRestrictionsCard(sites = it) }
                NotesCard(notes = state.notes, isExpanded = state.isNotesExpanded, onAction = onAction)
                Box(modifier = Modifier.padding(bottom = SCREEN_PADDING))
            }
            LazyColumn(
                modifier = Modifier.weight(RIGHT_COLUMN_WEIGHT),
                contentPadding = PaddingValues(bottom = SCREEN_PADDING),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                item(key = "forecast") { ForecastCard(forecast = state.forecast) }
                historySection(state = state, history = history, onAction = onAction)
            }
        }
    }
}

/**
 * §4.8.7, as list items so both layouts can place them in whichever list they own.
 *
 * The rows come off [history] one page at a time, so only what is on screen is ever composed — which
 * is what §2.3.2's scroll SLO asks of a history that has no upper bound.
 */
private fun LazyListScope.historySection(
    state: ProtocolDetailState,
    history: LazyPagingItems<ProtocolHistoryEntryUi>,
    onAction: (ProtocolDetailAction) -> Unit,
) {
    item(key = "history-header") { HistoryHeader(loggedDoseCount = state.loggedDoseCount) }
    if (history.itemCount == 0 && history.loadState.refresh is LoadState.NotLoading) {
        item(key = "history-empty") {
            Text(
                text = stringResource(R.string.protocol_detail_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(count = history.itemCount, key = history.itemKey { it.eventId }) { index ->
        history[index]?.let { entry -> HistoryRow(entry = entry, onAction = onAction) }
    }
}

/** §4.8.9: the sticky dock — Log dose, then Archive. Spans the detail pane, never the list pane. */
@Suppress("FunctionName")
@Composable
private fun ProtocolDetailDock(onAction: (ProtocolDetailAction) -> Unit) {
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
                    onClick = { onAction(ProtocolDetailAction.OnLogDoseClick) },
                    modifier = Modifier.weight(LOG_DOSE_WEIGHT),
                    contentPadding = DOCK_BUTTON_PADDING,
                ) {
                    DockLabel(icon = StaxIcons.Add, label = stringResource(R.string.protocol_detail_log_dose))
                }
                FilledTonalButton(
                    onClick = { onAction(ProtocolDetailAction.OnArchiveClick) },
                    modifier = Modifier.weight(ARCHIVE_WEIGHT),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    contentPadding = DOCK_BUTTON_PADDING,
                ) {
                    DockLabel(icon = StaxIcons.Delete, label = stringResource(R.string.protocol_detail_archive))
                }
            }
        }
    }
}

/**
 * One dock button's contents: leading icon, one line of label.
 *
 * The label ellipsizes rather than wrapping — the two buttons share the width of a `360dp` detail
 * pane (§6.4.2), or of a cover display narrower still, and a dock that grows a second line pushes
 * the content above it off the screen.
 */
@Suppress("FunctionName")
@Composable
private fun RowScope.DockLabel(icon: Painter, label: String) {
    Icon(painter = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** §4.8.9: Archive is a soft delete, and it still asks first (§5.5). */
@Suppress("FunctionName")
@Composable
private fun ArchiveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.protocol_detail_archive)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.protocol_detail_archive_cancel)) }
        },
        title = { Text(text = stringResource(R.string.protocol_detail_archive_title)) },
        text = { Text(text = stringResource(R.string.protocol_detail_archive_supporting)) },
    )
}

@Composable
private fun statusLabel(pill: ProtocolPill): String = stringResource(pill.labelRes())

/**
 * The pane width at which §6.4.2's two-column detail layout starts paying for itself — the same
 * threshold §4.3's detail pane uses, and measured against the pane rather than the window.
 */
private val TWO_COLUMN_MIN_WIDTH = 720.dp

/** §6.4.2: schedule / compound / sites / notes left, forecast + history right. */
private const val LEFT_COLUMN_WEIGHT = 0.55f
private const val RIGHT_COLUMN_WEIGHT = 0.45f

private val CHIP_GAP = 8.dp
private val ICON_GAP = 8.dp

/** §4.8.9: Log dose is the primary action and takes the wider half of the dock. */
private const val LOG_DOSE_WEIGHT = 0.6f
private const val ARCHIVE_WEIGHT = 0.4f

/** Tighter than a button's default, so both labels still fit a narrow pane on one line. */
private val DOCK_BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Preview(name = "Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium detail pane", showBackground = true, widthDp = 540, heightDp = 841)
@Preview(name = "Expanded detail pane", showBackground = true, widthDp = 880, heightDp = 900)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ProtocolDetailScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ProtocolDetailScreen(
                state = previewState(),
                history = previewHistory().asLazyPagingItems(),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Paused · no titration · no history", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ProtocolDetailScreenPausedPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ProtocolDetailScreen(
                state = previewState().copy(
                    pill = ProtocolPill.PAUSED,
                    schedule = previewState().schedule?.copy(titration = null, dosageTimes = persistentListOf()),
                    forecast = ForecastUi(
                        dosesRemaining = null,
                        runOutDate = null,
                        requiredUntilEnd = null,
                        batchExpiry = null,
                    ),
                    notes = null,
                    loggedDoseCount = 0,
                ),
                history = emptyList<ProtocolHistoryEntryUi>().asLazyPagingItems(),
                onAction = {},
            )
        }
    }
}

private fun previewState() = ProtocolDetailState(
    name = "Sema weekly titration",
    pill = ProtocolPill.ACTIVE,
    compoundName = "Semaglutide",
    schedule = ScheduleCardUi(
        scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
        scheduleValue = null,
        weekdays = persistentListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        dosageTimes = persistentListOf(LocalTime(hour = 20, minute = 0)),
        titration = TitrationRuleUi(
            startDose = "0.25",
            targetDose = "1.0 mg",
            increaseAmount = "0.25",
            increaseEvery = EscalationIncreaseEvery.EVERY_X_WEEKS,
            increaseEveryValue = 4,
        ),
        startDate = LocalDate.parse("2026-05-01"),
        endDate = null,
        reminderOffsetMinutes = 10,
    ),
    compound = LinkedCompoundUi(
        id = 1,
        name = "Semaglutide",
        category = CompoundCategory.PEPTIDE,
        dose = "0.25 mg",
        volume = "0.1 ml",
        concentration = "2.5",
        concentrationUnit = UnitCode.MG,
        concentrationPerUnit = UnitCode.ML,
    ),
    forecast = ForecastUi(
        dosesRemaining = 18,
        runOutDate = LocalDate.parse("2026-07-28"),
        requiredUntilEnd = null,
        batchExpiry = LocalDate.parse("2026-07-14"),
    ),
    sites = SiteRestrictionsUi(region = BodyRegion.ABDOMEN, cooldownDays = 5),
    notes = "Titrating slowly to limit GI side effects. Hold dose if nausea lasts more than two days.",
    loggedDoseCount = 16,
)

/**
 * A fixed list as the paged stream the screen takes — for previews and nothing else.
 *
 * The load states are spelled out because `PagingData.from(list)` alone leaves them on their initial
 * `Loading`, which would keep §4.8.7's empty state from ever appearing.
 */
@Composable
private fun List<ProtocolHistoryEntryUi>.asLazyPagingItems(): LazyPagingItems<ProtocolHistoryEntryUi> = remember(this) {
    val loaded = LoadState.NotLoading(endOfPaginationReached = true)
    flowOf(PagingData.from(this, LoadStates(refresh = loaded, prepend = loaded, append = loaded)))
}.collectAsLazyPagingItems()

private fun previewHistory() = listOf(
    ProtocolHistoryEntryUi(
        eventId = 1,
        loggedAt = PREVIEW_NOW,
        status = AdministrationEventStatus.TAKEN,
        dose = "0.25 mg",
        volume = "0.1 ml",
        siteName = "Abdomen R",
    ),
    ProtocolHistoryEntryUi(
        eventId = 2,
        loggedAt = PREVIEW_NOW - 7.days,
        status = AdministrationEventStatus.TAKEN,
        dose = "0.25 mg",
        volume = "0.1 ml",
        siteName = "Abdomen L",
    ),
    ProtocolHistoryEntryUi(
        eventId = 3,
        loggedAt = PREVIEW_NOW - 14.days,
        status = AdministrationEventStatus.SKIPPED,
        dose = "0.25 mg",
        volume = null,
        siteName = null,
    ),
)

/** Previews render outside a clock the test can move, so their "now" is simply the real one. */
private val PREVIEW_NOW = Clock.System.now()
