# `:feature:protocols:presentation` — Protocols

## Purpose
Dosing protocols: protocols list (+ multi-select), Protocol Detail, Create/Edit Protocol (with live
forecast & warnings), escalation rules, and the pause-with-unsaved-changes flow.

## Module coordinates
- Gradle: `:feature:protocols:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.protocols.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `ProtocolsPresentationModule` (Koin). Coming: list/detail/create ViewModels & State/Action/Event,
  `Routes.kt` (NavKeys), `ProtocolsEntries`, Root/Screen composables, forecast preview.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Protocols feature.

## Notes
- Escalation math + scheduled-dose generation live in `:core:domain`/`:core:data`
  (`ScheduledDoseGenerator`) — the UI only configures them; do not reimplement dose math here.
- List+Detail uses the Nav3 list-detail Scene at Medium+ (§6.4.2).
- Create form reused by Onboarding step 3 — keep it self-contained.
- See spec §4.7–§4.9, §6.4.2 Protocols; ISSUES M9-*.
