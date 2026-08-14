package com.stax.feature.compounds.presentation.list

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.AdaptiveFab
import com.stax.core.design.system.NoWindowInsets
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.presentation.ObserveAsEvents
import com.stax.core.presentation.asString
import com.stax.feature.compounds.presentation.R
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel

/**
 * Root of the Compounds list (§10.1): holds the [CompoundsListViewModel] and turns its navigation
 * events into the callbacks `:app` wired into the entry (§10.3).
 */
@Suppress("FunctionName")
@Composable
fun CompoundsListRoot(
    onCompoundClick: (Long) -> Unit,
    onCreateCompound: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompoundsListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Multi-select replaces the bottom nav with its own dock (§4.2.4), and the chrome belongs to
    // `:app` — so the screen reports the mode and `:app` hides the bar. Reported on dispose too:
    // navigating out of the list while a selection stands must not leave the bar hidden behind it.
    DisposableEffect(state.isSelectionMode, onSelectionModeChange) {
        onSelectionModeChange(state.isSelectionMode)
        onDispose { onSelectionModeChange(false) }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events, key1 = onCompoundClick, key2 = onCreateCompound) { event ->
        when (event) {
            is CompoundsListEvent.NavigateToCompoundDetail -> onCompoundClick(event.compoundId)
            CompoundsListEvent.NavigateToCreateCompound -> onCreateCompound()
            is CompoundsListEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }
        }
    }

    CompoundsListScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Compounds list (§4.2): app bar with the leading `search` icon, the filter chip row, the compound
 * rows, and the extended "Add" FAB.
 *
 * This is the **list pane** of the Compounds list-detail Scene (§6.4.2), so its width is the pane's
 * — `360dp` at Medium, `400dp` at Expanded — and the layout is the same at every breakpoint: one row
 * per line, and the extended FAB floating at the pane's bottom-end ([AdaptiveFab], §6.4.6). The search
 * overlay (§4.0.1) replaces the whole pane while it is open.
 *
 * Multi-select (§4.2.4) is a mode of this same pane: the contextual bar takes over the app bar and
 * the chip row, the dock takes over the bottom, and the FAB steps aside — a screen with two primary
 * actions on it has none.
 */
@Suppress("FunctionName")
@Composable
fun CompoundsListScreen(
    state: CompoundsListState,
    onAction: (CompoundsListAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .paneInsets(),
    ) {
        if (state.isSearchOpen) {
            CompoundsSearchOverlay(state = state, onAction = onAction)
        } else {
            // Back leaves multi-select before it leaves the screen (§4.2.4) — the selection is the
            // nearest thing the gesture can dismiss.
            BackHandler(enabled = state.isSelectionMode) {
                onAction(CompoundsListAction.Selection.OnDismiss)
            }
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.isSelectionMode) {
                    CompoundsSelectionTopBar(
                        selectedCount = state.selectedIds.size,
                        onDismiss = { onAction(CompoundsListAction.Selection.OnDismiss) },
                    )
                } else {
                    CompoundsTopBar(onSearchClick = { onAction(CompoundsListAction.OnSearchClick) })
                    CompoundsFilterRow(state = state, onAction = onAction)
                }
                CompoundsList(
                    items = state.items,
                    selectedIds = state.selectedIds,
                    isSelectionMode = state.isSelectionMode,
                    onCompoundClick = { onAction(CompoundsListAction.OnCompoundClick(it)) },
                    onCompoundLongPress = { onAction(CompoundsListAction.Selection.OnLongPress(it)) },
                    modifier = Modifier.weight(1f),
                )
                if (state.isSelectionMode) {
                    CompoundsSelectionDock(
                        onDuplicate = { onAction(CompoundsListAction.Selection.OnDuplicate) },
                        onArchive = { onAction(CompoundsListAction.Selection.OnArchiveClick) },
                    )
                }
            }
            if (!state.isSelectionMode) {
                AdaptiveFab(
                    onClick = { onAction(CompoundsListAction.OnAddCompoundClick) },
                    label = { Text(text = stringResource(R.string.compounds_add)) },
                ) {
                    Icon(painter = StaxIcons.Add, contentDescription = null)
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.isArchiveDialogOpen) {
        ArchiveCompoundsDialog(
            selectedCount = state.selectedIds.size,
            onConfirm = { onAction(CompoundsListAction.Selection.OnArchiveConfirm) },
            onDismiss = { onAction(CompoundsListAction.Selection.OnArchiveDismiss) },
        )
    }
}

/** §4.2.1: leading `search` icon opening the overlay, title "Compounds". */
@Suppress("FunctionName")
@Composable
private fun CompoundsTopBar(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.compounds_title)) },
        modifier = modifier,
        // The pane already claimed the status bar via paneInsets, so the bar's own default insets
        // would stack a second status bar's worth of padding on top of it (§2.3.6).
        windowInsets = NoWindowInsets,
        navigationIcon = {
            IconButton(onClick = onSearchClick) {
                Icon(
                    painter = StaxIcons.Search,
                    contentDescription = stringResource(R.string.compounds_search),
                )
            }
        },
    )
}

