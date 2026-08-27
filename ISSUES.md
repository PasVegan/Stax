# Stax — Issues + Milestones

Source of truth: `detailed-spec.md`. Every issue cites the spec sections it implements; AI agent must read those sections before coding. Issues are ordered so each one is unblocked when previous milestones are merged.

Each issue includes:
- **Depends on**: prerequisite issue IDs (must be merged first).
- **Spec refs**: sections of `detailed-spec.md` to implement.
- **Description**: what to build.
- **Acceptance criteria**: testable, mergeable conditions.
- **Out of scope**: explicit non-goals (prevents scope creep).

Conventions:
- Kotlin idiom + lint clean (ktlint + detekt baseline).
- KDoc required on: public APIs surfaced across packages (repository methods, public domain functions, value-class operators, custom motion/theme helpers, custom lint targets) + non-obvious business logic. **Not** required on every composable, state class, or trivially named sealed-interface case.
- Every new screen has at least one Compose UI test on its primary breakpoint (Compact for compact-first screens; the breakpoint named by its `§6.4.2` entry for adaptive screens). The full §6.4.8 cross-profile matrix is enforced once per milestone in **M19-04**, not per PR.
- Every new repository method has a Robolectric DAO test exercising one happy path + one failure path.
- No `Double`/`Float` for dose math anywhere (§3.0.1).
- No `LocalDateTime` ambiguous types — use `Instant` + `LocalDate` + `LocalTime` from `kotlinx-datetime` (§5.7).
- Every module owns a `CLAUDE.md` (with an `AGENT.md` symlink → `CLAUDE.md`) and every package owns a `_Package.kt` with KDoc on the `package` declaration. The repo root owns a `CLAUDE.md` (+ `AGENT.md` symlink) too — read it first. See **X-05 / X-06** for the policy and spec §10.6.

### Skill alignment (normative)

Every milestone follows these reference skills. AI agents must read the referenced skill before coding the corresponding work.

| Concern          | Skill                               | Stax-specific notes                                                                                                                                                                                                                                                                                                                                                                            |
|------------------|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MVI presentation | `android-presentation-mvi`          | Use `Action` + `Event` names (not `Intent` / `Effect`). VM exposes `state: StateFlow<S>` + `events: Channel<E>` via `receiveAsFlow()`. Function = `onAction(action)`. Root + Screen composables in the same file. Map errors via `UiText`.                                                                                                                                                     |
| Compose UI       | `android-compose-ui`                | `@Stable` only when state class contains unstable fields (List/Map/interface) — strong skipping handles primitive-only. No `remember*` for app state. Animate via `graphicsLayer` + deferred state reads (lambda offsets). Slot APIs only in design system.                                                                                                                                    |
| Koin DI          | `android-di-koin`                   | Prefer constructor-reference (`viewModelOf(::Foo)`, `singleOf(::Bar)`). Modules named `<feature><Layer>Module`. **Stax divergence**: `startKoin` runs from `KoinInitializer` (App Startup) per §2.3.4 — not from `Application.onCreate`. Reason: cold-start parallelization.                                                                                                                   |
| Navigation       | `navigation-3`                      | Nav3: routes are `@Serializable` `NavKey`s. `:app` hosts one `NavDisplay` + `entryProvider`; each feature exposes an `EntryProviderScope.<feature>Entries(onNavigateToX)` extension (replaces the old `NavGraphBuilder.<feature>Graph`). `NavBackStack` per top-level destination (multiple-backstacks recipe). Modularize via the **Koin** recipe. No `NavController` / `NavHost`. See §10.3. |
| Adaptive UI      | `adaptive`                          | Nav chrome via `NavigationSuiteScaffold` (+ `NavigationSuiteScaffoldState` for hide-on-scroll). **Multi-pane via Nav3 Scene strategies** `ListDetailSceneStrategy` / `SupportingPaneSceneStrategy` — NOT `*PaneScaffold`. Adaptive lists via `GridCells.Adaptive`; experimental `Grid` / `FlexBox` / `MediaQuery` (Compose `1.11.0-beta01`+, opt-in). See §6.4.                                |
| Edge-to-edge     | `edge-to-edge`                      | `enableEdgeToEdge()` before `setContent`; manifest `adjustResize` for keyboard Activities; insets from `WindowInsets.{statusBars,navigationBars,ime}` (one method per surface, no double padding); lists/FAB not under nav bar; text fields above IME. See §2.3.6.                                                                                                                             |
| Data layer       | `android-data-layer`                | **Stax divergence**: no remote data sources (offline-only). We use the "Repository" name for all data accessors (matches spec §10.2). DTOs are not used; mappers are `Entity ↔ Domain` only. Single shared Room DB lives in `:core:database`.                                                                                                                                                  |
| Error handling   | `android-error-handling`            | Use the custom `Result<D, E : Error>` + `DataError.Local` + `EmptyResult<E>` + chain helpers from `core:domain`. Map user-facing errors via `.toUiText()` in `core:presentation` or feature `presentation`. Never throw on expected failures.                                                                                                                                                  |
| Testing          | `android-testing` + `testing-setup` | JUnit5 + AssertK + Turbine + `UnconfinedTestDispatcher`. Fakes over mocks. Compose UI tests via `createComposeRule()` + `DeviceConfigurationOverride`. `testing-setup` drives harness install + the **screenshot-test** layer (`@PreviewTest` + `@FormFactorPreviews` / Roborazzi). See §10.5.                                                                                                 |
| R8 / app size    | `r8-analyzer`                       | Release optimization: AGP `9.0`+, R8 **full mode** ON (no `enableR8.fullMode=false`), keep-rule audit via the R8 configuration analyzer. See §2.3.9, M20-01.                                                                                                                                                                                                                                   |
| Tooling          | `android-cli`                       | Use the `android` CLI for project creation, run/deploy, SDK management, device screenshots, and env diagnostics.                                                                                                                                                                                                                                                                               |

### Module layout (multi-module mandate)

Stax follows the skill's feature-layered modularization with one Stax-specific shape: a single shared Room DB + a heavily interlinked domain → we put domain + database + repository impls in `:core:*` and split only `:feature:<name>:presentation` per feature.

```
:app
:build-logic                       # Gradle convention plugins
:core:domain                       # all domain models, repository interfaces, errors, Result
:core:database                     # Room DB, entities, DAOs, migrations
:core:data                         # repository impls (transactional logic, mappers)
:core:presentation                 # ObserveAsEvents, UiText, shared UI utilities
:core:design-system                # M3 Expressive theme, motion, icons, design tokens, Nav3 Scene-strategy wrappers (list-detail / supporting-pane), NavigationSuiteScaffold chrome, AdaptiveFab

:feature:onboarding:presentation
:feature:compounds:presentation
:feature:protocols:presentation
:feature:sites:presentation
:feature:dashboard:presentation
:feature:reconstitution:presentation
:feature:logging:presentation
:feature:settings:presentation

:widget                            # Glance widget + actions
:shortcut                          # static shortcut router
:work                              # WorkManager workers
:notification                      # AlarmManager + channels
```

Dependency rules:

| Layer                                               | May depend on                                               |
|-----------------------------------------------------|-------------------------------------------------------------|
| `:core:domain`                                      | nothing                                                     |
| `:core:database`                                    | `:core:domain`                                              |
| `:core:data`                                        | `:core:domain`, `:core:database`                            |
| `:core:presentation`                                | `:core:domain`                                              |
| `:core:design-system`                               | nothing (pure Compose)                                      |
| `:feature:<x>:presentation`                         | `:core:domain`, `:core:presentation`, `:core:design-system` |
| `:widget` / `:shortcut` / `:work` / `:notification` | `:core:domain`, `:core:data`                                |
| `:app`                                              | everything                                                  |

Features never depend on each other. Cross-feature integration happens in `:app` via Navigation 3 callbacks.

This list supersedes §10.4 of `detailed-spec.md`.

---

## Milestone M0 — Project bootstrap

Goal: empty app launches to a blank Compose Dashboard placeholder on a Pixel 10 emulator.

### M0-01 · Initialize Gradle project with version catalog + build-logic
- **Depends on**: none.
- **Spec refs**: §2.4, Conventions / Module layout.
- **Description**: Create Android Gradle project (scaffold via the `android` CLI per `android-cli`). Add `:build-logic` included build (per `android-module-structure`). Version catalog `gradle/libs.versions.toml` covering Kotlin 2.x, AGP latest stable, Compose BOM, Koin, Room, Glance, kotlinx-datetime, kotlinx-collections-immutable, kotlinx-serialization, Coroutines, AndroidX Lifecycle / Navigation 3 / Window / Compose-Adaptive. `minSdk = 36`, `targetSdk = 36`, `compileSdk = 37` (`adaptive-navigation3` ≥`1.3.0-beta02` requires API 37; runtime min stays Android 16).
- **Acceptance**:
  - `./gradlew assembleDebug` succeeds on an empty `:app`.
  - `gradle/libs.versions.toml` committed.
  - `:build-logic` included build registered in `settings.gradle.kts`.
  - Kotlin compiler args: `-Xjvm-default=all`, `-opt-in=kotlin.RequiresOptIn`.

### M0-02 · Convention plugins
- **Depends on**: M0-01.
- **Spec refs**: Conventions / Module layout.
- **Description**: In `:build-logic`, implement convention plugins per `android-module-structure`:
  - `stax.android.application`
  - `stax.android.library`
  - `stax.android.feature` (= library + Compose + Koin + shared feature deps)
  - `stax.kotlin.library` (pure Kotlin, no Android)
  - `stax.compose`
  - `stax.koin`
  - `stax.room`
  - `stax.kotlinx.serialization`
- **Acceptance**: Each plugin applied to a sample module builds clean. Plugins published to the local plugin marker namespace `com.stax.*`.

### M0-03 · Module skeleton (multi-module bootstrap)
- **Depends on**: M0-02.
- **Spec refs**: Conventions / Module layout.
- **Description**: Create all module directories per the Conventions layout: `:app`, `:core:domain`, `:core:database`, `:core:data`, `:core:presentation`, `:core:design-system`, `:feature:onboarding:presentation`, `:feature:compounds:presentation`, `:feature:protocols:presentation`, `:feature:sites:presentation`, `:feature:dashboard:presentation`, `:feature:reconstitution:presentation`, `:feature:logging:presentation`, `:feature:settings:presentation`, `:widget`, `:shortcut`, `:work`, `:notification`. Apply convention plugins per dependency-rules table. Each module starts empty with a placeholder `CLAUDE.md` + `AGENT.md` symlink (X-05) + `_Package.kt` (X-06).
- **Acceptance**: `./gradlew :app:assembleDebug` builds the full graph. Dependency rules enforced via the `checkForbiddenModuleDependencies` Gradle task (root `build.gradle.kts`, wired into `check`).

