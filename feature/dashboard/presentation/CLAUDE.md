# `:feature:dashboard:presentation` — Dashboard / Home

## Purpose
The Home screen: day-chip strip, today's dose cards (with swipe gestures), inventory warnings,
recent activity, grouped-administration suggestion, and the direct-log/menu FAB. Start destination.

## Module coordinates
- Gradle: `:feature:dashboard:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.dashboard.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only. **Never** `:core:data`/`:core:database`,
never another feature.

## Key types
- `DashboardPresentationModule` (Koin); `navigation/Routes.kt` (`DashboardRoute` main pane +
  `DashboardSupportingRoute` supporting pane) + `dashboardEntries` (Nav3 entryProvider extension,
  tagged `StaxSupportingPaneScene.mainPane()` / `supportingPane()`, §6.4.2). Coming: `DashboardViewModel`
  + `DashboardState/Action/Event`, Root + Screen composables, dose-card UI.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Dashboard feature.

## Notes
- MVI: `state: StateFlow` + `events: Channel` + `onAction`; Root holds VM via `koinViewModel()`,
  Screen takes `state` + `onAction` only. Compose receives **UI models**, not domain models.
- Adaptive (§6.4.2): Compact single-column; Medium supporting-pane Scene; Expanded three-region grid.
- Cross-feature nav (→ Take Dose, Compound Detail) via lambda callbacks wired in `:app`.
- See spec §4.1, §6.4.2 Dashboard, §10.1; ISSUES M12-*.
