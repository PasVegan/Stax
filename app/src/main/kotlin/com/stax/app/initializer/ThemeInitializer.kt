package com.stax.app.initializer

import android.content.Context
import androidx.startup.Initializer

/**
 * Eager initializer — must complete before the first frame.
 *
 * Reads the DataStore theme cache (`theme`, `dynamicColor`) so the first
 * Compose frame can apply the correct theme without touching Room (§2.3.4).
 *
 * Stub until M4 (theme + DataStore wiring).
 */
class ThemeInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // TODO(M4): read DataStore theme cache and populate ThemeStateHolder
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(KoinInitializer::class.java)
}
