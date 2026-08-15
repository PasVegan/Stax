package com.stax.feature.compounds.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.CompoundDosesLeft
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.DataError
import com.stax.core.domain.Result
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InventoryRepository
import com.stax.core.presentation.toUiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * MVI ViewModel for the Compounds list (§4.2, §10.1).
 *
 * Two sources feed one row model: [CompoundRepository] owns the compounds themselves, while the
 * "doses left" figure behind the Low stock chip is an aggregation over active protocols that only
 * [InventoryRepository] can compute (§4.3.2). They are combined once, mapped to
 * [CompoundListItemUi], and kept whole in [allItems]; [CompoundsListState.items] is the filtered
 * view of that list, recomputed whenever either the data or a filter changes.
 *
 * [today] is a parameter so the Expiring soon window is testable without freezing the system clock;
 * production resolves the default.
 */
class CompoundsListViewModel(
    private val compoundRepository: CompoundRepository,
    inventoryRepository: InventoryRepository,
    private val today: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) : ViewModel() {

    private val _state = MutableStateFlow(CompoundsListState())
    val state = _state.asStateFlow()

    private val _events = Channel<CompoundsListEvent>()
    val events = _events.receiveAsFlow()

    /** Every active compound, unfiltered. The screen never sees it — only what survives the chips. */
    private var allItems: List<CompoundListItemUi> = emptyList()

    init {
        combine(
            compoundRepository.observeAll(),
            inventoryRepository.observeDosesLeftPerCompound(),
        ) { compounds, dosesLeft -> compounds.toListItems(dosesLeft) }
            .onEach { items ->
                allItems = items
                _state.update { it.copy(items = it.resultsFrom(items), isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: CompoundsListAction) {
        when (action) {
            is CompoundsListAction.OnStatusFilterClick ->
                updateFilters { it.copy(statusFilter = action.filter) }

            is CompoundsListAction.OnCategoryToggle ->
                updateFilters { it.copy(selectedCategories = it.selectedCategories.toggle(action.category)) }

            is CompoundsListAction.OnFormToggle ->
                updateFilters { it.copy(selectedForms = it.selectedForms.toggle(action.form)) }

            is CompoundsListAction.OnFilterMenuOpen ->
                _state.update { it.copy(openFilterMenu = action.menu) }

            CompoundsListAction.OnFilterMenuDismiss ->
                _state.update { it.copy(openFilterMenu = null) }

            CompoundsListAction.OnSearchClick ->
                _state.update { it.copy(isSearchOpen = true, openFilterMenu = null) }

            // Leaving the overlay drops the query with it: the list underneath is filtered by the
            // chips alone, so a query left behind would keep narrowing a list with nothing on screen
            // to explain why (§4.0.1).
            CompoundsListAction.OnSearchDismiss ->
                updateFilters { it.copy(isSearchOpen = false, searchQuery = "") }

            is CompoundsListAction.OnSearchQueryChange ->
                updateFilters { it.copy(searchQuery = action.query) }

            // While multi-select is on the row tap is the toggle (§4.2.4) — navigating away instead
            // would abandon a selection the user is still building.
            is CompoundsListAction.OnCompoundClick -> if (_state.value.isSelectionMode) {
                toggleSelection(action.compoundId)
            } else {
                viewModelScope.launch {
                    _events.send(CompoundsListEvent.NavigateToCompoundDetail(action.compoundId))
                }
            }

            CompoundsListAction.OnAddCompoundClick -> viewModelScope.launch {
                _events.send(CompoundsListEvent.NavigateToCreateCompound)
            }

            is CompoundsListAction.Selection -> onSelectionAction(action)
        }
    }

    /** Multi-select mode (§4.2.4). */
    private fun onSelectionAction(action: CompoundsListAction.Selection) {
        when (action) {
            is CompoundsListAction.Selection.OnLongPress -> toggleSelection(action.compoundId)

            CompoundsListAction.Selection.OnDismiss -> exitSelection()

            CompoundsListAction.Selection.OnDuplicate -> runOnSelection(compoundRepository::duplicate)

            CompoundsListAction.Selection.OnArchiveClick ->
                _state.update { it.copy(isArchiveDialogOpen = true) }

            CompoundsListAction.Selection.OnArchiveDismiss ->
                _state.update { it.copy(isArchiveDialogOpen = false) }

            CompoundsListAction.Selection.OnArchiveConfirm -> runOnSelection(compoundRepository::archive)
        }
    }

    private fun toggleSelection(compoundId: Long) {
        _state.update { it.copy(selectedIds = it.selectedIds.toggle(compoundId)) }
    }

    /** Clears the selection, which is what multi-select mode is, and any dialog it was showing. */
    private fun exitSelection() {
        _state.update { it.copy(selectedIds = persistentSetOf(), isArchiveDialogOpen = false) }
    }

    /**
     * Applies [operation] to every selected compound, then leaves multi-select whatever happened
     * (§4.2.4). The selection is snapshotted first because the repository emits as it goes and the
     * whole batch has to run against what the user actually ticked.
     *
     * Every compound is attempted even after one fails — a batch that stopped half-way would leave
     * the user guessing which rows it got to — but only the first failure is reported, because one
     * message per compound in a batch of ten is noise.
     */
    private fun runOnSelection(operation: suspend (Long) -> Result<*, DataError.Local>) {
        val selected = _state.value.selectedIds
        exitSelection()
        viewModelScope.launch {
            selected
                .mapNotNull { id ->
                    when (val result = operation(id)) {
                        is Result.Error -> result.error
                        is Result.Success -> null
                    }
                }
                .firstOrNull()
                ?.let { _events.send(CompoundsListEvent.ShowError(it.toUiText())) }
        }
    }

    /** Applies a filter change and re-derives the result list from the unfiltered source in one step. */
    private fun updateFilters(transform: (CompoundsListState) -> CompoundsListState) {
        _state.update { current ->
            val filtered = transform(current)
            filtered.copy(items = filtered.resultsFrom(allItems))
        }
    }

    /**
     * The four filter groups AND together (§4.2.2). An empty Category or Form selection is "no
     * constraint" — the chip is only narrowing once the user picks something in its menu.
     */
    private fun CompoundsListState.resultsFrom(source: List<CompoundListItemUi>): ImmutableList<CompoundListItemUi> {
        val query = searchQuery.trim()
        return source.filter { item ->
            matchesStatus(item) &&
                (selectedCategories.isEmpty() || item.category in selectedCategories) &&
                (selectedForms.isEmpty() || item.form in selectedForms) &&
                (query.isEmpty() || item.name.contains(query, ignoreCase = true))
        }.toImmutableList()
    }

    private fun CompoundsListState.matchesStatus(item: CompoundListItemUi): Boolean = when (statusFilter) {
        CompoundStatusFilter.ALL -> true
        CompoundStatusFilter.LOW_STOCK -> item.isLowStock
        CompoundStatusFilter.EXPIRING_SOON -> item.isExpiringSoon
    }

    private fun List<CompoundSupply>.toListItems(dosesLeft: List<CompoundDosesLeft>): List<CompoundListItemUi> {
        val dosesById = dosesLeft.associate { it.compoundSupplyId to it.dosesLeft }
        val expiringBefore = today().plus(EXPIRING_SOON_DAYS, DateTimeUnit.DAY)
        return map { compound ->
            val doses = dosesById[compound.id]
            val expiry = compound.effectiveExpiry()
            CompoundListItemUi(
                id = compound.id,
                name = compound.name,
                category = compound.category,
                form = compound.form,
                // No active protocol means no doses figure at all (§4.3.2) — that is "unknown",
                // not "empty", so such a compound is never reported as low stock.
                isLowStock = doses != null && doses < LOW_STOCK_DOSES,
                isExpiringSoon = expiry != null && expiry < expiringBefore,
                dosesLeft = doses,
                remaining = compound.currentOpened?.remainingAmount?.toString(),
                sealedContainers = compound.numberOfContainers,
                effectiveExpiry = expiry,
            )
        }
    }

    /**
     * The date the row shows and the Expiring soon chip tests: the earlier of the batch expiry and
     * the opened container's effective expiry (`userDefinedExpiryDate ?? predictedExpiryDate`, §3.1),
     * matching the "whichever has shorter date" rule of the detail stat tile (§4.3.2). Null when the
     * compound carries neither, in which case expiry is hidden and the chip never matches it.
     */
    private fun CompoundSupply.effectiveExpiry(): LocalDate? {
        val opened = currentOpened?.let { it.userDefinedExpiryDate ?: it.predictedExpiryDate }
        return listOfNotNull(batchExpiryDate, opened).minOrNull()
    }

    private fun <T> ImmutableSet<T>.toggle(value: T): ImmutableSet<T> =
        toPersistentSet().let { if (value in it) it.remove(value) else it.add(value) }

    private companion object {
        /** §4.2.2 Low stock: fewer than seven doses left. Mirrors `RoomInventoryRepository`'s threshold. */
        const val LOW_STOCK_DOSES = 7

        /** §4.2.2 Expiring soon: an effective expiry inside the next four weeks. */
        const val EXPIRING_SOON_DAYS = 28
    }
}
