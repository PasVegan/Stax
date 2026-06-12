# Stax — AI agent guide (root)

Stax is a **local-first Android app** for tracking peptide / supplement / hormone protocols:
compounds + inventory, reconstitution math, dosing protocols with escalation, a dose log,
injection-site rotation, reminders, a home-screen widget, and app shortcuts. No backend, no
network — everything lives in a local Room database.

This file is the entry point for any AI agent working in this repo. Read it first.
`AGENT.md` (sibling symlink) resolves to this same file.

---

## Source-of-truth map (read in this order)

1. **`CLAUDE.md/AGENT.md`** (this file) — orientation, conventions, commands, gotchas.
2. **`detailed-spec.md`** — the canonical product + engineering spec. Every `§x.y` reference
   anywhere (issues, code comments, module docs) points here. **Do not change `§` numbers** —
   they are referenced by GitHub issues.
3. **`ISSUES.md`** — the milestone/issue backlog and the **normative "Skill alignment" table**
   (which reference skill governs which work). This file is the **single source of truth for
   GitHub issues** — see "Issue workflow" below.
4. **Per-module `CLAUDE.md/AGENT.md`** — local rules + key types for the module you're editing.
5. **`_Package.kt`** — per-package KDoc: purpose, boundaries, entry points.

When the spec and code disagree, the spec is canonical (X-04) — but if the code reveals a real
gap, update the spec in the same change.

---

## Tech stack (exact)

| Area | Choice |
|------|--------|
| Language / build | Kotlin `2.4.0`, AGP `9.2.1`, Gradle convention plugins in `:build-logic`, JDK **21** toolchain |
| SDK | `compileSdk = 37` (required by `adaptive-navigation3` `1.3.0-beta02`), `minSdk = targetSdk = 36` (Android 16 only) |
| UI | Jetpack Compose, **Material 3 Expressive** (BOM `2026.05.01`, material3 `1.5.0-alpha20`), Google Sans Flex, Material Symbols Rounded icons as **vector drawables** via `Icon` (no icon font, no `material-icons-extended`) |
| Navigation | **Navigation 3** (`NavDisplay` + `entryProvider` + `NavBackStack`), `navigation3 1.1.2` |
| Adaptive | `NavigationSuiteScaffold` chrome + Nav3 **Scene strategies** (`ListDetailSceneStrategy`, `SupportingPaneSceneStrategy`) from `adaptive-navigation3` — **never** `*PaneScaffold` |
| Architecture | **MVI** per screen, **Koin** DI (`4.2.1`) |
| Data | **Room** (`2.8.4`, WAL + FK on), DataStore (theme cache), kotlinx-datetime (`0.8.0`) |
| Background | WorkManager + AlarmManager (exact reminders). No foreground services. |
| Widget | Glance (`1.1.1`) |
| Testing | JUnit5 (`6.1.0`), AssertK, Turbine, Robolectric, Compose UI test, Macrobenchmark |

---

## Architecture in one screen

- **MVI** (§10.1): every screen ViewModel exposes `state: StateFlow<S>` + `events: Channel<E>`
  (via `receiveAsFlow()`) + one `onAction(action: A)`. Root composable holds the VM via
  `koinViewModel()` and observes events with `ObserveAsEvents`; Screen composable takes
  `state` + `onAction` only and is previewable. Skill: `android-presentation-mvi`.
- **UI models, never domain models** in Compose (§2.3.1). ViewModels map domain → UI model when
  emitting state. State classes hold primitives + value classes + `ImmutableList/Set/Map` only.
- **Repository layer** (§10.2): ViewModels never touch Room/DataStore. Repository interfaces live
  in `:core:domain`; `Room*` impls live in `:core:data`. Mutating ops return
  `Result<T, DataError.Local>` / `EmptyResult<DataError.Local>`. Skills: `android-data-layer`,
  `android-error-handling`.
- **Navigation 3** (§10.3): routes are `@Serializable` `NavKey` types; `:app` hosts one
  `NavDisplay` whose `entryProvider` is built from per-feature
  `EntryProviderScope.<feature>Entries(...)` extensions. One `NavBackStack` per top-level
  destination. Skill: `navigation-3`.
- **Adaptive** (§6.4): multi-pane via Scene strategies; lists via `GridCells.Adaptive`. Skill:
  `adaptive`. Edge-to-edge per `edge-to-edge` skill (§2.3.6).

---

## Module map + dependency rules (§10.4)

```
:app                 wires modules, NavDisplay + entryProvider, Application, App Startup initializers
:build-logic         Gradle convention plugins (com.stax.*)
:detekt-rules        custom detekt ruleset (stax) — e.g. NoCrossFeatureRouteImport; loaded via detektPlugins
:core:domain         domain models, repository INTERFACES, errors, Result, Decimal/Quantity/Concentration   (pure Kotlin)
:core:database       Room @Database, entities, DAOs, converters, migrations, seed callback
:core:data           repository IMPLs (Room*), Entity↔Domain mappers, DataStore, ScheduledDoseGenerator, Koin coreDataModule
:core:presentation   UiText, DataError→UiText, shared UI utils (ObserveAsEvents — planned, lands with first feature)
:core:design-system  M3 Expressive theme, typography, motion, shapes, tokens, Nav3 Scene wrappers, AdaptiveFab, icons
:feature:<x>:presentation   one per feature (compounds, protocols, sites, dashboard, reconstitution, logging, settings, onboarding)
:widget :shortcut :work :notification   out-of-app surfaces
:benchmark           Macrobenchmark + Baseline Profile
```

