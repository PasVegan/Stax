package com.stax.feature.protocols.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxColors
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.ScheduleType
import com.stax.feature.protocols.presentation.R
import com.stax.feature.protocols.presentation.form.bodyRegionName
import com.stax.feature.protocols.presentation.form.formatShort
import com.stax.feature.protocols.presentation.form.formatTime
import com.stax.feature.protocols.presentation.form.formatWithYear
import com.stax.feature.protocols.presentation.form.shortLabel
import com.stax.feature.protocols.presentation.form.unitLabel
import com.stax.feature.protocols.presentation.list.formatDayAndTime
import kotlinx.datetime.TimeZone

// ---------------------------------------------------------------------------
// §4.8.3 Schedule
// ---------------------------------------------------------------------------

/**
 * §4.8.3's Schedule card: five key-value rows, of which Times and Titration appear only when the
 * protocol has them to show.
 *
 * Every value is written here rather than pre-formatted by the ViewModel, because every one of them
 * is localized: weekday names, the 12h/24h clock, and the plural of "4 wk" all come from the device.
 */
@Suppress("FunctionName")
@Composable
internal fun ScheduleCard(schedule: ScheduleCardUi, modifier: Modifier = Modifier) {
    DetailCard(icon = StaxIcons.CalendarMonth, title = stringResource(R.string.protocol_detail_schedule), modifier) {
        KeyValueRow(
            label = stringResource(R.string.protocol_detail_frequency),
            value = schedule.frequency(),
        )
        if (schedule.dosageTimes.isNotEmpty()) {
            KeyValueRow(
                label = stringResource(R.string.protocol_detail_times),
                // Mapped before joining: `formatTime` is composable, and a `joinToString` lambda is
                // not a composable context.
                value = schedule.dosageTimes.map { it.formatTime() }.joinToString(LIST_SEPARATOR),
            )
        }
        schedule.titration?.let { titration ->
            KeyValueRow(
                label = stringResource(R.string.protocol_detail_titration),
                value = stringResource(
                    R.string.protocol_detail_titration_value,
                    titration.startDose,
                    titration.targetDose,
                    titration.increaseAmount,
                    titration.every(),
                ),
            )
        }
        KeyValueRow(
            label = stringResource(R.string.protocol_detail_duration),
            value = stringResource(
                R.string.protocol_detail_duration_value,
                schedule.startDate.formatShort(),
                schedule.endDate?.formatShort() ?: stringResource(R.string.protocol_detail_duration_open_ended),
            ),
        )
        KeyValueRow(
            label = stringResource(R.string.protocol_detail_reminder),
            value = when (schedule.reminderOffsetMinutes) {
                null -> stringResource(R.string.protocol_detail_reminder_off)
                0 -> stringResource(R.string.protocol_detail_reminder_at_time)
                else -> pluralStringResource(
                    R.plurals.protocol_detail_reminder_value,
                    schedule.reminderOffsetMinutes,
                    schedule.reminderOffsetMinutes,
                )
            },
        )
    }
}

/** "Weekly · Mon, Thu" — the same sentence §4.7.3's schedule chip writes, from the same parts. */
@Composable
private fun ScheduleCardUi.frequency(): String {
    val label = when (scheduleType) {
        ScheduleType.DAILY -> stringResource(R.string.protocols_schedule_daily)
        ScheduleType.SPECIFIC_WEEKDAYS -> stringResource(R.string.protocols_schedule_weekly)
        ScheduleType.EVERY_X_DAYS -> plural(R.plurals.protocols_schedule_every_x_days, scheduleValue)
        ScheduleType.X_TIMES_PER_DAY -> plural(R.plurals.protocols_schedule_times_per_day, scheduleValue)
        ScheduleType.X_TIMES_PER_WEEK -> plural(R.plurals.protocols_schedule_times_per_week, scheduleValue)
        ScheduleType.X_TIMES_PER_MONTH -> plural(R.plurals.protocols_schedule_times_per_month, scheduleValue)
    }
    if (scheduleType != ScheduleType.SPECIFIC_WEEKDAYS || weekdays.isEmpty()) return label
    return stringResource(
        R.string.protocols_meta,
        label,
        weekdays.map { it.shortLabel() }.joinToString(LIST_SEPARATOR),
    )
}

