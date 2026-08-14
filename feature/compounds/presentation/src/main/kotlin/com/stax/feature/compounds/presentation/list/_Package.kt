/**
 * The Compounds list screen (§4.2) — filter chip row, search, and the compound rows it resolves to.
 *
 * MVI per §10.1: `CompoundsListState` / `CompoundsListAction` / `CompoundsListEvent` +
 * `CompoundsListViewModel`. Boundaries: no Room — compounds arrive through `CompoundRepository` and
 * the low-stock signal through `InventoryRepository`; the ViewModel maps both to
 * `CompoundListItemUi` and never leaks a domain model into state.
 *
 * Entry points: `CompoundsListViewModel`, `CompoundsListState`. The `CompoundsListRoot` /
 * `CompoundsListScreen` pair lands with M7-02.
 */
package com.stax.feature.compounds.presentation.list