| Layer | May depend on |
|-------|---------------|
| `:core:domain` | nothing |
| `:core:database` | `:core:domain` |
| `:core:data` | `:core:domain`, `:core:database` |
| `:core:presentation` | `:core:domain` |
| `:core:design-system` | nothing (pure Compose) |
| `:feature:<x>:presentation` | `:core:domain`, `:core:presentation`, `:core:design-system` |
| `:widget`/`:shortcut`/`:work`/`:notification` | `:core:domain`, `:core:data` |
| `:app` | everything |

**Features never depend on each other.** Cross-feature wiring happens only in `:app` via Nav3
lambda callbacks. Enforced by the `checkForbiddenModuleDependencies` Gradle task (defined in root
`build.gradle.kts`, allow-list = the table above, wired into `./gradlew check`). A feature
presentation module must **never** import `:core:database` or `:core:data`.

Adding a module: apply the right convention plugin (below), register it in `settings.gradle.kts`,
add a `CLAUDE.md` + `AGENT.md` symlink + `_Package.kt`. Skill: `android-module-structure`.

### Convention plugins (`:build-logic`, ids `com.stax.*`)

`com.stax.android.application` · `com.stax.android.library` · `com.stax.android.feature`
(library + compose + koin + feature deps) · `com.stax.kotlin.library` (pure Kotlin) ·
`com.stax.compose` · `com.stax.koin` · `com.stax.room` · `com.stax.kotlinx.serialization` ·
`com.stax.testing` · `com.stax.ktlint` · `com.stax.detekt`. SDK/JDK constants live in
`StaxConventionPlugins.kt` — change them there, not per module.

---

## Hard rules (lint/detekt-enforced — do not violate)

- **No `Double` / `Float` for dose math anywhere** (§3.0.1). Use `Decimal` / `Quantity` /
  `Concentration` value types from `:core:domain`.
- **No `LocalDateTime`.** Use `Instant` + `LocalDate` + `LocalTime` from kotlinx-datetime (§5.7).
- **No raw color literals** (`Color(0xff…)`) outside `Tokens.kt`; **no `tween(`** outside
  `StaxMotion`; **no Room access** from `:feature:*`.
- **No `remember`/`rememberSaveable` for app state** — app state lives in the ViewModel,
  collected via `collectAsStateWithLifecycle()`. Only Compose-internal state (`LazyListState`,
  etc.) uses `remember*`.
- **`startKoin` runs from `KoinInitializer`** (App Startup), not `Application.onCreate` (§2.3.4).
- Errors flow through typed `Result` + `DataError`; user-facing strings through `UiText`. Never
  throw on expected failures.
- Multi-pane = Nav3 Scene strategies, **not** `ListDetailPaneScaffold` / `SupportingPaneScaffold`.
- **Icons** = `Icon` + `StaxIcons` vector drawables (`:core:design-system/res/drawable/ic_*.xml`). No icon
  font, no `material-icons-extended`. **Need an icon that isn't there? STOP and ask** — give the Material
  Symbol name (Rounded, w400/grade0/24dp; `+_filled` if selectable). Never invent/substitute (spec §9).

---

## Build & test commands

```bash
./gradlew :app:assembleDebug          # build the app
./gradlew test                        # JVM unit tests (JUnit5)
./gradlew connectedCheck              # instrumented + Compose UI tests (needs device/emulator)
./gradlew ktlintCheck detekt          # lint — must be clean
./gradlew check                       # all verification incl. checkForbiddenModuleDependencies (dep-rule guardrail)
./gradlew :core:domain:test           # scope tests to one module
```

Emulator/device control, SDK management, and screenshots use the **`android`
CLI** (skill: `android-cli`). Release optimization (R8 full mode, keep-rule audit) uses
`r8-analyzer` (§2.3.9, M20-01). Testing harness/strategy uses `testing-setup` (§10.5).

---

## Documentation convention (this is what you're reading)

- **Root `CLAUDE.md/`** + per-module `CLAUDE.md`, each with an `AGENT.md` symlink → `CLAUDE.md`.
- **`_Package.kt`** per Kotlin package: KDoc-only (purpose / boundaries / entry points).
- Keep them in sync: a PR that changes a module's public types updates its `CLAUDE.md`; a new
  package adds a `_Package.kt`. Policy: X-05 / X-06, spec §10.6.

---

## Issue + spec workflow (important)

- **`ISSUES.md` is the single source of truth for GitHub issues.** Each `### Mx-yy · Title`
  section maps to the GitHub issue whose title starts with the same `Mx-yy` id.
- To change an issue: **edit `ISSUES.md` (and the relevant `§` in `detailed-spec.md`), then run**
  `python3 scripts/sync-issues.py` — it regenerates every issue's title + body. **Never hand-edit
  GitHub issue bodies** (they are generated; flags: `--dry-run`, `--only Mx-yy`,
  `--include-closed`). Closed issues are skipped by default and are historical record.
- `start-oldest-feature.sh` picks the oldest open issue, branches, opens a draft PR, and feeds the
  issue body to the agent as the work order.
- **Git**: branch off `dev` (default). Commit/push only when asked or when the issue workflow
  requires it. Never add yourself as a co-author.

---

## What to read for a given task

| Task | Read |
|------|------|
| New screen / ViewModel | spec §4.x for that screen, §10.1; skill `android-presentation-mvi` + `android-compose-ui` |
| Repository / DAO / mapper | spec §3, §5.8, §10.2; skills `android-data-layer`, `android-error-handling` |
| Navigation | spec §6, §10.3; skill `navigation-3` |
| Adaptive / multi-pane | spec §6.4; skill `adaptive`; edge-to-edge skill |
| Theme / tokens / motion | spec §9, §5.9; module `:core:design-system` |
| Reminders / workers | spec §5.1–§5.3; modules `:work`, `:notification` |
| Tests | spec §10.5; skills `android-testing`, `testing-setup` |
| Release / R8 | spec §2.3.9; skill `r8-analyzer` |
