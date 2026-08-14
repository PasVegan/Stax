# `:notification` — reminders, channels, exact alarms

## Purpose
Notification channels + builders and the `AlarmManager` scheduling layer for exact dose reminders,
including bucket aggregation and the snooze/log/skip cancellation flow. Owns reminder delivery; the
`:work` module owns the periodic background jobs that feed it.

## Module coordinates
- Gradle: `:notification` · plugins `com.stax.android.library` + `com.stax.koin` + `com.stax.testing`.
- Package: `com.stax.notification` (`.alarm`, `.di`).

## Allowed dependencies
`:core:domain`, `:core:data`.

## Key types
- `ExactAlarmPermission` — the "Alarms & reminders" special access as an interface (`isGranted()` +
  `observe(): Flow<Boolean>`), so its consumers unit-test without a device. `AndroidExactAlarmPermission`
  is the `AlarmManager` implementation; `observe()` is a `callbackFlow` that seeds
  `canScheduleExactAlarms()` on subscribe and re-reads it on every
  `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. That broadcast is protected and is delivered
  **only** to runtime-registered receivers — a manifest `<receiver>` would never fire.
- `ExactAlarmPermissionMonitor` — mirrors that permission into `Settings.exactAlarmDegraded` (§3.8) and
  writes only on an actual change, since `update` bumps `updatedAt` and re-emits to every observer.
  `sync()` runs forever; `:app`'s `ExactAlarmInitializer` owns the process-lifetime scope it runs on.
  Everything that renders reminder precision (Settings warning row §4.13.3, widget §4.16.4) reads the
  persisted flag rather than calling `AlarmManager` itself — this class is the single writer.
- `notificationModule` (Koin) — assembled into the graph by `:app`'s `KoinInitializer`.
- Coming: notification channels + builder (`dose_reminders`, per-user style settings), `AlarmScheduler`
  (exact alarm with fallback), bucket alarm aggregation, snooze/log/skip cancellation.

## Applicable skills
`android-data-layer` (reads schedule/settings via repositories).

## Owned by
Shared (out-of-app surface).

## Notes
- Exact alarms need `SCHEDULE_EXACT_ALARM` (declared in `:app`'s manifest, M6-05) and a degraded
  fallback when the permission is denied (§5.1). `USE_EXACT_ALARM` is not declared: it is reserved for
  alarm-clock/calendar apps and Play restricts it — Stax asks the user via the Settings CTA instead.
- M13-02 hooks its reschedule/cancel into `ExactAlarmPermissionMonitor`'s transition (TODO in place).
- Reminders are `AlarmManager`-driven, no foreground service (battery-aware, §2.3).
- Re-arm alarms after boot via `:work`'s `AlarmReconcileWorker` + `BootReceiver`.
- See spec §5.1, §4.15; ISSUES M13-01/M13-02/M13-07/M13-08.
