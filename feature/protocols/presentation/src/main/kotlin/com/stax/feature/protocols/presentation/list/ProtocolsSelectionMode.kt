package com.stax.feature.protocols.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.feature.protocols.presentation.R

/**
 * Contextual app bar of multi-select mode (§4.7.4): leading `close` leaves the mode, the title is
 * the live selection count, and the trailing `more_vert` carries Select all / Invert.
 *
 * It replaces both the ordinary app bar and the filter chip row — changing tabs mid-selection would
 * swap out the very cards the dock is about to act on, and would let a batch run against protocols
 * no longer on screen.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
internal fun ProtocolsSelectionTopBar(
    selectedCount: Int,
    isMenuOpen: Boolean,
    onDismiss: () -> Unit,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.protocols_selection_count, selectedCount)) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = StaxIcons.Close,
                    contentDescription = stringResource(R.string.protocols_selection_close),
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        painter = StaxIcons.MoreVert,
                        contentDescription = stringResource(R.string.protocols_selection_menu),
                    )
                }
                DropdownMenu(expanded = isMenuOpen, onDismissRequest = onMenuDismiss) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.protocols_selection_select_all)) },
                        onClick = onSelectAll,
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.protocols_selection_invert)) },
                        onClick = onInvert,
                    )
                }
            }
        },
    )
}

/**
 * Bottom dock of multi-select mode (§4.7.4): sticky `surface-container-low` carrying the five batch
 * actions — Pause / Resume / Complete / Duplicate as `secondary-container` tonals, Archive as
 * `error-container`. It takes the place of the bottom nav, which `:app` hides while the mode is on.
 *
 * Three buttons to a line, so the two destructive-adjacent ones (Duplicate, Archive) sit together on
 * the second — and a pane too narrow for three simply wraps further rather than clipping them.
 *
 * Each button is disabled when the selection has nothing it applies to (§4.7.4); the ones still
 * enabled act on the compatible part alone.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
internal fun ProtocolsSelectionDock(
    state: ProtocolsListState,
    onAction: (ProtocolsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        FlowRow(
            modifier = Modifier.padding(DOCK_PADDING),
            horizontalArrangement = Arrangement.spacedBy(DOCK_GAP),
            verticalArrangement = Arrangement.spacedBy(DOCK_GAP),
            maxItemsInEachRow = DOCK_ITEMS_PER_ROW,
        ) {
            DockButton(
                label = stringResource(R.string.protocols_selection_pause),
                icon = StaxIcons.Pause,
                enabled = state.canPause,
                onClick = { onAction(ProtocolsListAction.Selection.Batch.OnPause) },
            )
            DockButton(
                label = stringResource(R.string.protocols_selection_resume),
                icon = StaxIcons.PlayArrow,
                enabled = state.canResume,
                onClick = { onAction(ProtocolsListAction.Selection.Batch.OnResume) },
            )
            DockButton(
                label = stringResource(R.string.protocols_selection_complete),
                icon = StaxIcons.DoneAll,
                enabled = state.canComplete,
                onClick = { onAction(ProtocolsListAction.Selection.Batch.OnComplete) },
            )
            DockButton(
                label = stringResource(R.string.protocols_selection_duplicate),
                icon = StaxIcons.AddCircle,
                enabled = state.canDuplicate,
                onClick = { onAction(ProtocolsListAction.Selection.Batch.OnDuplicate) },
            )
            DockButton(
                label = stringResource(R.string.protocols_selection_archive),
                icon = StaxIcons.Delete,
                enabled = state.canArchive,
                isDestructive = true,
                onClick = { onAction(ProtocolsListAction.Selection.OnArchiveClick) },
            )
        }
    }
}

/**
 * Archive confirmation (§4.7.4). Archiving is a soft delete, so the dialog says what survives it.
 */
@Suppress("FunctionName")
@Composable
internal fun ArchiveProtocolsDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.protocols_selection_archive))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.protocols_archive_cancel))
            }
        },
        title = {
            Text(text = pluralStringResource(R.plurals.protocols_archive_title, selectedCount, selectedCount))
        },
        text = { Text(text = stringResource(R.string.protocols_archive_supporting)) },
    )
}

/**
 * One dock button: leading icon, one line of label. The label ellipsizes rather than wrapping — five
 * buttons share the width of a `360dp` list pane (§6.4.2), and a two-line button makes the dock jump
 * in height as the selection changes what is enabled.
 */
@Suppress("FunctionName", "LongParameterList")
@Composable
private fun FlowRowScope.DockButton(
    label: String,
    icon: Painter,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Icon(painter = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (isDestructive) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            contentPadding = DOCK_BUTTON_PADDING,
        ) { content() }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            contentPadding = DOCK_BUTTON_PADDING,
        ) { content() }
    }
}

/** §4.7.4's dock: Pause / Resume / Complete on the first line, Duplicate / Archive on the second. */
private const val DOCK_ITEMS_PER_ROW = 3

private val DOCK_PADDING = 16.dp
private val DOCK_GAP = 8.dp

/** Tighter than the Material default: three labelled buttons have to fit one `360dp` line. */
private val DOCK_BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