/** The step's period — "4 wk", "10 d", "3 doses" — which is a plural in every locale that has them. */
@Composable
private fun TitrationRuleUi.every(): String = pluralStringResource(
    when (increaseEvery) {
        EscalationIncreaseEvery.EVERY_X_DAYS -> R.plurals.protocol_detail_titration_every_days
        EscalationIncreaseEvery.EVERY_X_WEEKS -> R.plurals.protocol_detail_titration_every_weeks
        EscalationIncreaseEvery.AFTER_X_DOSES -> R.plurals.protocol_detail_titration_every_doses
    },
    increaseEveryValue,
    increaseEveryValue,
)

// ---------------------------------------------------------------------------
// §4.8.4 Linked compound
// ---------------------------------------------------------------------------

/**
 * §4.8.4: the compound this protocol doses, as the same avatar + name + meta + chevron row §4.2.3
 * uses. Tapping it leaves for §4.3 Compound Detail.
 *
 * A null [compound] is an archived one (§4.7.2) — the protocol outlives it, and the card says so
 * rather than offering a row that leads nowhere.
 */
@Suppress("FunctionName")
@Composable
internal fun LinkedCompoundCard(
    compound: LinkedCompoundUi?,
    onAction: (ProtocolDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailCard(icon = StaxIcons.Inventory2, title = stringResource(R.string.protocol_detail_compound), modifier) {
        if (compound == null) {
            Text(
                text = stringResource(R.string.protocol_detail_compound_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DetailCard
        }
        Surface(
            onClick = { onAction(ProtocolDetailAction.OnCompoundClick) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = compound.category.container(),
                    contentColor = compound.category.onContainer(),
                ) {
                    Icon(
                        painter = compound.category.icon(),
                        contentDescription = null,
                        modifier = Modifier.padding(AVATAR_PADDING),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
                    Text(text = compound.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        text = compound.meta(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = META_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    painter = StaxIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "0.25 mg = 0.10 mL · 2.5 mg/mL" — the halves the compound actually has, in that order (§4.8.4). */
@Composable
private fun LinkedCompoundUi.meta(): String {
    val dose = volume
        ?.let { stringResource(R.string.protocol_detail_compound_dose, this.dose, it) }
        ?: this.dose
    val strength = concentration?.let { amount ->
        val unit = concentrationUnit ?: return@let null
        val per = concentrationPerUnit ?: return@let null
        stringResource(R.string.protocol_form_concentration, amount, unitLabel(unit), unitLabel(per))
    }
    return listOfNotNull(dose, strength).joinToString(META_SEPARATOR)
}

// ---------------------------------------------------------------------------
// §4.8.5 Inventory forecast
// ---------------------------------------------------------------------------

/**
 * §4.8.5: three key-value rows over the one warning worth acting on — a batch whose shelf life runs
 * out before the stock does, which is the whole of §4.8.5's warning row.
 *
 * A figure the forecast cannot reach — a paused protocol has no run-out day, an archived compound no
 * stock to divide — shows as "—" rather than as a zero, which would read as "none left".
 */
@Suppress("FunctionName")
@Composable
internal fun ForecastCard(forecast: ForecastUi, modifier: Modifier = Modifier) {
    DetailCard(icon = StaxIcons.Inventory2, title = stringResource(R.string.protocol_detail_forecast), modifier) {
        KeyValueRow(
            label = stringResource(R.string.protocol_detail_doses_remaining),
            value = forecast.dosesRemaining
                ?.let { pluralStringResource(R.plurals.protocol_detail_doses_value, it, it) }
                ?: stringResource(R.string.protocol_detail_unknown),
        )
        KeyValueRow(
            label = stringResource(R.string.protocol_detail_run_out),
            value = forecast.runOutDate?.formatWithYear() ?: stringResource(R.string.protocol_detail_unknown),
        )
        KeyValueRow(
            label = stringResource(R.string.protocol_detail_required),
            value = forecast.requiredUntilEnd ?: stringResource(R.string.protocol_detail_open_ended),
        )
        forecast.batchExpiry?.let { expiry ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LABEL_GAP),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(ROW_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = StaxIcons.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_SMALL),
                    )
                    Text(
                        text = stringResource(R.string.protocol_detail_expiry_warning, expiry.formatShort()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// §4.8.6 Site restrictions
// ---------------------------------------------------------------------------

/**
 * §4.8.6: the region chip and the rotation rule beside it.
 *
 * The rotation chip states the cooldown the app actually enforces on log (§5.3) — the protocol's own
 * override where it has one, the Settings default for its route otherwise. Its figure is therefore
 * never absent, which is why the card has no empty state.
 */
@Suppress("FunctionName")
@Composable
internal fun SiteRestrictionsCard(sites: SiteRestrictionsUi, modifier: Modifier = Modifier) {
    DetailCard(icon = StaxIcons.PersonPinCircle, title = stringResource(R.string.protocol_detail_sites), modifier) {
        // Wraps rather than squeezes: two chips rarely fit one line of a `360dp` pane, and a chip
        // compressed into two lines of its own reads as a layout bug (the rule §4.7.3's card uses).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
        ) {
            TonalChip(
                text = sites.region
                    ?.let { stringResource(R.string.protocol_detail_sites_region, bodyRegionName(it)) }
                    ?: stringResource(R.string.protocol_detail_sites_any),
            )
            TonalChip(
                text = pluralStringResource(
                    R.plurals.protocol_detail_sites_rotation,
                    sites.cooldownDays,
                    sites.cooldownDays,
                ),
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun TonalChip(text: String) {
    Surface(
        shape = StaxShapes.Pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = PILL_PADDING, vertical = CHIP_GAP),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

// ---------------------------------------------------------------------------
// §4.8.7 Dose history
// ---------------------------------------------------------------------------

/** §4.8.7: "Dose history" on the left, this protocol's all-time Taken + Partial count on the right. */
@Suppress("FunctionName")
@Composable
internal fun HistoryHeader(loggedDoseCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.protocol_detail_history),
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
                    R.plurals.protocol_detail_history_count,
                    loggedDoseCount,
                    loggedDoseCount,
                ),
                modifier = Modifier.padding(horizontal = PILL_PADDING, vertical = LABEL_GAP),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** One history row (§4.8.7): status dot, when it was logged, and what it was. */
@Suppress("FunctionName")
@Composable
internal fun HistoryRow(
    entry: ProtocolHistoryEntryUi,
    onAction: (ProtocolDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onAction(ProtocolDetailAction.OnHistoryEntryClick(entry.eventId)) },
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
                    text = entry.loggedAt.formatDayAndTime(withTime = true, zone = TimeZone.currentSystemDefault()),
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

/** "0.25 mg · 0.10 mL · Taken · Abdomen R" — the parts that exist, in that order (§4.8.7). */
@Composable
private fun ProtocolHistoryEntryUi.supporting(): String = listOfNotNull(
    dose,
    volume,
    stringResource(status.labelRes()),
    siteName,
).joinToString(META_SEPARATOR)

@Suppress("FunctionName")
@Composable
private fun StatusDot(status: AdministrationEventStatus) {
    Surface(shape = StaxShapes.Pill, color = status.container(), contentColor = status.onContainer()) {
        Icon(painter = status.painter(), contentDescription = null, modifier = Modifier.padding(CHIP_GAP))
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

private fun AdministrationEventStatus.labelRes(): Int = when (this) {
    AdministrationEventStatus.TAKEN -> R.string.protocol_detail_status_taken
    AdministrationEventStatus.PARTIAL -> R.string.protocol_detail_status_partial
    AdministrationEventStatus.SKIPPED -> R.string.protocol_detail_status_skipped
}

// ---------------------------------------------------------------------------
// §4.8.8 Notes
// ---------------------------------------------------------------------------

/** §4.8.8, as §4.3.5: two lines, then "Show more" unfolds the rest in place. */
@Suppress("FunctionName")
@Composable
internal fun NotesCard(
    notes: String?,
    isExpanded: Boolean,
    onAction: (ProtocolDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailCard(icon = StaxIcons.Edit, title = stringResource(R.string.protocol_detail_notes), modifier) {
        Text(
            text = notes ?: stringResource(R.string.protocol_detail_notes_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isExpanded) Int.MAX_VALUE else NOTES_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        // Nothing to unfold when there are no notes — but a collapsed `Text` cannot report whether it
        // clipped without a layout callback, so the link is offered whenever there is text at all.
        if (notes != null) {
            Row(
                modifier = Modifier.clickable { onAction(ProtocolDetailAction.OnToggleNotes) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (isExpanded) R.string.protocol_detail_show_less else R.string.protocol_detail_show_more,
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

// ---------------------------------------------------------------------------
// Shared card shapes
// ---------------------------------------------------------------------------

/** The `surface-container` card every §4.8 section is drawn in: an icon, a title, then its content. */
@Suppress("FunctionName")
@Composable
private fun DetailCard(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = icon, contentDescription = null)
                Text(
                    text = title,
                    modifier = Modifier.padding(start = CARD_GAP),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            content()
        }
    }
}

/**
 * One row of a §4.8.3 / §4.8.5 key-value table: label left, value right.
 *
 * The two halves share the row evenly rather than the label taking what it wants: inside a `360dp`
 * detail pane (§6.4.2) — or a cover display narrower still — an intrinsically-sized label leaves the
 * value too little to fit "Open-ended", which then breaks mid-word. An even split wraps the label
 * instead, which is the half that reads fine on two lines.
 */
@Suppress("FunctionName")
@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * The avatar's palette and glyph, per §4.2.3's compound row. Mirrored rather than shared: features
 * never depend on features (§10.4), and this is four lines of `when`.
 */
@Composable
private fun CompoundCategory.container(): Color = when (this) {
    CompoundCategory.PEPTIDE -> MaterialTheme.colorScheme.primaryContainer
    CompoundCategory.SUPPLEMENT -> MaterialTheme.colorScheme.tertiaryContainer
    CompoundCategory.HORMONE -> MaterialTheme.colorScheme.secondaryContainer
    CompoundCategory.MEDICATION -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun CompoundCategory.onContainer(): Color = when (this) {
    CompoundCategory.PEPTIDE -> MaterialTheme.colorScheme.onPrimaryContainer
    CompoundCategory.SUPPLEMENT -> MaterialTheme.colorScheme.onTertiaryContainer
    CompoundCategory.HORMONE -> MaterialTheme.colorScheme.onSecondaryContainer
    CompoundCategory.MEDICATION -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun CompoundCategory.icon(): Painter = when (this) {
    CompoundCategory.PEPTIDE -> StaxIcons.Colorize
    CompoundCategory.SUPPLEMENT -> StaxIcons.Medication
    CompoundCategory.HORMONE -> StaxIcons.Science
    CompoundCategory.MEDICATION -> StaxIcons.Pill
}

@Composable
private fun plural(id: Int, count: Int?): String = (count ?: 1).let { pluralStringResource(id, it, it) }

private const val LIST_SEPARATOR = ", "
private const val META_SEPARATOR = " · "
private const val META_MAX_LINES = 2

/** §4.8.8: the notes body is two lines until "Show more" unfolds it. */
private const val NOTES_COLLAPSED_LINES = 2

internal val SCREEN_PADDING = 16.dp
internal val CARD_GAP = 12.dp
private val CARD_PADDING = 16.dp
private val ROW_PADDING = 12.dp
private val AVATAR_PADDING = 10.dp
private val LABEL_GAP = 4.dp
private val CHIP_GAP = 8.dp
private val PILL_PADDING = 12.dp
private val ICON_SMALL = 18.dp