/**
 * §4.2.2: All / Low stock / Expiring soon are one mutually exclusive group; Category and Form each
 * open a multi-select menu and label themselves "Category · N" once something is picked. The row
 * scrolls horizontally — five chips never fit a Compact width.
 */
@Suppress("FunctionName")
@Composable
private fun CompoundsFilterRow(
    state: CompoundsListState,
    onAction: (CompoundsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SCREEN_PADDING),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        items(CompoundStatusFilter.entries) { filter ->
            val selected = state.statusFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onAction(CompoundsListAction.OnStatusFilterClick(filter)) },
                label = { Text(text = stringResource(filter.labelRes())) },
                leadingIcon = if (selected) {
                    { Icon(painter = StaxIcons.Done, contentDescription = null) }
                } else {
                    null
                },
            )
        }
        item {
            FilterMenuChip(
                menu = CompoundFilterMenu.CATEGORY,
                labelRes = R.string.compounds_filter_category,
                selectedCount = state.selectedCategories.size,
                isOpen = state.openFilterMenu == CompoundFilterMenu.CATEGORY,
                onAction = onAction,
            ) {
                CompoundCategory.entries.forEach { category ->
                    FilterMenuItem(
                        label = categoryLabel(category),
                        selected = category in state.selectedCategories,
                        onClick = { onAction(CompoundsListAction.OnCategoryToggle(category)) },
                    )
                }
            }
        }
        item {
            FilterMenuChip(
                menu = CompoundFilterMenu.FORM,
                labelRes = R.string.compounds_filter_form,
                selectedCount = state.selectedForms.size,
                isOpen = state.openFilterMenu == CompoundFilterMenu.FORM,
                onAction = onAction,
            ) {
                CompoundForm.entries.forEach { form ->
                    FilterMenuItem(
                        label = formLabel(form),
                        selected = form in state.selectedForms,
                        onClick = { onAction(CompoundsListAction.OnFormToggle(form)) },
                    )
                }
            }
        }
    }
}

/**
 * A chip that owns a multi-select menu (§4.2.2). The menu is anchored to the chip by nesting it in
 * the chip's own `Box`, and stays open while items are toggled — picking two categories is one trip.
 */
@Suppress("FunctionName")
@Composable
private fun FilterMenuChip(
    menu: CompoundFilterMenu,
    @StringRes labelRes: Int,
    selectedCount: Int,
    isOpen: Boolean,
    onAction: (CompoundsListAction) -> Unit,
    modifier: Modifier = Modifier,
    items: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        val label = stringResource(labelRes)
        FilterChip(
            selected = selectedCount > 0,
            onClick = { onAction(CompoundsListAction.OnFilterMenuOpen(menu)) },
            label = {
                Text(
                    text = if (selectedCount > 0) {
                        stringResource(R.string.compounds_filter_selected_count, label, selectedCount)
                    } else {
                        label
                    },
                )
            },
            trailingIcon = { Icon(painter = StaxIcons.ExpandMore, contentDescription = null) },
        )
        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = { onAction(CompoundsListAction.OnFilterMenuDismiss) },
            content = { items() },
        )
    }
}

/** One menu row: `check_circle` when selected, `add_circle` when not (§4.2.2). */
@Suppress("FunctionName")
@Composable
private fun FilterMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = label) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = if (selected) StaxIcons.CheckCircle else StaxIcons.AddCircle,
                contentDescription = null,
            )
        },
    )
}

