package com.stax.app.initializer

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.startup.Initializer
import com.stax.core.database.StaxDatabase
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * Builds and registers [StaxDatabase] into the Koin graph.
 *
 * Configuration (§2.3.5):
 *  - Journal mode: WRITE_AHEAD_LOGGING — concurrent reads during worker writes.
 *  - Foreign keys: ON — Room default, no explicit pragma needed.
 *  - Query logging: enabled on debug builds only (logcat tag "StaxRoom").
 *
 * Runs after [KoinInitializer] so `loadKoinModules` has a live Koin instance.
 * Repository bindings in `coreDataModule` resolve [StaxDatabase] lazily on
 * first access, which is always after this initializer has run.
 */
class RoomDatabaseInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val db = StaxDatabase.build(context, enableQueryLog = isDebug)
        loadKoinModules(module { single { db } })
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(KoinInitializer::class.java)
}
