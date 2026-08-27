package com.stax.feature.reconstitution.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.domain.UnitCode

/**
 * §4.6.2's hero: the number to draw over the syringe it is drawn on, with the size badge that picks
 * which syringe that is on the same top row.
 *
 * The figure and the barrel are one statement — "10 units" and a barrel filled a tenth of the way say
 * the same thing twice, once to read and once to recognise at the bench.
 */
@Suppress("FunctionName")
@Composable
internal fun DrawToHero(
    state: ReconstitutionState,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(HERO_PADDING),
            verticalArrangement = Arrangement.spacedBy(HERO_GAP),
        ) {
            // The badge shares the label's line rather than the figure's: "U-100 · 1 mL" is wide, and
            // beside the display-sized number it squeezed "units" into two lines on a Compact phone.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.reconstitution_draw_to),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SyringeSizeBadge(syringeSize = state.syringeSize, onAction = onAction)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.drawTo ?: stringResource(R.string.reconstitution_no_value),
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                )
                Text(
                    text = drawToUnitLabel(state.display),
                    modifier = Modifier.padding(start = VALUE_UNIT_GAP, bottom = UNIT_BASELINE_NUDGE),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            SyringeVisualization(
                syringeSize = state.syringeSize,
                fill = state.syringeFill,
                drawTo = state.drawTo,
                display = state.display,
            )
            state.equivalence?.let { equivalence ->
                EquivalenceChips(
                    equivalence = equivalence,
                    doseUnit = state.doseUnit,
                    // §4.6.3's third chip is the insulin one, and there is no U-100 figure to put on
                    // it while the dose is being drawn on a barrel graduated in millilitres.
                    showUnits = state.syringeSize.isInsulin,
                )
            }
        }
    }
}

/**
 * §4.6.3: the drawn dose said two or three ways, side by side under the syringe.
 *
 * Mass, volume and — on an insulin barrel — insulin units are the same dose in the three vocabularies
 * the bench uses: the protocol is written in one, the syringe is graduated in another, and the vial
 * label is in the third. Reading across the row is the check that the mix is right.
 *
 * The chips carry no tap of their own: they restate what §4.6.2 already drew, and the unit the dose is
 * *stated* in is §4.6.4's Display tile.
 */
