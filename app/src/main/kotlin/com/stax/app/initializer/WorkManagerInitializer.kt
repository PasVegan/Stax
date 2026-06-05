package com.stax.app.initializer

import android.content.Context
import androidx.startup.Initializer

/**
 * Deferred initializer — WorkManager custom config + periodic worker
 * enqueue happens post-first-frame on `Lifecycle.STARTED` (§2.3.4).
 *
 * The androidx.work library ships its own `WorkManagerInitializer`
 * content provider; that provider is removed via manifest merger tools
 * node so Stax controls initialization order explicitly (M0-13).
 *
 * Stub until M0-13 (WorkManager wiring).
 */
class WorkManagerInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // TODO(M0-13): configure WorkManager and enqueue periodic workers on Lifecycle.STARTED
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(KoinInitializer::class.java)
}