### M0-04 · Compose + Material 3 Expressive + Adaptive libs
- **Depends on**: M0-03.
- **Spec refs**: §2.4, §6.4, §6.4.1.
- **Description**: Add Compose BOM (stable), `androidx.compose.material3`, `androidx.compose.material3.adaptive:adaptive-layout` + `:adaptive-navigation3` (the latter provides the Nav3 Scene strategies — list-detail / supporting-pane; the former backs them + supplies `WindowSizeClass` / `currentWindowAdaptiveInfo`), `androidx.window` to the `stax.compose` convention plugin. **Do not add `androidx.compose.material.icons-extended`** — icons are hand-picked Material Symbols Rounded vector drawables owned by `:core:design-system` rendered with the `Icon` composable (§9, M4-03). Do **not** *use* the `ListDetailPaneScaffold` / `SupportingPaneScaffold` composables — multi-pane is Scene-strategy based per §6.4 (the `adaptive-layout` **artifact** stays, since the Scene strategies depend on it). The experimental adaptive `Grid` / `FlexBox` / `MediaQuery` APIs (Compose `1.11.0-beta01`+) are optional — add the dependency + opt-in only if/when a screen adopts them. `:core:design-system` + every `:feature:*:presentation` consumes it.
- **Acceptance**: `:core:design-system` compiles with a single placeholder composable.

### M0-05 · Add Koin DI (libraries only)
- **Depends on**: M0-03.
- **Spec refs**: §2.4, §10.4, `android-di-koin`.
- **Description**: Add Koin Android + Compose modules via `stax.koin` convention plugin. Create empty per-module Koin module files: `coreDataModule` (in `:core:data`), `<feature>PresentationModule` in each feature presentation module. Constructor-reference form (`viewModelOf(::X)`, `singleOf(::Y)`) is mandatory. Do NOT call `startKoin` in `Application.onCreate` — Koin is started by `KoinInitializer` (M0-09) per §2.3.4. **Stax divergence** from `android-di-koin`: reason recorded in Conventions table.
- **Acceptance**: `Application` subclass registered in manifest with no `startKoin` call. Koin libraries on classpath. Build green.

### M0-06 · Add Room + DataStore + kotlinx-datetime
- **Depends on**: M0-03.
- **Spec refs**: §2.4, §3.8, §5.8.
- **Description**: Add `androidx.room:room-runtime + room-ktx + room-compiler` (KSP) via `stax.room` plugin applied only to `:core:database`. Add `androidx.datastore:datastore-preferences` to `:core:data`. `org.jetbrains.kotlinx:kotlinx-datetime` + `kotlinx-collections-immutable` to `:core:domain`.
- **Acceptance**: KSP processor registered. Empty `StaxDatabase` interface compiles with `@Database(entities=[], version=1)` in `:core:database`.

### M0-07 · Add WorkManager + AlarmManager permission
- **Depends on**: M0-03.
- **Spec refs**: §2.4, §5.1.
- **Description**: Add `androidx.work:work-runtime-ktx` to `:work`. Declare manifest permissions in `:app` (where merged): `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` (where required), `RECEIVE_BOOT_COMPLETED`.
- **Acceptance**: Manifest permissions present after manifest merge. `WorkManager.initialize` not yet called (M14).

### M0-08 · Add Glance for widget
- **Depends on**: M0-04.
- **Spec refs**: §2.4, §4.16.
- **Description**: Add `androidx.glance:glance-appwidget` + `androidx.glance:glance-material3` to `:widget`. No widget registered yet.
- **Acceptance**: Dependencies resolve. `:widget` compiles.

### M0-09 · Configure App Startup library + start Koin
- **Depends on**: M0-05, M0-06, M0-07.
- **Spec refs**: §2.3.4, §10.4.
- **Description**: Add `androidx.startup:startup-runtime` to `:app`. Create five `Initializer<Unit>` implementations: `KoinInitializer`, `ThemeInitializer`, `RoomDatabaseInitializer`, `WorkManagerInitializer`, `FontPreloadInitializer`. `KoinInitializer.create()` calls `startKoin { modules(coreDataModule, ...all module references) }`. Others are no-op stubs at this stage; filled in by Theme/Font (M4), Room (M2), WorkManager (M13). Wire dependency ordering per §2.3.4 eager/deferred split: ThemeInitializer depends on KoinInitializer; RoomDatabaseInitializer + WorkManagerInitializer + FontPreloadInitializer declare KoinInitializer as dep but stay deferred via `LifecycleStartedRegistrar` (do real work on `Lifecycle.STARTED`, not at startup).
- **Acceptance**: Five initializers exist. Manifest provider entry registers them. Cold app launch starts Koin via the initializer chain — not from `Application.onCreate`. Process death restart still wires Koin correctly.

### M0-10 · Configure edge-to-edge + insets
- **Depends on**: M0-04.
- **Spec refs**: §2.3.6, `edge-to-edge`.
- **Description**: Per the `edge-to-edge` skill: call `enableEdgeToEdge()` **before** `setContent` in `MainActivity.onCreate`; set `android:windowSoftInputMode="adjustResize"` in the manifest for keyboard Activities. Apply `WindowInsets.systemBars` (+ `Modifier.imePadding()` where text fields exist) via a single inset method per surface — no double padding. Rely on framework adaptive bar-icon contrast.
- **Acceptance**: Status bar + nav bar are transparent. Content draws edge-to-edge; lists/FAB never under the nav bar; text fields stay visible above the IME. No hardcoded inset dimensions anywhere in the codebase (grep check in CI).

### M0-11 · Configure SQLite WAL + foreign keys
- **Depends on**: M0-06.
- **Spec refs**: §2.3.5, §5.8.
- **Description**: In `RoomDatabaseInitializer`, build the Room database with `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` + `setQueryCallback` (debug only). Confirm `foreign_keys=ON` (Room default).
- **Acceptance**: First DB open succeeds. `PRAGMA journal_mode` returns `wal`.

### M0-12 · Add testing toolchain
- **Depends on**: M0-01.
- **Spec refs**: §10.5, `android-testing`, `testing-setup`.
- **Description**: Per the `testing-setup` skill, add JUnit 5 (`org.junit.jupiter:junit-jupiter`), AssertK, Robolectric, Compose UI test, Turbine, `kotlinx-coroutines-test` (`UnconfinedTestDispatcher`), Room `MigrationTestHelper`, Macrobenchmark module, **and the screenshot-test layer** (Compose Preview Screenshot Testing tool — `screenshotTest` source set + `@PreviewTest` — and/or Roborazzi). Provide a shared `@FormFactorPreviews` annotation (Phone / Foldable / Tablet / Desktop) in test infra. Tests use `Dispatchers.setMain(UnconfinedTestDispatcher())` in setup.
- **Acceptance**: `./gradlew test` runs an empty JUnit5 test. `./gradlew connectedCheck` runs an empty instrumentation test. `./gradlew validateDebugScreenshotTest` (or Roborazzi `verifyRoborazziDebug`) runs against an empty reference set.

### M0-13 · Configure ktlint + detekt + Compose Compiler metrics + forbidden-dependencies
- **Depends on**: M0-03.
- **Spec refs**: §2.3.1, Conventions / Module layout.
- **Description**: Add `ktlint-gradle` + `detekt-gradle-plugin`. Enable Compose compiler metrics output (`-P plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=...`). Add the `checkForbiddenModuleDependencies` Gradle verification task (root `build.gradle.kts`) enforcing the dependency-rules table via a per-module allow-list (e.g. `:feature:*:presentation` must not depend on `:core:database` or `:core:data` directly); wire it into `check`.
- **Acceptance**: `./gradlew ktlintCheck detekt` succeeds clean. Compose metrics emitted to `build/compose_metrics/`. A dependency-rule violation fails `./gradlew check` via `checkForbiddenModuleDependencies`.

---

## Milestone M1 — Core value types + Result wrapper

Goal: `:core:domain` module populated with `Result`/`DataError`/`UiText`, `Decimal`, `Quantity`, `Concentration`, `UnitCode`, arithmetic, validation — all pure Kotlin, fully unit-tested.

### M1-00a · Result wrapper + Error supertype + extension helpers
- **Depends on**: M0-03.
- **Spec refs**: `android-error-handling`.
- **Description**: In `:core:domain`, add the canonical `Result<D, E : Error>` + `EmptyResult<E>` typealias + `Error` supertype interface + extension helpers `map / onSuccess / onFailure / asEmptyResult` exactly as defined by the `android-error-handling` skill.
- **Acceptance**:
  - `Result.Success(1).onSuccess { ... }.map { it + 1 }` chains and yields `Result.Success(2)`.
  - `Result.Error(MyError.X).onFailure { ... }.asEmptyResult()` returns `Result.Error(MyError.X)`.
  - 100% line coverage.

### M1-00b · `DataError.Local`
- **Depends on**: M1-00a.
- **Spec refs**: `android-error-handling`.
- **Description**: Add `DataError : Error` sealed interface with the `Local` enum: `DISK_FULL`, `NOT_FOUND`, `CONSTRAINT_VIOLATION`, `UNKNOWN`. No `Network` enum (Stax is offline-only).
- **Acceptance**: `DataError.Local.DISK_FULL is DataError` compiles. Tests verify type hierarchy.

### M1-00c · `UiText` + DataError → UiText mapping
- **Depends on**: M1-00b, M0-03.
- **Spec refs**: `android-presentation-mvi` `UiText`, `android-error-handling`.
- **Description**: In `:core:presentation`, add `UiText` sealed interface with `DynamicString(String)` + `StringResource(Int, Array<Any>)`. Provide a `Context.asString(UiText): String` resolver composable + non-Composable variant. Add `DataError.toUiText()` extension mapping every `DataError.Local` value to a `R.string.*` resource.
- **Acceptance**: Resolver works inside + outside composition. Localized strings present for every enum case.

### M1-01 · Implement `Decimal` value class
- **Depends on**: M0-03.
- **Spec refs**: §3.0.1.
- **Description**: Create `core/Decimal.kt` in `:core:domain` exactly as in §3.0.1. `@JvmInline value class` wrapping `BigDecimal` with `MATH = MathContext.DECIMAL64`. Implement `plus/minus/times/div/compareTo/toPlainString/parse`.
- **Acceptance**:
  - `Decimal.parse("0.25") + Decimal.parse("0.1")` == `Decimal.parse("0.35")`.
  - `Decimal.parse("1.0") / Decimal.parse("3.0")` does not throw, returns 16-digit HALF_EVEN result.
  - `Decimal.parse("0.250").toPlainString() == "0.25"` (trailing-zero strip).
  - 100% line + branch coverage on `core/Decimal.kt`.

