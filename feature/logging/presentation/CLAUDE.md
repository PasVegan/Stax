# `:feature:logging:presentation` — Dose logging

## Purpose
All the ways a dose gets recorded: Take Dose bottom sheet, Log Dose forms (Dashboard / Compound /
Protocol variants), Log Grouped Event sheet, Edit Dose, and the Administration Event detail screen.
This is where inventory deduction is triggered (executed in the data layer).

## Module coordinates
- Gradle: `:feature:logging:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.logging.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `LoggingPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` routes:
  `LogDoseRoute(compoundId, protocolId)`, `AdministrationEventDetailRoute(eventId)`) +
  `loggingEntries` (Nav3 entryProvider extension). Coming: Take Dose + Log Dose variant ViewModels &
  State/Action/Event, grouped-event sheet, edit-dose, admin-event-detail.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `edge-to-edge`, `android-di-koin`.

## Owned by
Logging feature.

## Notes
- Inventory deduction is transactional and lives in `:core:data` (§5.3, §5.8.5) — the form calls the
  repository; it does not mutate inventory itself.
- Bottom sheets live in parent screen state (not Nav3 routes) per §10.3; at Expanded they become
  side sheets (§6.4.2). Form drafts survive rotation via `rememberSaveable`/SavedStateHandle.
- Take Dose is a deep-link target from `:widget` / `:shortcut`.
- **`LogDoseRoute` carries which context it was opened from, and both halves are optional**:
  `compoundId` for §4.10.2-b (Compound Detail's dock), `protocolId` for §4.10.2-c (Protocol Detail's),
  neither for §4.10.2-a, which opens the picker first. The screens that push it exist ahead of the
  form, so the argument lands before there is anything to prefill from.
- See spec §4.10, §4.11, §5.3; ISSUES M11-*.
