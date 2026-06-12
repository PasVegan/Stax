/**
 * Navigation 3 typed routes (`CompoundsRoute`, `CompoundDetailRoute`, …) and the
 * [com.stax.feature.compounds.presentation.navigation.compoundsEntries] entryProvider extension for
 * the Compounds feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`
 * (spec §10.3).
 *
 * Entry points: `compoundsEntries(...)`.
 */
package com.stax.feature.compounds.presentation.navigation