### M1-02 · Implement `UnitCode` + unit families
- **Depends on**: M1-01.
- **Spec refs**: §3.0.3.
- **Description**: Sealed enum `UnitCode { MCG, MG, G, IU, ML, CAPSULE, TABLET, SCOOP, DROP }`. Implement `family: UnitFamily` property and `convertTo(target: UnitCode, value: Decimal): Decimal` for mass conversions only. Throw `IllegalArgumentException` on cross-family conversion.
- **Acceptance**:
  - `UnitCode.MG.convertTo(MCG, 1.dec)` returns 1000.
  - `UnitCode.CAPSULE.convertTo(TABLET, ...)` throws.
  - `UnitCode.IU.family == UnitFamily.IU` (own family).

### M1-03 · Implement `Quantity`
- **Depends on**: M1-02.
- **Spec refs**: §3.0.2, §3.0.4.
- **Description**: `@Immutable data class Quantity(val value: Decimal, val unit: UnitCode)`. Operators `plus/minus` require same unit. `times(scalar: Decimal)`. Add `toString` returning `"{value} {unit.lower}"`. Equality is structural.
- **Acceptance**:
  - `Quantity(0.25.dec, MG) + Quantity(0.1.dec, MG)` == `Quantity(0.35.dec, MG)`.
  - `Quantity(1.dec, MG) + Quantity(1.dec, MCG)` throws.
  - Stable across JSON round-trip (will be tested in M5).

### M1-04 · Implement `Concentration`
- **Depends on**: M1-03.
- **Spec refs**: §3.0.2.
- **Description**: `@Immutable data class Concentration(val amount: Quantity, val per: Quantity)`. Add `toString` returning `"{amount} / {per}"`.
- **Acceptance**: Equality structural. `toString` matches "2.5 mg / 1 mL" format.

### M1-05 · Implement typed `Quantity / Concentration` arithmetic
- **Depends on**: M1-04.
- **Spec refs**: §3.0.4.
- **Description**: Implement `operator fun Quantity.div(c: Concentration): Quantity`. Family-check `this.unit` family vs `c.amount.unit` family. Convert within family if needed. Result unit = `c.per.unit`.
- **Acceptance**:
  - `Quantity(0.25.dec, MG) / Concentration(Quantity(2.5.dec, MG), Quantity(1.dec, ML))` == `Quantity(0.10.dec, ML)`.
  - `Quantity(1.dec, IU) / Concentration(Quantity(100.dec, IU), Quantity(1.dec, ML))` == `Quantity(0.01.dec, ML)`.
  - Cross-family throws `IllegalArgumentException` with descriptive message.
  - Table-driven JUnit5 `@ParameterizedTest` covering ≥10 representative cases (mass, IU, count, mixed units, family mismatch).

### M1-06 · Implement validation helpers
- **Depends on**: M1-03.
- **Spec refs**: §8.
- **Description**: `:core:domain` `Validation.kt` with `EmptyResult<ValidationError>`-returning checks for each row in §8 (name length, quantity > 0, etc.) using the custom `Result<D, E : Error>` from M1-00a. Sealed `ValidationError : Error` hierarchy (e.g. `NAME_TOO_LONG`, `QUANTITY_NOT_POSITIVE`).
- **Acceptance**: Every validation rule in §8 has a unit test pair (pass + fail case).

---

## Milestone M2 — Room database schema

Goal: complete v1 Room schema with all entities, DAOs, FK rules, indexes, seed callback. No business logic.

### M2-01 · Convert types (TypeConverters)
- **Depends on**: M1-01, M0-09.
- **Spec refs**: §5.8.
- **Description**: Implement `RoomConverters` for `Instant ↔ Long`, `LocalDate ↔ String`, `LocalTime ↔ String`, `Decimal ↔ String` (canonical plain string), `UnitCode ↔ String`, all enums ↔ String.
- **Acceptance**: Round-trip unit test per converter.

### M2-02 · `compound_supply` entity + DAO
- **Depends on**: M2-01.
- **Spec refs**: §3.1, §5.8.1.1, §5.8.4.
- **Description**: Entity with flattened columns named exactly per §5.8.1.1. Indexes per §5.8.4. DAO with: `insert`, `update`, `softDelete(id, deletedAt)`, `observeActive()`, `observeById(id)`, `observeLowStock()`, `observeExpiringSoon(days)`.
- **Acceptance**: All DAO methods return correct rows; soft-delete excludes from `observeActive`.

### M2-03 · `opened_container` entity + DAO
- **Depends on**: M2-02.
- **Spec refs**: §3.1.1, §5.8.2, §5.8.3.
- **Description**: Entity with unique FK `compoundSupplyId → compound_supply.id ON DELETE CASCADE`. Flattened `remainingAmountValue/Unit`.
- **Acceptance**: Unique constraint enforced. Deleting parent cascades.

### M2-04 · `protocol` entity + embedded value objects
- **Depends on**: M2-02.
- **Spec refs**: §3.2, §5.8.1.1.
- **Description**: Entity with `@Embedded(prefix="schedule")` `ScheduleEmbed`, `@Embedded(prefix="escalation")` `EscalationEmbed?`, `@Embedded(prefix="break")` `ProtocolBreakEmbed?`. `selectedWeekdaysBitmask: Int` column. `siteCooldownDays: Int?`.
- **Acceptance**: Embeds round-trip through DAO without loss.

### M2-05 · `protocol_dosage_time` child table + DAO
- **Depends on**: M2-04.
- **Spec refs**: §5.8, §5.8.1.1, §5.8.3.
- **Description**: Child table `protocol_dosage_time(protocolId, time)`. Unique index `(protocolId, time)`. Single-column index on `time` for bucket query.
- **Acceptance**: Inserting duplicate `(protocolId, time)` fails with constraint violation.

### M2-06 · `scheduled_dose` entity + DAO
- **Depends on**: M2-04, M2-02.
- **Spec refs**: §3.3, §5.7, §5.8.1.1, §5.8.4.
- **Description**: Entity with `originalLocalDate`, `originalLocalTime?`, `originalZone` per §5.7. Unique `(protocolId, scheduledAt)`. Indexes per §5.8.4 including `scheduled_dose(administrationEventId)`.
- **Acceptance**: Idempotent insert via `INSERT OR IGNORE` on conflict.

### M2-07 · `administration_event` entity + DAO
- **Depends on**: M2-01.
- **Spec refs**: §3.4, §5.8.4.
- **Description**: Entity with `status` as enum text (Taken/Skipped/Partial only). FK `injectionSiteId → injection_site.id ON DELETE SET NULL`.
- **Acceptance**: Status enum rejects `Missed` at insert layer (validate before DAO).

### M2-08 · `dose_component` entity + DAO
- **Depends on**: M2-07, M2-06, M2-02.
- **Spec refs**: §3.5, §5.8.1.1.
- **Description**: Entity with `concentrationAmountValue/Unit`, `concentrationPerValue/Unit` (all nullable), `inventoryDeductedValue/Unit`. FKs per §5.8.2. Unique `scheduledDoseId` when non-null.
- **Acceptance**: Unique constraint blocks double-logging of one ScheduledDose.

### M2-09 · `injection_site` entity + DAO
- **Depends on**: M2-01.
- **Spec refs**: §3.6, §5.8.4.
- **Description**: Entity with `bodyRegion`, `side`, `sublocation`, `lastUsedAt`, `avoidUntil`, `isAvailable`. Index `(bodyRegion, side, isAvailable, avoidUntil)`.
- **Acceptance**: DAO supports `observeReadySites(now)`, `observeCoolingSites(now)`.

### M2-10 · `inventory_transaction` entity + DAO
- **Depends on**: M2-02, M2-07.
- **Spec refs**: §3.7, §5.8.1.1.
- **Description**: Append-only entity. DAO supports `insert`, `observeByCompound(compoundSupplyId)`, `sumDelta(compoundSupplyId)`.
- **Acceptance**: No update/delete methods exposed.

### M2-11 · `settings` entity + DAO
- **Depends on**: M2-01.
- **Spec refs**: §3.8.
- **Description**: Entity with `id = 1` enforced. DAO with `observe()` (returns single row), `update(...)` transactional.
- **Acceptance**: Insert with `id != 1` rejected.

### M2-12 · Database class wiring
- **Depends on**: M2-02..M2-11.
- **Spec refs**: §5.8.
- **Description**: `@Database` annotation listing all entities. `exportSchema = true`. Schema JSON written to `app/schemas/`. Bind DAOs to Koin module.
- **Acceptance**: Schema JSON committed to VCS. Migration tests directory bootstrapped.

### M2-13 · First-launch seed callback
- **Depends on**: M2-09, M2-11.
- **Spec refs**: §5.8.6.
- **Description**: `RoomDatabase.Callback.onCreate` inserts Settings singleton with defaults + 14 InjectionSite presets per §5.8.6.
- **Acceptance**: First app launch on fresh install: 14 sites + 1 settings row present. Re-launch: no duplicates.

### M2-14 · Pending-regen scope query
- **Depends on**: M2-06.
- **Spec refs**: §5.8.2 regen scope rule.
- **Description**: DAO `deletePendingUnloggedForProtocol(protocolId)` with WHERE `status='Pending' AND administrationEventId IS NULL`. Wrap in `@Transaction`.
- **Acceptance**: Logged scheduled doses untouched by this delete. Pending+unlogged removed.

---

## Milestone M3 — Domain models + repositories

Goal: domain layer + repository layer per §10.2. ViewModels never touch DAOs directly.

### M3-01 · Define domain models
- **Depends on**: M1-04.
- **Spec refs**: §3.1–§3.8, §10.1.
- **Description**: `domain/` package with one file per entity. Plain Kotlin data classes mirroring §3 with domain semantics (e.g. `Quantity` not Pair<String,String>). UI never sees Room entities.
- **Acceptance**: Domain classes compile without Room imports.

### M3-02 · Entity ↔ domain mappers
- **Depends on**: M3-01, M2-12.
- **Spec refs**: §10.2.
- **Description**: `data/mapper/` package, one `<Entity>Mappers.kt` per entity. Each provides `Entity.toDomain()` + `Domain.toEntity()`.
- **Acceptance**: Round-trip property tests; entity → domain → entity yields identical entity.

### M3-03 · `SettingsRepository`
- **Depends on**: M3-02, M0-04.
- **Spec refs**: §3.8, §10.2.
- **Description**: Repository wraps Settings DAO. Includes write-through to DataStore for `theme` + `dynamicColor` per §3.8 storage rule.
- **Acceptance**: Updating theme also writes to DataStore. DataStore mirror always lags ≤ 1 commit behind Room.

### M3-04 · `CompoundRepository`
- **Depends on**: M3-02, M3-03.
- **Spec refs**: §3.1, §5.3, §5.5.
- **Description**: Methods: `observeAll()`, `observeById(id)`, `create(...)`, `update(...)`, `archive(id)`, `duplicate(id)`, `openContainer(id)`, `closeContainer(id, reason)`, `editOpenedContainer(...)`. All transactional per §5.8.5.
- **Acceptance**: Each method has a Robolectric test verifying side-effects (inventory_transaction rows, opened_container state).

