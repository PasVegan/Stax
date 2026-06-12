/**
 * Navigation 3 typed route (`SettingsRoute`) and the
 * [com.stax.feature.settings.presentation.navigation.settingsEntries] entryProvider extension for
 * the Settings feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`.
 *
 * Entry points: `settingsEntries(...)`.
 */
package com.stax.feature.settings.presentation.navigation
