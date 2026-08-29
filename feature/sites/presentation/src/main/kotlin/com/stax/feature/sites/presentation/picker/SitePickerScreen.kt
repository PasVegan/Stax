package com.stax.feature.sites.presentation.picker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.presentation.ObserveAsEvents
import com.stax.feature.sites.presentation.R
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Root of the site picker (§10.1): holds the [SitePickerViewModel] and turns its two one-time events
 * into the callbacks `:app` wired into the entry (§10.3).
 */
@Suppress("FunctionName")
@Composable
fun SitePickerRoot(
    args: SitePickerArgs,
    onSitePicked: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SitePickerViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events, key1 = onSitePicked, key2 = onDismiss) { event ->
        when (event) {
            is SitePickerEvent.SitePicked -> onSitePicked(event.siteId)
            SitePickerEvent.Dismissed -> onDismiss()
        }
    }

    SitePickerScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Site picker (§4.12.7): "Pick site" over the caller's dose, the All / Ready / Cooling chips, the
 * rotation's own pick, every site it is offering, and the dock that hands one back.
 *
 * One layout at every width, adapted by the grid rather than by a breakpoint: rows are as wide as a
 * site name and its meta line need ([ROW_MIN_WIDTH]) and the grid fits as many columns as the pane
 * affords — one on a phone, two or three on a tablet or an unfolded inner screen. A picker is a list
 * of one kind of thing, so there is no second pane for it to become (§6.4.2 lists no arrangement of
 * its own for this screen).
 *
 * The chips, both section headers and the suggested row span every column: they are about the whole
 * list, and a header sharing a line with a row it heads reads as one of them.
 */
@Suppress("FunctionName")
@Composable
fun SitePickerScreen(state: SitePickerState, onAction: (SitePickerAction) -> Unit, modifier: Modifier = Modifier) {
    // Back leaves the picker with nothing, which is exactly what Cancel and the app bar's arrow do.
    BackHandler { onAction(SitePickerAction.OnCancelClick) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The pane opens with its own app bar, so the status bar is the bar's to claim and draw
            // its container behind (§2.3.6). The pane still takes the sides and the bottom.
            .paneInsets(claimTop = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SitePickerTopBar(state = state, onAction = onAction)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(ROW_MIN_WIDTH),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(SCREEN_PADDING),
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PickerFilterRow(selected = state.filter, onAction = onAction)
                }
                state.suggested?.let { suggested ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(text = stringResource(R.string.sites_picker_suggested))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SiteRow(
                            site = suggested,
                            isSelected = state.selectedSiteId == suggested.id,
                            isSuggested = true,
                            onAction = onAction,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(text = stringResource(R.string.sites_picker_all_sites, state.sites.size))
                }
                if (state.sites.isEmpty() && !state.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.sites_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(items = state.sites, key = { it.id }) { site ->
                    SiteRow(
                        site = site,
                        isSelected = state.selectedSiteId == site.id,
                        isSuggested = false,
                        onAction = onAction,
                    )
                }
            }
            SitePickerDock(canPick = state.selectedSiteId != null, onAction = onAction)
        }
    }
}

/** §4.12.7's app bar: leading `arrow_back`, "Pick site", and what the caller is dosing under it. */
@Suppress("FunctionName")
@Composable
private fun SitePickerTopBar(
    state: SitePickerState,
    onAction: (SitePickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.sites_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
                state.supportingText()?.let { supporting ->
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = { onAction(SitePickerAction.OnCancelClick) }) {
                Icon(
                    painter = StaxIcons.ArrowBack,
                    contentDescription = stringResource(R.string.sites_picker_back),
                )
            }
        },
    )
}

/**
 * "For {compound} · {route}" (§4.12.7), or whichever half the caller knew.
 *
 * Null when it knew neither — §4.12.5's "Pick another" opens the picker to choose a site and nothing
 * else, and a bar reading "For" with nothing after it says less than no second line at all.
 */
