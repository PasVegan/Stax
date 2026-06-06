# `:work` — WorkManager background workers

## Purpose
Background workers (WorkManager) for off-main heavy work: generating scheduled doses, inventory
expiry checks, inventory reconciliation, and alarm reconciliation after boot. No foreground services.

## Module coordinates
- Gradle: `:work` · plugin `com.stax.android.library` (+ `work-runtime-ktx`).
- Package: `com.stax.work`.

## Allowed dependencies
`:core:domain`, `:core:data`.

## Key types
- `GenerateScheduledDosesWorker`, `InventoryExpiryCheckWorker`, `InventoryReconcileWorker`,
  `AlarmReconcileWorker` (+ `BootReceiver` trigger).

## Applicable skills
`android-data-layer` (repository access from workers).

## Owned by
Shared (out-of-app surface).

## Notes
- Serialize contended unique work with `ExistingWorkPolicy.KEEP` + unique names (§2.3.8) — e.g.
  `GenerateScheduledDosesWorker` may run while the user is in the Take Dose sheet.
- Workers are enqueued post-first-frame on `Lifecycle.STARTED` by `WorkManagerInitializer` (§2.3.4),
  not at app start.
- Heavy math (recalc/regeneration) stays here, off the main thread (§2.3).
- See spec §5.1–§5.3, §2.3.8; ISSUES M13-03..M13-06.
