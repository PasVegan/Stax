package com.stax.feature.sites.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stax.core.design.system.StaxIcons
import kotlinx.collections.immutable.ImmutableList

// ---------------------------------------------------------------------------
// §4.12.2 Route filter chips
// ---------------------------------------------------------------------------

/**
 * All routes / SC / IM, single select (§4.12.2).
 *
 * The row scrolls rather than wraps: at the widths §6.4.2 gives the right pane the three chips are a
 * close fit, and a chip that dropped to a second line would push the stats strip down with it.
 */
@Suppress("FunctionName")
@Composable
internal fun RouteFilterRow(
    selected: RouteFilter,
    onAction: (SitesAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = SCREEN_PADDING),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        items(RouteFilter.entries) { filter ->
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onAction(SitesAction.OnRouteFilterClick(filter)) },
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

internal fun RouteFilter.labelRes(): Int = when (this) {
    RouteFilter.ALL -> R.string.sites_filter_all
    RouteFilter.SUBCUTANEOUS -> R.string.sites_filter_subcutaneous
    RouteFilter.INTRAMUSCULAR -> R.string.sites_filter_intramuscular
}

// ---------------------------------------------------------------------------
// §4.12.3 Stats strip
// ---------------------------------------------------------------------------

/**
 * Ready / Cooling / This month (§4.12.3).
 *
 * [isVertical] is §6.4.2's Medium right pane, where the three tiles stack instead of sharing a row —
 * a 320dp pane split three ways leaves each tile too narrow for its own label.
 */
@Suppress("FunctionName")
@Composable
internal fun SitesStatsStrip(state: SitesState, isVertical: Boolean, modifier: Modifier = Modifier) {
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { tileModifier: Modifier ->
            StatTile(
                icon = StaxIcons.CheckCircle,
                label = stringResource(R.string.sites_stat_ready),
                value = state.readyCount.toString(),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = tileModifier,
            )
        },
        { tileModifier: Modifier ->
            StatTile(
                icon = StaxIcons.RestartAlt,
                label = stringResource(R.string.sites_stat_cooling),
                value = state.coolingCount.toString(),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = tileModifier,
            )
        },
        { tileModifier: Modifier ->
            StatTile(
                icon = StaxIcons.Bolt,
                label = stringResource(R.string.sites_stat_month),
                value = state.usesThisMonth.toString(),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = tileModifier,
            )
        },
    )

    if (isVertical) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            tiles.forEach { tile -> tile(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            tiles.forEach { tile -> tile(Modifier.weight(1f)) }
        }
    }
}

/** One §4.12.3 tile: the icon and label on top, the count under them. */
@Suppress("FunctionName", "LongParameterList")
@Composable
private fun StatTile(
    icon: Painter,
    label: String,
    value: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(STAT_ICON_SIZE))
                // Three tiles on a Compact phone leave each about 120dp, and "This month" needs
                // most of it: the label shrinks to fit rather than ellipsizing into "This m…",
                // which names no tile at all.
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = LABEL_MIN_FONT_SIZE,
                        maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// §4.12.4 Body map hero
// ---------------------------------------------------------------------------

/**
 * The body map (§4.12.4): the Front / Back tabs and the Dots / Heat toggle across the top, the
 * silhouette itself, and the legend of whichever mode is on.
 *
 * [showBothViews] is §6.4.2's Expanded arrangement — Front and Back side by side rather than tabbed —
 * so the tabs go away with it: two silhouettes on screen is what the tabs were for.
 */
