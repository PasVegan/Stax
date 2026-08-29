package com.stax.feature.protocols.presentation.list

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.domain.ScheduleType
import com.stax.feature.protocols.presentation.R
import com.stax.feature.protocols.presentation.form.formatTime
import com.stax.feature.protocols.presentation.form.routeLabel
import com.stax.feature.protocols.presentation.form.shortLabel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.Locale as JavaLocale

/**
 * One protocol card (§4.7.3): name + meta line with the status pill, the schedule and next-dose
 * chips, and the titration bar when the protocol escalates.
 *
 * The whole card is the tap target — it opens Protocol Detail (§4.8).
 *
 * In multi-select mode (§4.7.4) a checkbox circle takes the lead and shifts the name right; a
 * selected card fills with `secondary-container`. [onLongClick] is what enters the mode, so the
 * search overlay simply leaves it out and its cards never long-press into a selection.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
internal fun ProtocolCard(
    item: ProtocolListItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    Card(
        // Clipped before it is made clickable so the ripple follows the card's corners; `Card`'s own
        // `onClick` overload cannot carry a long press, which is what enters multi-select (§4.7.4).
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.protocols_card_select),
            )
            .semantics { if (isSelectionMode) selected = isSelected },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            CardHeader(item = item, isSelectionMode = isSelectionMode, isSelected = isSelected)
            // Two chips rarely fit one line inside a `360dp` list pane (§6.4.2), so they wrap instead
            // of scrolling — a chip half off the edge of a card reads as a layout bug.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            ) {
                OutlinedChip(icon = StaxIcons.CalendarMonth, text = item.scheduleSummary())
                OutlinedChip(icon = StaxIcons.Schedule, text = item.nextDoseSummary())
            }
            item.titration?.let { TitrationBar(titration = it) }
        }
    }
}

/** §4.7.3's top row: the multi-select checkbox, name + meta line, and the status pill. */
@Suppress("FunctionName")
@Composable
private fun CardHeader(item: ProtocolListItemUi, isSelectionMode: Boolean, isSelected: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
        if (isSelectionMode) {
            SelectionCheckbox(isSelected = isSelected, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.metaLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = META_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(pill = item.pill)
    }
}

/**
 * §4.7.3's status pill. Active and In break are tonal — one `primary-container`, the other
 * `tertiary-container` — Paused is the neutral outlined surface, and Completed is outline-only,
 * carrying no fill at all because a finished protocol has nothing left to draw attention to.
 */
@Suppress("FunctionName")
@Composable
private fun StatusPill(pill: ProtocolPill, modifier: Modifier = Modifier) {
    val container = when (pill) {
        ProtocolPill.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        ProtocolPill.IN_BREAK -> MaterialTheme.colorScheme.tertiaryContainer
        ProtocolPill.PAUSED -> MaterialTheme.colorScheme.surfaceContainerHighest
        ProtocolPill.COMPLETED -> Color.Transparent
    }
    val content = when (pill) {
        ProtocolPill.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        ProtocolPill.IN_BREAK -> MaterialTheme.colorScheme.onTertiaryContainer
        ProtocolPill.PAUSED, ProtocolPill.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = StaxShapes.Pill,
        color = container,
        contentColor = content,
        border = when (pill) {
            ProtocolPill.PAUSED, ProtocolPill.COMPLETED ->
                BorderStroke(OUTLINE_WIDTH, MaterialTheme.colorScheme.outline)
            else -> null
        },
    ) {
        Text(
            text = stringResource(pill.labelRes()),
            modifier = Modifier.padding(horizontal = PILL_PADDING, vertical = LABEL_GAP),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The card's leading checkbox in multi-select mode (§4.7.4): an outlined circle, filled with
 * `primary` and a `check` once selected. Not itself clickable — the whole card is the target, and its
 * `selected` semantics is what a screen reader announces.
 */
@Suppress("FunctionName")
@Composable
private fun SelectionCheckbox(isSelected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(CHECKBOX_SIZE)
            .then(
                if (isSelected) {
                    Modifier.background(color = MaterialTheme.colorScheme.primary, shape = StaxShapes.Pill)
                } else {
                    Modifier.border(
                        width = OUTLINE_WIDTH,
                        color = MaterialTheme.colorScheme.outline,
                        shape = StaxShapes.Pill,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                painter = StaxIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(CHECKBOX_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** §4.7.3's two chips: outlined pill, leading icon, one line of text. */
@Suppress("FunctionName")
@Composable
private fun OutlinedChip(icon: Painter, text: String) {
    Row(
        modifier = Modifier
            .border(OUTLINE_WIDTH, MaterialTheme.colorScheme.outlineVariant, StaxShapes.Pill)
            .padding(horizontal = PILL_PADDING, vertical = LABEL_GAP),
        horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(CHIP_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** §4.7.3's titration bar: a "Titration" label, the right-aligned value, and the filled bar. */
@Suppress("FunctionName")
@Composable
private fun TitrationBar(titration: TitrationUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.protocols_titration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.protocols_titration_value, titration.current, titration.target),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { titration.progress },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            // §4.7.3 draws one continuous bar; the M3 default breaks it with a stop dot and a gap.
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

/** `{compound} · {dose} {route}`, or `{compound} · 0.25 → 1.0 mg` while the protocol titrates (§4.7.3). */
@Composable
private fun ProtocolListItemUi.metaLine(): String {
    val doseText = titration
        ?.let { stringResource(R.string.protocols_meta_titration, it.current, it.target) }
        ?: stringResource(R.string.protocols_meta_dose, dose, routeLabel(route))
    // A protocol outlives an archived compound (§4.7.2), and the meta line then drops the name
    // rather than showing a placeholder where it used to be.
    return compoundName?.let { stringResource(R.string.protocols_meta, it, doseText) } ?: doseText
}

/**
 * The schedule chip's label (§4.7.3): the frequency, then the detail that frequency has — the
 * weekdays it names, or the times of day it doses at. Weekday and time formats come from the device
 * locale, which is what makes "Weekly · Mon, Thu" become "lun., jeu." and "8 PM" become "20:00"
 * without a translation of their own.
 */
@Composable
private fun ProtocolListItemUi.scheduleSummary(): String {
    val frequency = when (scheduleType) {
        ScheduleType.DAILY -> stringResource(R.string.protocols_schedule_daily)
        ScheduleType.SPECIFIC_WEEKDAYS -> stringResource(R.string.protocols_schedule_weekly)
        ScheduleType.EVERY_X_DAYS ->
            pluralSchedule(R.plurals.protocols_schedule_every_x_days, scheduleValue)
        ScheduleType.X_TIMES_PER_DAY ->
            pluralSchedule(R.plurals.protocols_schedule_times_per_day, scheduleValue)
        ScheduleType.X_TIMES_PER_WEEK ->
            pluralSchedule(R.plurals.protocols_schedule_times_per_week, scheduleValue)
        ScheduleType.X_TIMES_PER_MONTH ->
            pluralSchedule(R.plurals.protocols_schedule_times_per_month, scheduleValue)
    }
    val detail = when {
        scheduleType == ScheduleType.SPECIFIC_WEEKDAYS && weekdays.isNotEmpty() ->
            weekdays.map { it.shortLabel() }.joinToString(LIST_SEPARATOR)
        dosageTimes.isNotEmpty() -> dosageTimes.map { it.formatTime() }.joinToString(LIST_SEPARATOR)
        else -> null
    }
    return detail?.let { stringResource(R.string.protocols_meta, frequency, it) } ?: frequency
}

/**
 * The next-dose chip's label (§4.7.3): "Today 8 PM" / "Tomorrow 8 AM" while the protocol is dosing,
 * "In 5 d (break)" while it is inside its `daysOff` window.
 *
 * A protocol with nothing pending says so rather than showing an empty chip — paused, completed and
 * archived protocols generate no doses at all (§5.2), and neither does a break longer than the
 * generated horizon.
 */
@Composable
private fun ProtocolListItemUi.nextDoseSummary(): String {
    val zone = TimeZone.currentSystemDefault()
    val next = nextDoseAt ?: return stringResource(
        if (isInBreak) R.string.protocols_next_dose_in_break else R.string.protocols_next_dose_none,
    )
    if (!isInBreak) return next.formatDayAndTime(withTime = nextDoseHasTime, zone = zone)

    val days = Clock.System.todayIn(zone).daysUntil(next.toLocalDateTime(zone).date)
    return pluralStringResource(R.plurals.protocols_next_dose_break_in_days, days, days)
}

/** A schedule count is only ever absent on a malformed row; one is the harmless reading of it. */
@Composable
private fun pluralSchedule(id: Int, count: Int?): String = (count ?: 1).let { pluralStringResource(id, it, it) }

/** "Today 8 PM" / "Tomorrow 8 AM", else "Thu May 8 8 PM" in the order the device locale writes it. */
@Composable
internal fun Instant.formatDayAndTime(withTime: Boolean, zone: TimeZone): String {
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
        R.string.protocols_day_and_time,
        day,
        formatter.format(dateTime.time.toJavaLocalTime()),
    )
}

@Composable
private fun LocalDate.formatRelativeDay(zone: TimeZone): String {
    val today = Clock.System.todayIn(zone)
    return when (this) {
        today -> stringResource(R.string.protocols_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(R.string.protocols_yesterday)
        today.plus(1, DateTimeUnit.DAY) -> stringResource(R.string.protocols_tomorrow)
        else -> {
            val languageTag = Locale.current.toLanguageTag()
            val formatter = remember(languageTag) {
                val locale = JavaLocale.forLanguageTag(languageTag)
                DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_SKELETON), locale)
            }
            formatter.format(toJavaLocalDate())
        }
    }
}

internal fun ProtocolPill.labelRes(): Int = when (this) {
    ProtocolPill.ACTIVE -> R.string.protocols_pill_active
    ProtocolPill.IN_BREAK -> R.string.protocols_pill_in_break
    ProtocolPill.PAUSED -> R.string.protocols_pill_paused
    ProtocolPill.COMPLETED -> R.string.protocols_pill_completed
}

private const val LIST_SEPARATOR = ", "
private const val META_MAX_LINES = 2

/** Skeletons, not patterns: `getBestDateTimePattern` reorders them per locale. */
private const val DAY_SKELETON = "EEEMMMd"
private const val TIME_SKELETON_12H = "hmm"
private const val TIME_SKELETON_24H = "Hmm"

private val CARD_PADDING = 16.dp
private val SECTION_GAP = 12.dp
private val CHIP_GAP = 8.dp
private val LABEL_GAP = 4.dp
private val PILL_PADDING = 12.dp
private val CHIP_ICON_SIZE = 16.dp
private val OUTLINE_WIDTH = 1.dp
private val CHECKBOX_SIZE = 40.dp
private val CHECKBOX_ICON_SIZE = 24.dp
