# `:widget` — Glance home-screen widget

## Purpose
The home-screen widget built with **Glance**: small/medium/large sizes showing next/pending doses,
with Take / Snooze / Open actions that deep-link into the app (Take Dose sheet or Dashboard).

## Module coordinates
- Gradle: `:widget` · plugin `com.stax.android.library` (+ Glance: `glance-appwidget`, `glance-material3`).
- Package: `com.stax.widget`.

## Allowed dependencies
`:core:domain`, `:core:data`.

## Key types
- Glance `GlanceAppWidget` + `GlanceAppWidgetReceiver`, widget content composables (per content state),
  action callbacks (Take / Snooze / Open), refresh broadcast plumbing.

## Applicable skills
`android-compose-ui` (Glance is Compose-flavored), `android-data-layer` (reads via repositories).

## Owned by
Shared (out-of-app surface).

## Notes
- Glance renders to RemoteViews — not full Compose; theming via `glance-material3`, not `StaxTheme`.
- Actions deep-link through the same routes the app uses; keep deep-link contract in sync with
  `:shortcut` and `:app` nav.
- Refresh is broadcast-driven, not polling (battery-aware, §2.3).
- See spec §4.16, §6.3.1; ISSUES M15-*.
