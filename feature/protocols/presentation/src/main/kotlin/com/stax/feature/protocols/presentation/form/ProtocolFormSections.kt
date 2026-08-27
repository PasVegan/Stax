package com.stax.feature.protocols.presentation.form

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.feature.protocols.presentation.R
import kotlinx.datetime.LocalTime

/**
 * §4.9.2's edit-mode banner: saving regenerates the pending doses, and the logged history does not
 * move. Always visible in Edit mode, because that is the one consequence of Save the form cannot
 * show any other way.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.EditWarningBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FIELD_GAP),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_PADDING),
        ) {
            Icon(
                painter = StaxIcons.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column {
                Text(
                    text = stringResource(R.string.protocol_form_edit_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.protocol_form_edit_banner_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * §4.9.3 Compound (required): a `primary-container` card carrying the compound and its meta, opening
 * the §4.0.2 picker. Before anything is picked it is the same card with a prompt in place of a name,
 * so the section keeps its shape rather than appearing once a choice has been made.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.CompoundSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_compound))
    val compound = state.compound
    Surface(
        onClick = { onAction(ProtocolFormAction.Overlay.OnPickerOpen(ProtocolFormPicker.COMPOUND)) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    painter = StaxIcons.Colorize,
                    contentDescription = null,
                    modifier = Modifier.padding(AVATAR_PADDING),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = compound?.name ?: stringResource(R.string.protocol_form_compound_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = compound?.let { compoundMeta(it) }
                        ?: stringResource(R.string.protocol_form_compound_empty_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                painter = StaxIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    state.errorText(ProtocolFormField.COMPOUND)?.let { FieldError(message = it) }
}

/** "Peptide · 5 mg vial · 2.5 mg/mL" (§4.9.3) — the concentration half only when there is one. */
@Composable
internal fun compoundMeta(compound: CompoundPickUi): String {
    val size = stringResource(
        R.string.protocol_form_compound_size,
        compound.amount,
        unitLabel(compound.amountUnit),
        containerTypeLabel(compound.containerType),
    )
    val concentration = compound.concentration?.let { amount ->
        val unit = compound.concentrationUnit ?: return@let null
        val per = compound.concentrationPerUnit ?: return@let null
        stringResource(R.string.protocol_form_concentration, amount, unitLabel(unit), unitLabel(per))
    }
    return listOfNotNull(categoryLabel(compound.category), size, concentration).joinToString(META_SEPARATOR)
}

/** §4.9.3 Route (required): four segments, defaulted from the compound but the user's to override. */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.RouteSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_route))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Route.entries.forEachIndexed { index, route ->
            SegmentedButton(
                selected = state.draft.route == route,
                onClick = { onAction(ProtocolFormAction.Pick.OnRouteSelected(route)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = Route.entries.size),
            ) {
                Text(text = routeLabel(route), maxLines = 1)
            }
        }
    }
}

