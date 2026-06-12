# `:app` — application shell

## Purpose
The application module: `Application` subclass, single `Activity`, App Startup initializers, and the
root **Navigation 3** host (`NavDisplay` + `entryProvider` assembled from every feature's
`<feature>Entries` extension). This is the only module that may depend on everything and the only
place cross-feature navigation is wired (via lambda callbacks).

## Module coordinates
- Gradle: `:app` · plugin `com.stax.android.application` (+ compose, koin, room consumers).
- Package: `com.stax.app` (`.initializer`).
- `applicationId` + version live in the application convention plugin.

## Allowed dependencies
Everything (all `:core:*`, all `:feature:*:presentation`, `:widget`, `:shortcut`, `:work`,
`:notification`).

## Key types
- `StaxApplication` — Application subclass; **does not** call `startKoin` (App Startup does).
- `MainActivity` — `enableEdgeToEdge()` before `setContent`; applies `StaxTheme`, hosts `MainScaffold`.
- `MainScaffold` (`MainScaffold.kt`) — top-level `NavigationSuiteScaffold` chrome wrapping the single
  `NavDisplay`. `layoutType` computed from `currentWindowAdaptiveInfoV2()` with M3 Expressive types:
  `ShortNavigationBarCompact` <600dp · `WideNavigationRailCollapsed` 600dp+ ·
  `WideNavigationRailExpanded` 840dp+ (§6.4.1). Holds a
  `rememberNavigationSuiteScaffoldState` for hide-on-scroll chrome (§6.4.9). `StaxNavDisplay` (private)
  assembles the `entryProvider` from every feature's `<feature>Entries` + wires cross-feature callbacks,
  rendering `MainNavigationState.toDecoratedEntries` and the `StaxListDetailScene` strategy in
  `NavDisplay.sceneStrategies` (Compounds / Protocols list-detail, §6.4.2).
- `MainNavigationState` / `rememberMainNavigationState` (`MainNavigationState.kt`) — one saveable
  `NavBackStack` per destination (Nav3 multiple-back-stacks recipe, §6.2 / §6.4.5). `onTopLevelSelected`
  switches destination or, on re-tap, pops that stack to root; `push` adds a stacked screen to the active
  stack; `showDetail` replaces the same-type detail (two-pane swap, §6.4.2); `goBack` pops or returns to
  Home ("exit through home"). Active route + every stack survive config changes + process death
  (`rememberSerializable` + `rememberNavBackStack`).
- `TopLevelDestination` — the 5 destinations (Home/Compounds/Protocols/Sites/Settings): Nav3 root route
  + `StaxIcons` outlined/`Filled` icon + label (§4.0).
- `initializer/` — `KoinInitializer` (starts Koin, eager), `ThemeInitializer` (eager, DataStore theme cache),
  `RoomDatabaseInitializer`, `WorkManagerInitializer`, `FontPreloadInitializer` (deferred, `Lifecycle.STARTED`).

## Applicable skills
`navigation-3` (NavDisplay/entryProvider wiring), `adaptive` (NavigationSuiteScaffold chrome),
`edge-to-edge`, `android-di-koin` (module assembly).

## Owned by
Shared.

## Notes
- App Startup eager/deferred split is performance-critical (§2.3.4) — keep `KoinInitializer` +
  `ThemeInitializer` eager; everything else deferred. Cold-start SLO < 400ms (§2.3.2).
- Cross-feature nav callbacks live here; feature modules never import each other.
- R8/ProGuard config lands here at M20-01 (skill `r8-analyzer`, §2.3.9).
- See spec §4.0, §10.3, §2.3.4; ISSUES M0-09, M5-02.
