package com.stax.feature.compounds.presentation.detail

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stax.core.design.system.StaxColors
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.feature.compounds.presentation.R
import com.stax.feature.compounds.presentation.form.OpenedContainerUi
import com.stax.feature.compounds.presentation.form.containerTypeLabel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.Locale as JavaLocale

// ---------------------------------------------------------------------------
// §4.3.2 Stat strip
// ---------------------------------------------------------------------------

/**
 * The stat strip (§4.3.2): doses left, days left, and — only when the compound has one — the nearer
 * of its two expiries.
 *
 * The tiles share the row equally, so §4.3.2's "if only 2 tiles are relevant, render 2 across full
 * width" falls out of the weights rather than needing a branch.
 */
@Suppress("FunctionName")
@Composable
internal fun StatStrip(stats: CompoundStatsUi, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        StatTile(
            value = stats.dosesLeft?.toString() ?: stringResource(R.string.compound_detail_stat_unknown),
            label = stringResource(R.string.compound_detail_stat_doses_left),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = stats.daysLeft
                ?.let { stringResource(R.string.compound_detail_stat_days_value, it) }
                ?: stringResource(R.string.compound_detail_stat_unknown),
            label = stringResource(R.string.compound_detail_stat_days_left),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        stats.expiry?.let { expiry ->
            StatTile(
                value = expiry.date.formatShort(),
                label = stringResource(
                    if (expiry.isContainerExpiry) {
                        R.string.compound_detail_stat_container_expiry
                    } else {
                        R.string.compound_detail_stat_batch_expiry
                    },
                ),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun StatTile(value: String, label: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                // The value is the point of the tile, so on a narrow pane it shrinks rather than
                // ellipsizing into "Jul 1…", which would say the wrong thing rather than less of it.
                // Three tiles on a Compact phone leave each about 90dp, and "Jul 14" needs all of it.
                autoSize = TextAutoSize.StepBased(minFontSize = STAT_MIN_FONT_SIZE, maxFontSize = STAT_MAX_FONT_SIZE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        }
    }
}

// ---------------------------------------------------------------------------
// §4.3.3 Opened vial card
// ---------------------------------------------------------------------------

/**
 * The opened-container card (§4.3.3): what is left of the container, over the ten-segment track, with
 * how long ago it was opened — and the "Edit" that opens the §4.5 sheet on it.
 *
 * The card is shown even with nothing open, as the way in to §4.5's Create Already Opened variant;
 * §4.3.3 only ever describes the populated case because §4.4.3's form is the other way in.
 */
@Suppress("FunctionName")
@Composable
internal fun OpenedContainerCard(
    opened: OpenedContainerUi?,
    onAction: (CompoundDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
            OpenedContainerHeader(opened = opened, onAction = onAction)
            if (opened != null) {
                SegmentedProgressBar(fraction = opened.fillFraction)
                OpenedContainerMeta(opened = opened)
            }
        }
    }
}

/** The card's top row: what is open, and the button that opens §4.5's sheet on it. */
@Suppress("FunctionName")
@Composable
private fun OpenedContainerHeader(opened: OpenedContainerUi?, onAction: (CompoundDetailAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painter = StaxIcons.Colorize, contentDescription = null)
        Text(
            text = if (opened == null) {
                stringResource(R.string.compound_detail_opened_empty)
            } else {
                stringResource(R.string.compound_form_opened_title, containerTypeLabel(opened.containerType))
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = CARD_GAP),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = { onAction(CompoundDetailAction.OnOpenedContainerClick) }) {
            Icon(
                painter = if (opened == null) StaxIcons.Add else StaxIcons.Edit,
                contentDescription = null,
                modifier = Modifier.size(ICON_SMALL),
            )
            Text(
                text = stringResource(
                    if (opened == null) R.string.compound_detail_opened_add else R.string.compound_detail_opened_edit,
                ),
                modifier = Modifier.padding(start = LABEL_GAP),
            )
        }
    }
}

/**
 * §4.3.3's meta row. Half the row each rather than `SpaceBetween`: the gap between only exists while
 * both labels fit on one line, and the moment either wraps the two run together.
 */
@Suppress("FunctionName")
@Composable
private fun OpenedContainerMeta(opened: OpenedContainerUi) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        Text(
            text = stringResource(
                R.string.compound_form_opened_remaining,
                opened.remaining,
                opened.capacity,
                opened.unit,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(
                R.plurals.compound_form_opened_days_ago,
                opened.openedDaysAgo,
                opened.openedDaysAgo,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * §4.3.3's segmented progress bar: ten segments, each filled by how much of *it* the remaining amount
 * covers — so the partial segment §4.3.3 asks about is the one the fraction lands inside, drawn part
 * full rather than rounded away.
 *
 * Decorative: the numbers underneath already state the same thing to a screen reader, so the track
 * clears its own semantics rather than repeating them.
 */
@Suppress("FunctionName")
@Composable
private fun SegmentedProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        repeat(SEGMENT_COUNT) { index ->
            val filled = (fraction * SEGMENT_COUNT - index).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SEGMENT_HEIGHT)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, StaxShapes.Pill),
            ) {
                if (filled > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(filled)
                            .height(SEGMENT_HEIGHT)
                            .background(MaterialTheme.colorScheme.primary, StaxShapes.Pill),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// §4.3.4 Active protocols
// ---------------------------------------------------------------------------

/** The active-protocols card (§4.3.4): one tappable sub-row per protocol, each with its next dose. */
@Suppress("FunctionName")
@Composable
internal fun ActiveProtocolsCard(
    protocols: List<ActiveProtocolUi>,
    onAction: (CompoundDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = StaxIcons.CalendarMonth, contentDescription = null)
                Text(
                    text = stringResource(R.string.compound_detail_protocols, protocols.size),
                    modifier = Modifier.padding(start = CARD_GAP),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (protocols.isEmpty()) {
                Text(
                    text = stringResource(R.string.compound_detail_protocols_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            protocols.forEach { protocol ->
                ProtocolRow(protocol = protocol, onAction = onAction)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun ProtocolRow(protocol: ActiveProtocolUi, onAction: (CompoundDetailAction) -> Unit) {
    Surface(
        onClick = { onAction(CompoundDetailAction.OnProtocolClick(protocol.id)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.padding(CARD_PADDING), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
                Text(text = protocol.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = protocol.details(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NextDosePill(protocol = protocol, modifier = Modifier.padding(top = LABEL_GAP))
            }
            Icon(painter = StaxIcons.ChevronRight, contentDescription = null)
        }
    }
}

/** §4.3.4's tag pill: `schedule` icon + when the next generated dose is due. */
@Suppress("FunctionName")
@Composable
private fun NextDosePill(protocol: ActiveProtocolUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = StaxShapes.Pill,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PILL_PADDING, vertical = LABEL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = StaxIcons.Schedule, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
            Text(
                text = protocol.nextDoseAt
                    ?.let {
                        stringResource(
                            R.string.compound_detail_next_dose,
                            it.formatDayAndTime(withTime = protocol.nextDoseHasTime),
                        )
                    }
                    ?: stringResource(R.string.compound_detail_next_dose_none),
                modifier = Modifier.padding(start = LABEL_GAP),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** §4.3.4's details line: "Mon, Thu · 0.25 mg sc". */
@Composable
private fun ActiveProtocolUi.details(): String = listOf(
    scheduleSummary(),
    "$dose ${stringResource(route.labelRes())}",
).joinToString(stringResource(R.string.compound_detail_meta_separator))

/**
 * The schedule half of that line. Weekday names come from the device locale rather than from a string
 * resource, which is what makes "Mon, Thu" become "lun., jeu." without a translation of its own.
 */
@Composable
private fun ActiveProtocolUi.scheduleSummary(): String = when (scheduleType) {
    ScheduleType.DAILY -> stringResource(R.string.compound_detail_schedule_daily)

    ScheduleType.SPECIFIC_WEEKDAYS -> weekdays.weekdayLabels()

    ScheduleType.EVERY_X_DAYS ->
        pluralSchedule(R.plurals.compound_detail_schedule_every_x_days, scheduleValue)

    ScheduleType.X_TIMES_PER_DAY ->
        pluralSchedule(R.plurals.compound_detail_schedule_times_per_day, scheduleValue)

    ScheduleType.X_TIMES_PER_WEEK ->
        pluralSchedule(R.plurals.compound_detail_schedule_times_per_week, scheduleValue)

    ScheduleType.X_TIMES_PER_MONTH ->
        pluralSchedule(R.plurals.compound_detail_schedule_times_per_month, scheduleValue)
}

/** A schedule count is only ever absent on a malformed row; one is the harmless reading of it. */
@Composable
private fun pluralSchedule(id: Int, count: Int?): String = (count ?: 1).let { pluralStringResource(id, it, it) }

// ---------------------------------------------------------------------------
// §4.3.5 Notes
// ---------------------------------------------------------------------------

/** The notes card (§4.3.5): two lines, then "Show more" unfolds the rest in place. */
@Suppress("FunctionName")
@Composable
internal fun NotesCard(
    notes: String?,
    isExpanded: Boolean,
    onAction: (CompoundDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = StaxIcons.Edit, contentDescription = null)
                Text(
                    text = stringResource(R.string.compound_detail_notes),
                    modifier = Modifier.padding(start = CARD_GAP),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = notes ?: stringResource(R.string.compound_detail_notes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else NOTES_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            // Nothing to unfold when there are no notes, and nothing to fold back when two lines were
            // all of them — but a collapsed `Text` cannot report whether it clipped without a layout
            // callback, so the link is offered whenever there is text at all.
            if (notes != null) {
                Row(
                    modifier = Modifier.clickable { onAction(CompoundDetailAction.OnToggleNotes) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (isExpanded) {
                                R.string.compound_detail_show_less
                            } else {
                                R.string.compound_detail_show_more
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        painter = if (isExpanded) StaxIcons.ExpandLess else StaxIcons.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = LABEL_GAP)
                            .size(ICON_SMALL),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// §4.3.6 – §4.3.8 History
// ---------------------------------------------------------------------------

/** §4.3.6: "Dose history" on the left, the all-time Taken + Partial count on the right. */
@Suppress("FunctionName")
@Composable
internal fun HistoryHeader(loggedDoseCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.compound_detail_history),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
        )
        Surface(
            shape = StaxShapes.Pill,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.compound_detail_history_count,
                    loggedDoseCount,
                    loggedDoseCount,
                ),
                modifier = Modifier.padding(horizontal = PILL_PADDING, vertical = LABEL_GAP),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** §4.3.7: All / Taken / Partial / Skipped, single-select, All by default. */
@Suppress("FunctionName")
@Composable
internal fun HistoryFilterRow(
    selected: HistoryStatusFilter,
    onAction: (CompoundDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Scrolls rather than wraps: four chips fit a Compact phone but not the narrow right-hand
    // column of §6.4.2's two-column detail pane.
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
        items(HistoryStatusFilter.entries) { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onAction(CompoundDetailAction.OnHistoryFilterClick(filter)) },
                label = { Text(text = stringResource(filter.labelRes()), maxLines = 1) },
                leadingIcon = if (isSelected) {
                    { Icon(painter = StaxIcons.Done, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

/** One history row (§4.3.8): status dot, when it was logged, and what it was. */
@Suppress("FunctionName")
@Composable
internal fun HistoryRow(
    entry: HistoryEntryUi,
    onAction: (CompoundDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onAction(CompoundDetailAction.OnHistoryEntryClick(entry.eventId)) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(status = entry.status)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
                Text(
                    text = entry.loggedAt.formatDayAndTime(withTime = true),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = entry.supporting(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "0.25 mg · 0.10 mL · Taken · Abdomen R" — the parts that exist, in that order (§4.3.8). */
@Composable
private fun HistoryEntryUi.supporting(): String = listOfNotNull(
    dose,
    volume,
    stringResource(status.labelRes()),
    siteName,
).joinToString(stringResource(R.string.compound_detail_meta_separator))

@Suppress("FunctionName")
@Composable
private fun StatusDot(status: AdministrationEventStatus) {
    Surface(
        shape = StaxShapes.Pill,
        color = status.container(),
        contentColor = status.onContainer(),
    ) {
        Icon(
            painter = status.painter(),
            contentDescription = null,
            modifier = Modifier.padding(STATUS_DOT_PADDING),
        )
    }
}

@Composable
private fun AdministrationEventStatus.painter(): Painter = when (this) {
    AdministrationEventStatus.TAKEN -> StaxIcons.Check
    AdministrationEventStatus.PARTIAL -> StaxIcons.Schedule
    AdministrationEventStatus.SKIPPED -> StaxIcons.Close
}

@Composable
private fun AdministrationEventStatus.container(): Color = when (this) {
    AdministrationEventStatus.TAKEN -> StaxColors.doseTakenContainer
    AdministrationEventStatus.PARTIAL -> StaxColors.dosePartialContainer
    AdministrationEventStatus.SKIPPED -> StaxColors.doseSkippedContainer
}

@Composable
private fun AdministrationEventStatus.onContainer(): Color = when (this) {
    AdministrationEventStatus.TAKEN -> StaxColors.onDoseTakenContainer
    AdministrationEventStatus.PARTIAL -> StaxColors.onDosePartialContainer
    AdministrationEventStatus.SKIPPED -> StaxColors.onDoseSkippedContainer
}

internal fun AdministrationEventStatus.labelRes(): Int = when (this) {
    AdministrationEventStatus.TAKEN -> R.string.compound_detail_status_taken
    AdministrationEventStatus.PARTIAL -> R.string.compound_detail_status_partial
    AdministrationEventStatus.SKIPPED -> R.string.compound_detail_status_skipped
}

internal fun HistoryStatusFilter.labelRes(): Int = when (this) {
    HistoryStatusFilter.ALL -> R.string.compound_detail_filter_all
    HistoryStatusFilter.TAKEN -> R.string.compound_detail_filter_taken
    HistoryStatusFilter.PARTIAL -> R.string.compound_detail_filter_partial
    HistoryStatusFilter.SKIPPED -> R.string.compound_detail_filter_skipped
}

private fun Route.labelRes(): Int = when (this) {
    Route.SUBCUTANEOUS -> R.string.compound_detail_route_sc
    Route.INTRAMUSCULAR -> R.string.compound_detail_route_im
    Route.ORAL -> R.string.compound_detail_route_oral
    Route.TOPICAL -> R.string.compound_detail_route_topical
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

/**
 * "Today · 8:00 PM" / "Thu May 8 · 8:00 PM" — the day named relatively when it is near enough for
 * that to be the clearer thing to say (§4.3.4, §4.3.8).
 *
 * The clock format follows the device's 24-hour setting, not just the locale: someone who has turned
 * that switch on expects "20:00" everywhere, and `getBestDateTimePattern` alone would not give it.
 * [withTime] is false for an all-day scheduled dose (§3.3), which has no clock time to show.
 */
@Composable
internal fun Instant.formatDayAndTime(withTime: Boolean, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val dateTime = toLocalDateTime(zone)
    val day = dateTime.date.formatRelativeDay(zone)
    if (!withTime) return day

    val languageTag = Locale.current.toLanguageTag()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val formatter = remember(languageTag, is24Hour) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        val skeleton = if (is24Hour) TIME_SKELETON_24H else TIME_SKELETON_12H
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    return stringResource(
        R.string.compound_detail_day_and_time,
        day,
        formatter.format(dateTime.time.toJavaLocalTime()),
    )
}

/** "Today" / "Yesterday" / "Tomorrow", else "Thu May 8" in the order the device locale writes it. */
@Composable
private fun LocalDate.formatRelativeDay(zone: TimeZone): String {
    val today = Clock.System.now().toLocalDateTime(zone).date
    return when (this) {
        today -> stringResource(R.string.compound_detail_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(R.string.compound_detail_yesterday)
        today.plus(1, DateTimeUnit.DAY) -> stringResource(R.string.compound_detail_tomorrow)
        else -> formatWeekdayAndDate()
    }
}

@Composable
private fun LocalDate.formatWeekdayAndDate(): String {
    val languageTag = Locale.current.toLanguageTag()
    val formatter = remember(languageTag) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_SKELETON), locale)
    }
    return formatter.format(toJavaLocalDate())
}

/** "Jul 14" — month + day in the order the device locale writes them, no year (§4.3.2). */
@Composable
private fun LocalDate.formatShort(): String {
    val languageTag = Locale.current.toLanguageTag()
    val formatter = remember(languageTag) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, SHORT_DATE_SKELETON), locale)
    }
    return formatter.format(toJavaLocalDate())
}

/** "Mon, Thu" — the locale's short weekday names, which is what makes the line translate itself. */
@Composable
private fun List<DayOfWeek>.weekdayLabels(): String {
    val languageTag = Locale.current.toLanguageTag()
    return remember(this, languageTag) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        joinToString(WEEKDAY_SEPARATOR) {
            java.time.DayOfWeek.of(it.isoDayNumber).getDisplayName(TextStyle.SHORT, locale)
        }
    }
}

/** Skeletons, not patterns: `getBestDateTimePattern` reorders each one per locale. */
private const val SHORT_DATE_SKELETON = "MMMd"
private const val DAY_SKELETON = "EEEMMMd"
private const val TIME_SKELETON_12H = "hmma"
private const val TIME_SKELETON_24H = "Hm"

private const val WEEKDAY_SEPARATOR = ", "

/** §4.3.3: ten segments, each drawn part-full when the fill lands inside it. */
private const val SEGMENT_COUNT = 10

/** §4.3.5: the notes body is two lines until "Show more" unfolds it. */
private const val NOTES_COLLAPSED_LINES = 2

internal val SCREEN_PADDING = 16.dp
internal val CARD_GAP = 12.dp
private val CARD_PADDING = 16.dp
private val LABEL_GAP = 4.dp
private val CHIP_GAP = 8.dp
private val PILL_PADDING = 10.dp
private val ICON_SMALL = 18.dp
private val SEGMENT_HEIGHT = 10.dp
private val SEGMENT_GAP = 4.dp
private val STATUS_DOT_PADDING = 8.dp

private val STAT_MIN_FONT_SIZE = 16.sp
private val STAT_MAX_FONT_SIZE = 24.sp
