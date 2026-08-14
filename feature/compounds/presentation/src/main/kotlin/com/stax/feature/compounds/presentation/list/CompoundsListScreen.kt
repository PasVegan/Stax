package com.stax.feature.compounds.presentation.list

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.AdaptiveFab
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.presentation.ObserveAsEvents
import com.stax.feature.compounds.presentation.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
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
    modifier: Modifier = Modifier,
    viewModel: CompoundsListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events, key1 = onCompoundClick, key2 = onCreateCompound) { event ->
        when (event) {
            is CompoundsListEvent.NavigateToCompoundDetail -> onCompoundClick(event.compoundId)
            CompoundsListEvent.NavigateToCreateCompound -> onCreateCompound()
        }
    }

    CompoundsListScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Compounds list (§4.2): app bar with the leading `search` icon, the filter chip row, the compound
 * rows, and the extended "Add" FAB.
 *
 * This is the **list pane** of the Compounds list-detail Scene (§6.4.2), so its width is the pane's
 * — `360dp` at Medium, `400dp` at Expanded — and the rows stay one per line at every breakpoint;
 * what the window width changes here is the FAB, which moves from floating bottom-end to the rail
 * slot ([AdaptiveFab], §6.4.6). The search overlay (§4.0.1) replaces the whole pane while it is open.
 */
@Suppress("FunctionName")
@Composable
fun CompoundsListScreen(
    state: CompoundsListState,
    onAction: (CompoundsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .paneInsets(),
    ) {
        if (state.isSearchOpen) {
            CompoundsSearchOverlay(state = state, onAction = onAction)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CompoundsTopBar(onSearchClick = { onAction(CompoundsListAction.OnSearchClick) })
                CompoundsFilterRow(state = state, onAction = onAction)
                CompoundsList(
                    items = state.items,
                    onCompoundClick = { onAction(CompoundsListAction.OnCompoundClick(it)) },
                )
            }
            AdaptiveFab(
                onClick = { onAction(CompoundsListAction.OnAddCompoundClick) },
                label = { Text(text = stringResource(R.string.compounds_add)) },
            ) {
                // Described rather than decorative: in the rail slot the FAB is collapsed to this
                // icon alone (§6.4.6), and the label that would name it is not composed.
                Icon(
                    painter = StaxIcons.Add,
                    contentDescription = stringResource(R.string.compounds_add),
                )
            }
        }
    }
}

/** §4.2.1: leading `search` icon opening the overlay, title "Compounds". */
@Suppress("FunctionName")
@Composable
private fun CompoundsTopBar(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.compounds_title)) },
        modifier = modifier,
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

/** The rows themselves (§4.2.3). */
@Suppress("FunctionName")
@Composable
private fun CompoundsList(
    items: List<CompoundListItemUi>,
    onCompoundClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        items(items = items, key = { it.id }) { item ->
            CompoundRow(
                item = item,
                name = AnnotatedString(item.name),
                onClick = { onCompoundClick(item.id) },
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
