package com.stax.feature.reconstitution.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.core.domain.UnitCode

/**
 * §4.6.2's hero: the number to draw, in whichever unit §4.6.4's Display tile is on.
 *
 * The syringe visualization and the size badge that shares this card land with M8-02 — what is here
 * is the figure they will be drawn around.
 */
@Suppress("FunctionName")
@Composable
internal fun DrawToHero(state: ReconstitutionState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(HERO_PADDING)) {
            Text(
                text = stringResource(R.string.reconstitution_draw_to),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.drawTo ?: stringResource(R.string.reconstitution_no_value),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = drawToUnitLabel(state.display),
                    modifier = Modifier.padding(start = VALUE_UNIT_GAP, bottom = UNIT_BASELINE_NUDGE),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * §4.6's progressive disclosure: one row that unfolds Mix (and, with M8-03, the dose ladder).
 *
 * Only Compact shows it — §6.4.2 gives Medium and Expanded the horizontal room to keep the same
 * sections open, so there is nothing there for a "Show calculation" row to reveal.
 */
@Suppress("FunctionName")
@Composable
internal fun ShowCalculationRow(
    isExpanded: Boolean,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onAction(ReconstitutionAction.OnToggleCalculation) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TILE_PADDING, vertical = ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(TILE_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (isExpanded) {
                        R.string.reconstitution_hide_calculation
                    } else {
                        R.string.reconstitution_show_calculation
                    },
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                painter = if (isExpanded) StaxIcons.ExpandLess else StaxIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * §4.6.4's Mix grid: container, diluent, desired dose, display.
 *
 * Two rows of two at Compact, which is the arrangement §4.6.4 draws; §6.4.2's Expanded layout puts
 * all four on one line, and that reflow lands with M8-05.
 *
 * [width] is the room the grid was given, and the tiles reflow on it rather than on the breakpoint —
 * see [MixTile]. It is passed in rather than measured here because the rows size themselves off their
 * tallest tile (`IntrinsicSize.Min`), and a `BoxWithConstraints` inside one of those cannot be
 * measured at all.
 */
@Suppress("FunctionName")
@Composable
internal fun MixSection(
    state: ReconstitutionState,
    width: Dp,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIconInline = (width - TILE_GAP) / 2 < ICON_INLINE_MIN_WIDTH
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        SectionHeader(text = stringResource(R.string.reconstitution_mix))
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            ContainerTile(state, isIconInline, onAction, Modifier.weight(1f))
            DiluentTile(state, isIconInline, onAction, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            DesiredDoseTile(state, isIconInline, onAction, Modifier.weight(1f))
            DisplayTile(state, isIconInline, onAction, Modifier.weight(1f))
        }
    }
}

/** §4.6.6: what the mix comes to — the concentration, and how many doses one container yields. */
@Suppress("FunctionName")
@Composable
internal fun ResultSection(state: ReconstitutionState, modifier: Modifier = Modifier) {
    val noValue = stringResource(R.string.reconstitution_no_value)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        SectionHeader(text = stringResource(R.string.reconstitution_result))
        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            ResultTile(
                icon = StaxIcons.Calculate,
                value = state.concentration ?: noValue,
                unit = stringResource(
                    R.string.reconstitution_concentration_unit,
                    unitLabel(state.containerUnit),
                ),
                label = stringResource(R.string.reconstitution_concentration),
                modifier = Modifier.weight(1f),
            )
            ResultTile(
                icon = StaxIcons.Inventory2,
                value = state.dosesPerContainer?.toString() ?: noValue,
                unit = null,
                label = stringResource(R.string.reconstitution_doses_per_container),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// §4.6.4 tiles
// ---------------------------------------------------------------------------

/** Read-only from the compound, typed in the standalone calculator (§4.6.4 #1). */
@Suppress("FunctionName")
@Composable
private fun ContainerTile(
    state: ReconstitutionState,
    isIconInline: Boolean,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    MixTile(
        icon = StaxIcons.Colorize,
        label = stringResource(R.string.reconstitution_container),
        isIconInline = isIconInline,
        modifier = modifier,
    ) {
        if (state.isContainerEditable) {
            TileValueField(
                value = state.containerAmount,
                onValueChange = { onAction(ReconstitutionAction.OnContainerAmountChange(it)) },
                modifier = Modifier.weight(1f),
            )
            UnitPicker(
                unit = state.containerUnit,
                options = RECONSTITUTABLE_UNITS,
                picker = ReconstitutionPicker.CONTAINER_UNIT,
                openPicker = state.openPicker,
                onSelect = { onAction(ReconstitutionAction.OnContainerUnitSelected(it)) },
                onAction = onAction,
            )
        } else {
            TileValueText(text = state.containerAmount, modifier = Modifier.weight(1f))
            TileUnitText(text = unitLabel(state.containerUnit))
        }
    }
}

/** §4.6.4 #2: the millilitres of diluent, and one of the two numbers the screen lives off. */
@Suppress("FunctionName")
@Composable
private fun DiluentTile(
    state: ReconstitutionState,
    isIconInline: Boolean,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    MixTile(
        icon = StaxIcons.WaterDrop,
        label = stringResource(R.string.reconstitution_diluent),
        isIconInline = isIconInline,
        modifier = modifier,
    ) {
        TileValueField(
            value = state.diluent,
            onValueChange = { onAction(ReconstitutionAction.OnDiluentChange(it)) },
            modifier = Modifier.weight(1f),
        )
        TileUnitText(text = unitLabel(UnitCode.ML))
    }
}

/** §4.6.4 #3: the dose to draw, in whichever unit of the container's family the user picks. */
@Suppress("FunctionName")
@Composable
private fun DesiredDoseTile(
    state: ReconstitutionState,
    isIconInline: Boolean,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    MixTile(
        icon = StaxIcons.Straighten,
        label = stringResource(R.string.reconstitution_desired_dose),
        isIconInline = isIconInline,
        modifier = modifier,
    ) {
        TileValueField(
            value = state.desiredDose,
            onValueChange = { onAction(ReconstitutionAction.OnDesiredDoseChange(it)) },
            modifier = Modifier.weight(1f),
        )
        UnitPicker(
            unit = state.doseUnit,
            options = state.doseUnitOptions,
            picker = ReconstitutionPicker.DOSE_UNIT,
            openPicker = state.openPicker,
            onSelect = { onAction(ReconstitutionAction.OnDoseUnitSelected(it)) },
            onAction = onAction,
        )
    }
}

/** §4.6.4 #4: millilitres or insulin units — the unit §4.6.2's "Draw to" is stated in. */
@Suppress("FunctionName")
@Composable
private fun DisplayTile(
    state: ReconstitutionState,
    isIconInline: Boolean,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    MixTile(
        icon = StaxIcons.Tune,
        label = stringResource(R.string.reconstitution_display),
        isIconInline = isIconInline,
        modifier = modifier,
        onClick = { onAction(ReconstitutionAction.OnPickerClick(ReconstitutionPicker.DISPLAY)) },
    ) {
        TileValueText(text = displayShortLabel(state.display), modifier = Modifier.weight(1f))
        Box {
            Icon(
                painter = StaxIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DropdownMenu(
                expanded = state.openPicker == ReconstitutionPicker.DISPLAY,
                onDismissRequest = { onAction(ReconstitutionAction.OnPickerDismiss) },
            ) {
                DoseDisplay.entries.forEach { option ->
                    PickerItem(
                        label = displayLabel(option),
                        isSelected = option == state.display,
                        onClick = { onAction(ReconstitutionAction.OnDisplaySelected(option)) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared tile shapes
// ---------------------------------------------------------------------------

/**
 * §4.6.4's tile: `surface-container`, a leading icon, the label held small above the value row.
 *
 * [isIconInline] moves the icon onto the label's line, which [MixSection] asks for once a tile is
 * narrower than [ICON_INLINE_MIN_WIDTH]: beside the value the icon costs `36dp` the value does not
 * have there — a `301dp` window leaves the typed number about four characters of field, and "0.25"
 * scrolls inside it and shows as "5". The rule is the width the tile was given rather than the
 * breakpoint, so it covers a small phone, a split screen and §6.4.2's narrow columns alike.
 *
 * [onClick] is set only on the tiles that open a menu of their own — the two typed tiles must leave
 * their taps to the field inside them, or tapping the number would dismiss the keyboard it summoned.
 */
@Suppress("FunctionName")
@Composable
private fun MixTile(
    icon: Painter,
    label: String,
    isIconInline: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    value: @Composable RowScope.() -> Unit,
) {
    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.padding(TILE_PADDING)) {
            if (isIconInline) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TileIcon(icon)
                        TileLabel(label)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, content = value)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TILE_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TileIcon(icon)
                    Column(modifier = Modifier.weight(1f)) {
                        TileLabel(label)
                        Row(verticalAlignment = Alignment.CenterVertically, content = value)
                    }
                }
            }
        }
    }
    val color = MaterialTheme.colorScheme.surfaceContainer
    val shape = MaterialTheme.shapes.large
    val tile = modifier.fillMaxHeight()
    if (onClick == null) {
        Surface(modifier = tile, shape = shape, color = color, content = content)
    } else {
        Surface(onClick = onClick, modifier = tile, shape = shape, color = color, content = content)
    }
}

@Suppress("FunctionName")
@Composable
private fun TileIcon(icon: Painter) {
    Icon(painter = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Suppress("FunctionName")
@Composable
private fun TileLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The number in a tile, typed.
 *
 * A [BasicTextField] rather than a `TextField`: §4.6.4's tile already draws the container and holds
 * the label, and a filled field inside it would stack a second container and a second label on top of
 * the ones that are there. Decimal keyboard, one line, no formatting on the way in — what is typed is
 * what the ViewModel parses, half-finished decimal points and all.
 */
@Suppress("FunctionName")
@Composable
private fun TileValueField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = LocalContentColor.current),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            // An empty field is zero-wide and has no caret to tap; the minimum keeps one there.
            Box(modifier = Modifier.widthIn(min = MinFieldWidth)) { inner() }
        },
    )
}

@Suppress("FunctionName")
@Composable
private fun TileValueText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.titleMedium, maxLines = 1)
}

@Suppress("FunctionName")
@Composable
private fun TileUnitText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = VALUE_UNIT_GAP),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * The unit that follows a typed number (§4.6.4).
 *
 * With a single option there is nothing to pick, so it is plain text — the IU family has one member,
 * and a menu offering it back to you is a button that does nothing.
 *
 * The minimum interactive size is waived for the same reason `:feature:compounds` waives it on its
 * unit suffix: enforced, the button is `48dp` tall and pushes the unit off the value's line. The tile
 * itself is well past `48dp` and the label above says what the number is.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
private fun UnitPicker(
    unit: UnitCode,
    options: List<UnitCode>,
    picker: ReconstitutionPicker,
    openPicker: ReconstitutionPicker?,
    onSelect: (UnitCode) -> Unit,
    onAction: (ReconstitutionAction) -> Unit,
) {
    if (options.size <= 1) {
        TileUnitText(text = unitLabel(unit))
        return
    }
    Box {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TextButton(
                onClick = { onAction(ReconstitutionAction.OnPickerClick(picker)) },
                contentPadding = UNIT_BUTTON_PADDING,
            ) {
                Text(text = unitLabel(unit), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        DropdownMenu(
            expanded = openPicker == picker,
            onDismissRequest = { onAction(ReconstitutionAction.OnPickerDismiss) },
        ) {
            options.forEach { option ->
                PickerItem(
                    label = unitLabel(option),
                    isSelected = option == unit,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun PickerItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = label) },
        onClick = onClick,
        leadingIcon = if (isSelected) {
            { Icon(painter = StaxIcons.Check, contentDescription = null) }
        } else {
            null
        },
    )
}

/** §4.6.6's tile: leading icon, the figure with its unit, the label underneath. */
@Suppress("FunctionName")
@Composable
private fun ResultTile(icon: Painter, value: String, unit: String?, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(TILE_PADDING),
            verticalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                unit?.let { TileUnitText(text = it) }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** §4.6.4 / §4.6.6: section labels are `primary`, with no card wrapping the section they head. */
@Suppress("FunctionName")
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

// ---------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------

@Composable
internal fun unitLabel(unit: UnitCode): String = stringResource(
    when (unit) {
        UnitCode.MCG -> R.string.reconstitution_unit_mcg
        UnitCode.G -> R.string.reconstitution_unit_g
        UnitCode.IU -> R.string.reconstitution_unit_iu
        UnitCode.ML -> R.string.reconstitution_unit_ml
        // Every other unit is a count, and a container measured in tablets has nothing to
        // reconstitute (§4.6) — it can only reach here as a compound the Helper was opened on
        // by mistake, where "mg" is the least misleading thing to draw.
        else -> R.string.reconstitution_unit_mg
    },
)

/** §4.6.4's Display menu: the choice, spelled out. */
@Composable
private fun displayLabel(display: DoseDisplay): String = stringResource(
    when (display) {
        DoseDisplay.MILLILITRES -> R.string.reconstitution_display_millilitres
        DoseDisplay.INSULIN_UNITS -> R.string.reconstitution_display_insulin_units
    },
)

/**
 * The same choice on the tile itself, which has a unit's worth of room and not a sentence's —
 * "Insulin units" ran out of tile at Compact. It reads as the unit §4.6.2 states the dose in.
 */
@Composable
private fun displayShortLabel(display: DoseDisplay): String = drawToUnitLabel(display)

/** §4.6.2's "Draw to": the same choice as a unit after a number — "0.10 mL", "10 units". */
@Composable
private fun drawToUnitLabel(display: DoseDisplay): String = stringResource(
    when (display) {
        DoseDisplay.MILLILITRES -> R.string.reconstitution_unit_ml
        DoseDisplay.INSULIN_UNITS -> R.string.reconstitution_units
    },
)

/** Enough of a field to aim at while it is empty. */
private val MinFieldWidth = 24.dp

/** Below this a §4.6.4 tile puts its icon on the label's line — see `MixTile`. */
private val ICON_INLINE_MIN_WIDTH = 132.dp

internal val SCREEN_PADDING = 16.dp
internal val SECTION_GAP = 16.dp
private val HERO_PADDING = 20.dp
private val TILE_PADDING = 12.dp
private val TILE_GAP = 8.dp
private val ROW_PADDING = 14.dp
private val VALUE_UNIT_GAP = 4.dp
private val UNIT_BASELINE_NUDGE = 6.dp
private val UNIT_BUTTON_PADDING = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
