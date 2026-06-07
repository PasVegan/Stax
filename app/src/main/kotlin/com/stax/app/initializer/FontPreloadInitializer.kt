package com.stax.app.initializer

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.stax.core.design.system.StaxTypography

private const val TAG = "FontPreloadInitializer"
private const val ASYNC_FONT_LOAD_THRESHOLD_MILLIS = 40L

/**
 * Deferred initializer — measures Google Sans Flex load cost for §2.3.4.
 * Icons are vector drawables (not a font), so nothing else loads here.
 */
class FontPreloadInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val elapsedMillis = StaxTypography.measureGoogleSansFlexLoadCostMillis(context)
        val mode = if (elapsedMillis > ASYNC_FONT_LOAD_THRESHOLD_MILLIS) {
            "async fallback recommended"
        } else {
            "eager load acceptable"
        }
        Log.d(TAG, "Google Sans Flex load cost: ${elapsedMillis}ms ($mode)")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(KoinInitializer::class.java)
}
