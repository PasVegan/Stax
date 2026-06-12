/**
 * Navigation 3 typed route (`LogDoseRoute`) and the
 * [com.stax.feature.logging.presentation.navigation.loggingEntries] entryProvider extension for the
 * Logging feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`.
 *
 * Entry points: `loggingEntries(...)`.
 */
package com.stax.feature.logging.presentation.navigation
