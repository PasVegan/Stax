package com.stax.feature.compounds.presentation.list

import androidx.compose.runtime.Immutable
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate

/**
 * The left, single-select half of the filter chip row (§4.2.2). All / Low stock / Expiring soon are
 * mutually exclusive; [ALL] is the default and doubles as the reset.
 */
enum class CompoundStatusFilter { ALL, LOW_STOCK, EXPIRING_SOON }

/**
 * The right, multi-select half of the filter chip row (§4.2.2) — the two chips that open a menu.
 * At most one menu is open at a time, which is why the open one is a nullable enum in state rather
 * than a boolean per chip.
 */
enum class CompoundFilterMenu { CATEGORY, FORM }

/**
 * One compound row (§4.2.3).
 *
 * [category] + [form] drive the avatar (category-colored fill + form icon), [isLowStock] overrides
 * it with the error-container / `warning` variant. [remaining] is the opened container's remaining
 * amount pre-rendered by `Quantity.toString()` ("8.5 mg") and is null while nothing is open;
 * [sealedContainers] is `numberOfContainers`, which counts unopened containers only (§3.1).
 *
 * `@Immutable` because [effectiveExpiry] is an external type the Compose compiler cannot infer
 * stability for (§2.3.1); every field is in fact a read-only value.
 */
@Immutable
data class CompoundListItemUi(
    val id: Long,
    val name: String,
    val category: CompoundCategory,
    val form: CompoundForm,
    val isLowStock: Boolean,
    val isExpiringSoon: Boolean,
    val dosesLeft: Int?,
    val remaining: String?,
    val sealedContainers: Int,
    val effectiveExpiry: LocalDate?,
)

/**
 * UI state of the Compounds list (§4.2).
 *
 * [items] is the *result* list — the compounds left after [statusFilter], [selectedCategories],
 * [selectedForms] and [searchQuery] have all been applied. The three filter groups AND together;
 * within Category and Form an empty selection means "no constraint", not "match nothing" (§4.2.2).
 * The unfiltered source stays inside the ViewModel so the screen only ever sees what it renders.
 *
 * [openFilterMenu] and [isSearchOpen] are here rather than in a `remember` because which chip menu is
 * open and whether the search overlay (§4.0.1) is showing are app state, not Compose-internal state.
 */
data class CompoundsListState(
    val items: ImmutableList<CompoundListItemUi> = persistentListOf(),
    val statusFilter: CompoundStatusFilter = CompoundStatusFilter.ALL,
    val selectedCategories: ImmutableSet<CompoundCategory> = persistentSetOf(),
    val selectedForms: ImmutableSet<CompoundForm> = persistentSetOf(),
    val searchQuery: String = "",
    val openFilterMenu: CompoundFilterMenu? = null,
    val isSearchOpen: Boolean = false,
    val isLoading: Boolean = true,
)
