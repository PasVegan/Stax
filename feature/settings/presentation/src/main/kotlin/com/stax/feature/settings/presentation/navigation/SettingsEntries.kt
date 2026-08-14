package com.stax.feature.settings.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.feature.settings.presentation.settings.SettingsRoot

/**
 * Contributes the Settings `NavEntry` to the app's `NavDisplay` `entryProvider`. A self-contained
 * top-level destination; any future sub-screen navigation is wired as `:app` callbacks (spec §10.3).
 */
fun EntryProviderScope<NavKey>.settingsEntries() {
    entry<SettingsRoute> { SettingsRoot() }
}