### M3-05 · `ProtocolRepository`
- **Depends on**: M3-04.
- **Spec refs**: §3.2, §5.2, §5.4.
- **Description**: CRUD + `pause/resume/complete/archive`. `save()` triggers ScheduledDose regen via `ScheduledDoseGenerator` (M9).
- **Acceptance**: Pending-regen scope rule honored.

### M3-06 · `ScheduledDoseRepository`
- **Depends on**: M3-05, M2-14.
- **Spec refs**: §3.3, §5.2.
- **Description**: Methods: `observePending(date, zone)`, `observeForProtocol(protocolId)`, `snooze(id, delta)`, `skip(id)`, `markMissed(id)`, `markTaken(id, eventId)`.
- **Acceptance**: Snooze updates `scheduledAt` + `originalLocalTime` stays unchanged.

### M3-07 · `AdministrationEventRepository`
- **Depends on**: M3-06, M3-04.
- **Spec refs**: §3.4, §3.5, §5.3, §5.8.5.
- **Description**: Methods: `log(event, components)`, `edit(id, edits)`, `delete(id)`. Implements inventory deduction + concentration snapshot capture + site cooldown updates per §5.3 — all inside one `@Transaction`.
- **Acceptance**: Inventory ledger balanced after each op (sum delta == compound state delta within tolerance).

### M3-08 · `InjectionSiteRepository`
- **Depends on**: M3-02.
- **Spec refs**: §3.6.
- **Description**: CRUD + `observeReady()`, `observeCooling()`, `suggestNext(protocol, route)` implementing rotation rule (oldest `lastUsedAt`, respecting `avoidUntil` and `siteRestriction`).
- **Acceptance**: Rotation suggestion deterministic for fixed input set.

### M3-09 · `InventoryRepository` (read-side aggregations)
- **Depends on**: M3-04, M3-05.
- **Spec refs**: §4.1.4, §4.3.2, §5.8.0.
- **Description**: Methods: `observeWarnings()`, `observeDosesLeftPerCompound()`, `observeRunOutDate(protocolId)`. Implements `dosesPerActualInjection` per §4.3.2.
- **Acceptance**: Aggregation correctness against fixture DB.

### M3-10 · Koin module wiring
- **Depends on**: M3-03..M3-09.
- **Spec refs**: §10.2, §10.4, `android-di-koin`.
- **Description**: In `:core:data`, define `coreDataModule = module { ... }` binding every repository via constructor reference: `singleOf(::RoomCompoundRepository) { bind<CompoundRepository>() }`, etc. Module registered in `KoinInitializer.create()` modules list per M0-09.
- **Acceptance**: `get<CompoundRepository>()` succeeds from a ViewModel scope. No `factory` or lambda overload used unless a non-constructor injection is required.

---

## Milestone M4 — Theming + design system

Goal: app paints in M3 Expressive theme with Google Sans Flex + Material Symbols Rounded, dynamic color, dark/light, motion spec wired.

### M4-01 · M3 Expressive color scheme + dynamic color
- **Depends on**: M0-04, M3-03.
- **Spec refs**: §2.3.6, §4.13.2, §9.
- **Description**: Build `StaxTheme` composable consuming `Settings.theme` + `Settings.dynamicColor` from `SettingsRepository`. Light + dark schemes. Dynamic color via `dynamicLightColorScheme` / `dynamicDarkColorScheme` on Android 16. Wrap content in **`MaterialExpressiveTheme`** so the expressive `MotionScheme` is provided app-wide (every M3 component animates expressively, §5.9) — not plain `MaterialTheme`.
- **Acceptance**: Toggle theme in settings → all surfaces recolor with `defaultEffectsSpec()` 300ms cross-fade (§5.9).

### M4-02 · Google Sans Flex typography
- **Depends on**: M4-01.
- **Spec refs**: §2.4, §9.
- **Description**: Bundle Google Sans Flex Regular/Medium/SemiBold/Bold/Light as fonts. Build M3 `Typography` with display/headline/title/body/label scales + `-emphasized` variants.
- **Acceptance**: Compose preview renders all scales correctly. `FontPreloadInitializer` measures cost (§2.3.4).

### M4-03 · Material Symbols Rounded icon assets + `Icon` accessor
- **Depends on**: M4-02.
- **Spec refs**: §2.4, §9.
- **Description**: Bundle the hand-picked Material Symbols Rounded **vector drawables** in `:core:design-system/src/main/res/drawable/` (`ic_<name>.xml`, plus `ic_<name>_filled.xml` for the 5 bottom-nav destinations). Provide a type-safe `StaxIcons` accessor mapping each to its `painterResource`, used via the `Icon` composable (tint from `LocalContentColor`). No icon font; no `material-icons-extended`. Seed the set from the icon list in this milestone's comments (home, medication, calendar_month, person_pin_circle, settings, add, add_circle, edit, delete, close, arrow_back, arrow_forward, chevron_right, expand_more, expand_less, more_vert, check, check_circle, done, done_all, search, search_off, schedule, today, history, calculate, straighten, colorize, science, vaccines, bolt, flag, block, pause, play_arrow, restart_alt, warning, error, notifications, dark_mode, light_mode, event_available, event_busy).
- **Acceptance**: Each icon renders in a Compose preview and tints with `LocalContentColor`. `StaxIcons` is the only way features reference icons (lint/review). **Missing-icon policy** (§9) is documented: a needed-but-absent icon is requested by Material Symbol name, never invented or pulled from `material-icons-extended`.

### M4-04 · Motion specs
- **Depends on**: M4-01.
- **Spec refs**: §5.9.
- **Description**: Provide `StaxMotion` object exposing `fastSpatialSpec()`, `defaultSpatialSpec()`, `defaultEffectsSpec()` from `MotionScheme.expressive()`. Provide helpers for syringe spring (damping 0.8, stiffness 380), shape morph (24r → 28r), day-chip cross-fade (200ms).
- **Acceptance**: Motion specs centralized; no inline `tween(300)` calls anywhere (lint rule).

### M4-05 · Shape scale
- **Depends on**: M4-01.
- **Spec refs**: §9.
- **Description**: Define M3 Expressive shape scale (extra-small → extra-large, plus pill 999r). Wire to `MaterialTheme.shapes`.
- **Acceptance**: All cards/buttons use shape tokens, not inline `RoundedCornerShape(...)`.

### M4-06 · Design tokens — semantic colors + raw-color lint
- **Depends on**: M4-01.
- **Spec refs**: §9.
- **Description**: `:core:design-system` `Tokens.kt` exposing **`StaxColors`** — semantic / domain colors that M3 does **not** provide as a role: dose status (taken / missed / skipped / partial), low-stock vs `error`, success, the heat-map gradient ramp (§4.12.4), body-map dot + syringe-fill colors. Each maps to a `MaterialTheme.colorScheme` role where one fits (e.g. `missed → error`, `skipped → outline`), or defines a custom color **only** where M3 has no suitable role. **Do NOT re-wrap standard M3 roles** (`primary`, `surfaceContainerLow`, `onSurfaceVariant`, …) — those are read directly from `MaterialTheme.colorScheme`. `Tokens.kt` is also the **single legal home for raw `Color(0xFF…)` literals**; move the fallback color-scheme seeds currently in `StaxTheme` here.
- **Acceptance**: A lint (`checkForbiddenColorApis` Gradle task — mirroring the `tween` / `RoundedCornerShape` guards — or a detekt rule) fails on any `Color(0xFF…)` literal outside `Tokens.kt`. `StaxColors` defines at least the dose-status + heat-map tokens and they are consumed via `StaxColors`, not raw colors. Standard M3 roles are read from `MaterialTheme.colorScheme`, never re-wrapped.

---

## Milestone M5 — Navigation + adaptive scaffolding

Goal: working `NavigationSuiteScaffold` with 5 destinations swapping between bottom nav / rail / expanded rail at breakpoints. Each destination shows a placeholder.

### M5-01 · Define Nav 3 typed routes + per-feature entry provider
- **Depends on**: M0-04.
- **Spec refs**: §10.3, `navigation-3`.
- **Description**: Per the `navigation-3` skill, for each feature presentation module create `Routes.kt` (one `@Serializable` `NavKey` route per screen) + an `EntryProviderScope.<feature>Entries(onNavigateToX, ...)` extension contributing that feature's `NavEntry`s to the `:app` `NavDisplay` `entryProvider`. No `NavController` / `NavHost` / `NavGraphBuilder`. Cross-feature navigation is expressed as lambda callbacks passed in from `:app`; feature modules must never import another feature's route. Decouple via the Nav3 **modular (Koin)** recipe.
- **Acceptance**:
  - `NavBackStack` is saveable and survives process death; route params reach each ViewModel via the `NavKey` passed to its entry (no `toRoute<T>()`).
  - Each feature presentation module exports exactly one `<feature>Entries` extension.
  - Detekt rule confirms no cross-feature route imports.

### M5-02 · Top-level NavigationSuiteScaffold
- **Depends on**: M5-01, M4-01.
- **Spec refs**: §4.0, §6.1, §6.4.1.
- **Description**: `MainScaffold` composable using `NavigationSuiteScaffold` (items as `NavigationSuiteItem`) with 5 destinations: Home / Compounds / Protocols / Sites / Settings, wrapping the `NavDisplay`. Each destination's icon = `StaxIcons` vector (Material Symbols Rounded) per §4.0 — outlined when unselected, `_filled` when selected. Hold a `rememberNavigationSuiteScaffoldState()` for hide-on-scroll chrome (§6.4.9).
- **Acceptance**: Bottom nav at <600dp; rail at 600+; rail expands at 840+.

### M5-03 · Per-destination back stacks
- **Depends on**: M5-02.
- **Spec refs**: §6.2, §6.4.5.
- **Description**: Each destination owns its own `NavBackStack` (Nav3 multiple-backstacks recipe). Re-tapping a nav item pops to root (§6.4.5).
- **Acceptance**: Compounds → Compound Detail → tap Compounds again → back at list. Re-tap → no-op (already at root). State retained per stack across config changes + process death.

### M5-04 · List-detail Scene strategy
- **Depends on**: M5-02.
- **Spec refs**: §6.4.2 Compounds + Protocols + Settings, `adaptive`.
- **Description**: Per the `adaptive` skill, wire a `ListDetailSceneStrategy` (`rememberListDetailSceneStrategy`) into `NavDisplay.sceneStrategies` and tag entries with `listPane(detailPlaceholder = { … })` / `detailPane()` metadata. Reusable helper applies list-pane widths (`360dp` Medium, `400dp` Expanded) + divider per §6.4.2. Do **not** use `ListDetailPaneScaffold`. Detail entries show no back arrow in two-pane mode.
- **Acceptance**: Selecting an item in Compact pushes detail; Medium+ swaps detail pane without push; empty selection shows the placeholder.

