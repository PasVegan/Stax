package com.stax.app.initializer

import android.content.Context
import androidx.startup.Initializer
import com.stax.notification.alarm.ExactAlarmPermissionMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Deferred initializer — starts the exact-alarm permission mirror (§5.1, M6-05).
 *
 * `Settings.exactAlarmDegraded` has to be refreshed on app start *and* on every permission-state
 * change (§3.8). The change half arrives as a broadcast the OS delivers only to a runtime-registered
 * receiver, so it needs a live subscription for as long as the process lives — which is what the
 * scope below is: not tied to any Activity or ViewModel, and never cancelled.
 *
 * Runs after [RoomDatabaseInitializer]: resolving the monitor pulls `SettingsRepository`, which
 * resolves the `StaxDatabase` singleton that initializer binds.
 */
class ExactAlarmInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val monitor = GlobalContext.get().get<ExactAlarmPermissionMonitor>()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { monitor.sync() }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(KoinInitializer::class.java, RoomDatabaseInitializer::class.java)
}
