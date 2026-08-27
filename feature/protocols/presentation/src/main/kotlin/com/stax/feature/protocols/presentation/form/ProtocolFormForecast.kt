package com.stax.feature.protocols.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.core.domain.ReminderBucket
import com.stax.feature.protocols.presentation.R

/**
 * §4.9.3 Reminder: one switch, and — when the schedule has no time of day to hang an alarm off — the
 * bucket chips that give it one. The supporting line states the offset and the notification style
 * (§4.13.3) rather than assuming them, so it never claims a style Settings has turned off.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.ReminderSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_reminder))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = StaxIcons.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.protocol_form_reminder_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.protocol_form_reminder_supporting,
                        state.draft.reminderOffsetMinutes,
                        notificationStyleLabel(state.notificationStyle),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.draft.reminderEnabled,
                onCheckedChange = { onAction(ProtocolFormAction.Edit.OnReminderToggle(it)) },
            )
        }
    }
    if (state.isReminderBucketVisible) {
        Row(
            modifier = Modifier.padding(top = FIELD_GAP),
            horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
        ) {
            ReminderBucket.entries.forEach { bucket ->
                FilterChip(
                    selected = state.draft.reminderBucket == bucket,
                    onClick = { onAction(ProtocolFormAction.Pick.OnReminderBucketSelected(bucket)) },
                    label = { Text(text = reminderBucketLabel(bucket), maxLines = 1) },
                )
            }
        }
    }
}

/** §4.9.3 Site restriction (Optional): the §4.0.2 body-region picker behind one row. */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.SiteRestrictionSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_site))
    FormPickerField(
        label = stringResource(R.string.protocol_form_site_label),
        value = bodyRegionLabel(state.draft.siteRestriction),
        onClick = { onAction(ProtocolFormAction.Overlay.OnPickerOpen(ProtocolFormPicker.BODY_REGION)) },
        icon = StaxIcons.PersonPinCircle,
        trailingIcon = StaxIcons.ExpandMore,
        isOptional = true,
        supporting = state.draft.siteRestriction?.let { stringResource(R.string.protocol_form_site_supporting) },
    )
}

/** §4.9.3 Notes (Optional): three lines of room for the timing and rotation notes a rule cannot hold. */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.NotesSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_notes))
    FormTextField(
        label = stringResource(R.string.protocol_form_notes),
        value = state.draft.notes,
        onValueChange = { onAction(ProtocolFormAction.Edit.OnNotesChange(it)) },
        icon = StaxIcons.Edit,
        isOptional = true,
        placeholder = stringResource(R.string.protocol_form_notes_placeholder),
        minLines = NOTES_MIN_LINES,
        keyboardType = KeyboardType.Text,
    )
}

/**
 * §4.9.3 Forecast & warnings, live-computed: how many doses the stock covers, how long that lasts on
 * this schedule, and the day it runs out — plus the two things worth acting on, a batch that expires
 * first (§4.9.3) and a reorder the protocol's end date needs (11b).
 *
 * Before a compound and a dose are in place there is nothing to forecast, so the card says what it is
 * waiting for rather than showing three zeroes.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.ForecastSection(state: ProtocolFormState) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_forecast))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = StaxIcons.Monitoring,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.protocol_form_forecast_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (val forecast = state.forecast) {
                null -> Text(
                    text = stringResource(R.string.protocol_form_forecast_empty),
                    modifier = Modifier.padding(top = FIELD_GAP),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    ForecastTiles(forecast = forecast)
                    ForecastNotices(forecast = forecast)
                }
            }
        }
    }
}

/** §4.9.3's three equal-grow stat tiles: doses left, days left, run-out date. */
@Suppress("FunctionName")
@Composable
private fun ForecastTiles(forecast: ProtocolForecastUi) {
    Row(
        modifier = Modifier.padding(top = FIELD_GAP),
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
    ) {
        ForecastTile(
            value = forecast.dosesLeft.toString(),
            label = stringResource(R.string.protocol_form_forecast_doses_left),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ForecastTile(
            value = forecast.daysLeft
                ?.let { stringResource(R.string.protocol_form_forecast_days, it) }
                ?: BEYOND_HORIZON,
            label = stringResource(R.string.protocol_form_forecast_days_left),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ForecastTile(
            value = forecast.runOutDate?.formatShort() ?: BEYOND_HORIZON,
            label = stringResource(R.string.protocol_form_forecast_run_out),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** The two things worth acting on: a batch that expires first (§4.9.3), and a reorder (11b). */
@Suppress("FunctionName")
@Composable
private fun ForecastNotices(forecast: ProtocolForecastUi) {
    forecast.expiryWarning?.let { warning ->
        ForecastNotice(
            icon = StaxIcons.Warning,
            title = stringResource(R.string.protocol_form_forecast_expiry_title),
            supporting = stringResource(
                R.string.protocol_form_forecast_expiry_supporting,
                warning.batchExpiry.formatShort(),
                warning.runOut.formatShort(),
            ),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    forecast.reorder?.let { reorder ->
        ForecastNotice(
            icon = StaxIcons.Inventory2,
            title = pluralStringResource(
                R.plurals.protocol_form_forecast_reorder_title,
                reorder.containers,
                reorder.containers,
                containerTypeLabel(reorder.containerType),
                reorder.orderBy.formatShort(),
            ),
            supporting = stringResource(
                R.string.protocol_form_forecast_reorder_supporting,
                reorder.coversUntil.formatWithYear(),
            ),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** One of §4.9.3's three equal-grow stat tiles: the figure large, what it counts small underneath. */
@Suppress("FunctionName")
@Composable
private fun RowScope.ForecastTile(value: String, label: String, container: Color, content: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Column(modifier = Modifier.padding(TILE_PADDING)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = content,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

/** The warning and reorder rows under the tiles — same shape, different thing to act on. */
@Suppress("FunctionName")
@Composable
private fun ForecastNotice(icon: Painter, title: String, supporting: String, container: Color, content: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FIELD_GAP),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(TILE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SMALL),
                tint = content,
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = content)
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = content)
            }
        }
    }
}

/**
 * §4.9.5 Lifecycle (Edit only): the three things that can happen to a protocol other than editing it.
 *
 * Pause and Duplicate act immediately; Archive asks first, because it is the one that takes the
 * protocol off every list the user looks at (§5.5).
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.LifecycleSection(onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_lifecycle))
    LifecycleButton(
        icon = StaxIcons.Pause,
        label = stringResource(R.string.protocol_form_pause),
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = { onAction(ProtocolFormAction.OnPauseClick) },
    )
    FieldSpacer()
    LifecycleButton(
        icon = StaxIcons.AddCircle,
        label = stringResource(R.string.protocol_form_duplicate),
        container = MaterialTheme.colorScheme.surfaceContainer,
        content = MaterialTheme.colorScheme.onSurface,
        onClick = { onAction(ProtocolFormAction.OnDuplicateClick) },
    )
    FieldSpacer()
    LifecycleButton(
        icon = StaxIcons.Delete,
        label = stringResource(R.string.protocol_form_archive),
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        onClick = { onAction(ProtocolFormAction.OnArchiveClick) },
    )
}

@Suppress("FunctionName")
@Composable
private fun LifecycleButton(icon: Painter, label: String, container: Color, content: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = icon, contentDescription = null, tint = content)
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
            Icon(painter = StaxIcons.ChevronRight, contentDescription = null, tint = content)
        }
    }
}

/** What a tile shows when the stock outlives the forecast horizon — not a number anyone plans around. */
private const val BEYOND_HORIZON = "—"
private const val NOTES_MIN_LINES = 3
private val TILE_PADDING = 12.dp
