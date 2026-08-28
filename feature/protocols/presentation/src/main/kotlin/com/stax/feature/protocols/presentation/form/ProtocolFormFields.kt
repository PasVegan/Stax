package com.stax.feature.protocols.presentation.form

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.feature.protocols.presentation.R

/**
 * The form's shared field shapes (§4.9.3).
 *
 * They deliberately mirror the Create / Edit Compound form's (§4.4.3) rather than being shared with
 * it: features never depend on features (§10.4), and only three of that form's shapes are wanted
 * here — the rest of §4.9.3 is cards, chips and segments that form has nothing like.
 * ponytail: lift these into `:core:design-system` when a third form (§4.10.2) wants them too.
 */

/** §4.4.2's section label, which §4.9.3 reuses: `primary`, with no card wrapping the section it heads. */
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
 * A `surface-container` field the user types into: the label held small above the value, the
 * indicator line removed, and a failed Save drawn as a border around the whole row rather than as a
 * coloured underline.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
internal fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    isOptional: Boolean = false,
    placeholder: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    focusRequester: FocusRequester? = null,
    suffix: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                // On the field itself, not the wrapping Column: only a focusable node can be focused,
                // and focusing is what scrolls the first failing field into view.
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .errorOutline(isError = error != null),
            textStyle = MaterialTheme.typography.titleMedium,
            label = { FieldLabelRow(label = label, isOptional = isOptional) },
            placeholder = placeholder?.let { { Text(text = it) } },
            leadingIcon = icon?.let { { Icon(painter = it, contentDescription = null) } },
            suffix = suffix,
            isError = error != null,
            singleLine = minLines == 1,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = MaterialTheme.shapes.large,
            colors = fieldColors(),
        )
        error?.let { FieldError(message = it) }
    }
}

/**
 * A field the user chooses rather than types — §4.9.3's Site restriction row and the two Duration
 * boxes. Same `surface-container` row as [FormTextField], but it opens a picker instead of the
 * keyboard, so its trailing affordance is an icon rather than a caret.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
internal fun FormPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    trailingIcon: Painter? = null,
    isOptional: Boolean = false,
    supporting: String? = null,
    error: String? = null,
) {
    Column(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .errorOutline(isError = error != null),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = FIELD_INSET, vertical = ROW_VERTICAL_PADDING),
                horizontalArrangement = Arrangement.spacedBy(FIELD_INSET),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                trailingIcon?.let {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        error?.let { FieldError(message = it) }
    }
}

/** "Label" plus the `Optional` badge §4.9.3 marks the non-required fields with. */
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
            // `surface-container-highest`, not `surface-variant`: under a dynamic scheme those two
            // land on the same tone as the field, and a badge that is only a colour disappears.
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = stringResource(R.string.protocol_form_optional),
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

/** A rejected field carries an `error` icon and its reason under the row. */
@Suppress("FunctionName")
@Composable
internal fun FieldError(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = FIELD_INSET, top = LABEL_GAP),
        horizontalArrangement = Arrangement.spacedBy(LABEL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = StaxIcons.Error,
            contentDescription = null,
            modifier = Modifier.size(ICON_SMALL),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The full-row `error` outline, drawn only while the field is failing. */
@Composable
private fun Modifier.errorOutline(isError: Boolean): Modifier = if (isError) {
    border(ERROR_OUTLINE_WIDTH, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large)
} else {
    this
}

/**
 * `surface-container` in every state with no indicator line — the container *is* the field, and the
 * error state is the border [errorOutline] draws.
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

@Suppress("FunctionName")
@Composable
internal fun FieldSpacer(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(FIELD_GAP))
}

internal val SCREEN_PADDING = 16.dp
internal val FIELD_GAP = 8.dp
internal val SECTION_GAP = 16.dp
internal val CARD_PADDING = 16.dp
internal val FIELD_INSET = 16.dp
internal val LABEL_GAP = 4.dp
internal val ICON_SMALL = 18.dp
private val ROW_VERTICAL_PADDING = 12.dp
private val BADGE_PADDING = 6.dp
private val ERROR_OUTLINE_WIDTH = 2.dp
