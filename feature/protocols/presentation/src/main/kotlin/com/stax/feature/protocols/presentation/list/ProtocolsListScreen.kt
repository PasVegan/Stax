package com.stax.feature.protocols.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.AdaptiveFab
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.core.presentation.ObserveAsEvents
import com.stax.feature.protocols.presentation.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Instant

/**
 * Root of the Protocols list (§10.1): holds the [ProtocolsListViewModel] and turns its navigation
 * events into the callbacks `:app` wired into the entry (§10.3).
 */
@Suppress("FunctionName")
@Composable
fun ProtocolsListRoot(
    onProtocolClick: (Long) -> Unit,
    onCreateProtocol: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProtocolsListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events, key1 = onProtocolClick, key2 = onCreateProtocol) { event ->
        when (event) {
            is ProtocolsListEvent.NavigateToProtocolDetail -> onProtocolClick(event.protocolId)
            ProtocolsListEvent.NavigateToCreateProtocol -> onCreateProtocol()
        }
    }

    ProtocolsListScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Protocols list (§4.7): app bar, the four single-select filter chips, the protocol cards, and the
 * extended "New protocol" FAB.
 *
 * This is the **list pane** of the Protocols list-detail Scene (§6.4.2), so its width is the pane's
 * — `360dp` at Medium, `400dp` at Expanded — and the layout is the same at every breakpoint: one
 * full-width card per line, wrapping its own chips when the pane is too narrow for them side by
 * side.
 */