@Suppress("FunctionName")
@Composable
private fun EquivalenceChips(
    equivalence: DoseEquivalence,
    doseUnit: UnitCode,
    showUnits: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
    ) {
        EquivalenceChip(
            value = equivalence.mass,
            unit = unitLabel(doseUnit),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        EquivalenceChip(
            value = equivalence.volume,
            unit = unitLabel(UnitCode.ML),
            color = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (showUnits) {
            EquivalenceChip(
                value = equivalence.units,
                unit = stringResource(R.string.reconstitution_units),
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One §4.6.3 chip: the figure over its unit, both centred. */
@Suppress("FunctionName")
@Composable
private fun EquivalenceChip(
    value: String,
    unit: String,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        // The pair is one statement, so it is read as one — "0.25 mg", not "0.25" then "mg".
        modifier = modifier.clearAndSetSemantics { contentDescription = "$value $unit" },
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(vertical = CHIP_PADDING_V, horizontal = TILE_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = unit, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/**
 * §4.6's progressive disclosure: one row that unfolds Mix and the dose ladder.
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
 * Two rows of two, which is the arrangement §4.6.4 draws, until the grid is given [MIX_ROW_MIN_WIDTH]
 * — then it becomes §6.4.2's Expanded table and all four sit on one line.
 *
 * [width] is the room the grid was given, and the tiles reflow on it rather than on the breakpoint —
 * see [MixTile]. That is what makes the single row the *column's* decision rather than the window's:
 * §6.4.2's centre column is only as wide as the two side columns leave it, and four tiles in a narrow
 * one would be four fields too narrow to read their own contents. It is passed in rather than measured
 * here because the rows size themselves off their tallest tile (`IntrinsicSize.Min`), and a
 * `BoxWithConstraints` inside one of those cannot be measured at all.
 */
@Suppress("FunctionName")
@Composable
internal fun MixSection(
    state: ReconstitutionState,
    width: Dp,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSingleRow = width >= MIX_ROW_MIN_WIDTH
    val perRow = if (isSingleRow) SINGLE_ROW_TILES else GRID_ROW_TILES
    val isIconInline = (width - TILE_GAP * (perRow - 1)) / perRow < ICON_INLINE_MIN_WIDTH
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        SectionHeader(text = stringResource(R.string.reconstitution_mix))
        if (isSingleRow) {
            TileRow {
                ContainerTile(state, isIconInline, onAction, Modifier.weight(1f))
                DiluentTile(state, isIconInline, onAction, Modifier.weight(1f))
                DesiredDoseTile(state, isIconInline, onAction, Modifier.weight(1f))
                DisplayTile(state, isIconInline, onAction, Modifier.weight(1f))
            }
        } else {
            TileRow {
                ContainerTile(state, isIconInline, onAction, Modifier.weight(1f))
                DiluentTile(state, isIconInline, onAction, Modifier.weight(1f))
            }
            TileRow {
                DesiredDoseTile(state, isIconInline, onAction, Modifier.weight(1f))
                DisplayTile(state, isIconInline, onAction, Modifier.weight(1f))
            }
        }
    }
}

/** One line of §4.6.4 tiles, every tile as tall as the tallest — a label that wraps lifts the row. */
@Suppress("FunctionName")
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        content = content,
    )
}

/**
 * §4.6.5's ladder: the five default rungs, scrollable sideways.
 *
 * The selected rung breaks shape rather than only colour — `primary` at a `16dp` corner against
 * outlined pills — because on a row of same-sized capsules the fill alone is easy to lose, and the
 * shape survives a colour-blind reading of it.
 *
 * A tap types the rung's figure into §4.6.4's Desired dose, which is what previews it: the syringe
 * fill springs to the new dose on §4.6.8's spec, and the ladder recomputes around the tapped value so
 * the next doubling is one rung further up.
 */
@Suppress("FunctionName")
@Composable
internal fun DoseLadderSection(
    state: ReconstitutionState,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        SectionHeader(text = stringResource(R.string.reconstitution_dose_ladder))
        Row(
            // The rungs keep their own width and run off the end — five of them do not fit a Compact
            // phone, and squeezing them to would cost the figures their legibility (§4.6.5).
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            val unit = unitLabel(state.doseUnit)
            state.ladder.forEach { rung ->
                DoseRungPill(
                    rung = rung,
                    doseUnit = unit,
                    display = state.display,
                    onClick = { onAction(ReconstitutionAction.OnDesiredDoseChange(rung.dose)) },
                )
            }
        }
    }
}

/**
 * One rung (§4.6.5): the dose over what it draws to.
 *
 * The second line falls back to the dose's own unit when the mix has no concentration yet — the
 * ladder still picks doses before there is a diluent to convert them through.
 */
@Suppress("FunctionName")
@Composable
private fun DoseRungPill(
    rung: DoseRung,
    doseUnit: String,
    display: DoseDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { selected = rung.isSelected },
        shape = if (rung.isSelected) MaterialTheme.shapes.large else StaxShapes.Pill,
        color = if (rung.isSelected) colors.primary else colors.surface,
        contentColor = if (rung.isSelected) colors.onPrimary else colors.onSurface,
        border = if (rung.isSelected) null else BorderStroke(RUNG_BORDER, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = RUNG_PADDING_H, vertical = RUNG_PADDING_V),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = rung.dose, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = rung.equivalent?.let {
                    stringResource(R.string.reconstitution_rung_equivalent, it, rungUnitLabel(display))
                } ?: doseUnit,
                style = MaterialTheme.typography.bodySmall,
                color = if (rung.isSelected) colors.onPrimary else colors.onSurfaceVariant,
                maxLines = 1,
            )
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
internal fun drawToUnitLabel(display: DoseDisplay): String = stringResource(
    when (display) {
        DoseDisplay.MILLILITRES -> R.string.reconstitution_unit_ml
        DoseDisplay.INSULIN_UNITS -> R.string.reconstitution_units
    },
)

/** §4.6.5's rung: the same unit as "Draw to", shortened to what fits a pill — "10 u", "0.10 mL". */
@Composable
private fun rungUnitLabel(display: DoseDisplay): String = stringResource(
    when (display) {
        DoseDisplay.MILLILITRES -> R.string.reconstitution_unit_ml
        DoseDisplay.INSULIN_UNITS -> R.string.reconstitution_units_short
    },
)

/** Enough of a field to aim at while it is empty. */
private val MinFieldWidth = 24.dp

/** Below this a §4.6.4 tile puts its icon on the label's line — see `MixTile`. */
private val ICON_INLINE_MIN_WIDTH = 132.dp

/**
 * The width at which §4.6.4's grid unfolds into §6.4.2's one-line table: four tiles of about `128dp`
 * once the gaps are out, which is the narrowest [MixTile] renders a typed "0.25" whole at.
 */
private val MIX_ROW_MIN_WIDTH = 480.dp

private const val SINGLE_ROW_TILES = 4
private const val GRID_ROW_TILES = 2

internal val SCREEN_PADDING = 16.dp
internal val SECTION_GAP = 16.dp
private val HERO_PADDING = 20.dp
private val HERO_GAP = 12.dp
private val TILE_PADDING = 12.dp
private val TILE_GAP = 8.dp
private val ROW_PADDING = 14.dp
private val CHIP_PADDING_V = 12.dp
private val RUNG_PADDING_H = 14.dp
private val RUNG_PADDING_V = 8.dp
private val RUNG_BORDER = 1.dp
private val VALUE_UNIT_GAP = 4.dp
private val UNIT_BASELINE_NUDGE = 6.dp
private val UNIT_BUTTON_PADDING = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
