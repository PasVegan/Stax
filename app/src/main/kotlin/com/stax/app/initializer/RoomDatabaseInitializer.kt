package com.stax.app.initializer

import android.content.Context
import androidx.startup.Initializer

/**
 * Deferred initializer — declares a reference only; the real DB connection
 * is opened lazily on the first DAO call (§2.3.4).
 *
 * Real work (WAL mode, FK pragma, building the RoomDatabase instance) is
 * deferred to `Lifecycle.STARTED` via LifecycleStartedRegistrar.
 *
 * Stub until M2 (Room entities + DAOs).
 */
class RoomDatabaseInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // TODO(M2): obtain StaxDatabase from Koin and warm the connection
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(KoinInitializer::class.java)
}