### M5-05 · Supporting-pane Scene strategy for Dashboard
- **Depends on**: M5-02.
- **Spec refs**: §6.4.2 Dashboard, `adaptive`.
- **Description**: Per the `adaptive` skill, wire a `SupportingPaneSceneStrategy` (`rememberSupportingPaneSceneStrategy`) into `NavDisplay.sceneStrategies` with `mainPane()` / `supportingPane()` entry metadata. Used by Dashboard Medium layout. Do **not** use `SupportingPaneScaffold`.
- **Acceptance**: Renders main + supporting at correct width ratios.

### M5-06 · FAB placement helper
- **Depends on**: M5-02.
- **Spec refs**: §6.4.6.
- **Description**: `AdaptiveFab` composable that places FAB at bottom-end on Compact and rail-top slot on Medium+.
- **Acceptance**: FAB transitions across breakpoints with animated position change.

### M5-07 · Foldable hinge detection
- **Depends on**: M5-04, M5-05.
- **Spec refs**: §6.4.3.
- **Description**: Wrap navigation roots in `WindowInfoTracker.windowLayoutInfo` collector. Expose `FoldingFeature?` via CompositionLocal. The Scene strategies consume it to align the pane divider to the fold.
- **Acceptance**: On Fold inner with vertical hinge, divider snaps to hinge x-coordinate.

### M5-08 · Predictive back
- **Depends on**: M5-04.
- **Spec refs**: §6.4.5.
- **Description**: Predictive back via Nav3 `NavDisplay` popping the `NavBackStack`; the active Scene strategy resolves the detail → list transition. Animate the predictive peek.
- **Acceptance**: System back gesture shows predictive peek animation on Pixel 10 Android 16.

### M5-09 · Edge-to-edge insets per pane
- **Depends on**: M5-04, M0-08.
- **Spec refs**: §2.3.6, `edge-to-edge`.
- **Description**: Each Scene pane consumes the correct `WindowInsets` slice (e.g. nav bar inset only on the bottom-most pane), one inset method per pane per the `edge-to-edge` skill.
- **Acceptance**: No double padding; no content under system bars.

---

## Milestone M6 — Onboarding + permission gate

### M6-01 · Onboarding step 1 (Welcome)
- **Depends on**: M5-02, M4-02.
- **Spec refs**: §4.14 step 1, §6.4.2 Onboarding.
- **Description**: Hero blob illustration (3 overlapping shapes) + headline + subtitle + Continue + Skip + step indicator pills.
- **Acceptance**: Compact + Medium + Expanded layouts per §6.4.2 with hero-left/content-right on Medium+.

### M6-02 · Onboarding step 2 (reuse Create Compound)
- **Depends on**: M6-01, M7-04.
- **Spec refs**: §4.14 step 2.
- **Description**: Reuses §4.4 with app bar adjusted ("Add your first compound · 2 of 3"). Skip in trailing app bar.
- **Acceptance**: Skipping advances to step 3 without saving.

### M6-03 · Onboarding step 3 (reuse Create Protocol)
- **Depends on**: M6-02, M9-03.
- **Spec refs**: §4.14 step 3.
- **Description**: Reuses §4.9. Skip allowed.
- **Acceptance**: Completion sets `Settings.onboardingCompleted = true` and navigates to Dashboard.

### M6-04 · Notification permission gate
- **Depends on**: M6-01.
- **Spec refs**: §4.15.
- **Description**: Adaptive screen with Allow CTA + Open Settings (only if permanently denied via `shouldShowRequestPermissionRationale = false`). Triggers `POST_NOTIFICATIONS` request flow.
- **Acceptance**: First launch shows gate after onboarding. On grant, proceeds. On deny, secondary path visible.

### M6-05 · Exact alarm permission rationale
- **Depends on**: M6-04.
- **Spec refs**: §5.1 exact alarm handling.
- **Description**: Detect `AlarmManager.canScheduleExactAlarms() = false`; show Settings warning row + "Enable exact reminders" CTA opening Alarms & Reminders settings. Listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- **Acceptance**: Toggling permission updates `Settings.exactAlarmDegraded` and reschedules pending alarms (post-M14).

---

## Milestone M7 — Compounds feature

### M7-01 · Compounds list ViewModel + state
- **Depends on**: M3-04, M3-09.
- **Spec refs**: §4.2, §10.1.
- **Description**: `CompoundsListViewModel` exposing `state: StateFlow<CompoundsListState>` with filter chip selections + search query + result list (UI models).
- **Acceptance**: Filter combinations match spec (All / Low stock / Expiring soon / Category / Form).

### M7-02 · Compounds list screen
- **Depends on**: M7-01, M5-04.
- **Spec refs**: §4.2, §6.4.2 Compounds.
- **Description**: Compose screen with filter chips, list, FAB. Search overlay (§4.0.1) wired to leading search icon.
- **Acceptance**: All Compact + Medium + Expanded layouts per §6.4.2 verified by Compose tests.

### M7-03 · Compounds multi-select mode
- **Depends on**: M7-02.
- **Spec refs**: §4.2.4.
- **Description**: Long-press enters multi-select; contextual app bar; bottom dock Duplicate + Archive. Bottom nav hidden during multi-select.
- **Acceptance**: Archive sets `deletedAt` on selected; Duplicate creates copies with " (copy)" suffix.

### M7-04 · Create / Edit Compound screen
- **Depends on**: M7-01, M1-06.
- **Spec refs**: §4.4, §6.4.2 Create Compound.
- **Description**: Adaptive form per §6.4.2 (single-col / two-col / wider two-col). Smart defaults per Form selection (§4.4.3). Discard confirm dialog. Auto-save draft on background.
- **Acceptance**: Validation per §4.4.4. Save behavior including `numberOfContainers` math (§4.4.4 worked example).

### M7-05 · Amount-per-container shrink dialog
- **Depends on**: M7-04.
- **Spec refs**: §4.4.4 Edit case.
- **Description**: On Save in Edit mode, if `amountPerContainer < currentOpened.remainingAmount`: show dialog with Keep / Cap / Cancel actions per §4.4.4. Cap option writes `InventoryTransaction { type=Manual, reason="Compound size reduced" }`.
- **Acceptance**: Each action produces correct DB state.

### M7-06 · Opened container bottom sheets
- **Depends on**: M7-04.
- **Spec refs**: §4.5.
- **Description**: Edit + Create-Already-Opened variants per §4.5. Adaptive width (Compact full-width; Medium clamped 560dp; Expanded side sheet 420dp per §6.4.2).
- **Acceptance**: Save behaviors covered including natural depletion prompt.

### M7-07 · Compound Detail screen
- **Depends on**: M7-04, M3-09.
- **Spec refs**: §4.3, §6.4.2 Compound Detail.
- **Description**: Stat strip, opened vial card, active protocols card, notes card, history list (Paging 3). Bottom dock Log dose + Adjust. Two-column internal layout at Expanded per §6.4.2.
- **Acceptance**: All sections render; chevron navigation works.

### M7-08 · Compound history paging
- **Depends on**: M7-07.
- **Spec refs**: §4.3.8, §2.3.1, §10.1.
- **Description**: `PagingSource` for AdministrationEvent rows filtered by compound, with status filter chip support.
- **Acceptance**: Scroll smooth at 60fps with 1000 history rows (per §2.3.2 SLO).

---

## Milestone M8 — Reconstitution Helper

### M8-01 · Reconstitution screen scaffold + ViewModel
- **Depends on**: M7-04.
- **Spec refs**: §4.6, §6.4.2 Reconstitution.
- **Description**: Screen with progressive disclosure (collapsed Mix + Dose ladder on Compact). ViewModel computes concentration + doses-per-container reactively.
- **Acceptance**: Live updates on diluent / desired-dose edits.

### M8-02 · Syringe visualization composable
- **Depends on**: M8-01, M4-04.
- **Spec refs**: §4.6.2, §4.6.8, §2.3.7.
- **Description**: Custom `Canvas` syringe renderer. Insulin (U-30/U-50/U-100) + regular (1/2/3/5 mL) sizes. Graduations + fill animation via spring (damping 0.8, stiffness 380). Size badge pill tap cycles size.
- **Acceptance**: Spring animation correct; tap on size badge cycles per §4.6.2.

### M8-03 · Equivalence chips + Dose ladder
- **Depends on**: M8-01.
- **Spec refs**: §4.6.3, §4.6.5.
- **Description**: Chips row + horizontal scrollable rungs (selected = `primary` 16r, others outlined 999r). Tap rung previews syringe fill.
- **Acceptance**: Default rungs computed per §4.6.5.

### M8-04 · Save concentration action
- **Depends on**: M8-01, M3-04.
- **Spec refs**: §4.6.7.
- **Description**: Save updates `compound.concentration` + regenerates volume display on all Pending ScheduledDoses for this compound. Returns to caller.
- **Acceptance**: Pending doses reflect new concentration; logged history unchanged.

### M8-05 · Adaptive Reconstitution layout
- **Depends on**: M8-02, M8-03, M5-04.
- **Spec refs**: §6.4.2 Reconstitution.
- **Description**: 2-col Medium (syringe sticky left; mix/result right), 3-col Expanded (syringe + mix table + result/ladder). Progressive disclosure default expanded at Medium+.
- **Acceptance**: Per-breakpoint layouts verified by Compose test.

---

## Milestone M9 — Protocols feature + ScheduledDose generation

### M9-01 · `ScheduledDoseGenerator`
- **Depends on**: M3-05, M2-14.
- **Spec refs**: §5.2, §5.7.
- **Description**: Generator producing 7-day-horizon Pending rows. Respects schedule type, dosageTimes, escalation, protocol break (in-break formula §3.2), endDate. Captures `originalLocalDate/Time/Zone` per §5.7. Idempotent via `INSERT OR IGNORE`.
- **Acceptance**: Unit-tested across all schedule types + escalation + break.

### M9-02 · Escalation rule engine
- **Depends on**: M9-01.
- **Spec refs**: §3.2 Escalation.
- **Description**: Compute current dose at any date from `Escalation` + cumulative dose count. `stopAtTarget` + `maxDose` respected.
- **Acceptance**: Property-based test across random schedules.

### M9-03 · Create / Edit Protocol screen
- **Depends on**: M3-05, M9-01.
- **Spec refs**: §4.9, §6.4.2 Create Protocol.
- **Description**: Form with all sections from §4.9.3. Compound picker via §4.0.2. Body region picker via §4.0.2. Live Forecast & warnings card, including 11b's next-7-days preview strip and reorder row. Edit-mode warning banner + Lifecycle section. Adaptive layouts per §6.4.2 (2-col Medium; 2-col Expanded with sticky Forecast inset). Requires the §4.0.2 picker sheet in `:core:design-system`, and the schedule rule (`Protocol.dosingTimesOn`, §5.2) in `:core:domain` — the live preview and forecast read it, and a feature module may not import `:core:data`.
- **Acceptance**: Save Create generates 7-day Pending rows; Save Edit calls Pending-regen scope rule.