/** The rows themselves (§4.2.3), with their multi-select checkboxes when the mode is on (§4.2.4). */
@Suppress("FunctionName")
@Composable
private fun CompoundsList(
    items: List<CompoundListItemUi>,
    selectedIds: ImmutableSet<Long>,
    isSelectionMode: Boolean,
    onCompoundClick: (Long) -> Unit,
    onCompoundLongPress: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = SCREEN_PADDING,
            top = SCREEN_PADDING,
            end = SCREEN_PADDING,
            // Extra room at the bottom so the last row can be scrolled clear of the floating FAB.
            // The dock that replaces it in multi-select is laid out, not floating, so it needs none.
            bottom = if (isSelectionMode) SCREEN_PADDING else LIST_BOTTOM_PADDING,
        ),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        items(items = items, key = { it.id }) { item ->
            CompoundRow(
                item = item,
                name = AnnotatedString(item.name),
                onClick = { onCompoundClick(item.id) },
                isSelectionMode = isSelectionMode,
                isSelected = item.id in selectedIds,
                onLongClick = { onCompoundLongPress(item.id) },
            )
        }
    }
}

private fun CompoundStatusFilter.labelRes(): Int = when (this) {
    CompoundStatusFilter.ALL -> R.string.compounds_filter_all
    CompoundStatusFilter.LOW_STOCK -> R.string.compounds_filter_low_stock
    CompoundStatusFilter.EXPIRING_SOON -> R.string.compounds_filter_expiring_soon
}

private val SCREEN_PADDING = 16.dp
private val CHIP_GAP = 8.dp
private val ROW_GAP = 8.dp

/** Screen padding + the extended FAB's height + its `16dp` inset (§6.4.6). */
private val LIST_BOTTOM_PADDING = 96.dp

@Preview(name = "Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium list pane", showBackground = true, widthDp = 360, heightDp = 841)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun CompoundsListScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            CompoundsListScreen(state = previewState(), onAction = {})
        }
    }
}

@Preview(name = "Multi-select · Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Multi-select · Medium list pane", showBackground = true, widthDp = 360, heightDp = 841)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun CompoundsListScreenMultiSelectPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            CompoundsListScreen(
                state = previewState().copy(selectedIds = persistentSetOf(1L, 2L)),
                onAction = {},
            )
        }
    }
}

private fun previewState() = CompoundsListState(
    items = persistentListOf(
        CompoundListItemUi(
            id = 1,
            name = "Semaglutide",
            category = CompoundCategory.PEPTIDE,
            form = CompoundForm.INJECTABLE,
            isLowStock = false,
            isExpiringSoon = false,
            dosesLeft = 12,
            remaining = "4.5 mg",
            sealedContainers = 1,
            effectiveExpiry = LocalDate.parse("2026-07-14"),
        ),
        CompoundListItemUi(
            id = 2,
            name = "BPC-157",
            category = CompoundCategory.PEPTIDE,
            form = CompoundForm.INJECTABLE,
            isLowStock = true,
            isExpiringSoon = true,
            dosesLeft = 3,
            remaining = "1.2 mg",
            sealedContainers = 0,
            effectiveExpiry = LocalDate.parse("2026-05-31"),
        ),
        CompoundListItemUi(
            id = 3,
            name = "Vitamin D3",
            category = CompoundCategory.SUPPLEMENT,
            form = CompoundForm.CAPSULE,
            isLowStock = false,
            isExpiringSoon = false,
            dosesLeft = null,
            remaining = null,
            sealedContainers = 2,
            effectiveExpiry = LocalDate.parse("2027-03-27"),
        ),
        CompoundListItemUi(
            id = 4,
            name = "Testosterone Cyp",
            category = CompoundCategory.HORMONE,
            form = CompoundForm.INJECTABLE,
            isLowStock = false,
            isExpiringSoon = false,
            dosesLeft = 20,
            remaining = "6 mL",
            sealedContainers = 1,
            effectiveExpiry = LocalDate.parse("2026-10-04"),
        ),
    ),
    selectedCategories = persistentSetOf(CompoundCategory.PEPTIDE),
    isLoading = false,
)