@Suppress("FunctionName")
@Composable
fun ProtocolsListScreen(
    state: ProtocolsListState,
    onAction: (ProtocolsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // The app bar below opens the pane, so the status bar is its to claim and draw its
            // container behind (§2.3.6). The pane still takes the sides and the bottom.
            .paneInsets(claimTop = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(R.string.protocols_title)) },
            )
            ProtocolsFilterRow(selected = state.filter, onAction = onAction)
            if (state.items.isEmpty() && !state.isLoading) {
                ProtocolsEmptyState(
                    filter = state.filter,
                    hasAnyProtocol = state.hasAnyProtocol,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )
            } else {
                ProtocolsList(
                    items = state.items,
                    onProtocolClick = { onAction(ProtocolsListAction.OnProtocolClick(it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AdaptiveFab(
            onClick = { onAction(ProtocolsListAction.OnCreateProtocolClick) },
            label = { Text(text = stringResource(R.string.protocols_new)) },
        ) {
            Icon(painter = StaxIcons.Add, contentDescription = null)
        }
    }
}

/** §4.7.2: Active / Paused / Completed / Archived, single select. The row scrolls — four chips never fit a Compact width. */
@Suppress("FunctionName")
@Composable
private fun ProtocolsFilterRow(
    selected: ProtocolFilter,
    onAction: (ProtocolsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SCREEN_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        items(ProtocolFilter.entries) { filter ->
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onAction(ProtocolsListAction.OnFilterClick(filter)) },
                label = { Text(text = stringResource(filter.labelRes())) },
                leadingIcon = if (isSelected) {
                    { Icon(painter = StaxIcons.Done, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

/** The cards themselves (§4.7.3). */
@Suppress("FunctionName")
@Composable
private fun ProtocolsList(
    items: List<ProtocolListItemUi>,
    onProtocolClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = SCREEN_PADDING,
            top = SCREEN_PADDING,
            end = SCREEN_PADDING,
            // Extra room at the bottom so the last card can be scrolled clear of the floating FAB.
            bottom = LIST_BOTTOM_PADDING,
        ),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        items(items = items, key = { it.id }) { item ->
            ProtocolCard(item = item, onClick = { onProtocolClick(item.id) })
        }
    }
}

/**
 * §7's empty state. An app with no protocols at all gets the hero and its CTA; a tab that is merely
 * empty gets a line saying so, because the FAB is already on screen and a second "Create protocol"
 * under every empty tab is noise.
 */
@Suppress("FunctionName")
@Composable
private fun ProtocolsEmptyState(
    filter: ProtocolFilter,
    hasAnyProtocol: Boolean,
    onAction: (ProtocolsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SCREEN_PADDING, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasAnyProtocol) {
            Text(
                text = stringResource(filter.emptyRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = stringResource(R.string.protocols_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { onAction(ProtocolsListAction.OnCreateProtocolClick) }) {
                Text(text = stringResource(R.string.protocols_new))
            }
        }
    }
}

private fun ProtocolFilter.labelRes(): Int = when (this) {
    ProtocolFilter.ACTIVE -> R.string.protocols_filter_active
    ProtocolFilter.PAUSED -> R.string.protocols_filter_paused
    ProtocolFilter.COMPLETED -> R.string.protocols_filter_completed
    ProtocolFilter.ARCHIVED -> R.string.protocols_filter_archived
}

private fun ProtocolFilter.emptyRes(): Int = when (this) {
    ProtocolFilter.ACTIVE -> R.string.protocols_empty_filtered_active
    ProtocolFilter.PAUSED -> R.string.protocols_empty_filtered_paused
    ProtocolFilter.COMPLETED -> R.string.protocols_empty_filtered_completed
    ProtocolFilter.ARCHIVED -> R.string.protocols_empty_filtered_archived
}

private val SCREEN_PADDING = 16.dp
private val CHIP_GAP = 8.dp
private val CARD_GAP = 12.dp

/** Screen padding + the extended FAB's height + its `16dp` inset (§6.4.6). */
private val LIST_BOTTOM_PADDING = 96.dp

@Preview(name = "Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium list pane", showBackground = true, widthDp = 360, heightDp = 841)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ProtocolsListScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ProtocolsListScreen(state = previewState(), onAction = {})
        }
    }
}

@Preview(name = "Empty · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ProtocolsListScreenEmptyPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ProtocolsListScreen(
                state = ProtocolsListState(isLoading = false),
                onAction = {},
            )
        }
    }
}

private fun previewState() = ProtocolsListState(
    items = persistentListOf(
        previewItem(
            id = 1,
            name = "Sema weekly titration",
            compoundName = "Semaglutide",
            dose = "0.25 mg",
            scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
            weekdays = persistentListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dosageTimes = persistentListOf(LocalTime(20, 0)),
            nextDoseAt = PREVIEW_NEXT_DOSE,
            titration = TitrationUi(current = "0.25", target = "1 mg", progress = 0.25f),
        ),
        previewItem(
            id = 2,
            name = "BPC-157 healing cycle",
            compoundName = "BPC-157",
            dose = "250 mcg",
            pill = ProtocolPill.IN_BREAK,
            dosageTimes = persistentListOf(LocalTime(8, 0), LocalTime(20, 0)),
            nextDoseAt = PREVIEW_NEXT_DOSE,
            isInBreak = true,
        ),
        previewItem(
            id = 3,
            name = "Testosterone Cyp",
            compoundName = "Test Cyp",
            dose = "100 mg",
            route = Route.INTRAMUSCULAR,
            pill = ProtocolPill.PAUSED,
            scheduleType = ScheduleType.EVERY_X_DAYS,
            scheduleValue = 7,
        ),
        previewItem(
            id = 4,
            name = "Vit D supplementation",
            compoundName = "Vitamin D3",
            dose = "4000 iu",
            route = Route.ORAL,
            pill = ProtocolPill.COMPLETED,
            dosageTimes = persistentListOf(LocalTime(9, 0)),
        ),
    ),
    hasAnyProtocol = true,
    isLoading = false,
)

@Suppress("LongParameterList")
private fun previewItem(
    id: Long,
    name: String,
    compoundName: String,
    dose: String,
    route: Route = Route.SUBCUTANEOUS,
    pill: ProtocolPill = ProtocolPill.ACTIVE,
    scheduleType: ScheduleType = ScheduleType.DAILY,
    scheduleValue: Int? = null,
    weekdays: ImmutableList<DayOfWeek> = persistentListOf(),
    dosageTimes: ImmutableList<LocalTime> = persistentListOf(),
    nextDoseAt: Instant? = null,
    isInBreak: Boolean = false,
    titration: TitrationUi? = null,
) = ProtocolListItemUi(
    id = id,
    name = name,
    compoundName = compoundName,
    dose = dose,
    route = route,
    pill = pill,
    scheduleType = scheduleType,
    scheduleValue = scheduleValue,
    weekdays = weekdays,
    dosageTimes = dosageTimes,
    nextDoseAt = nextDoseAt,
    nextDoseHasTime = nextDoseAt != null,
    isInBreak = isInBreak,
    titration = titration,
)

private val PREVIEW_NEXT_DOSE = Instant.parse("2026-06-06T20:00:00Z")
