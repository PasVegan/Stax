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
- `MainActivity` — `enableEdgeToEdge()` before `setContent`; applies `StaxTheme`, wraps the nav root in
  `ProvideFoldingFeature` (hinge detection, §6.4.3), hosts `MainScaffold`. It observes `SettingsRepository`
  and composes **nothing** until the first emission: `Settings.onboardingCompleted` decides the *initial*
  back stack (§4.14), so rendering earlier would flash Dashboard before onboarding animates in. The
  settings row is seeded on database creation, so this resolves within a frame or two of a cold start.
- `MainScaffold` (`MainScaffold.kt`) — takes `onboardingCompleted: Boolean`; top-level `NavigationSuiteScaffold` chrome wrapping the single
  `NavDisplay`. `layoutType` computed from `currentWindowAdaptiveInfoV2()` with M3 Expressive types:
  `ShortNavigationBarCompact` <600dp · `WideNavigationRailCollapsed` 600dp+ ·
  `WideNavigationRailExpanded` 840dp+ (§6.4.1). Holds a
  `rememberNavigationSuiteScaffoldState` for hide-on-scroll chrome (§6.4.9). `StaxNavDisplay` (private)
  assembles the `entryProvider` from every feature's `<feature>Entries` + wires cross-feature callbacks,
  rendering `MainNavigationState.toDecoratedEntries` and the `StaxListDetailScene` (Compounds /
  Protocols list-detail) + `StaxSupportingPaneScene` (Dashboard main/supporting) strategies in
  `NavDisplay.sceneStrategies` (§6.4.2). Predictive back: `NavDisplay`'s `predictivePopTransitionSpec`
  (+ matching pop/forward specs, `StaxMotion`-driven scale + fade peek) animates the system back
  gesture while the active Scene strategy resolves the detail → list transition (§6.4.5,
  `enableOnBackInvokedCallback` in the manifest). The nav chrome is **hidden for the first-run flow**
  (`isFirstRunFlow`: Welcome, the notification gate, and the two Create forms flagged `onboarding = true`)
  — a visible nav item would be a one-tap exit out of a flow the user has neither finished nor skipped.
  It is seeded as `rememberNavigationSuiteScaffoldState`'s `initialValue` as well as driven by a
  `LaunchedEffect`, so first launch never flashes the bar in. It is **also hidden for Compound Detail
  while that screen is the whole pane** (`hidesChromeAsSolePane`, §4.3.9): its own dock takes that
  edge. Only below the Medium width — from there up the detail is one pane of the list-detail Scene
  beside the Compounds list, which is a top-level destination and keeps its rail (§6.4.2). The chrome is **also hidden while a
  screen is in multi-select** (§4.2.4), which replaces the nav bar with its own bottom dock: only the
  screen knows the mode is on, so Compounds reports it through `compoundsEntries(onSelectionModeChange
  = …)` and the decision is made here. `navSuiteType()` (private) resolves the M3 Expressive nav type
  from `currentWindowAdaptiveInfoV2()`.
  Finishing/skipping onboarding persists completion, then **clears the flow off the stack**
  (`goToStartRoot`) before routing to the gate — pushing the gate on top of the stepper would leave a
  back gesture walking back into the completed forms — and routes there **only when
  `POST_NOTIFICATIONS` is not granted** (§4.15's trigger); already-granted goes straight to Dashboard.
- `MainNavigationState` / `rememberMainNavigationState` (`MainNavigationState.kt`) — one saveable
  `NavBackStack` per destination (Nav3 multiple-back-stacks recipe, §6.2 / §6.4.5). `onTopLevelSelected`
  switches destination or, on re-tap, pops that stack to root; `push` adds a stacked screen to the active
  stack; `showDetail` replaces the same-type detail (two-pane swap, §6.4.2); `goBack` pops or returns to
  Home ("exit through home"); `goToStartRoot` ends a flow outright — it clears the active stack and lands
  on Home's root (onboarding completion, §4.14). `rememberMainNavigationState`'s `initialStackedRoute` seeds
  the start route's stack with a flow that must be answered before the user reaches Home — first launch
  passes `OnboardingRoute`, so the app opens on onboarding without it becoming a top-level destination. It
  is only an *initial* value (a restored stack wins), so a configuration change or process death mid-flow
  never re-pushes it, and persisting `onboardingCompleted` mid-flow does not reset the stack.
  `currentRoute` is the top of the active stack (what the user is looking at) — `MainScaffold` uses it
  to hide the chrome for the first-run flow.
  Active route + every stack survive config changes + process death
  (`rememberSerializable` + `rememberNavBackStack`). Every saveable here sits inside a `key(...)` — an
  unkeyed one takes its registry key from the *enclosing composable's* compound hash, so the five stacks
  of the loop (and their sibling state) would share one key and restore positionally.
- `TopLevelDestination` — the 5 destinations (Home/Compounds/Protocols/Sites/Settings): Nav3 root route
  + `StaxIcons` outlined/`Filled` icon + label (§4.0). Item labels are `maxLines = 1` +
  `TextAutoSize.StepBased(min = 9.sp, max = LocalTextStyle.current.fontSize)` + `Ellipsis`: the item's
  label slot imposes no line limit of its own, and `ShortNavigationBar` gives each item `barWidth / 5`,
  so on a very narrow window (a folded cover screen is ~330dp) "Compounds" wrapped to a second line.
  Taking the max from `LocalTextStyle` keeps each nav suite type's own token size as the ceiling — only
  labels that do not fit shrink, and only below 9.sp do they ellipsize.
- `res/values/themes.xml` — `Theme.Stax`, the window theme declared on `<application>`: DeviceDefault
  DayNight with **no title / no action bar**. The window is pure Compose and edge-to-edge, so a
  platform ActionBar would overlay pane content (it claims no inset of its own, §2.3.6).
- `initializer/` — `KoinInitializer` (starts Koin, eager), `ThemeInitializer` (eager, DataStore theme cache),
  `RoomDatabaseInitializer`, `ExactAlarmInitializer`, `WorkManagerInitializer`, `FontPreloadInitializer`
  (deferred, `Lifecycle.STARTED`). `ExactAlarmInitializer` starts `:notification`'s
  `ExactAlarmPermissionMonitor` on a process-lifetime scope, mirroring the "Alarms & reminders"
  permission into `Settings.exactAlarmDegraded` (§5.1, M6-05). The scope is deliberately unscoped to any
  Activity: the permission-change broadcast reaches only a runtime-registered receiver, so the
  subscription has to outlive every screen. It declares `RoomDatabaseInitializer` as a dependency —
  resolving the monitor pulls `SettingsRepository`, hence the `StaxDatabase` singleton that binds there.

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
