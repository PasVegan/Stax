package com.stax.app.initializer

import android.content.Context
import androidx.startup.Initializer

/**
 * Deferred initializer — measures the combined load cost of Google Sans Flex
 * and Material Symbols Rounded. If cost >40ms on the current device, fonts
 * are loaded asynchronously with a Compose `FontFamily` fallback for the
 * first frame (§2.3.4).
 *
 * Stub until M4 (font + theme wiring).
 */
class FontPreloadInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // TODO(M4): measure font load cost; preload async if >40ms
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(KoinInitializer::class.java)
}
