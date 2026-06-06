# `:notification` — reminders, channels, exact alarms

## Purpose
Notification channels + builders and the `AlarmManager` scheduling layer for exact dose reminders,
including bucket aggregation and the snooze/log/skip cancellation flow. Owns reminder delivery; the
`:work` module owns the periodic background jobs that feed it.

## Module coordinates
- Gradle: `:notification` · plugin `com.stax.android.library`.
- Package: `com.stax.notification`.

## Allowed dependencies
`:core:domain`, `:core:data`.

## Key types
- Notification channels + builder (`dose_reminders` channel, per-user style settings).
- `AlarmScheduler` (exact alarm with fallback), bucket alarm aggregation, snooze/log/skip cancellation.

## Applicable skills
`android-data-layer` (reads schedule/settings via repositories).

## Owned by
Shared (out-of-app surface).

## Notes
- Exact alarms need `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (+ rationale gate, M6-05) and a degraded
  fallback when the permission is denied (§5.1).
- Reminders are `AlarmManager`-driven, no foreground service (battery-aware, §2.3).
- Re-arm alarms after boot via `:work`'s `AlarmReconcileWorker` + `BootReceiver`.
- See spec §5.1, §4.15; ISSUES M13-01/M13-02/M13-07/M13-08.
