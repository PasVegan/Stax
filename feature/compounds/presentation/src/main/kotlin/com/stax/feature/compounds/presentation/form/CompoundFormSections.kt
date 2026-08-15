package com.stax.feature.compounds.presentation.form

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.ContainerType
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import com.stax.feature.compounds.presentation.R
import com.stax.feature.compounds.presentation.list.categoryLabel
import com.stax.feature.compounds.presentation.list.formLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale as JavaLocale

/** §4.4.3 Basics — every field required. */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.BasicsSection(
    state: CompoundFormState,
    onAction: (CompoundFormAction) -> Unit,
    focus: CompoundFormFocus,
) {
    val draft = state.draft
    FormSectionHeader(text = stringResource(R.string.compound_form_section_basics))

    FormTextField(
        label = stringResource(R.string.compound_form_name),
        value = draft.name,
        onValueChange = { onAction(CompoundFormAction.Edit.OnNameChange(it)) },
        icon = StaxIcons.Edit,
        error = state.errorText(CompoundFormField.NAME),
        focusRequester = focus.name,
    )
    FieldSpacer()

    EnumPickerField(
        label = stringResource(R.string.compound_form_category),
        icon = StaxIcons.Science,
        picker = CompoundFormPicker.CATEGORY,
        options = CompoundCategory.entries,
        selected = draft.category,
        labelOf = { categoryLabel(it) },
        openPicker = state.openPicker,
        onAction = onAction,
        onSelect = { CompoundFormAction.Pick.OnCategorySelected(it) },
    )
    FieldSpacer()

    EnumPickerField(
        label = stringResource(R.string.compound_form_form),
        icon = StaxIcons.Colorize,
        picker = CompoundFormPicker.FORM,
        options = CompoundForm.entries,
        selected = draft.form,
        labelOf = { formLabel(it) },
        openPicker = state.openPicker,
        onAction = onAction,
        onSelect = { CompoundFormAction.Pick.OnFormSelected(it) },
    )
    FieldSpacer()

    EnumPickerField(
        label = stringResource(R.string.compound_form_container_type),
        icon = StaxIcons.Inventory2,
        picker = CompoundFormPicker.CONTAINER_TYPE,
        options = ContainerType.entries,
        selected = draft.containerType,
        labelOf = { containerTypeLabel(it) },
        openPicker = state.openPicker,
        onAction = onAction,
        onSelect = { CompoundFormAction.Pick.OnContainerTypeSelected(it) },
    )
}

