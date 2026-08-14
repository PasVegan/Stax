/**
 * The Compounds list screen (§4.2) — filter chip row, search, and the compound rows it resolves to.
 *
 * MVI per §10.1: `CompoundsListState` / `CompoundsListAction` / `CompoundsListEvent` +
 * `CompoundsListViewModel`, rendered by the `CompoundsListRoot` / `CompoundsListScreen` pair and its
 * `CompoundRow` + `CompoundsSearchOverlay` parts. Boundaries: no Room — compounds arrive through
 * `CompoundRepository` and the low-stock signal through `InventoryRepository`; the ViewModel maps
 * both to `CompoundListItemUi` and never leaks a domain model into state.
 *
 * Entry points: `CompoundsListRoot`, `CompoundsListViewModel`, `CompoundsListState`.
 */
package com.stax.feature.compounds.presentation.list