### M9-04 · Pause-with-unsaved-changes flow
- **Depends on**: M9-03.
- **Spec refs**: §4.9.6.
- **Description**: Dialog "Save changes before pausing?" with Save+Pause / Pause without saving / Cancel.
- **Acceptance**: Each path produces correct DB state.

### M9-05 · Protocols list ViewModel + screen
- **Depends on**: M3-05.
- **Spec refs**: §4.7, §6.4.2 Protocols.
- **Description**: Tabs Active / Paused / Completed / Archived. Cards with status pill, schedule chips, next-dose chip, titration progress bar.
- **Acceptance**: Archived filter uses `deletedAt != null` per §4.7.2.

### M9-06 · Protocols multi-select mode
- **Depends on**: M9-05.
- **Spec refs**: §4.7.4.
- **Description**: Long-press → multi-select. Bottom dock Pause/Resume/Complete/Duplicate/Archive. Buttons disabled when selection incompatible.
- **Acceptance**: Each action correctly handles incompatible selections.

### M9-07 · Protocol Detail screen
- **Depends on**: M9-05, M3-09.
- **Spec refs**: §4.8, §6.4.2 Protocol Detail.
- **Description**: Quick action chips, Schedule card, Linked compound card, Inventory forecast, Site restrictions, Dose history, Notes, Bottom dock. Two-column internal layout at Expanded.
- **Acceptance**: All sections render; warning row shows when batch expires before run-out.

---

## Milestone M10 — Injection sites + body map

### M10-01 · Sites screen + ViewModel
- **Depends on**: M3-08.
- **Spec refs**: §4.12, §6.4.2 Sites.
- **Description**: Route filter chips, stats strip, body map hero, suggested site hero, recent activity carousel. Adaptive layouts per §6.4.2 including Expanded Front+Back side-by-side.
- **Acceptance**: All layouts render correctly per breakpoint.

### M10-02 · Body map vector renderer
- **Depends on**: M10-01.
- **Spec refs**: §4.12.4.
- **Description**: Custom `Canvas` drawing human silhouette (Front + Back). Dots at fixed normalized coordinates per preset site list (§5.8.6). Dot states (Suggested / Cooling / Recent / Available) per §4.12.4.
- **Acceptance**: Hit-test scales with canvas.

### M10-03 · Heat map mode
- **Depends on**: M10-02.
- **Spec refs**: §4.12.4 Heat map, §2.3.7.
- **Description**: Blurred ellipses via `RenderEffect.createBlurEffect()`. Opacity scales with usage frequency over last 30 days.
- **Acceptance**: Heat legend renders; Heat ↔ Dots toggle smooth.

### M10-04 · Site detail bottom sheet
- **Depends on**: M10-01.
- **Spec refs**: §4.12.8.
- **Description**: Stats row, recent uses, actions (View history / Mark unavailable). Adaptive: bottom sheet Compact; clamped Medium; side sheet Expanded per §6.4.2 Sites.
- **Acceptance**: Mark unavailable toggles `isAvailable`.

### M10-05 · Site picker full-screen flow
- **Depends on**: M10-01.
- **Spec refs**: §4.12.7.
- **Description**: Full-screen list, filter chips All/Ready/Cooling, Suggested section, full list. Bottom dock Cancel + Pick site.
- **Acceptance**: Returns selected site to caller via `SavedStateHandle`.

### M10-06 · Rotation suggestion algorithm
- **Depends on**: M3-08.
- **Spec refs**: §4.12.4 Suggested, §5.3 site cooldown.
- **Description**: Suggest oldest-used available site within `Protocol.siteRestriction`, honoring `avoidUntil`. Cooldown source order per §5.3.
- **Acceptance**: Unit-tested across rotation scenarios.

---

## Milestone M11 — Logging flows

### M11-01 · Take Dose bottom sheet (§4.10.1)
- **Depends on**: M3-07, M10-06.
- **Spec refs**: §4.10.1, §6.4.2 Take Dose.
- **Description**: Modal sheet with hero dose card + adjust chips + site card + when field + inventory preview + Confirm. Long-press Confirm shows note option. Adaptive width/side-sheet per §6.4.2.
- **Acceptance**: Save creates AdministrationEvent + DoseComponent + InventoryTransaction in one transaction (§5.8.5).

### M11-02 · Log Dose (Dashboard) form (§4.10.2-a)
- **Depends on**: M11-01.
- **Spec refs**: §4.10.2-a, §6.4.2 Log Dose.
- **Description**: Full-screen form with compound/protocol chip, planned/actual columns, route/when/site, inventory preview, Save.
- **Acceptance**: Marks linked ScheduledDose with correct status.

### M11-03 · Log Dose (Compound) form (§4.10.2-b)
- **Depends on**: M11-02.
- **Spec refs**: §4.10.2-b.
- **Description**: Manual mode with single Actual hero. Link-to-protocol row morphs to dual-column when set.
- **Acceptance**: Manual log produces DoseComponent with `protocolId = null`.

### M11-04 · Log Dose (Protocol) form (§4.10.2-c)
- **Depends on**: M11-02.
- **Spec refs**: §4.10.2-c.
- **Description**: Variant with protocol hero card + dual columns + meta chips.
- **Acceptance**: Prefilled with next Pending dose context.

### M11-05 · Log Grouped Event bottom sheet (§4.10.3)
- **Depends on**: M11-01.
- **Spec refs**: §4.10.3, §6.4.2 Other modal sheets.
- **Description**: Shared route/site/time pills + component rows + Add-another-compound. Validation: all components Injectable, route SC or IM, ≥2 components. Single AdministrationEvent created with N components in one transaction.
- **Acceptance**: Marks all linked ScheduledDoses Taken in one go.

### M11-06 · Edit dose (§4.10.4)
- **Depends on**: M11-01.
- **Spec refs**: §4.10.4.
- **Description**: Stripped form. Status segmented Taken/Skipped only (Partial deduced). Inventory side-effects per §4.10.4.
- **Acceptance**: Reversal/adjustment InventoryTransactions written; concentration snapshot used (§3.5).

### M11-07 · Administration Event detail screen (§4.11)
- **Depends on**: M11-01.
- **Spec refs**: §4.11.
- **Description**: Status hero, dose components, field rows, inventory effect, bottom dock Delete + Edit.
- **Acceptance**: Delete reverses inventory + resets linked ScheduledDose to Pending.

---

## Milestone M12 — Dashboard

### M12-01 · Dashboard ViewModel + state
- **Depends on**: M3-06, M3-07, M3-09.
- **Spec refs**: §4.1, §10.1.
- **Description**: State includes selected date, pending doses (UI models), inventory warnings, recent activity. Events: NavigateToTakeDose, ShowSnackbar. Follows the MVI Action/Event/onAction/Channel pattern from `android-presentation-mvi`.
- **Acceptance**: Handles all states (Default / Empty / All done / Grouped suggestion).

### M12-02 · Day chip strip
- **Depends on**: M12-01.
- **Spec refs**: §4.1.1, §6.4.2 Dashboard.
- **Description**: Horizontal scrollable strip with lazy load (load 7 chips on edge, recycle off-screen). Long-press opens Material Date Picker. Selected chip uses motion morph 999r → 20r per §5.9.
- **Acceptance**: Smooth scroll across months; chip window stays ~14 in memory.

### M12-03 · Dose cards
- **Depends on**: M12-01.
- **Spec refs**: §4.1.2.
- **Description**: Cards per Pending dose. First card uses `primary-container`. Action row Take/Snooze/Skip. ETA badge logic.
- **Acceptance**: Sorted per spec; without-time doses sort last.

### M12-04 · Swipe gestures on dose card
- **Depends on**: M12-03.
- **Spec refs**: §4.1.2 swipe gestures.
- **Description**: `SwipeToDismissBox`-style gesture. Threshold 40% width. Haptic on threshold cross. Right swipe = Take (opens sheet), Left = Skip (undo snackbar).
- **Acceptance**: Disabled on grouped suggestion card.

### M12-05 · Snooze submenu (standardized)
- **Depends on**: M12-03.
- **Spec refs**: §4.1.2 Snooze, §4.1 state 5.
- **Description**: Submenu 1h/3h/1d for timed, 1d only for untimed. Same submenu used by overflow menu.
- **Acceptance**: Both entry points reuse same composable.

### M12-06 · Grouped administration suggestion
- **Depends on**: M12-03, M11-05.
- **Spec refs**: §4.1 state 4, §4.10.3.
- **Description**: Hero replaces individual cards when grouping rule matches (30-min window timed; same-day untimed; mixed falls back to same-day per §4.1 state 4 corrected rule).
- **Acceptance**: Detects + collapses correctly across mixed timed/untimed cases.

### M12-07 · Inventory warnings + recent activity sections
- **Depends on**: M12-01.
- **Spec refs**: §4.1.4, §4.1.5.
- **Description**: Warning rows (error-container fill) + recent activity rows. Missed never appears in recent activity (per §4.1.5 correction).
- **Acceptance**: Taps navigate to Compound Detail / Administration Event detail.

### M12-08 · FAB direct-log + menu
- **Depends on**: M12-03, M11-02.
- **Spec refs**: §4.1.6, §4.1.7.
- **Description**: Single tap → direct Take Dose for next Pending (when exists); else FAB menu. Long-press always shows menu.
- **Acceptance**: Menu items navigate per §4.1.7.

### M12-09 · Adaptive Dashboard layout
- **Depends on**: M12-02..M12-08, M5-05.
- **Spec refs**: §6.4.2 Dashboard.
- **Description**: Compact = single column; Medium = supporting-pane Scene (`SupportingPaneSceneStrategy`, M5-05); Expanded = three-region grid with "Up next" rail.
- **Acceptance**: All three layouts pass Compose tests on profiles in §6.4.8.

---

## Milestone M13 — Notifications + alarms + workers

### M13-01 · Notification channels + builder
- **Depends on**: M3-03.
- **Spec refs**: §5.1.
- **Description**: Create `dose_reminders` + `warnings` channels at app start (idempotent). Builder for grouped dose notifications.
- **Acceptance**: Channels visible in system notification settings.

### M13-02 · `AlarmScheduler` with exact alarm fallback
- **Depends on**: M13-01, M6-05.
- **Spec refs**: §5.1 exact alarm handling.
- **Description**: Wraps `AlarmManager.setExactAndAllowWhileIdle`. Checks `canScheduleExactAlarms()` first. Catches `SecurityException` → degrade flag. Listens for permission-state-changed broadcast.
- **Acceptance**: Toggling Alarms & reminders permission updates scheduler state.

### M13-03 · `BootReceiver` + `AlarmReconcileWorker`
- **Depends on**: M13-02.
- **Spec refs**: §5.1.
- **Description**: BroadcastReceiver for `BOOT_COMPLETED` enqueues one-shot `AlarmReconcileWorker` re-scheduling all Pending alarms.
- **Acceptance**: After reboot, alarms restored.