/**
 * §4.9.3 Planned dose (required): the figure large enough to read at arm's length, its unit as a
 * tonal pill beside it, and — when the compound states a concentration — what that dose comes to in
 * volume and insulin units.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.PlannedDoseSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_dose))
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
                    painter = StaxIcons.Straighten,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                    value = state.draft.doseAmount,
                    onValueChange = { onAction(ProtocolFormAction.Edit.OnDoseChange(it)) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.protocol_form_dose_placeholder),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    colors = transparentFieldColors(),
                )
                DoseUnitPill(state = state, onAction = onAction)
            }
            state.equivalence?.let { EquivalenceChip(equivalence = it) }
        }
    }
    state.errorText(ProtocolFormField.DOSE)?.let { FieldError(message = it) }
}

/** The `secondary-container` unit pill of §4.9.3, and the dropdown of units the compound's family offers. */
@Suppress("FunctionName")
@Composable
private fun DoseUnitPill(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    Box {
        Surface(
            onClick = { onAction(ProtocolFormAction.Overlay.OnPickerOpen(ProtocolFormPicker.DOSE_UNIT)) },
            shape = StaxShapes.Pill,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PILL_PADDING, vertical = PILL_PADDING / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = unitLabel(state.draft.doseUnit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Icon(
                    painter = StaxIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SMALL),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        DropdownMenu(
            expanded = state.openPicker == ProtocolFormPicker.DOSE_UNIT,
            onDismissRequest = { onAction(ProtocolFormAction.Overlay.OnPickerDismiss) },
        ) {
            state.doseUnitOptions.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(text = unitLabel(unit)) },
                    onClick = { onAction(ProtocolFormAction.Pick.OnDoseUnitSelected(unit)) },
                    leadingIcon = if (unit == state.draft.doseUnit) {
                        { Icon(painter = StaxIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** §4.9.3's equivalence chip — shown only when the compound's concentration makes it derivable. */
@Suppress("FunctionName")
@Composable
private fun EquivalenceChip(equivalence: DoseEquivalenceUi) {
    val volume = stringResource(
        R.string.protocol_form_equivalent_volume,
        equivalence.volume,
        unitLabel(equivalence.volumeUnit),
    )
    val text = equivalence.insulinUnits
        ?.let { stringResource(R.string.protocol_form_equivalent_with_units, volume, it) }
        ?: stringResource(R.string.protocol_form_equivalent, volume)
    Surface(
        modifier = Modifier.padding(top = FIELD_GAP),
        shape = StaxShapes.Pill,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PILL_PADDING, vertical = PILL_PADDING / 2),
            horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = StaxIcons.Calculate,
                contentDescription = null,
                modifier = Modifier.size(ICON_SMALL),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/**
 * §4.9.3 Schedule (required): the single-select chip row, whatever input the chosen chip needs, and
 * the times of day — which may legitimately be none, meaning "no specific time".
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.ScheduleSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_schedule))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
    ) {
        SCHEDULE_CHIPS.forEach { type ->
            FilterChip(
                selected = state.draft.scheduleType == type,
                onClick = { onAction(ProtocolFormAction.Pick.OnScheduleTypeSelected(type)) },
                label = { Text(text = scheduleLabel(type), maxLines = 1) },
                leadingIcon = if (state.draft.scheduleType == type) {
                    { Icon(painter = StaxIcons.Check, contentDescription = null, modifier = Modifier.size(ICON_SMALL)) }
                } else {
                    null
                },
            )
        }
    }

    scheduleCountLabel(state.draft.scheduleType)?.let { label ->
        FieldSpacer()
        FormTextField(
            label = label,
            value = state.draft.scheduleCount().orEmpty(),
            onValueChange = { onAction(ProtocolFormAction.Edit.OnScheduleCountChange(it)) },
            icon = StaxIcons.Tune,
            error = state.errorText(ProtocolFormField.SCHEDULE_COUNT),
            keyboardType = KeyboardType.Number,
        )
    }

    if (state.draft.scheduleType == ScheduleType.SPECIFIC_WEEKDAYS) {
        FieldSpacer()
        WeekdayPicker(state = state, onAction = onAction)
        state.errorText(ProtocolFormField.WEEKDAYS)?.let { FieldError(message = it) }
    }

    FieldSpacer()
    TimesOfDayRow(state = state, onAction = onAction)
}

/** §4.9.3's 7-day circle picker: selected = `primary` fill, unselected = outlined. */
@Suppress("FunctionName")
@Composable
private fun WeekdayPicker(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
    ) {
        weekdaysInLocaleOrder().forEach { day ->
            val isSelected = day in state.draft.weekdays
            val name = day.fullLabel()
            Surface(
                onClick = { onAction(ProtocolFormAction.Pick.OnWeekdayToggled(day)) },
                modifier = Modifier
                    .weight(1f)
                    .size(WEEKDAY_CIRCLE)
                    // The letter alone is meaningless read aloud, and two of them are "T".
                    .clearAndSetSemantics {
                        contentDescription = name
                        selected = isSelected
                    },
                shape = StaxShapes.Pill,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = if (isSelected) {
                    null
                } else {
                    SegmentedButtonDefaults.borderStroke(
                        MaterialTheme.colorScheme.outline,
                    )
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = day.narrowLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

/**
 * §4.9.3 Times of day: a pill per time, plus "Add time". Tapping a pill removes it — the list is
 * short enough that a separate delete affordance on each would cost more room than it buys.
 *
 * An empty list is allowed and means "no specific time" (§5.2), which the supporting line says out
 * loud so it does not read as a field the user forgot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionName")
@Composable
private fun TimesOfDayRow(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
        verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
    ) {
        state.draft.dosageTimes.forEach { time -> TimePill(time = time, onAction = onAction) }
        TextButton(onClick = { onAction(ProtocolFormAction.OnAddTimeClick) }) {
            Icon(painter = StaxIcons.Add, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
            Text(
                text = stringResource(R.string.protocol_form_add_time),
                modifier = Modifier.padding(start = LABEL_GAP),
            )
        }
    }
    if (state.draft.dosageTimes.isEmpty()) {
        Text(
            text = stringResource(R.string.protocol_form_no_specific_time),
            modifier = Modifier.padding(start = FIELD_INSET, top = LABEL_GAP),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun TimePill(time: LocalTime, onAction: (ProtocolFormAction) -> Unit) {
    val label = time.formatTime()
    val removeLabel = stringResource(R.string.protocol_form_time_remove, label)
    Surface(
        onClick = { onAction(ProtocolFormAction.OnTimeRemoved(time)) },
        shape = StaxShapes.Pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = PILL_PADDING, vertical = PILL_PADDING / 2)
                .clearAndSetSemantics {
                    contentDescription = removeLabel
                },
            horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = StaxIcons.Schedule,
                contentDescription = null,
                modifier = Modifier.size(ICON_SMALL),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * 11b's "Next 7 days · N doses" strip: the horizon Save will actually generate, drawn from the same
 * schedule rule the generator uses (§5.2). It is what turns "every 3 days from the 26th" from a rule
 * into something the user can check at a glance before saving.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.SchedulePreview(preview: SchedulePreviewUi) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FIELD_GAP),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = StaxIcons.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SMALL),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.protocol_form_preview_title,
                        pluralStringResource(
                            R.plurals.protocol_form_preview_doses,
                            preview.doseCount,
                            preview.doseCount,
                        ),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Row(
                modifier = Modifier.padding(top = FIELD_GAP),
                horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
            ) {
                preview.days.forEach { day -> PreviewDayCell(day = day, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun PreviewDayCell(day: PreviewDayUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (day.hasDose) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = FIELD_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = day.date.dayOfWeek.narrowLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            // A dot rather than a second label: the fill already says "dose here", and the dot is
            // what carries that to anyone who cannot separate the two container tones.
            Box(
                modifier = Modifier
                    .padding(top = DOT_GAP)
                    .size(DOT_SIZE),
            ) {
                if (day.hasDose) {
                    Surface(
                        modifier = Modifier.size(DOT_SIZE),
                        shape = StaxShapes.Pill,
                        color = MaterialTheme.colorScheme.primary,
                        content = {},
                    )
                }
            }
        }
    }
}

/** §4.9.3 Duration: Start (required) and End (Optional, "Open-ended" when unset), side by side. */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.DurationSection(state: ProtocolFormState, onAction: (ProtocolFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.protocol_form_section_duration))
    Row(horizontalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
        FormPickerField(
            label = stringResource(R.string.protocol_form_start),
            value = state.draft.startDate?.formatShort().orEmpty(),
            onClick = { onAction(ProtocolFormAction.Overlay.OnDateFieldClick(ProtocolDateField.START)) },
            modifier = Modifier.weight(1f),
            icon = StaxIcons.Today,
            error = state.errorText(ProtocolFormField.START_DATE),
        )
        FormPickerField(
            label = stringResource(R.string.protocol_form_end),
            value = state.draft.endDate?.formatShort()
                ?: stringResource(R.string.protocol_form_end_open_ended),
            onClick = { onAction(ProtocolFormAction.Overlay.OnDateFieldClick(ProtocolDateField.END)) },
            modifier = Modifier.weight(1f),
            icon = StaxIcons.Today,
            isOptional = true,
            error = state.errorText(ProtocolFormField.END_DATE),
        )
    }
}

/** The message Save put under [field], or null while the field is fine. */
@Composable
internal fun ProtocolFormState.errorText(field: ProtocolFormField): String? =
    errors[field]?.let { stringResource(it.messageRes()) }

internal fun ProtocolFormError.messageRes(): Int = when (this) {
    ProtocolFormError.COMPOUND_REQUIRED -> R.string.protocol_form_error_compound_required
    ProtocolFormError.DOSE_NOT_POSITIVE -> R.string.protocol_form_error_dose_not_positive
    ProtocolFormError.SCHEDULE_COUNT_INVALID -> R.string.protocol_form_error_schedule_count
    ProtocolFormError.WEEKDAYS_REQUIRED -> R.string.protocol_form_error_weekdays_required
    ProtocolFormError.START_DATE_REQUIRED -> R.string.protocol_form_error_start_required
    ProtocolFormError.END_DATE_NOT_AFTER_START -> R.string.protocol_form_error_end_before_start
}

/**
 * The dose field draws no container of its own — the card it sits in is the container, and a second
 * filled surface inside it would read as a field within a field.
 */
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

private const val META_SEPARATOR = " · "
private val AVATAR_PADDING = 10.dp
private val PILL_PADDING = 12.dp
private val WEEKDAY_CIRCLE = 44.dp
private val DOT_SIZE = 6.dp
private val DOT_GAP = 4.dp
