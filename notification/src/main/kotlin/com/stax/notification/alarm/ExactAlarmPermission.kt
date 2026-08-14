package com.stax.notification.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The "Alarms & reminders" special access that gates exact dose reminders (§5.1).
 *
 * An interface rather than a direct `AlarmManager` call so the pieces that depend on it — the
 * degrade-flag sync ([ExactAlarmPermissionMonitor], and the alarm scheduler at M13-02) — stay unit
 * testable without a device or a Robolectric shadow.
 */
interface ExactAlarmPermission {

    /** Whether the OS currently allows `setExactAndAllowWhileIdle` — `canScheduleExactAlarms()`. */
    fun isGranted(): Boolean

    /**
     * Emits [isGranted] now and again on every subsequent change, so a caller can hold one
     * subscription instead of polling.
     */
    fun observe(): Flow<Boolean>
}

/**
 * [ExactAlarmPermission] backed by the platform `AlarmManager`.
 *
 * [observe] is driven by `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (§5.1). That
 * broadcast carries no payload — it only says "something changed" — so each delivery re-reads
 * `canScheduleExactAlarms()`. It is a protected system broadcast, hence `RECEIVER_NOT_EXPORTED`.
 *
 * The OS only delivers it to a *runtime-registered* receiver, never to a manifest-declared one, so
 * observation necessarily lasts exactly as long as the collection does.
 */
class AndroidExactAlarmPermission(private val context: Context) : ExactAlarmPermission {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun isGranted(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun observe(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(isGranted())
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        // Seeds the "refreshed on app start" half of §3.8: a permission revoked while the process was
        // dead produces no broadcast, so the current value has to be read once on subscribe.
        send(isGranted())

        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