/**
 * §4.4.3 Stock — the section whose layout follows the width it is *given*, not the window's.
 *
 * A Compact phone has room to put the two counts side by side, but the left column of §6.4.2's
 * two-column layout is narrower than that phone at Medium, and the same row there wraps "Amount per
 * container" onto three lines. So the split is decided from the column's own width: side by side
 * when there is room, stacked when there is not, at any breakpoint. The "Helper" button (§4.6) moves
 * out of the concentration row on the same rule, for the same reason.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.StockSection(
    state: CompoundFormState,
    onAction: (CompoundFormAction) -> Unit,
    focus: CompoundFormFocus,
) {
    FormSectionHeader(text = stringResource(R.string.compound_form_section_stock))
    BoxWithConstraints {
        val hasRoomForTwoFields = maxWidth >= SIDE_BY_SIDE_MIN_WIDTH
        Column {
            val containers = @Composable { modifier: Modifier ->
                FormTextField(
                    label = stringResource(R.string.compound_form_containers),
                    value = state.draft.totalContainers,
                    onValueChange = { onAction(CompoundFormAction.Edit.OnTotalContainersChange(it)) },
                    icon = null,
                    modifier = modifier,
                    error = state.errorText(CompoundFormField.TOTAL_CONTAINERS),
                    keyboardType = KeyboardType.Number,
                    focusRequester = focus.totalContainers,
                )
            }
            if (hasRoomForTwoFields) {
                // Not an even split: a container count is one or two digits, while "Amount per
                // container" has a long label *and* a unit picker sharing its width.
                Row(horizontalArrangement = Arrangement.spacedBy(FIELD_GAP)) {
                    containers(Modifier.weight(COUNT_FIELD_WEIGHT))
                    AmountPerContainerField(
                        state = state,
                        onAction = onAction,
                        focus = focus,
                        modifier = Modifier.weight(AMOUNT_FIELD_WEIGHT),
                    )
                }
            } else {
                containers(Modifier.fillMaxWidth())
                FieldSpacer()
                AmountPerContainerField(state = state, onAction = onAction, focus = focus)
            }
            FieldSpacer()
            ConcentrationField(
                state = state,
                onAction = onAction,
                focus = focus,
                isHelperInline = hasRoomForTwoFields,
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun AmountPerContainerField(
    state: CompoundFormState,
    onAction: (CompoundFormAction) -> Unit,
    focus: CompoundFormFocus,
    modifier: Modifier = Modifier,
) {
    FormTextField(
        label = stringResource(R.string.compound_form_amount_per_container),
        value = state.draft.amountPerContainer,
        onValueChange = { onAction(CompoundFormAction.Edit.OnAmountPerContainerChange(it)) },
        icon = null,
        modifier = modifier,
        error = state.errorText(CompoundFormField.AMOUNT_PER_CONTAINER),
        keyboardType = KeyboardType.Decimal,
        focusRequester = focus.amountPerContainer,
        suffix = {
            UnitPicker(
                picker = CompoundFormPicker.PRIMARY_UNIT,
                options = state.draft.form.primaryUnitOptions(),
                selected = state.draft.primaryUnit,
                labelOf = { unitLabel(it) },
                openPicker = state.openPicker,
                onAction = onAction,
                onSelect = { CompoundFormAction.Pick.OnPrimaryUnitSelected(it) },
            )
        },
    )
}

@Suppress("FunctionName")
@Composable
private fun ConcentrationField(
    state: CompoundFormState,
    onAction: (CompoundFormAction) -> Unit,
    focus: CompoundFormFocus,
    isHelperInline: Boolean,
) {
    val helper = @Composable { modifier: Modifier ->
        FilledTonalButton(onClick = { onAction(CompoundFormAction.OnReconstitutionHelperClick) }, modifier = modifier) {
            Icon(painter = StaxIcons.Calculate, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
            Text(text = stringResource(R.string.compound_form_helper), modifier = Modifier.padding(start = LABEL_GAP))
        }
    }
    FormTextField(
        label = stringResource(R.string.compound_form_concentration),
        value = state.draft.concentrationAmount,
        onValueChange = { onAction(CompoundFormAction.Edit.OnConcentrationChange(it)) },
        icon = StaxIcons.Straighten,
        // §4.4.3 marks the field Optional, but a non-ampoule injectable has no usable dose without
        // it — so the badge disappears exactly when the rule makes it required.
        isOptional = !state.isConcentrationRequired,
        error = state.errorText(CompoundFormField.CONCENTRATION),
        keyboardType = KeyboardType.Decimal,
        focusRequester = focus.concentration,
        suffix = {
            UnitPicker(
                picker = CompoundFormPicker.CONCENTRATION_UNIT,
                // Per the Form, not per the app: a tablet's strength is per tablet, not per mL.
                options = state.draft.form.concentrationUnitOptions(),
                selected = ConcentrationUnits(state.draft.concentrationUnit, state.draft.concentrationPerUnit),
                labelOf = { concentrationUnitLabel(it) },
                openPicker = state.openPicker,
                onAction = onAction,
                onSelect = { CompoundFormAction.Pick.OnConcentrationUnitSelected(it) },
            )
        },
        trailing = if (isHelperInline) {
            { helper(Modifier.padding(end = FIELD_GAP)) }
        } else {
            null
        },
    )
    if (!isHelperInline) {
        Box(modifier = Modifier.padding(top = LABEL_GAP)) { helper(Modifier) }
    }
}

/** §4.4.3 Storage & batch — one required dropdown, then four optional fields. */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.StorageSection(state: CompoundFormState, onAction: (CompoundFormAction) -> Unit) {
    val draft = state.draft
    FormSectionHeader(text = stringResource(R.string.compound_form_section_storage))

    EnumPickerField(
        label = stringResource(R.string.compound_form_storage_location),
        icon = StaxIcons.Inventory,
        picker = CompoundFormPicker.STORAGE_LOCATION,
        options = StorageLocation.entries,
        selected = draft.storageLocation,
        labelOf = { storageLocationLabel(it) },
        openPicker = state.openPicker,
        onAction = onAction,
        onSelect = { CompoundFormAction.Pick.OnStorageLocationSelected(it) },
    )
    FieldSpacer()

    FormPickerField(
        label = stringResource(R.string.compound_form_batch_expiry),
        value = draft.batchExpiryDate?.formatLong() ?: stringResource(R.string.compound_form_tap_to_set),
        icon = StaxIcons.CalendarMonth,
        trailingIcon = StaxIcons.Edit,
        onClick = { onAction(CompoundFormAction.Overlay.OnBatchExpiryClick) },
        isOptional = true,
    )
    FieldSpacer()

    FormTextField(
        label = stringResource(R.string.compound_form_batch_number),
        value = draft.batchNumber,
        onValueChange = { onAction(CompoundFormAction.Edit.OnBatchNumberChange(it)) },
        icon = StaxIcons.Tune,
        isOptional = true,
        placeholder = stringResource(R.string.compound_form_tap_to_set),
    )
    FieldSpacer()

    FormTextField(
        label = stringResource(R.string.compound_form_supplier),
        value = draft.supplier,
        onValueChange = { onAction(CompoundFormAction.Edit.OnSupplierChange(it)) },
        icon = StaxIcons.Flag,
        isOptional = true,
        placeholder = stringResource(R.string.compound_form_tap_to_set),
    )
    FieldSpacer()

    FormTextField(
        label = stringResource(R.string.compound_form_expiry_after_opening),
        value = draft.expiryAfterOpeningDays,
        onValueChange = { onAction(CompoundFormAction.Edit.OnExpiryAfterOpeningDaysChange(it)) },
        icon = StaxIcons.EventBusy,
        isOptional = true,
        supporting = stringResource(R.string.compound_form_expiry_after_opening_supporting),
        keyboardType = KeyboardType.Number,
        suffix = { Text(text = stringResource(R.string.compound_form_days)) },
    )
}

