/**
 * Navigation 3 typed route (`ReconstitutionRoute`) and the
 * [com.stax.feature.reconstitution.presentation.navigation.reconstitutionEntries] entryProvider
 * extension for the Reconstitution feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`.
 *
 * Entry points: `reconstitutionEntries(...)`.
 */
package com.stax.feature.reconstitution.presentation.navigation