### M13-04 · `GenerateScheduledDosesWorker`
- **Depends on**: M9-01.
- **Spec refs**: §5.1, §5.2, §2.3.8.
- **Description**: Daily PeriodicWorkRequest. Enqueued with `ExistingWorkPolicy.KEEP` + unique work name (per §2.3.8). Runs §5.2 generation.
- **Acceptance**: Idempotent; no duplicates across runs.

### M13-05 · `InventoryExpiryCheckWorker`
- **Depends on**: M9-01.
- **Spec refs**: §5.1.
- **Description**: Daily worker. Marks Pending → Missed when `scheduledAt + missedDoseWindowMinutes < now`. Recomputes inventory warnings.
- **Acceptance**: Missed transition correct; no AdministrationEvent created (per §3.4).

### M13-06 · `InventoryReconcileWorker`
- **Depends on**: M3-09.
- **Spec refs**: §5.8.0.
- **Description**: Daily worker. Compares ledger sum vs mutable state. Debug logs mismatch; release sets drift flag on Settings.
- **Acceptance**: Never auto-applies; only flags.

### M13-07 · Bucket alarm aggregation
- **Depends on**: M13-02.
- **Spec refs**: §5.1 bucket aggregation.
- **Description**: When multiple Pending doses share `(date, bucket)`, schedule one grouped notification listing N doses. Idempotent.
- **Acceptance**: Tap opens Dashboard filtered to today.

### M13-08 · Snooze + log + skip alarm cancellation
- **Depends on**: M13-02, M11-01.
- **Spec refs**: §5.1 reminder lifecycle.
- **Description**: Repository hooks cancel + reschedule alarms after relevant state changes. Alarm scheduling runs only after DB transaction commits (§5.8.5).
- **Acceptance**: No stale alarms after logging.

---

## Milestone M14 — Settings + Export/Import

### M14-01 · Settings screen + adaptive layout
- **Depends on**: M3-03, M5-04.
- **Spec refs**: §4.13, §6.4.2 Settings.
- **Description**: Section list + section detail (List-Detail Pane at Medium+). Appearance / Reminders / Data / About.
- **Acceptance**: All breakpoints per §6.4.2.

### M14-02 · Theme picker dialog
- **Depends on**: M14-01, M4-01.
- **Spec refs**: §4.13.2.
- **Description**: Centered dialog with 3 radio rows. Save updates `Settings.theme` → triggers `defaultEffectsSpec()` cross-fade via §5.9.
- **Acceptance**: Cross-fade animates; DataStore mirror updates.

### M14-03 · Reminders section rows
- **Depends on**: M14-01.
- **Spec refs**: §4.13.3.
- **Description**: Notification style choice dialog + time zone searchable dialog + Missed dose window numeric input + Exact alarm degraded warning row (per M6-05).
- **Acceptance**: TZ change triggers §5.7 re-anchor.

### M14-04 · Export JSON
- **Depends on**: M3-04..M3-09.
- **Spec refs**: §5.6, §5.8.7.
- **Description**: Background worker exporting all entities + soft-deleted rows. IDs compacted per table. Includes `schemaVersion`, app version, exportedAt, timezone.
- **Acceptance**: Round-trip export → import on empty DB yields identical state.

### M14-05 · Import JSON with FK remap
- **Depends on**: M14-04.
- **Spec refs**: §5.6, §5.8.7.
- **Description**: Full validation pass before any write. Preview dialog with row counts. ID remap per §5.6. All-or-nothing transaction. Post-import: ScheduledDose reconciliation + alarm reconciliation + warning recompute.
- **Acceptance**: schemaVersion-too-new rejected; preview shows correct counts.

### M14-06 · Reset all data
- **Depends on**: M14-01, M13-02.
- **Spec refs**: §4.13.4, §5.11.
- **Description**: Typed-confirm dialog ("Type RESET to confirm"). Executes the 6-step §5.11 reset sequence (cancel alarms + cancel workers + transactional wipe + explicit re-seed of Settings + 14 InjectionSite presets + DataStore mirror overwrite + widget refresh broadcast + worker re-enqueue).
- **Acceptance**: After reset, DB contains 1 Settings row + 14 InjectionSite rows + zero everything else. Widget repaints into empty state. `RoomDatabase.Callback.onCreate` is NOT relied on (no DB file recreation).

### M14-07 · Repair inventory flow
- **Depends on**: M13-06.
- **Spec refs**: §5.8.0 Repair flow.
- **Description**: Settings row appearing only when drift flag set. Preview dialog shows current vs ledger-derived state + per-compound delta. Confirm writes ledger-derived state + one `Manual` reconciliation transaction.
- **Acceptance**: Worker never auto-applies; only user-confirmed path triggers writes.

---

## Milestone M15 — Home-screen widget

### M15-01 · Glance widget skeleton
- **Depends on**: M3-06.
- **Spec refs**: §4.16.1.
- **Description**: `StaxWidget : GlanceAppWidget` with `SizeMode.Responsive` mapping to small/medium/large composables.
- **Acceptance**: Widget appears in launcher widget picker.

### M15-02 · Widget content states
- **Depends on**: M15-01.
- **Spec refs**: §4.16.2.
- **Description**: 4 states (next pending / all done / no doses / degraded). Each state composable per size.
- **Acceptance**: Each state visually verified on screenshot test.

### M15-03 · Widget actions (Take / Snooze / Open)
- **Depends on**: M15-02, M11-01.
- **Spec refs**: §4.16.4.
- **Description**: `ActionRunCallback`s resolving next Pending at click time. Take opens Activity deep-linked to Take Dose sheet. Snooze updates `scheduledAt` + reschedules.
- **Acceptance**: Both actions update widget after completion via `STAX_WIDGET_REFRESH` broadcast.

### M15-04 · Widget refresh broadcast plumbing
- **Depends on**: M15-03, M3-07.
- **Spec refs**: §4.16.5, §5.1.
- **Description**: Repository emits `STAX_WIDGET_REFRESH` LocalBroadcast after every committed AdministrationEvent + regen + TZ re-anchor + daily worker. Widget collector re-renders.
- **Acceptance**: No periodic poll.

### M15-05 · Widget theming + a11y
- **Depends on**: M15-02, M4-01.
- **Spec refs**: §4.16.3, §4.16.6.
- **Description**: GlanceTheme with dynamic color. Content descriptions per §4.16.6.
- **Acceptance**: TalkBack reads buttons correctly.

---

## Milestone M16 — App shortcuts

### M16-01 · Static shortcuts XML
- **Depends on**: M0-03.
- **Spec refs**: §4.17.1.
- **Description**: `res/xml/shortcuts.xml` with 4 entries per §4.17.1. Manifest meta-data on launcher Activity.
- **Acceptance**: Long-press app icon shows 4 shortcuts.

### M16-02 · Shortcut deep-link router
- **Depends on**: M16-01, M11-02, M8-01.
- **Spec refs**: §4.17.2.
- **Description**: `MainActivity.onCreate` reads `shortcutId` extra, pushes matching route onto Dashboard stack.
- **Acceptance**: All 4 shortcuts land on correct screen; back returns to Dashboard.

### M16-03 · log_next_dose fallback
- **Depends on**: M16-02.
- **Spec refs**: §4.17.2.
- **Description**: If no Pending dose at launch, fall back to §4.10.2-a Log Dose.
- **Acceptance**: Fallback engages when DB has no Pending.

---

## Milestone M17 — Accessibility audit

### M17-01 · Content descriptions audit
- **Depends on**: M7-02, M9-05, M10-01, M12-09.
- **Spec refs**: §5.10, §4.12.4.
- **Description**: Every icon/button/dot has `contentDescription`. Body map dots: "{site name}, {status}".
- **Acceptance**: Accessibility Scanner lint passes with zero issues on every screen.

### M17-02 · Status + color independence
- **Depends on**: M17-01.
- **Spec refs**: §5.10.
- **Description**: Every status communicated via icon + color, never color alone. Audit recent activity / dose card / site dot / status hero.
- **Acceptance**: Greyscale screenshot test: all statuses still distinguishable.

### M17-03 · TalkBack navigation order
- **Depends on**: M17-01.
- **Spec refs**: §5.10.
- **Description**: Focus order matches visual order. Pane scaffolds expose pane labels.
- **Acceptance**: Manual TalkBack QA pass per screen.

### M17-04 · Font scaling support
- **Depends on**: M4-02.
- **Spec refs**: §5.10 implicit.
- **Description**: Verify layouts at 100%, 150%, 200% font scale. No clipping; reflow not break.
- **Acceptance**: Screenshot test matrix passes.

---

## Milestone M18 — Performance hardening

### M18-01 · Compose stability metrics baseline
- **Depends on**: M12-09.
- **Spec refs**: §2.3.1.
- **Description**: Enable compose-compiler metrics output. Establish baseline `skippable` + `restartable` percentages from first build.
- **Acceptance**: CI fails if metrics regress below baseline.

### M18-02 · Stable annotations sweep
- **Depends on**: M18-01.
- **Spec refs**: §2.3.1.
- **Description**: Annotate UI models + value types per §2.3.1. Fix unstable params flagged by metrics.
- **Acceptance**: `skippable` rate ≥ baseline +5% post-sweep.

### M18-03 · Lazy list keys + Paging
- **Depends on**: M7-08.
- **Spec refs**: §P2, §4.3.8.
- **Description**: Audit LazyColumns: stable keys present, content types declared. PagingSource for all unbounded lists.
- **Acceptance**: 60fps scroll at 200 items SLO (§2.3.2).

### M18-04 · Baseline Profile generation
- **Depends on**: M12-09, M11-01.
- **Spec refs**: §2.3.3.
- **Description**: Macrobenchmark module with `BaselineProfileRule` running 4 hot paths from §2.3.3. Generated profile committed.
- **Acceptance**: Cold start delta measurable: <400ms vs >400ms before profile.

### M18-05 · SLO measurement harness
- **Depends on**: M18-04.
- **Spec refs**: §2.3.2.
- **Description**: Macrobenchmarks measuring each SLO row. Reports per release.
- **Acceptance**: All 5 SLOs hit on Pixel 10 reference device.

### M18-06 · Font cost measurement
- **Depends on**: M4-02.
- **Spec refs**: §2.3.4 FontPreloadInitializer.
- **Description**: Benchmark Google Sans Flex load time. If >40ms, switch to async preload with fallback. (Icons are vector drawables, not a font — no icon-font load to measure.)
- **Acceptance**: Decision recorded in `FontPreloadInitializer` with measurement evidence.

---

## Milestone M19 — Testing surface completeness

### M19-01 · Migration test scaffold
- **Depends on**: M2-12.
- **Spec refs**: §5.8.6, §10.5.
- **Description**: `MigrationTestHelper`-based test verifying v1 schema. Add new test per future version.
- **Acceptance**: CI runs migration tests on every PR.

