package com.stax.feature.sites.presentation

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stax.core.design.system.StaxAdaptiveSheet
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.Route
import com.stax.core.domain.Sublocation
import com.stax.core.domain.routes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import java.util.Locale as JavaLocale

/**
 * §4.12.8's site detail sheet: what the dot could not say about one site, and the two things that can
 * be done about it.
 *
 * Adaptive through [StaxAdaptiveSheet] (§6.4.2 Sites): a full-width modal bottom sheet at Compact,
 * the same sheet clamped and centred at Medium, an end-edge `360dp` side sheet at Expanded. The
 * content is one scrolling column at every width, with the action row pulled out of the scroll — at
 * Expanded the side sheet is as tall as a landscape phone, and two actions below three stats and a
 * list of uses are two actions below the fold.
 *
 * No "Use this site" here (§4.12.8): the sheet is informational and management, and picking a site to
 * dose into is §4.12.5's hero and the picker's business.
 */
@Suppress("FunctionName")
@Composable
internal fun SiteDetailSheet(detail: SiteDetailUi, onAction: (SitesAction) -> Unit, modifier: Modifier = Modifier) {
    StaxAdaptiveSheet(
        onDismissRequest = { onAction(SitesAction.OnSiteDetailDismiss) },
        modifier = modifier,
        sideSheetWidth = SIDE_SHEET_WIDTH,
    ) {
        SiteDetailContent(detail = detail, onAction = onAction)
    }
}

/**
 * The sheet's body, separate from the surface it is presented on.
 *
 * A modal sheet is a window of its own, which no `@Preview` renders — so the previews below take this
 * and the app takes the sheet, and the thing being looked at is the same either way.
 */