@Suppress("FunctionName")
@Composable
internal fun BodyMapHero(
    state: SitesState,
    showBothViews: Boolean,
    onAction: (SitesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            BodyMapControls(state = state, showBothViews = showBothViews, onAction = onAction)
            if (showBothViews) {
                Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                    BodyView.entries.forEach { view ->
                        BodySlot(state = state, view = view, onAction = onAction, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                BodySlot(state = state, view = state.bodyView, onAction = onAction)
            }
            BodyMapLegend(mode = state.mapMode)
        }
    }
}

/**
 * The tabs and the toggle. Both are single-select, and both are the screen's state, not the map's.
 *
 * They wrap rather than share one line at every width: §6.4.2's Medium left pane is around 290dp, and
 * two segmented rows squeezed into that clip their own labels — "Front" under the first divider says
 * nothing the tab was for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionName")
@Composable
private fun BodyMapControls(
    state: SitesState,
    showBothViews: Boolean,
    onAction: (SitesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (!showBothViews) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                BodyView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = state.bodyView == view,
                        onClick = { onAction(SitesAction.OnBodyViewClick(view)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = BodyView.entries.size),
                    ) {
                        Text(text = stringResource(view.labelRes()), maxLines = 1)
                    }
                }
            }
        }
        // Alone on its line once the tabs are gone, the toggle sizes to its own labels: a Dots / Heat
        // pair stretched across an Expanded left pane reads as the map's own header, not a control.
        SingleChoiceSegmentedButtonRow(modifier = if (showBothViews) Modifier else Modifier.weight(1f)) {
            MapMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mapMode == mode,
                    onClick = { onAction(SitesAction.OnMapModeClick(mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = MapMode.entries.size),
                    icon = {
                        Icon(
                            painter = mode.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(SEGMENT_ICON_SIZE),
                        )
                    },
                ) {
                    Text(text = stringResource(mode.labelRes()), maxLines = 1)
                }
            }
        }
    }
}

/**
 * One silhouette, sized from the room it is given (§6.4.2 "scale to allocated bounds").
 *
 * Capped in height rather than simply filling the width: a Compact phone would otherwise hand the map
 * a 1.4:1 slot most of a screen tall, pushing §4.12.5's hero — the screen's actual answer — below the
 * fold on every launch.
 */
@Suppress("FunctionName")
@Composable
private fun BodySlot(
    state: SitesState,
    view: BodyView,
    onAction: (SitesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BodyMap(
            view = view,
            sites = state.sitesOn(view),
            mode = state.mapMode,
            onSiteClick = { siteId -> onAction(SitesAction.OnSiteClick(siteId)) },
            modifier = Modifier
                .heightIn(max = BODY_MAX_HEIGHT)
                // Height first: a standing figure is sized by how tall the hero can afford to be,
                // and a width-first ratio inside a scrolling column has no height to be capped by.
                .aspectRatio(BODY_ASPECT_RATIO, matchHeightConstraintsFirst = true),
        )
    }
}

/** §4.12.4's legend — the four dot states in Dots mode, the four heat bands in Heat mode. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionName")
@Composable
private fun BodyMapLegend(mode: MapMode, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LEGEND_GAP),
        verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
    ) {
        legendEntries(mode).forEach { (labelRes, color) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(SWATCH_SIZE).background(color = color, shape = CircleShape))
                Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/**
 * The swatches, in §4.12.4's order.
 *
 * Heat mode samples the map's own ramp ([heatAlpha]) at four points rather than listing four
 * opacities of its own: the swatches are the ink the blobs are drawn in, and a key mixed separately
 * from the map it explains drifts off it the first time either is tuned.
 */
@Composable
private fun legendEntries(mode: MapMode): List<Pair<Int, Color>> = when (mode) {
    MapMode.DOTS -> listOf(
        R.string.sites_legend_suggested to MaterialTheme.colorScheme.primary,
        R.string.sites_legend_cooling to MaterialTheme.colorScheme.error,
        R.string.sites_legend_recent to MaterialTheme.colorScheme.secondary,
        R.string.sites_legend_ready to MaterialTheme.colorScheme.outline,
    )

    MapMode.HEAT -> HEAT_LEGEND_BANDS.map { (labelRes, heat) ->
        labelRes to MaterialTheme.colorScheme.error.copy(alpha = heatAlpha(heat))
    }
}

internal fun BodyView.labelRes(): Int = when (this) {
    BodyView.FRONT -> R.string.sites_body_front
    BodyView.BACK -> R.string.sites_body_back
}

internal fun MapMode.labelRes(): Int = when (this) {
    MapMode.DOTS -> R.string.sites_map_dots
    MapMode.HEAT -> R.string.sites_map_heat
}

@Composable
private fun MapMode.icon(): Painter = when (this) {
    MapMode.DOTS -> StaxIcons.PersonPinCircle
    MapMode.HEAT -> StaxIcons.Bolt
}

// ---------------------------------------------------------------------------
// §4.12.5 Suggested site hero
// ---------------------------------------------------------------------------

/**
 * The rotation's next pick (§4.12.5), with the two facts behind it and the two ways to act on it.
 *
 * With nothing ready the same card states that instead: every site cooling at once is a real state of
 * a tight rotation, and an absent hero would read as a screen that failed to load.
 */
@Suppress("FunctionName")
@Composable
internal fun SuggestedSiteHero(
    suggested: SuggestedSiteUi?,
    onAction: (SitesAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            if (suggested == null) {
                Text(
                    text = stringResource(R.string.sites_suggested_none),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.sites_suggested_none_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                SuggestedSiteHeader(suggested = suggested)
                SuggestedSiteFacts(suggested = suggested)
                SuggestedSiteActions(onAction = onAction)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun SuggestedSiteHeader(suggested: SuggestedSiteUi, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(AVATAR_SIZE),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painter = StaxIcons.PersonPinCircle, contentDescription = null)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
            TagPill(text = stringResource(R.string.sites_suggested_tag), icon = StaxIcons.Bolt)
            Text(
                text = suggested.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "14 days rested" + "Cooling complete" (§4.12.5). Both are claims about this site, so both are earned. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionName")
@Composable
private fun SuggestedSiteFacts(suggested: SuggestedSiteUi, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        TagPill(
            text = suggested.daysRested
                ?.let { pluralStringResource(R.plurals.sites_suggested_rested, it, it) }
                ?: stringResource(R.string.sites_suggested_never_used),
            icon = StaxIcons.Schedule,
        )
        if (suggested.isCoolingComplete) {
            TagPill(text = stringResource(R.string.sites_suggested_cooling_complete), icon = StaxIcons.CheckCircle)
        }
    }
}

/**
 * §4.12.5's action row. "Use this site" inverts the card's own colours — `on-primary-container` behind
 * `primary-container` text — because a filled `primary` button on a `primary-container` card is two
 * shades of the same thing and reads as a label, not a button.
 *
 * The two wrap onto separate lines rather than share a narrow pane: the CTA carries the site the
 * whole screen just argued for, and "Us…" is not a button anyone presses on purpose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionName")
@Composable
private fun SuggestedSiteActions(onAction: (SitesAction) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onAction(SitesAction.OnUseSuggestedSiteClick) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                contentColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            contentPadding = BUTTON_PADDING,
        ) {
            Icon(painter = StaxIcons.ArrowForward, contentDescription = null)
            Text(
                text = stringResource(R.string.sites_suggested_use),
                modifier = Modifier.padding(start = ICON_GAP),
                maxLines = 1,
            )
        }
        TextButton(
            onClick = { onAction(SitesAction.OnPickAnotherSiteClick) },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(text = stringResource(R.string.sites_suggested_pick_another), maxLines = 1)
        }
    }
}

/** The filled pill both §4.12.5 rows use: an icon, a short label, and the card's accent behind them. */
@Suppress("FunctionName")
@Composable
private fun TagPill(text: String, icon: Painter, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(PILL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(PILL_ICON_SIZE))
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// §4.12.6 Recent activity carousel
// ---------------------------------------------------------------------------

/**
 * The sites the rotation has been through lately (§4.12.6).
 *
 * [isVertical] is §6.4.2's "now a vertical list when narrower than 360dp": a square card that has to
 * fit a site name and a date is not a card at 120dp of pane, so the carousel unrolls into rows.
 */
@Suppress("FunctionName")
@Composable
internal fun RecentActivitySection(
    recent: ImmutableList<SiteUi>,
    isVertical: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = SCREEN_PADDING),
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        Text(
            text = stringResource(R.string.sites_recent_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(contentPadding),
        )
        when {
            recent.isEmpty() -> Text(
                text = stringResource(R.string.sites_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(contentPadding),
            )

            isVertical -> Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) {
                recent.forEach { site -> RecentSiteCard(site = site, modifier = Modifier.fillMaxWidth()) }
            }

            else -> LazyRow(
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) {
                items(items = recent, key = { it.id }) { site ->
                    RecentSiteCard(site = site, modifier = Modifier.width(RECENT_CARD_WIDTH))
                }
            }
        }
    }
}

/** One carousel card (§4.12.6): the status avatar, the site, and how long ago it was last used. */
@Suppress("FunctionName")
@Composable
private fun RecentSiteCard(site: SiteUi, modifier: Modifier = Modifier) {
    val isCooling = site.status == SiteStatus.COOLING
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (isCooling) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (isCooling) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
        ) {
            Surface(
                modifier = Modifier.size(AVATAR_SIZE),
                shape = CircleShape,
                color = if (isCooling) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
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
                    )
                }
            }
            Text(
                text = site.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = daysAgoLabel(site.daysSinceLastUse),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** "2 days ago", or "Today" — a dose logged this morning is not "0 days ago" in anyone's reading. */
@Composable
private fun daysAgoLabel(days: Int?): String = when {
    days == null -> stringResource(R.string.sites_suggested_never_used)
    days == 0 -> stringResource(R.string.sites_recent_today)
    else -> pluralStringResource(R.plurals.sites_recent_days_ago, days, days)
}

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------

internal val SCREEN_PADDING = 16.dp
internal val CARD_GAP = 12.dp
internal val CHIP_GAP = 8.dp
private val CARD_PADDING = 16.dp
private val LABEL_GAP = 4.dp
private val ICON_GAP = 8.dp
private val LEGEND_GAP = 12.dp
private val STAT_ICON_SIZE = 18.dp

/** The floor a stat label shrinks to before it ellipsizes; an `sp` value, so it still tracks font scale. */
private val LABEL_MIN_FONT_SIZE = 9.sp
private val SEGMENT_ICON_SIZE = 18.dp
private val PILL_ICON_SIZE = 16.dp
private val SWATCH_SIZE = 10.dp
private val AVATAR_SIZE = 44.dp
private val PILL_PADDING = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
private val BUTTON_PADDING = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/** §4.12.6's square card. Two lines of site name at `titleSmall` need about this much. */
private val RECENT_CARD_WIDTH = 168.dp

/** §4.12.4's figure draws into `BodyArt`'s viewport, so the slot is that viewport's own proportion. */
private const val BODY_ASPECT_RATIO = BodyArt.VIEWPORT_WIDTH / BodyArt.VIEWPORT_HEIGHT
private val BODY_MAX_HEIGHT = 344.dp

/**
 * Heat mode's four bands (§4.12.4), as shares of the busiest site: the one the rotation leans on, one
 * still cooling from a recent dose, one used once a while back, and one untouched in 30 days.
 */
private val HEAT_LEGEND_BANDS = listOf(
    R.string.sites_heat_recent to 1f,
    R.string.sites_heat_cooling to 0.6f,
    R.string.sites_heat_older to 0.25f,
    R.string.sites_heat_untouched to 0f,
)