@Composable
private fun SitePickerState.supportingText(): String? {
    val routeLabel = route?.let { stringResource(it.labelRes()) }
    return when {
        compoundName != null && routeLabel != null ->
            stringResource(R.string.sites_picker_supporting, compoundName, routeLabel)

        compoundName != null -> stringResource(R.string.sites_picker_supporting_one, compoundName)
        routeLabel != null -> stringResource(R.string.sites_picker_supporting_one, routeLabel)
        else -> null
    }
}

/**
 * All / Ready / Cooling, single select (§4.12.7).
 *
 * A plain `Row` and not §4.12.2's scrolling one: three one-word chips fit the narrowest pane this
 * full-screen flow is ever given, and a second scrollable inside the grid is one more thing that can
 * swallow a drag meant for the list.
 */
@Suppress("FunctionName")
@Composable
private fun PickerFilterRow(
    selected: PickerFilter,
    onAction: (SitePickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        PickerFilter.entries.forEach { filter ->
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onAction(SitePickerAction.OnFilterClick(filter)) },
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

@Suppress("FunctionName")
@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = SCREEN_PADDING),
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * One site the picker is offering (§4.12.7): its status avatar, its name and meta line, the cooling
 * countdown where there is one, and the radio that says which one the dock will hand back.
 *
 * The whole row is the target — a `48dp` radio inside a `72dp` row is a smaller thing to hit than
 * the row it sits in — and the radio is along for the ride (`onClick = null`), so a tap anywhere on
 * the row is one selection and not two.
 */
@Suppress("FunctionName")
@Composable
private fun SiteRow(
    site: PickerSiteUi,
    isSelected: Boolean,
    isSuggested: Boolean,
    onAction: (SitePickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = isSelected,
        onClick = { onAction(SitePickerAction.OnSiteClick(site.id)) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isSuggested) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (isSuggested) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        // The selected row carries a ring as well as its radio: on the suggested row the container is
        // already `primary-container`, so colour alone could not tell "recommended" from "chosen".
        border = if (isSelected) BorderStroke(SELECTION_BORDER, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusAvatar(isCooling = site.isCooling)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
            ) {
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = lastUsedLabel(site.daysSinceLastUse),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSuggested) {
                RowPill(
                    text = stringResource(R.string.sites_picker_best),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
            }
            site.daysCoolingRemaining?.let { days ->
                RowPill(
                    text = stringResource(R.string.sites_picker_cooling_pill, days),
                    container = MaterialTheme.colorScheme.error,
                    content = MaterialTheme.colorScheme.onError,
                )
            }
            RadioButton(selected = isSelected, onClick = null)
        }
    }
}

/** §4.12.7's status dot: `error` + `restart_alt` while cooling, `secondary-container` + `check` when ready. */
@Suppress("FunctionName")
@Composable
private fun StatusAvatar(isCooling: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(AVATAR_SIZE),
        shape = CircleShape,
        color = if (isCooling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isCooling) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = if (isCooling) StaxIcons.RestartAlt else StaxIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(AVATAR_ICON_SIZE),
            )
        }
    }
}

/** The small filled pill a row carries on its right: "Best" on the suggestion, "Cool 2d" while cooling. */
@Suppress("FunctionName")
@Composable
private fun RowPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = text,
            modifier = Modifier.padding(PILL_PADDING),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/**
 * §4.12.7's dock: Cancel, then the filled "Pick site".
 *
 * Disabled until something is selected, which is what "requires selection" has to look like — the
 * button is the only thing on the screen that can return a site, so it states plainly that it has
 * none yet rather than failing silently on a tap.
 */
