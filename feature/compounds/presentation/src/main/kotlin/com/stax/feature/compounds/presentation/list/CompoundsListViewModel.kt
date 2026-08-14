package com.stax.feature.compounds.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.CompoundDosesLeft
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InventoryRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
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
    compoundRepository: CompoundRepository,
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

            is CompoundsListAction.OnSearchQueryChange ->
                updateFilters { it.copy(searchQuery = action.query) }

            is CompoundsListAction.OnCompoundClick -> viewModelScope.launch {
                _events.send(CompoundsListEvent.NavigateToCompoundDetail(action.compoundId))
            }

            CompoundsListAction.OnAddCompoundClick -> viewModelScope.launch {
                _events.send(CompoundsListEvent.NavigateToCreateCompound)
            }
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
