/**
 * Navigation 3 typed route (`DashboardRoute`) and the
 * [com.stax.feature.dashboard.presentation.navigation.dashboardEntries] entryProvider extension for
 * the Dashboard (Home) feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`.
 *
 * Entry points: `dashboardEntries(...)`.
 */
package com.stax.feature.dashboard.presentation.navigation
