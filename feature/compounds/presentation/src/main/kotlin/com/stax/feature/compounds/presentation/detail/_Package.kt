/**
 * The Compound Detail screen (§4.3) — stat strip, opened container, active protocols, notes, and the
 * paginated dose history, over the Log dose / Adjust dock.
 *
 * MVI per §10.1: `CompoundDetailState` / `CompoundDetailAction` / `CompoundDetailEvent` +
 * `CompoundDetailViewModel`, rendered by the `CompoundDetailRoot` / `CompoundDetailScreen` pair over
 * the sections of `CompoundDetailSections.kt`. Boundaries: no Room — the compound, its supply
 * figures, its protocols, their generated doses and its history all arrive through repository
 * interfaces, and the ViewModel maps every one of them to a UI model before it reaches state.
 *
 * This is the **detail pane** of the Compounds list-detail Scene (§6.4.2), and it hosts the §4.5
 * opened-container sheet as a mode of itself rather than as a destination (§10.3).
 *
 * Entry points: `CompoundDetailRoot`, `CompoundDetailViewModel`, `CompoundDetailState`.
 */
package com.stax.feature.compounds.presentation.detail
