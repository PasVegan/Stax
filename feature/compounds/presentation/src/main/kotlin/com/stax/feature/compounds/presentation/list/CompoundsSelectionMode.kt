package com.stax.feature.compounds.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.NoWindowInsets
import com.stax.core.design.system.StaxIcons
import com.stax.feature.compounds.presentation.R

/**
 * Contextual app bar of multi-select mode (§4.2.4): leading `close` leaves the mode, and the title
 * is the live selection count. It replaces both the ordinary app bar and the filter chip row —
 * changing a filter mid-selection would hide rows the dock is about to act on.
 */
@Suppress("FunctionName")
@Composable
internal fun CompoundsSelectionTopBar(selectedCount: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.compounds_selection_count, selectedCount)) },
        modifier = modifier,
        // The pane already claimed the status bar via paneInsets (§2.3.6).
        windowInsets = NoWindowInsets,
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = StaxIcons.Close,
                    contentDescription = stringResource(R.string.compounds_selection_close),
                )
            }
        },
    )
}

/**
 * Bottom dock of multi-select mode (§4.2.4): sticky `surface-container-low` (§4.3.9) carrying two
 * equal-grow buttons — Duplicate as a `secondary-container` tonal, Archive as `error-container`.
 * It takes the place of the bottom nav, which `:app` hides while the mode is on.
 */
@Suppress("FunctionName")
@Composable
internal fun CompoundsSelectionDock(onDuplicate: () -> Unit, onArchive: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.padding(DOCK_PADDING),
            horizontalArrangement = Arrangement.spacedBy(DOCK_GAP),
        ) {
            FilledTonalButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                ButtonIcon(StaxIcons.AddCircle)
                Text(text = stringResource(R.string.compounds_selection_duplicate))
            }
            Button(
                onClick = onArchive,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                ButtonIcon(StaxIcons.Delete)
                Text(text = stringResource(R.string.compounds_selection_archive))
            }
        }
    }
}

/**
 * Archive confirmation (§4.2.4). Archiving is a soft delete, so the dialog says what survives it —
 * and it is the only guard, because §4.2.4 rules out the undo snackbar afterwards.
 */
@Suppress("FunctionName")
@Composable
internal fun ArchiveCompoundsDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.compounds_selection_archive))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.compounds_archive_cancel))
            }
        },
        title = {
            Text(text = pluralStringResource(R.plurals.compounds_archive_title, selectedCount, selectedCount))
        },
        text = { Text(text = stringResource(R.string.compounds_archive_supporting)) },
    )
}

/** A dock button's leading icon, sized and spaced by the Material button tokens. */
@Suppress("FunctionName")
@Composable
private fun ButtonIcon(painter: Painter) {
    Icon(painter = painter, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
}

private val DOCK_PADDING = 16.dp
private val DOCK_GAP = 12.dp
