/**
 * Reconstitution Helper (§4.6): the MVI triad and Compose screen for the reconstitution calculator.
 *
 * Purpose: turn a container amount, a diluent volume and a desired dose into the concentration of the
 * mix, the volume to draw and the doses one container yields — live, on every keystroke.
 *
 * Boundaries: `:core:domain`, `:core:presentation`, `:core:design-system` only. All arithmetic runs on
 * `Decimal` / `Quantity` / `Concentration`; this package renders the result and never a `Double`
 * (§3.0.1).
 *
 * Entry points: `ReconstitutionRoot` (held by
 * [com.stax.feature.reconstitution.presentation.navigation.reconstitutionEntries]),
 * `ReconstitutionViewModel`.
 */
package com.stax.feature.reconstitution.presentation