### M19-02 · Domain math test suite
- **Depends on**: M1-05, M9-02.
- **Spec refs**: §10.5.
- **Description**: JUnit5 + AssertK suite covering Decimal math + Quantity arithmetic + Escalation + In-break + Inventory deduction. Property-based where feasible.
- **Acceptance**: ≥90% line coverage on `domain/`.

### M19-03 · ViewModel action → state + event tests
- **Depends on**: M12-01, M11-01.
- **Spec refs**: §10.5, `android-testing`, `android-presentation-mvi`.
- **Description**: Turbine-driven tests per ViewModel verifying that each `Action` produces the expected `State` mutation + emitted `Event` stream. Use fakes (over mocks). `Dispatchers.setMain(UnconfinedTestDispatcher())` in test setup.
- **Acceptance**: Every sealed `Action` case has at least one happy-path test. Every emitted `Event` is verified via `events.test { ... }`.

### M19-04 · Compose UI + screenshot tests per breakpoint
- **Depends on**: M5-04..M5-07.
- **Spec refs**: §6.4.8, §6.4.9, §10.5, `testing-setup`, `adaptive`.
- **Description**: For every top-level screen, one behavior test per profile from §6.4.8 (10 profiles × ~12 screens = ~120 tests) using `DeviceConfigurationOverride` + `WindowLayoutInfoPublisherRule`. **Plus** a `@PreviewTest` `@FormFactorPreviews` screenshot test per major screen (Phone / Foldable / Tablet / Desktop) via the Compose Preview Screenshot Testing tool / Roborazzi, so Nav3 Scene-strategy layout regressions are caught as golden diffs. Reference images regenerated only on intentional UI change.
- **Acceptance**: CI shards complete in <10 min. Screenshot diffs gate the PR; reference set committed.

### M19-05 · Hinge posture tests
- **Depends on**: M19-04.
- **Spec refs**: §6.4.3, §6.4.8.
- **Description**: Parameterized tests with `FoldingFeature.State.HALF_OPENED` × VERTICAL / HORIZONTAL on fold profiles.
- **Acceptance**: Take Dose + Reconstitution + Log Dose render bottom-half-controls in tabletop posture.

### M19-06 · E2E happy-path Macrobenchmark
- **Depends on**: M18-04.
- **Spec refs**: §10.5.
- **Description**: Macrobenchmark: launch → onboarding skip → Dashboard → swipe-Take → confirm → snackbar. Asserts SLOs.
- **Acceptance**: Runs on every release tag.

---

## Milestone M20 — Release prep

### M20-01 · R8 + ProGuard rules
- **Depends on**: M19-04.
- **Spec refs**: §2.3, §2.3.9, `r8-analyzer`.
- **Description**: Enable R8 for release per the `r8-analyzer` skill: AGP `9.0`+, R8 **full mode** ON (no `android.enableR8.fullMode=false`). Minimal keep rules for Room/Glance/Koin/kotlinx-serialization + reflection-touched Compose surfaces; run the **R8 configuration analyzer** to drop redundant / overly broad package-wide rules and avoid subsuming library consumer keep rules. Verify shrunk APK runs all hot paths (smoke run of §2.3.3 hot paths on release build). Measure APK size + record in `apk_size.txt` committed to repo; track release-over-release as a measurement, not a hard gate.
- **Acceptance**: Release APK launches and exercises hot paths without R8-induced runtime failures. Keep-rule audit report attached; no redundant/over-broad rules remain. `apk_size.txt` updated. Cold start SLO from §2.3.2 still hit on Pixel 10 reference device. No hard size cap (Compose + M3 + fonts + Glance realistically lands 15–25 MB; treat regressions >10% as review-worthy, not auto-blockers).

### M20-02 · Signing config + Play Console upload
- **Depends on**: M20-01.
- **Spec refs**: none (process).
- **Description**: Keystore config via secure CI secrets. Internal track upload via `play-publisher` Gradle plugin.
- **Acceptance**: First internal track build available.

### M20-03 · Privacy policy + Data Safety section
- **Depends on**: none.
- **Spec refs**: §2.1.
- **Description**: Privacy policy declaring "all data local; no network". Data Safety form filled accordingly.
- **Acceptance**: Play Console Data Safety passes review.

### M20-04 · Local diagnostics + crash log
- **Depends on**: M20-01.
- **Spec refs**: §2.1, §5.8.0.
- **Description**: App stays strictly offline (§2.1) — no Crashlytics, no Firebase, no network calls. Implement a local rolling diagnostic log file (`diagnostics.log`, ≤ 1 MB, rotates 3 files) capturing: uncaught exceptions via `Thread.setDefaultUncaughtExceptionHandler`, inventory drift events from §5.8.0, alarm scheduling failures from §5.1. Log entries are timestamped + tagged + free of PII (never write compound names, dose values, or notes). Settings § 4.13.4 Export JSON optionally includes the log; user opts in per export.
- **Acceptance**: No network calls from release build (verified via OkHttp/HTTPS interception lint test). Crash on test build appears in `diagnostics.log`. No compound names or dose values in log content.

### M20-05 · Release checklist
- **Depends on**: M20-01..M20-04.
- **Spec refs**: all.
- **Description**: Markdown checklist: schemaVersion bumped if any DB change; baseline profile regenerated; SLO macrobenchmarks pass; a11y scanner clean; widget + shortcuts manual smoke; export/import round-trip on full DB fixture.
- **Acceptance**: Checklist run before every release tag.

---

## Cross-cutting issues (always-on, no specific milestone)

### X-01 · Strict mode + leak detection (debug)
- **Description**: StrictMode `detectAll().penaltyDeath()` in debug. LeakCanary attached. Fix all leaks before merging features.
- **Acceptance**: CI debug build runs without StrictMode violations.

### X-02 · CI matrix
- **Description**: GitHub Actions matrix: unit tests + lint + ktlint + detekt + Compose metrics regression + assemble debug + macrobenchmark dry-run.
- **Acceptance**: All checks required before merge.

### X-03 · Lint rules custom
- **Description**: Custom lint: forbid `Color(0xff...)` outside Tokens; forbid `tween(` outside StaxMotion; forbid `Double`/`Float` in domain/data; forbid raw `Room` access from `feature/` packages.
- **Acceptance**: CI fails on rule violation.

### X-04 · Documentation sync
- **Description**: Whenever an issue is closed, update `detailed-spec.md` if implementation revealed a gap. Spec is canonical.
- **Acceptance**: Spec PR linked from every issue closure that changes behavior.

### X-05 · Per-module `CLAUDE.md` + `AGENT.md` symlink
- **Description**: Every Gradle module owns a top-level `CLAUDE.md` plus an `AGENT.md` symlink → `CLAUDE.md`, and the repo root owns one too. Purpose: orient an AI agent (or human reading cold) in under 30 seconds before they touch code. `CLAUDE.md` is auto-loaded by Claude Code when working in that subtree; `AGENT.md` is the cross-tool alias for other agents. Required sections (per spec §10.6):
  1. **Purpose** — one paragraph: what this module does and why it exists.
  2. **Module coordinates** — Gradle path, package namespace, convention plugins applied.
  3. **Allowed dependencies** — list of modules this one may depend on, citing the dependency-rules table in Conventions.
  4. **Key types** — bullet list of the most important public symbols + one-line role.
  5. **Applicable skills** — which reference skills (Skill alignment table) govern work in this module.
  6. **Owned by** — for `:feature:*` modules, the feature name; for `:core:*`, "shared".
  7. **Notes** — Stax-specific divergences from any skill, gotchas, perf budgets, transactional boundaries.
- **Rule**: any PR that adds or removes a public type in a module MUST update its `CLAUDE.md`. The `AGENT.md` symlink is created once and never hand-edited. CI runs `scripts/check_docs_drift.sh` which fails if a module's public API changed without a `CLAUDE.md` diff in the same PR, or if an `AGENT.md` symlink is missing / not pointing at `CLAUDE.md`.
- **Acceptance**:
  - Root + every module has a `CLAUDE.md` with an `AGENT.md` symlink → `CLAUDE.md`.
  - `scripts/check_docs_drift.sh` exists and blocks drift + missing symlinks in CI.

### X-06 · Per-package KDoc via `_Package.kt`
- **Description**: Every package within a module owns a single `_Package.kt` file containing only KDoc on the `package` declaration. Kotlin has no `package-info.java`; this is the idiomatic substitute. Required content (1–3 sentences):
  1. **Purpose** — what lives here.
  2. **Boundaries** — what does NOT live here (e.g. "no Room imports", "no Compose imports").
  3. **Entry points** — the 1–3 public symbols an outsider is most likely to touch.

  File template:
  ```kotlin
  /**
   * Domain models for compound supply and inventory state.
   *
   * Contains [CompoundSupply], [OpenedContainer], [InventoryTransaction], and the
   * [CompoundRepository] interface. Implementations live in `:core:data`.
   *
   * Boundaries: pure Kotlin, no Android imports, no framework imports.
   */
  @file:Suppress("MatchingDeclarationName")

  package com.stax.core.domain.compound
  ```
- **Rule**: any PR that introduces a new package MUST add its `_Package.kt`. Any PR that materially changes a package's public API (new public symbol, moved symbol, removed symbol) MUST update the relevant `_Package.kt`. Lint rule `MissingPackageKDoc` (detekt custom) enforces presence.
- **Acceptance**:
  - Custom detekt rule `MissingPackageKDoc` exists and fails on any package with ≥1 source file but no `_Package.kt`.
  - All seed packages introduced through M0 + M1 + M2 ship with `_Package.kt`.

---

## Dependency graph summary

```
M0 (bootstrap)
 └─> M1 (value types) ──┐
 └─> M4 (theming)       │
       └─> M5 (nav)     │
M1 ──> M2 (DB) ─────────┤
M2 + M1 ──> M3 (repos) ─┤
M3 + M5 ──> M6 (onboarding) ──> M7 (compounds) ──> M8 (reconstitution)
                                     │
                                     └──> M9 (protocols) ──> M10 (sites) ──> M11 (logging)
                                                                                  │
                                                                                  └──> M12 (dashboard)
                                                                                            │
M3 ──> M13 (notifications) <──────────────────────────────────────────────────────────────┘
M3 + M14-01 ──> M14 (settings + export)
M3 + M11 ──> M15 (widget)
M0 ──> M16 (shortcuts)
all ──> M17 (a11y) ──> M18 (perf) ──> M19 (tests) ──> M20 (release)
```

---

## Issue sizing

Aim per issue: **one PR, <800 lines diff, <2 days for one focused dev or AI agent**. Issues exceeding that should be split.

If an AI agent reads spec + issues end-to-end and outputs working app, all of the following must already be unambiguous:
- Every screen's layout per breakpoint.
- Every DB column name and FK rule.
- Every transaction boundary.
- Every motion spec.
- Every accessibility expectation.
- Every test profile.

If something is ambiguous, the spec — not the issue — is the bug. Fix the spec first.
