package com.stax.core.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preference keys for the theme mirror (§2.3.4, §3.8).
 *
 * Only `theme` and `dynamicColor` are mirrored; all other Settings fields live
 * exclusively in Room.
 */
object ThemePreferences {
    /** Serialised [com.stax.core.domain.AppTheme.name]. */
    val THEME = stringPreferencesKey("theme")

    /** Mirror of [com.stax.core.domain.Settings.dynamicColor]. */
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

    /** DataStore file name (without `.preferences_pb` extension). */
    const val FILE_NAME = "theme_prefs"
}
