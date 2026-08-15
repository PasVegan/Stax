package com.stax.feature.compounds.presentation.form

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.feature.compounds.presentation.R

/** §4.4.2: section labels are `primary`, with no card wrapping the section they head. */
@Suppress("FunctionName")
@Composable
internal fun FormSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = FIELD_INSET, top = SECTION_GAP, bottom = FIELD_GAP),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The form's one field shape (§4.4.3): a `surface-container` row with a leading icon, the label held
 * small above the value, and an optional trailing affordance.
 *
 * Built on the **filled** `TextField` rather than the outlined one because the label belongs inside
 * the container, as §4.4b draws it — an outlined field would notch its border around the label
 * instead. The indicator line is removed and the error outline drawn as a border, which is what puts
 * the whole row in `error` on a failed Save rather than only its bottom edge.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
internal fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    // Null for the two Stock counts (§4.4.3), which sit side by side: at half a Compact width a
    // leading icon costs the label the room it needs and "Amount per container" wraps to three lines.
    icon: Painter?,
    modifier: Modifier = Modifier,
    isOptional: Boolean = false,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    focusRequester: FocusRequester? = null,
    suffix: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                // On the field itself, not on the wrapping Column: §4.4.4 asks for the failing field
                // to take focus, and only a focusable node can.
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .errorOutline(isError = error != null),
            textStyle = MaterialTheme.typography.titleMedium,
            label = { FieldLabelRow(label = label, isOptional = isOptional) },
            placeholder = placeholder?.let { { Text(text = it) } },
            leadingIcon = icon?.let { { Icon(painter = it, contentDescription = null) } },
            trailingIcon = trailing,
            suffix = suffix,
            isError = error != null,
            singleLine = minLines == 1,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = MaterialTheme.shapes.large,
            colors = fieldColors(),
        )
        // The error replaces the hint rather than stacking under it: two lines of small text under
        // one field is where the thing the user has to act on gets lost.
        when {
            error != null -> FieldError(message = error)
            supporting != null -> Text(
                text = supporting,
                modifier = Modifier.padding(start = FIELD_INSET, top = LABEL_GAP),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A field the user chooses rather than types (§4.4.3 Category / Form / Container type / Storage
 * location, and the batch expiry date). Same row as [FormTextField] so a column of them reads as one
 * list, but it opens a menu or a picker instead of the keyboard.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
internal fun FormPickerField(
    label: String,
    value: String,
    icon: Painter,
    trailingIcon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOptional: Boolean = false,
    supporting: String? = null,
    menu: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FIELD_INSET, vertical = ROW_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(FIELD_INSET),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                FieldLabelRow(label = label, isOptional = isOptional)
                Text(text = value, style = MaterialTheme.typography.titleMedium)
                supporting?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The menu hangs off the chevron, not off the row: anchored to the row it opened at the
            // far start edge, a whole field's width from the control that summoned it.
            Box {
                Icon(
                    painter = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                menu()
            }
        }
    }
}

/** One option of a [FormPickerField]'s menu, ticked when it is the current value. */
@Suppress("FunctionName")
@Composable
internal fun FormPickerItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
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

/**
 * The unit picker that sits inline after a numeric value (§4.4.3, "+ unit picker"). It is the field's
 * `suffix`, so it stays on the value's own line at every width — which is what §6.4.2 asks the wider
 * layouts for and what the narrow one gets for free.
 *
 * The minimum interactive size is waived for the button on purpose: enforced, it is `48dp` tall,
 * which is taller than the line it is a suffix of — the field grew to fit it and left the value
 * stranded at the top with the unit alone at the bottom. The row itself is well past `48dp` and the
 * whole of it focuses the same field, so the target that matters is not the one being waived.
 */
@Suppress("FunctionName")
@Composable
internal fun UnitSuffix(
    unit: String,
    onClick: () -> Unit,
    isMenuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TextButton(onClick = onClick, contentPadding = UNIT_BUTTON_PADDING) {
                Text(text = unit, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = onMenuDismiss, content = { menu() })
    }
}

/**
 * "Label" plus the `Optional` badge §4.4.3 marks the non-required fields with.
 *
 * The badge is `surface-container-highest` rather than `surface-variant`: the field it sits on is
 * `surface-container`, and under a dynamic scheme those two land on the same tone — the badge was
 * invisible on a Samsung device, which is exactly where a "pill" that is only a colour disappears.
 * The tonal palette guarantees `highest` a step above the container it is on.
 *
 * The badge never wraps and the label yields room before it does: with both free to wrap, a narrow
 * column broke "Optional" across two lines mid-word.
 */
@Suppress("FunctionName")
@Composable
private fun RowScope.FieldLabel(label: String, isOptional: Boolean) {
    Text(
        text = label,
        modifier = Modifier.weight(1f, fill = false),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (isOptional) {
        Surface(
            modifier = Modifier.padding(start = LABEL_GAP),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = stringResource(R.string.compound_form_optional),
                modifier = Modifier.padding(horizontal = BADGE_PADDING, vertical = BADGE_PADDING / 2),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** [FieldLabel] in the `Row` it needs to hand the badge its space. */
@Suppress("FunctionName")
@Composable
private fun FieldLabelRow(label: String, isOptional: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(label = label, isOptional = isOptional)
    }
}

/** §4.4.3: a failed field carries an `error` icon and its reason under the row (§4.4b). */
@Suppress("FunctionName")
@Composable
private fun FieldError(message: String) {
    Row(
        modifier = Modifier.padding(start = FIELD_INSET, top = LABEL_GAP),
        horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = StaxIcons.Error,
            contentDescription = null,
            modifier = Modifier.padding(end = 0.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The full-row `error` outline of §4.4b, drawn only while the field is failing. */
@Composable
private fun Modifier.errorOutline(isError: Boolean): Modifier = if (isError) {
    border(ERROR_OUTLINE_WIDTH, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large)
} else {
    this
}

/**
 * `surface-container` in every state with no indicator line — the container *is* the field, and the
 * error state is the border [errorOutline] draws, not a coloured underline.
 */
@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

internal val SCREEN_PADDING = 16.dp
internal val FIELD_GAP = 8.dp
internal val SECTION_GAP = 16.dp
private val FIELD_INSET = 16.dp
private val ROW_VERTICAL_PADDING = 12.dp
private val LABEL_GAP = 4.dp
private val BADGE_PADDING = 6.dp
private val ERROR_OUTLINE_WIDTH = 2.dp
private val UNIT_BUTTON_PADDING = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