/**
 * §4.4.3 Opened container: the empty state and its "Add already opened" CTA until one exists, then
 * the summary card of §4.3.3 with the pencil that edits it. Both open the §4.5 sheet, which is M7-06.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.OpenedContainerSection(state: CompoundFormState, onAction: (CompoundFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.compound_form_section_opened))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val opened = state.opened
        if (opened == null) {
            OpenedContainerEmptyState(onAction = onAction)
        } else {
            OpenedContainerSummary(opened = opened, onAction = onAction)
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun OpenedContainerEmptyState(onAction: (CompoundFormAction) -> Unit) {
    Row(
        modifier = Modifier.padding(CARD_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CARD_PADDING),
    ) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Icon(
                painter = StaxIcons.Inventory2,
                contentDescription = null,
                modifier = Modifier.padding(FIELD_GAP),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
            Text(
                text = stringResource(R.string.compound_form_opened_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.compound_form_opened_empty_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { onAction(CompoundFormAction.Overlay.OnOpenedContainerClick) },
                modifier = Modifier.padding(top = FIELD_GAP),
            ) {
                Icon(painter = StaxIcons.Add, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
                Text(
                    text = stringResource(R.string.compound_form_opened_add),
                    modifier = Modifier.padding(start = LABEL_GAP),
                )
            }
        }
    }
}

/** The opened vial card of §4.3.3, reused here: how much is left, of how much, and since when. */
@Suppress("FunctionName")
@Composable
private fun OpenedContainerSummary(opened: OpenedContainerUi, onAction: (CompoundFormAction) -> Unit) {
    Column(
        modifier = Modifier.padding(CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = StaxIcons.Colorize, contentDescription = null)
            Text(
                text = stringResource(R.string.compound_form_opened_title, containerTypeLabel(opened.containerType)),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = FIELD_GAP),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = { onAction(CompoundFormAction.Overlay.OnOpenedContainerClick) }) {
                Icon(painter = StaxIcons.Edit, contentDescription = null, modifier = Modifier.size(ICON_SMALL))
                Text(
                    text = stringResource(R.string.compound_form_opened_edit),
                    modifier = Modifier.padding(start = LABEL_GAP),
                )
            }
        }
        LinearProgressIndicator(progress = { opened.fillFraction }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(
                    R.string.compound_form_opened_remaining,
                    opened.remaining,
                    opened.capacity,
                    opened.unit,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.compound_form_opened_days_ago,
                    opened.openedDaysAgo,
                    opened.openedDaysAgo,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** §4.4.3 Notes — three lines of room, optional. */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.NotesSection(state: CompoundFormState, onAction: (CompoundFormAction) -> Unit) {
    FormSectionHeader(text = stringResource(R.string.compound_form_section_notes))
    FormTextField(
        label = stringResource(R.string.compound_form_notes),
        value = state.draft.notes,
        onValueChange = { onAction(CompoundFormAction.Edit.OnNotesChange(it)) },
        icon = StaxIcons.Edit,
        isOptional = true,
        placeholder = stringResource(R.string.compound_form_notes_placeholder),
        minLines = NOTES_MIN_LINES,
    )
}

/**
 * The live stock preview §6.4.2 puts under the wide layouts' right column. Everything in it is
 * derived from the fields the user is filling in, so it answers "what am I actually entering?"
 * without a save or a round trip.
 */
@Suppress("FunctionName")
@Composable
internal fun ColumnScope.ForecastSection(forecast: StockForecastUi) {
    FormSectionHeader(text = stringResource(R.string.compound_form_section_forecast))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(LABEL_GAP),
        ) {
            Text(
                text = stringResource(R.string.compound_form_forecast_total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = forecast.totalStock, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = pluralStringResource(
                    R.plurals.compound_form_forecast_containers,
                    forecast.containers,
                    forecast.containers,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            forecast.volumePerContainer?.let {
                Text(
                    text = stringResource(R.string.compound_form_forecast_volume, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The shape every §4.4.3 dropdown shares: a picker row whose menu lists one enum's values, ticking
 * the current one. [onSelect] builds the action rather than performing it, so a field only has to
 * name which `Pick` it is.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
private fun <T> EnumPickerField(
    label: String,
    icon: Painter,
    picker: CompoundFormPicker,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    openPicker: CompoundFormPicker?,
    onAction: (CompoundFormAction) -> Unit,
    onSelect: (T) -> CompoundFormAction,
) {
    FormPickerField(
        label = label,
        value = labelOf(selected),
        icon = icon,
        trailingIcon = StaxIcons.ExpandMore,
        onClick = { onAction(CompoundFormAction.Overlay.OnPickerOpen(picker)) },
    ) {
        DropdownMenu(
            expanded = openPicker == picker,
            onDismissRequest = { onAction(CompoundFormAction.Overlay.OnPickerDismiss) },
        ) {
            options.forEach { option ->
                FormPickerItem(
                    label = labelOf(option),
                    isSelected = option == selected,
                    onClick = { onAction(onSelect(option)) },
                )
            }
        }
    }
}

/** The same list, inline after a numeric value instead of as a row of its own (§4.4.3 "+ unit picker"). */
@Suppress("FunctionName", "LongParameterList")
@Composable
private fun <T> UnitPicker(
    picker: CompoundFormPicker,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    openPicker: CompoundFormPicker?,
    onAction: (CompoundFormAction) -> Unit,
    onSelect: (T) -> CompoundFormAction,
) {
    UnitSuffix(
        unit = labelOf(selected),
        onClick = { onAction(CompoundFormAction.Overlay.OnPickerOpen(picker)) },
        isMenuOpen = openPicker == picker,
        onMenuDismiss = { onAction(CompoundFormAction.Overlay.OnPickerDismiss) },
    ) {
        options.forEach { option ->
            FormPickerItem(
                label = labelOf(option),
                isSelected = option == selected,
                onClick = { onAction(onSelect(option)) },
            )
        }
    }
}

/** The message §4.4.4 put under [field], or null while the field is fine. */
@Composable
private fun CompoundFormState.errorText(field: CompoundFormField): String? =
    errors[field]?.let { stringResource(it.messageRes()) }

@Suppress("FunctionName")
@Composable
private fun FieldSpacer() {
    Box(modifier = Modifier.size(FIELD_GAP))
}

@Composable
internal fun containerTypeLabel(containerType: ContainerType): String = stringResource(
    when (containerType) {
        ContainerType.VIAL -> R.string.compound_form_container_vial
        ContainerType.BOTTLE -> R.string.compound_form_container_bottle
        ContainerType.BLISTER -> R.string.compound_form_container_blister
        ContainerType.PACKET -> R.string.compound_form_container_packet
        ContainerType.TUB -> R.string.compound_form_container_tub
        ContainerType.AMPOULE -> R.string.compound_form_container_ampoule
    },
)

@Composable
internal fun storageLocationLabel(storageLocation: StorageLocation): String = stringResource(
    when (storageLocation) {
        StorageLocation.FRIDGE -> R.string.compound_form_storage_fridge
        StorageLocation.ROOM_TEMP -> R.string.compound_form_storage_room_temp
        StorageLocation.FREEZER -> R.string.compound_form_storage_freezer
    },
)

@Composable
internal fun unitLabel(unit: UnitCode): String = stringResource(
    when (unit) {
        UnitCode.MCG -> R.string.compound_form_unit_mcg
        UnitCode.MG -> R.string.compound_form_unit_mg
        UnitCode.G -> R.string.compound_form_unit_g
        UnitCode.IU -> R.string.compound_form_unit_iu
        UnitCode.ML -> R.string.compound_form_unit_ml
        UnitCode.CAPSULE -> R.string.compound_form_unit_capsule
        UnitCode.TABLET -> R.string.compound_form_unit_tablet
        UnitCode.SCOOP -> R.string.compound_form_unit_scoop
        UnitCode.DROP -> R.string.compound_form_unit_drop
    },
)

/** "mg/mL", "mg/tablet" — the concentration picker names the whole ratio (§4.4.3). */
@Composable
internal fun concentrationUnitLabel(units: ConcentrationUnits): String =
    stringResource(R.string.compound_form_unit_per, unitLabel(units.amount), unitLabel(units.per))

/** The message §4.4.4 shows under a rejected field. */
internal fun CompoundFormError.messageRes(): Int = when (this) {
    CompoundFormError.NAME_REQUIRED -> R.string.compound_form_error_name_required
    CompoundFormError.NAME_TOO_LONG -> R.string.compound_form_error_name_too_long
    CompoundFormError.CONTAINERS_INVALID -> R.string.compound_form_error_containers_invalid
    CompoundFormError.CONTAINERS_BELOW_OPENED -> R.string.compound_form_error_containers_below_opened
    CompoundFormError.AMOUNT_NOT_POSITIVE -> R.string.compound_form_error_amount_not_positive
    CompoundFormError.CONCENTRATION_REQUIRED -> R.string.compound_form_error_concentration_required
    CompoundFormError.CONCENTRATION_NOT_POSITIVE -> R.string.compound_form_error_concentration_not_positive
}

/** "Jul 29, 2027" — a batch expiry is years out, so unlike the list row (§4.2.3) it carries its year. */
@Composable
private fun LocalDate.formatLong(): String {
    val languageTag = Locale.current.toLanguageTag()
    val formatter = remember(languageTag) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, EXPIRY_SKELETON), locale)
    }
    return formatter.format(toJavaLocalDate())
}

/** Skeleton, not a pattern: `getBestDateTimePattern` reorders it per locale ("Jul 29, 2027" / "29 juil. 2027"). */
private const val EXPIRY_SKELETON = "yMMMd"

private const val NOTES_MIN_LINES = 3

/** Below this, two fields on one line leave each label too little room and it wraps (§6.4.2). */
private val SIDE_BY_SIDE_MIN_WIDTH = 360.dp
private const val COUNT_FIELD_WEIGHT = 0.72f
private const val AMOUNT_FIELD_WEIGHT = 1.28f
private val CARD_PADDING = 16.dp
private val LABEL_GAP = 4.dp
private val ICON_SMALL = 18.dp
