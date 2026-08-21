/**
 * The opened-container bottom sheets of §4.5 — Edit Opened Container and Create Already Opened —
 * plus the "Open new container?" prompt natural depletion raises (§4.5.5).
 *
 * **Purpose**: one sheet for the two variants (they differ only by the Delete button), adaptive per
 * §6.4.2 through `StaxAdaptiveSheet`: full-width bottom sheet at Compact, clamped to `560dp` at
 * Medium, an end-edge `420dp` side sheet at Expanded.
 *
 * **Boundaries**: presentation only, and stateless — the sheet is not a nav destination, so it holds
 * no ViewModel of its own (§10.3). Whichever screen opens it keeps [OpenedContainerSheetState] in
 * its own state and performs the §4.5.5 writes: the Create / Edit Compound form (§4.4) today,
 * Compound Detail (§4.3) once M7-07 lands.
 *
 * **Entry points**: `OpenedContainerSheet(state, onAction, onDismiss)` and
 * `NaturalDepletionDialog(onOpenNew, onLeaveClosed)`.
 */
package com.stax.feature.compounds.presentation.container
