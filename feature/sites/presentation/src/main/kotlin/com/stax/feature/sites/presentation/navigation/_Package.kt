/**
 * Navigation 3 typed route (`SitesRoute`) and the
 * [com.stax.feature.sites.presentation.navigation.sitesEntries] entryProvider extension for the
 * Sites feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`.
 *
 * Entry points: `sitesEntries(...)`.
 */
package com.stax.feature.sites.presentation.navigation
