package com.stax.core.design.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The app's one **picker bottom sheet** (§4.0.2): a modal sheet listing things to choose from, where
 * tapping a row picks it and closes the sheet.
 *
 * Used for the Compound picker and the Body region picker (§4.9.3), and — as §4.0.2 lists them — the
 * Route picker and the inventory-adjust picker (§4.10.3, §4.13). The full-screen Site picker
 * (§4.12.7) is a flow of its own and deliberately not this.
 *
 * Adaptive behaviour comes from [StaxAdaptiveSheet]: full-width at Compact, clamped at Medium, an
 * end-edge side sheet at Expanded, which is why the width passed here is §4.0.2's narrower `360dp`.
 * [content] is a `LazyListScope` because the compound list is unbounded (§2.3.1); it is laid out with
 * `weight(1f, fill = false)` so a short list keeps the sheet short and a long one scrolls inside it
 * rather than pushing the header off the top.
 *
 * The search field appears only when [onQueryChange] is non-null, which §4.0.2 makes the caller's
 * decision ("only when item count > 5"). It is a plain field rather than an M3 `SearchBar`: a
 * `SearchBar` expands to own the window, and inside a modal sheet that is two surfaces competing for
 * the same space.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
fun StaxPickerSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    onQueryChange: ((String) -> Unit)? = null,
    searchPlaceholder: String = "",
    closeContentDescription: String = "",
    clearContentDescription: String = "",
    content: LazyListScope.() -> Unit,
) {
    StaxAdaptiveSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sideSheetWidth = PICKER_SIDE_SHEET_WIDTH,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SHEET_PADDING, end = SHEET_PADDING / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onDismissRequest) {
                Icon(painter = StaxIcons.Close, contentDescription = closeContentDescription)
            }
        }

        onQueryChange?.let { onChange ->
            TextField(
                value = query,
                onValueChange = onChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SHEET_PADDING, vertical = ROW_GAP),
                placeholder = { Text(text = searchPlaceholder) },
                leadingIcon = { Icon(painter = StaxIcons.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onChange("") }) {
                            Icon(painter = StaxIcons.Close, contentDescription = clearContentDescription)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = StaxShapes.Pill,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = SHEET_PADDING),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            contentPadding = PaddingValues(bottom = SHEET_PADDING),
            content = content,
        )
    }
}

/**
 * One row of a [StaxPickerSheet] (§4.0.2): leading avatar/icon, name over its supporting meta, and a
 * `chevron_right` that says the tap goes somewhere. Tapping picks it — the caller closes the sheet.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
fun StaxPickerRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    supporting: String? = null,
    isSelected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SHEET_PADDING, vertical = ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(SHEET_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        modifier = Modifier.padding(AVATAR_PADDING).size(AVATAR_ICON_SIZE),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                supporting?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painter = StaxIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * §4.0.2's empty state: nothing to pick, and a way to go and make something pickable. [onCtaClick]
 * is null when there is nowhere to send the user — a body region list is never empty, a compound
 * list is.
 */
@Suppress("FunctionName")
@Composable
fun ColumnScope.StaxPickerEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SHEET_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        Icon(
            painter = StaxIcons.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (ctaLabel != null && onCtaClick != null) {
            TextButton(onClick = onCtaClick) { Text(text = ctaLabel) }
        }
    }
}

/** §4.0.2 picker sheets are narrower than the default side sheet — they hold a list, not a form. */
private val PICKER_SIDE_SHEET_WIDTH = 360.dp
private val SHEET_PADDING = 16.dp
private val ROW_GAP = 8.dp
private val ROW_PADDING = 12.dp
private val AVATAR_PADDING = 8.dp
private val AVATAR_ICON_SIZE = 24.dp