@Suppress("FunctionName")
@Composable
internal fun SiteDetailContent(detail: SiteDetailUi, onAction: (SitesAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SHEET_PADDING, vertical = CARD_GAP),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        // `fill = false` keeps the bottom sheet sized to its content when there is room to spare;
        // the weight is what makes the action row below survive a side sheet too short for both.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            SiteDetailHeader(detail = detail)
            SiteDetailStats(detail = detail)
            SiteRecentUses(uses = detail.recentUses, isPending = detail.timesUsed == null)
        }
        if (detail.hasWriteError) {
            Text(
                text = stringResource(R.string.sites_detail_write_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SiteDetailActions(isAvailable = detail.isAvailable, onAction = onAction)
    }
}

/** The status avatar, the site's name, and "{status} · {info}" under it (§4.12.8). */
@Suppress("FunctionName")
@Composable
private fun SiteDetailHeader(detail: SiteDetailUi, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(AVATAR_SIZE),
            shape = CircleShape,
            color = detail.avatarContainer(),
            contentColor = detail.avatarContent(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painter = detail.avatarIcon(), contentDescription = null)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
            Text(
                text = detail.site.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail.supportingText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Times used · Route · Last used (§4.12.8). */
@Suppress("FunctionName")
@Composable
private fun SiteDetailStats(detail: SiteDetailUi, modifier: Modifier = Modifier) {
    val pending = stringResource(R.string.sites_detail_value_pending)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        DetailTile(
            value = detail.timesUsed?.toString() ?: pending,
            label = stringResource(R.string.sites_detail_stat_times_used),
            modifier = Modifier.weight(1f),
        )
        DetailTile(
            value = stringResource(detail.site.bodyRegion.routeValueRes()),
            label = stringResource(R.string.sites_detail_stat_route),
            modifier = Modifier.weight(1f),
        )
        DetailTile(
            value = lastUsedLabel(detail.site.daysSinceLastUse),
            label = stringResource(R.string.sites_detail_stat_last_used),
            modifier = Modifier.weight(1f),
        )
    }
}

/** One stats tile: the value on top, what it counts under it. */
@Suppress("FunctionName")
@Composable
private fun DetailTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(TILE_PADDING),
            verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
        ) {
            // Three tiles across a 360dp side sheet leave each about 100dp, and "Subcut · IM" needs
            // most of it — so both lines shrink to fit rather than ellipsize into a value that names
            // nothing. `sp`, so they still track the user's font scale.
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = VALUE_MIN_FONT_SIZE,
                    maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = LABEL_MIN_FONT_SIZE,
                    maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The last few doses given here (§4.12.8), newest first.
 *
 * [isPending] is the site's doses not having been read yet — the sheet opens on what the map already
 * knew, and "no dose has been logged here" is the wrong thing to say while the answer is on its way.
 */
@Suppress("FunctionName")
@Composable
private fun SiteRecentUses(uses: List<SiteDoseUi>, isPending: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        if (uses.isEmpty()) {
            if (!isPending) {
                Text(
                    text = stringResource(R.string.sites_detail_uses_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            uses.forEach { use -> SiteUseRow(use = use) }
        }
    }
}

/** One recent use: "Tirzepatide · 2.5 mg" over "2 days ago · 8:14 PM". */
@Suppress("FunctionName")
@Composable
private fun SiteUseRow(use: SiteDoseUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(TILE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(USE_AVATAR_SIZE),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                content = {},
            )
            Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
                Text(
                    text = stringResource(R.string.sites_detail_use_title, use.compoundName, use.dose),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = use.loggedAt.formatDaysAgoAndTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * §4.12.8's two actions: View full history, and the availability toggle.
 *
 * A `FlowRow` and no weights: both labels are full sentences, and a Compact phone at `301dp` cannot
 * fit them side by side — weighted, they would share the row and truncate to "View full hi…" and
 * "Mark unav…", which are not buttons anyone presses on purpose. Sized to their own labels they wrap
 * onto a second line where they have to and sit side by side where they fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionName")
@Composable
private fun SiteDetailActions(isAvailable: Boolean, onAction: (SitesAction) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        SheetActionButton(
            text = stringResource(R.string.sites_detail_view_history),
            icon = StaxIcons.History,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onAction(SitesAction.OnViewSiteHistoryClick) },
        )
        SheetActionButton(
            text = stringResource(
                if (isAvailable) R.string.sites_detail_mark_unavailable else R.string.sites_detail_mark_available,
            ),
            // The toggle keeps the destructive container in both directions: it is the same switch,
            // and a button that changed colour under the thumb would read as a different one.
            icon = StaxIcons.Block,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            onClick = { onAction(SitesAction.OnToggleSiteAvailabilityClick) },
        )
    }
}

@Suppress("FunctionName", "LongParameterList")
@Composable
private fun SheetActionButton(
    text: String,
    icon: Painter,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = BUTTON_PADDING,
    ) {
        Icon(painter = icon, contentDescription = null, modifier = Modifier.size(BUTTON_ICON_SIZE))
        Text(
            text = text,
            modifier = Modifier.padding(start = LABEL_GAP),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Labels (§4.12.8)
// ---------------------------------------------------------------------------

/**
 * "{status} · {info}" — the header's second line.
 *
 * Unavailable overrides the dot state: a site left out of the rotation is still ready or still
 * cooling underneath, and neither is the thing the user needs told about it.
 */
@Composable
private fun SiteDetailUi.supportingText(): String {
    val status = stringResource(
        when {
            !isAvailable -> R.string.sites_detail_status_unavailable
            site.status == SiteStatus.SUGGESTED -> R.string.sites_detail_status_suggested
            site.status == SiteStatus.COOLING -> R.string.sites_detail_status_cooling
            site.status == SiteStatus.RECENT -> R.string.sites_detail_status_recent
            else -> R.string.sites_detail_status_ready
        },
    )
    val info = when {
        !isAvailable -> stringResource(R.string.sites_detail_info_unavailable)
        site.status == SiteStatus.SUGGESTED -> stringResource(R.string.sites_detail_info_suggested)
        daysCoolingRemaining != null ->
            pluralStringResource(R.plurals.sites_detail_info_cooling, daysCoolingRemaining, daysCoolingRemaining)

        else -> daysAgoLabel(site.daysSinceLastUse)
    }
    return stringResource(R.string.sites_detail_supporting, status, info)
}

@Composable
private fun SiteDetailUi.avatarContainer(): Color = when {
    !isAvailable || site.status == SiteStatus.COOLING -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun SiteDetailUi.avatarContent(): Color = when {
    !isAvailable || site.status == SiteStatus.COOLING -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun SiteDetailUi.avatarIcon(): Painter = when {
    !isAvailable -> StaxIcons.Block
    site.status == SiteStatus.COOLING -> StaxIcons.RestartAlt
    else -> StaxIcons.Check
}

/** The Route tile (§4.12.8), derived from the region — a site carries no route of its own (§3.6). */
private fun BodyRegion.routeValueRes(): Int = when (routes()) {
    setOf(Route.INTRAMUSCULAR) -> R.string.sites_detail_route_im
    setOf(Route.SUBCUTANEOUS) -> R.string.sites_detail_route_sc
    else -> R.string.sites_detail_route_both
}

/** The Last used tile: "Today" / "2d ago" / "Never used" — short, because the tile is a third wide. */
@Composable
private fun lastUsedLabel(days: Int?): String = when {
    days == null -> stringResource(R.string.sites_suggested_never_used)
    days == 0 -> stringResource(R.string.sites_detail_last_used_today)
    else -> pluralStringResource(R.plurals.sites_detail_last_used_days, days, days)
}

/**
 * "2 days ago · 8:14 PM" for one recent use.
 *
 * The clock follows the device's 24-hour setting and not just the locale: someone who has turned that
 * switch on expects "20:14" everywhere, and `getBestDateTimePattern` alone would not give it.
 */
@Composable
private fun Instant.formatDaysAgoAndTime(zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dateTime = toLocalDateTime(zone)
    val languageTag = Locale.current.toLanguageTag()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val formatter = remember(languageTag, is24Hour) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        val skeleton = if (is24Hour) TIME_SKELETON_24H else TIME_SKELETON_12H
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    return stringResource(
        R.string.sites_detail_use_when,
        daysAgoLabel(days = dateTime.date.daysUntilToday(zone)),
        formatter.format(dateTime.time.toJavaLocalTime()),
    )
}

/** Whole days between a logged date and today, which is what "2 days ago" counts. */
private fun LocalDate.daysUntilToday(zone: TimeZone): Int = daysUntil(Clock.System.now().toLocalDateTime(zone).date)

// ---------------------------------------------------------------------------
// Layout constants
// ---------------------------------------------------------------------------

/** §6.4.2 Sites: the Expanded side sheet is `360dp`, narrower than the app's `420dp` default. */
private val SIDE_SHEET_WIDTH = 360.dp
private val SHEET_PADDING = 16.dp
private val TILE_PADDING = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
private val LABEL_GAP = 4.dp
private val AVATAR_SIZE = 56.dp
private val USE_AVATAR_SIZE = 36.dp
private val BUTTON_ICON_SIZE = 18.dp
private val BUTTON_PADDING = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
private val VALUE_MIN_FONT_SIZE = 11.sp
private val LABEL_MIN_FONT_SIZE = 9.sp

private const val TIME_SKELETON_12H = "hmm"
private const val TIME_SKELETON_24H = "Hmm"

// ---------------------------------------------------------------------------
// Previews (§6.4.8 profiles)
// ---------------------------------------------------------------------------

@Preview(name = "Cooling · Compact", showBackground = true, widthDp = 411)
@Preview(name = "Cooling · Expanded side sheet", showBackground = true, widthDp = 360)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SiteDetailCoolingPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SiteDetailContent(detail = previewDetail(), onAction = {})
        }
    }
}

@Preview(name = "Unavailable, never used · Compact", showBackground = true, widthDp = 411)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SiteDetailUnavailablePreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SiteDetailContent(
                detail = previewDetail().copy(
                    site = previewDetail().site.copy(
                        name = "Lateral Thigh · Right",
                        bodyRegion = BodyRegion.QUADRICEPS,
                        status = SiteStatus.READY,
                        daysSinceLastUse = null,
                    ),
                    isAvailable = false,
                    daysCoolingRemaining = null,
                    timesUsed = 0,
                    recentUses = persistentListOf(),
                ),
                onAction = {},
            )
        }
    }
}

private fun previewDetail() = SiteDetailUi(
    site = SiteUi(
        id = 2,
        name = "Abdomen · Left (upper)",
        bodyRegion = BodyRegion.ABDOMEN,
        side = InjectionSide.LEFT,
        sublocation = Sublocation.UPPER,
        status = SiteStatus.COOLING,
        daysSinceLastUse = 2,
    ),
    isAvailable = true,
    daysCoolingRemaining = 2,
    timesUsed = 8,
    recentUses = persistentListOf(
        SiteDoseUi(
            eventId = 1,
            compoundName = "Tirzepatide",
            dose = "2.5 mg",
            loggedAt = PREVIEW_NOW,
        ),
        SiteDoseUi(
            eventId = 2,
            compoundName = "Semaglutide",
            dose = "0.25 mg",
            loggedAt = PREVIEW_NOW - 7.days,
        ),
    ),
)

private val PREVIEW_NOW = Instant.parse("2026-05-08T20:14:00Z")
