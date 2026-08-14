package com.stax.notification.alarm

import com.stax.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Keeps `Settings.exactAlarmDegraded` in step with the "Alarms & reminders" permission (§3.8, §5.1).
 *
 * Room is the single place the rest of the app reads reminder precision from: the Settings warning
 * row (§4.13.3) and the widget's degraded state (§4.16.4) both render off the persisted flag rather
 * than calling `AlarmManager` themselves, so the flag has to be refreshed on app start and on every
 * permission-state-changed broadcast — which is exactly what [ExactAlarmPermission.observe] emits.
 */
class ExactAlarmPermissionMonitor(
    private val exactAlarmPermission: ExactAlarmPermission,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Mirrors the permission into `Settings.exactAlarmDegraded` until cancelled. Never returns —
     * callers run it on a process-lifetime scope (`ExactAlarmInitializer` in `:app`).
     */
    suspend fun sync() {
        exactAlarmPermission.observe().collect { granted -> writeDegraded(degraded = !granted) }
    }

    /**
     * Writes the flag only when it actually changed. `update` touches `updatedAt` and re-emits the
     * singleton row to every observer, so an unconditional write would churn the UI on each app
     * start for the overwhelmingly common case where nothing moved.
     */
    private suspend fun writeDegraded(degraded: Boolean) {
        val settings = settingsRepository.observe().first()
        if (settings.exactAlarmDegraded == degraded) return

        settingsRepository.update(settings.copy(exactAlarmDegraded = degraded))
        // TODO(M13-02): on the false transition, reschedule every Pending reminder alarm; on the
        //  true transition, cancel the exact alarms we can no longer honour (§5.1). Needs the
        //  AlarmScheduler, which lands with M13-02 and depends on this class.
    }
}
