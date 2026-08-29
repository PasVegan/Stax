package com.stax.feature.sites.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.Sublocation
import com.stax.core.presentation.ObserveAsEvents
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

/**
 * Root of the Sites screen (§10.1): holds the [SitesViewModel] and turns its navigation events into
 * the callbacks `:app` wired into the entry (§10.3).
 */
@Suppress("FunctionName")
@Composable
fun SitesRoot(
    onUseSite: (Long) -> Unit,
    onPickAnotherSite: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SitesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events, key1 = onUseSite, key2 = onPickAnotherSite) { event ->
        when (event) {
            is SitesEvent.UseSite -> onUseSite(event.siteId)
            SitesEvent.PickAnotherSite -> onPickAnotherSite()
        }
    }

    SitesScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Sites (§4.12): the route chips, the Ready / Cooling / This month strip, the body map, the suggested
 * site and the recent-activity carousel.
 *
 * The arrangement is measured on the **pane**, not the window (the rule §6.4.2 sets for the Compound
 * detail, for the same reason): this screen opens beside the navigation rail, so an Expanded window
 * hands it a good deal less than its own width and the thresholds below are what the content needs
 * rather than where the breakpoints fall.
 *
 * - under [TWO_PANE_MIN_WIDTH]: one column, in §6.4.2's order.
 * - above it: the map on the left, everything that reads against it on the right — §6.4.2's Medium
 *   two-pane.
 * - on an Expanded window with at least [BOTH_BODIES_MIN_WIDTH] of pane: the same two panes, with
 *   Front **and** Back drawn side by side in the left one and the tabs gone (§6.4.2 Expanded). Both
 *   conditions, because the breakpoint alone is not enough — the expanded navigation rail takes
 *   about `235dp`, so an Expanded window at its lower bound leaves this pane under `680dp`.
 */
@Suppress("FunctionName")
@Composable
fun SitesScreen(state: SitesState, onAction: (SitesAction) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // The pane opens with its own app bar, so the status bar is the bar's to claim and draw
            // its container behind (§2.3.6). The pane still takes the sides and the bottom.
            .paneInsets(claimTop = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SitesTopBar()
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                if (maxWidth < TWO_PANE_MIN_WIDTH) {
                    SingleColumn(state = state, onAction = onAction)
                } else {
                    TwoPane(
                        state = state,
                        onAction = onAction,
                        paneWidth = maxWidth,
                        // §6.4.2 puts both body views on an Expanded *window*; the pane floor is
                        // what keeps that honest once the expanded rail has taken its ~235dp, since
                        // two silhouettes narrower than their own dots are worse than tabs.
                        showBothViews = isExpandedWidth() && maxWidth >= BOTH_BODIES_MIN_WIDTH,
                    )
                }
            }
        }
    }
}

/** §4.12.1: the decorative leading `history` icon and the title. */
@Suppress("FunctionName")
@Composable
private fun SitesTopBar(modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.sites_title)) },
        modifier = modifier,
        navigationIcon = {
            // Decorative, per §4.12.1 — so it is an icon and not an `IconButton`: a 48dp target that
            // does nothing is worse than no target at all.
            Icon(
                painter = StaxIcons.History,
                contentDescription = null,
                modifier = Modifier.padding(horizontal = SCREEN_PADDING),
            )
        },
    )
}

/** §6.4.2 Compact: stats → body map → suggested → recent, in one scroll. */
@Suppress("FunctionName")
@Composable
private fun SingleColumn(state: SitesState, onAction: (SitesAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        RouteFilterRow(selected = state.routeFilter, onAction = onAction)
        Column(
            modifier = Modifier.padding(horizontal = SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            SitesStatsStrip(state = state, isVertical = false)
            BodyMapHero(state = state, showBothViews = false, onAction = onAction)
            SuggestedSiteHero(suggested = state.suggested, onAction = onAction)
        }
        // Full-bleed: §4.12.6's carousel scrolls off the edge of the pane rather than stopping short
        // of it, which is how a row of cards says there are more of them.
        RecentActivitySection(recent = state.recent, isVertical = false)
    }
}

/**
 * §6.4.2 Medium and Expanded: the map on the left, the numbers and the decision on the right.
 *
 * The chips span both panes because §4.12.2 filters both — a chip row inside one of them would look
 * like it only narrowed that one. The two panes scroll independently, which is the point of the
 * split: the map holds still while the user reads down the right-hand column.
 */
@Suppress("FunctionName")
@Composable
private fun TwoPane(
    state: SitesState,
    onAction: (SitesAction) -> Unit,
    paneWidth: Dp,
    showBothViews: Boolean,
    modifier: Modifier = Modifier,
) {
    val rightWidth = rightPaneWidth(paneWidth)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        RouteFilterRow(selected = state.routeFilter, onAction = onAction)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SCREEN_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            ScrollingPane(modifier = Modifier.weight(1f)) {
                BodyMapHero(state = state, showBothViews = showBothViews, onAction = onAction)
            }
            ScrollingPane(modifier = Modifier.width(rightWidth)) {
                SitesStatsStrip(state = state, isVertical = true)
                SuggestedSiteHero(suggested = state.suggested, onAction = onAction)
                RecentActivitySection(
                    recent = state.recent,
                    // §6.4.2: the carousel becomes a vertical list once the pane is too narrow for a
                    // card that still reads as one.
                    isVertical = rightWidth < CAROUSEL_MIN_WIDTH,
                    contentPadding = PaddingValues(0.dp),
                )
            }
        }
    }
}