@Suppress("FunctionName")
@Composable
private fun SitePickerDock(canPick: Boolean, onAction: (SitePickerAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SCREEN_PADDING, vertical = CHIP_GAP),
                horizontalArrangement = Arrangement.spacedBy(SCREEN_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onAction(SitePickerAction.OnCancelClick) }) {
                    Text(text = stringResource(R.string.sites_picker_cancel))
                }
                Button(
                    onClick = { onAction(SitePickerAction.OnPickClick) },
                    modifier = Modifier.weight(1f),
                    enabled = canPick,
                ) {
                    Icon(painter = StaxIcons.Check, contentDescription = null)
                    Text(
                        text = stringResource(R.string.sites_picker_pick),
                        modifier = Modifier.padding(start = CHIP_GAP),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Labels (§4.12.7)
// ---------------------------------------------------------------------------

private fun PickerFilter.labelRes(): Int = when (this) {
    PickerFilter.ALL -> R.string.sites_picker_filter_all
    PickerFilter.READY -> R.string.sites_picker_filter_ready
    PickerFilter.COOLING -> R.string.sites_picker_filter_cooling
}

private fun PickerRoute.labelRes(): Int = when (this) {
    PickerRoute.SUBCUTANEOUS -> R.string.sites_detail_route_sc
    PickerRoute.INTRAMUSCULAR -> R.string.sites_detail_route_im
}

/** "Last used 2 days ago" / "Last used today" / "Never used" — §4.12.7's meta line. */
@Composable
private fun lastUsedLabel(days: Int?): String = when {
    days == null -> stringResource(R.string.sites_suggested_never_used)
    days == 0 -> stringResource(R.string.sites_picker_last_used_today)
    else -> pluralStringResource(R.plurals.sites_picker_last_used, days, days)
}

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------

/** What a site name, its meta line, a pill and the radio need before a second column is worth it. */
private val ROW_MIN_WIDTH = 320.dp
private val SCREEN_PADDING = 16.dp
private val CHIP_GAP = 8.dp
private val LABEL_GAP = 4.dp
private val ROW_GAP = 12.dp
private val ROW_PADDING = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
private val PILL_PADDING = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
private val AVATAR_SIZE = 40.dp
private val AVATAR_ICON_SIZE = 20.dp
private val SELECTION_BORDER = 2.dp

// ---------------------------------------------------------------------------
// Previews (§6.4.8 profiles)
// ---------------------------------------------------------------------------

@Preview(name = "Compact · Pixel 10 portrait", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium · Fold inner portrait", showBackground = true, widthDp = 673, heightDp = 841)
@Preview(name = "Expanded · Tablet landscape", showBackground = true, widthDp = 1060, heightDp = 800)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SitePickerScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SitePickerScreen(state = previewState(), onAction = {})
        }
    }
}

@Preview(name = "Nothing selected yet · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SitePickerNoSelectionPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SitePickerScreen(
                state = previewState().copy(selectedSiteId = null, compoundName = null, route = null),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Cooling filter, nothing left · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SitePickerEmptyFilterPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SitePickerScreen(
                state = previewState().copy(
                    filter = PickerFilter.COOLING,
                    sites = emptyList<PickerSiteUi>().toImmutableList(),
                    isLoading = false,
                ),
                onAction = {},
            )
        }
    }
}

private fun previewState(): SitePickerState {
    val sites = listOf(
        PickerSiteUi(id = 1, name = "Abdomen · Lower right", daysCoolingRemaining = null, daysSinceLastUse = 14),
        PickerSiteUi(id = 2, name = "Lateral Thigh · Left", daysCoolingRemaining = null, daysSinceLastUse = null),
        PickerSiteUi(id = 3, name = "Abdomen · Upper left", daysCoolingRemaining = 2, daysSinceLastUse = 2),
        PickerSiteUi(id = 4, name = "Anterior Deltoid · Right", daysCoolingRemaining = null, daysSinceLastUse = 0),
    )
    return SitePickerState(
        compoundName = "Tirzepatide",
        route = PickerRoute.SUBCUTANEOUS,
        suggested = sites.first(),
        sites = sites.toImmutableList(),
        selectedSiteId = 3,
        isLoading = false,
    )
}