/**
 * §6.4.2's right pane: the ~45% share it asks for at Medium, capped at the `400dp` it asks for at
 * Expanded. One rule for both, because the cap is what the two arrangements actually differ by — and
 * everything the map does not need is width the map would only pad with.
 */
private fun rightPaneWidth(paneWidth: Dp): Dp =
    minOf((paneWidth - SCREEN_PADDING * 2 - CARD_GAP) * RIGHT_PANE_WEIGHT, RIGHT_PANE_MAX_WIDTH)

@Suppress("FunctionName")
@Composable
private fun ScrollingPane(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        content = content,
    )
}

/** §6.4.0's Expanded class (840dp+), which is the width §6.4.2 puts both body views at. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isExpandedWidth(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

// ---------------------------------------------------------------------------
// Layout thresholds (§6.4.2 Sites)
// ---------------------------------------------------------------------------

/** Below this the map and the column that reads against it cannot both be legible side by side. */
private val TWO_PANE_MIN_WIDTH = 520.dp

/** What Front + Back at ~180dp each plus the right pane and the gaps between them need. */
private val BOTH_BODIES_MIN_WIDTH = 640.dp

/** §6.4.2 Expanded's right pane, which is a cap rather than a width — see [rightPaneWidth]. */
private val RIGHT_PANE_MAX_WIDTH = 400.dp

/** §6.4.2 Medium's ~55 / ~45 split. */
private const val RIGHT_PANE_WEIGHT = 0.45f

/** §6.4.2: under this the carousel is a vertical list. */
private val CAROUSEL_MIN_WIDTH = 360.dp

// ---------------------------------------------------------------------------
// Previews (§6.4.8 profiles)
// ---------------------------------------------------------------------------

@Preview(name = "Compact · Pixel 10 portrait", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium · Fold inner portrait", showBackground = true, widthDp = 673, heightDp = 841)
@Preview(name = "Expanded · Pixel 10 landscape", showBackground = true, widthDp = 914, heightDp = 411)
@Preview(name = "Expanded · Tablet landscape", showBackground = true, widthDp = 1060, heightDp = 800)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SitesScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SitesScreen(state = previewState(), onAction = {})
        }
    }
}

@Preview(name = "Heat mode · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SitesScreenHeatPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SitesScreen(state = previewState().copy(mapMode = MapMode.HEAT), onAction = {})
        }
    }
}

@Preview(name = "Nothing ready · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SitesScreenNoSuggestionPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SitesScreen(
                state = SitesState(isLoading = false, coolingCount = 14),
                onAction = {},
            )
        }
    }
}

private fun previewState(): SitesState {
    val sites = listOf(
        previewSite(1, "Abdomen Lower-Right", BodyRegion.ABDOMEN, Sublocation.LOWER, SiteStatus.SUGGESTED, 14, 0.1f),
        previewSite(2, "Abdomen Upper-Left", BodyRegion.ABDOMEN, Sublocation.UPPER, SiteStatus.COOLING, 2, 1f),
        previewSite(3, "Abdomen Upper-Right", BodyRegion.ABDOMEN, Sublocation.UPPER, SiteStatus.RECENT, 5, 0.6f),
        previewSite(4, "Lateral Thigh Left", BodyRegion.QUADRICEPS, Sublocation.OUTER, SiteStatus.READY, 8, 0.3f),
        previewSite(5, "Anterior Deltoid Right", BodyRegion.DELT, null, SiteStatus.READY, null, 0f),
    )
    val back = listOf(
        previewSite(6, "Glute Upper-Outer Left", BodyRegion.GLUTE, Sublocation.UPPER, SiteStatus.READY, 21, 0.4f),
        previewSite(7, "Hamstring Right", BodyRegion.HAMSTRING, null, SiteStatus.READY, null, 0f),
    )
    return SitesState(
        readyCount = 12,
        coolingCount = 3,
        usesThisMonth = 42,
        frontSites = sites.toImmutableList(),
        backSites = back.toImmutableList(),
        suggested = SuggestedSiteUi(id = 1, name = "Abdomen · Lower right", daysRested = 14, isCoolingComplete = true),
        recent = (sites.drop(1) + back).sortedBy { it.daysSinceLastUse ?: Int.MAX_VALUE }.toImmutableList(),
        isLoading = false,
    )
}

@Suppress("LongParameterList")
private fun previewSite(
    id: Long,
    name: String,
    bodyRegion: BodyRegion,
    sublocation: Sublocation?,
    status: SiteStatus,
    daysSinceLastUse: Int?,
    heat: Float,
) = SiteUi(
    id = id,
    name = name,
    bodyRegion = bodyRegion,
    side = InjectionSide.LEFT,
    sublocation = sublocation,
    status = status,
    daysSinceLastUse = daysSinceLastUse,
    heat = heat,
)
