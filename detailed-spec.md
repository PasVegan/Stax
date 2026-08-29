# Stax — Peptide / Supplement / Hormone Tracker — App Spec v2

## 1. Overview

Material 3 Expressive Android app. Tracks peptides, supplements, hormones, injectables, topicals. Core features: compound inventory, protocol scheduling, dose logging (single + multi-compound injections), injection site rotation w/ heat map, reconstitution helper w/ syringe visualization, expiry/stock warnings, JSON export/import. Local-first, fully offline.

Target form factors: regular phones + foldables only. **No** desktop/ultra-wide monitor. Optimize for **compact** (<600dp) + **medium** (600-839dp) + **expanded** (840–1199dp) breakpoints.

Android 16+ application

---

## 2. Principles

### 2.1 Local-first
- Fully offline. Room database. No accounts, no network.
- All user data exportable + importable as JSON.

### 2.2 Use simplicity
- Every common action ≤2 taps from Dashboard.
- Never ask for data app already knows or can derive.
- Sensible defaults: actual dose = planned, suggested site from rotation, route from protocol, time = now.
- No multistep wizards when single screen + smart defaults works.

### 2.3 Performance
- Cold start to interactive: <400ms. Use Baseline Profiles + App Startup library.
- 16ms frame budget. No blocking I/O on main thread.
- Heavy work (export, ScheduledDose generation, inventory recalc) → coroutines + WorkManager off-main.
- Battery-aware: no foreground services. AlarmManager + WorkManager only.
- Beautiful UI non-negotiable: animations, M3 Expressive motion, dynamic color, shaders stay. Performance = doing them correctly (hardware-accelerated, hoisted state, stable composables) not removing them.
- `remember` for expensive calcs. Lazy layout keys. `derivedStateOf` to limit recomposition. Defer reads. Avoid backwards writes. Flatten hierarchy.

#### 2.3.1 Compose stability rules

Strong skipping mode is on by default — annotations are only required when fields are genuinely unstable.

- Composables receive **UI models**, never domain models directly. Domain models live in `:core:domain` + repositories + ViewModels; ViewModels map domain → UI model when emitting state. UI model `data class` fields = primitives + value classes + immutable collections only.
- `@Stable` on a UI-model `data class` only when it contains unstable fields (`List`, `Map`, `Set`, interfaces, abstract types). Primitive-only state classes need no annotation.
- `@Immutable` on shared read-only value types reused inside UI models: `Quantity`, `Concentration`, `Decimal`, `UiText`.
- No `remember` / `rememberSaveable` for app state — all app state lives in the ViewModel and is collected via `collectAsStateWithLifecycle()`. Only Compose-internal state (`LazyListState`, `ScrollState`, `PagerState`, etc.) uses `remember*`.
- Animate via `graphicsLayer` (alpha, scale, rotation, translation) + deferred state reads (`offset { provider() }`). Avoid recompositions during animation.
- Slot APIs (`@Composable () -> Unit` parameters) are reserved for design-system components in `:core:design-system`. Feature composables prefer typed parameters.
- Every Screen composable ships with a meaningful `@Preview` wrapped in `StaxTheme`.
- Hoist state. Pass lambdas, never callbacks-of-callbacks.
- `derivedStateOf` for filtered/computed list slices.
- `remember(key) { ... }` for any composable-local expensive calc.
- Use `SnapshotStateList` instead of `MutableState<List<T>>`.
- `snapshotFlow` only for actual snapshot reads, not general state.
- Enable Compose Compiler metrics in CI; track `skippable` + `restartable` rates per release. Baseline set after first profile pass, regress-only.

#### 2.3.2 Performance SLOs

| Scenario                                   | Target                       |
|--------------------------------------------|------------------------------|
| Dashboard cold load (no doses today)       | <300ms after Activity ready  |
| Dashboard cold load (10 pending doses)     | <400ms                       |
| Save dose (Confirm tap → snackbar visible) | <100ms                       |
| Compound list scroll                       | 60fps sustained at 200 items |
| Body map first frame                       | <200ms                       |

SLOs are targets, not gates pre-profile. Measure after first Baseline Profile pass before locking.

#### 2.3.3 Baseline Profile hot paths
Profile these flows:
- App start → Dashboard scroll
- Dashboard → Compound Detail
- Dashboard → Take Dose sheet open + save
- Dashboard → FAB menu → Log dose

#### 2.3.4 App Startup initializers — eager vs deferred
Eager init fights the <400ms cold-start SLO. Split:

**Eager** (must complete before first frame):
1. `KoinInitializer` — DI graph
2. `ThemeInitializer` — reads a tiny **DataStore cache** of theme-critical fields (`theme`, `dynamicColor`) only. Room remains the authoritative store for the full `Settings` entity (§3.8); the DataStore cache is written through whenever those two fields change. This lets first-frame avoid touching Room.

**Deferred** (lazy reference / first-use):
3. `RoomDatabaseInitializer` — declare reference only; real connection on first DAO call
4. `WorkManagerInitializer` — custom config registration; periodic workers enqueued post-first-frame on `Lifecycle.STARTED`
5. `FontPreloadInitializer` — measure Google Sans Flex cost before deciding eager vs async. If >40ms on a mid-tier device, load async with a Compose `FontFamily` fallback for first frame, swap when ready. (Icons are vector drawables, not a font — nothing to preload there.)

#### 2.3.5 Storage / SQLite
- Room journal mode = `WRITE_AHEAD_LOGGING`. Enables concurrent reads during background-worker writes.
- `PRAGMA foreign_keys=ON` (Room default).

#### 2.3.6 Edge-to-edge
Follow the `edge-to-edge` skill.
- `enableEdgeToEdge()` before `setContent` in every `Activity.onCreate`.
- `android:windowSoftInputMode="adjustResize"` in the manifest for every Activity that shows a soft keyboard (text fields).
- All padding derived from the framework insets (status bars, navigation bars, display cutout, IME). No hardcoded inset dimensions anywhere.
- Apply insets with exactly **one** method per surface (inset-padding OR ruler-alignment) — never both, to avoid double padding. Lists + FAB must not be obscured by the nav bar; text fields must stay visible above the IME.
- **Per-pane insets (§6.4, M5-09).** A `NavDisplay` entry *is* a Scene pane, and the adaptive Scene strategies propagate no insets of their own — so each pane claims its own, exactly once, at its content root via `Modifier.paneInsets()` from `:core:design-system` (inset padding: `safeDrawingPadding()`, which covers system bars + cutout + IME in one method).
  - Inset padding, **not** ruler alignment. `fitInside(WindowInsetsRulers.SafeDrawing.current)` resolves by *position* — `Ruler.calculateCoordinate` maps the value through `localPositionOf`, which includes every `graphicsLayer` transform between the window root and the pane. §6.4.5's entry transitions are `graphicsLayer` scale animations, so a pane reads its rulers through a `0.92` scale and lands short of the status bar; and because a layer property settling back to `1.0` triggers no relayout, the stale value is never re-read and the pane keeps the wrong inset for the rest of its life. Inset values are transform-independent, so they survive any entry animation.
  - Double padding is still structurally impossible: `safeDrawingPadding` **consumes** what it applies, so a nested `paneInsets`, the padding `NavigationSuiteScaffold` already consumed for its chrome, and a self-insetting Material component (`TopAppBar`, `SearchBar`, `ModalBottomSheet`) underneath a pane all resolve to zero.
  - **A pane that opens with its own top app bar passes `paneInsets(claimTop = false)`** and lets the bar take the status bar through its own `windowInsets`. Material applies those insets *inside* the bar's `Surface`, so the bar's container colour draws behind the status bar — which is what edge-to-edge is meant to look like. Claiming the top at the pane instead stops the bar short and strands a strip of page background under the status icons; invisible wherever `surface` happens to equal the page, obvious on any dark or dynamic-colour scheme where it does not. The pane still claims the sides and the bottom, so the bar's own horizontal insets resolve to zero underneath it.
  - The trade-off accepted here: consumption-based padding gives each pane the whole window inset, so in a side-by-side split with a landscape cutout both panes inset their inner edge. That is a few dp of dead space on the divider, against a class of frozen-inset bugs that rulers make unavoidable under animated panes.
  - Every other `WindowInsets` API is banned outside `:core:design-system`, enforced by the `stax:NoWindowInsetsOutsideDesignSystem` detekt rule (`:detekt-rules`).
- System-bar legibility: rely on the framework's adaptive bar-icon contrast; do not hardcode bar colors.

#### 2.3.7 RenderEffect blur
Heat-map (§4.12.4) uses `RenderEffect.createBlurEffect()` via `Modifier.graphicsLayer { renderEffect = ... }`. No CPU-blur fallback needed — app is Android 16+.

#### 2.3.8 Background contention
`GenerateScheduledDosesWorker` may run while user is in the Take Dose sheet. Serialize with `ExistingWorkPolicy.KEEP` + unique work name. DB transactions already isolated per §5.8.5; this prevents duplicate enqueue.

#### 2.3.9 R8 / app optimization
Release build optimization follows the `r8-analyzer` skill (see M20-01).
- AGP `9.0`+ (build-time + optimization improvements); R8 **full mode** ON (do **not** set `android.enableR8.fullMode=false`).
- Audit keep rules with the R8 configuration analyzer: remove redundant + overly broad package-wide `-keep` rules; do not subsume library consumer keep rules. Keep only the minimum reflection-touched surfaces (Room/Glance/Koin/kotlinx-serialization).

### 2.4 Technology
- Kotlin + Jetpack Compose + Material 3 Expressive
- MVI architecture, Koin DI
- **Google Sans Flex** font family (Regular, Medium, SemiBold, Bold, Light)
- **Material Symbols Rounded** for icons — hand-picked **vector drawables** in `:core:design-system/src/main/res/drawable/` (`ic_<name>.xml`), rendered with the `Icon` composable via a type-safe `StaxIcons` accessor. **No icon font, no `material-icons-extended`.** Missing icon → request it, never invent/substitute (icon policy in §9)
- Room database, WorkManager (background), AlarmManager (exact reminders)
- Navigation 3 (`NavDisplay` + `entryProvider` + `NavBackStack`), adaptive: bottom nav (compact), side rail (medium/foldables unfolded). Nav chrome via `NavigationSuiteScaffold`; multi-pane via Nav3 Scene strategies (`ListDetailSceneStrategy`, `SupportingPaneSceneStrategy`) — do not use the `*PaneScaffold` composables. Libs: `androidx.compose.material3.adaptive:adaptive-navigation3` (Scene strategies) + `:adaptive-layout` (backs them + `WindowSizeClass`). Per-feature navigation follows the `navigation-3` skill; adaptive layout follows the `adaptive` skill.
- **Material 3 components** for the whole design system (stable). Adaptive multi-column lists use the stable `GridCells.Adaptive`; the experimental `Grid` / `FlexBox` / `MediaQuery` APIs (Compose `1.11.0-beta01`+, opt-in) are optional and adopted only where a stable API can't express the layout.
- Glance for home-screen widgets (§4.16): `androidx.glance:glance-appwidget`, `androidx.glance:glance-material3`.
- Static app shortcuts via `<shortcuts>` XML (§4.17). No `androidx.sharetarget` needed at v1.
- Tooling: use the `android` CLI (project creation, run/deploy, SDK management, device screenshots, env diagnostics) per `android-cli` skill.
- only support android 16 and above, to be able to use all the new features. `compileSdk = 37` (required by `androidx.compose.material3.adaptive:adaptive-navigation3` ≥`1.3.0-beta02`); `minSdk = targetSdk = 36` (Android 16).
---

## 3. Domain Model

### 3.0 Quantities and units

Dose, volume, inventory, and concentration values are never represented as bare numbers in domain code.

#### 3.0.1 `Decimal` type

`Decimal` is a Kotlin `@JvmInline value class` wrapping `java.math.BigDecimal`.

```kotlin
@JvmInline
value class Decimal(val raw: BigDecimal) : Comparable<Decimal> {
    operator fun plus(o: Decimal): Decimal = Decimal(raw.add(o.raw))
    operator fun minus(o: Decimal): Decimal = Decimal(raw.subtract(o.raw))
    operator fun times(o: Decimal): Decimal = Decimal(raw.multiply(o.raw))
    operator fun div(o: Decimal): Decimal = Decimal(raw.divide(o.raw, MATH))
    override operator fun compareTo(o: Decimal): Int = raw.compareTo(o.raw)
    fun toPlainString(): String = raw.stripTrailingZeros().toPlainString()
    companion object {
        val MATH: MathContext = MathContext.DECIMAL64   // HALF_EVEN, 16 digits
        fun parse(s: String): Decimal = Decimal(BigDecimal(s))
    }
}
```

- Division uses `MathContext.DECIMAL64` (HALF_EVEN, 16 digits). Avoids `ArithmeticException` on non-terminating quotients (e.g. `1 / 3`).
- Persisted form is **canonical plain string**: `stripTrailingZeros().toPlainString()`. Ensures `"0.25"` ≠ `"0.250"` storage-equality collisions never happen.
- Never use `Double` or `Float` for dose math.

#### 3.0.2 `Quantity` and `Concentration`

```
Quantity:
value: Decimal
unit: UnitCode                           // mcg | mg | g | IU | mL | capsule | tablet | scoop | drop
```

```
Concentration:
amount: Quantity                         // e.g. 2.5 mg
per: Quantity                            // e.g. 1 mL or 1 tablet
```

#### 3.0.3 Unit families

| Family | Units                        | Convertible within family           |
|--------|------------------------------|-------------------------------------|
| Mass   | mcg, mg, g                   | yes (1 g = 1000 mg = 1_000_000 mcg) |
| Volume | mL                           | n/a (single unit)                   |
| Count  | capsule, tablet, scoop, drop | no (each is its own atom)           |
| IU     | IU                           | only if compound defines one        |

- Insulin units are display-only, derived from mL and syringe scale.
- Stored decimal values are not rounded for persistence. UI may round for display.

#### 3.0.4 Typed arithmetic

```kotlin
// Dose / Concentration → Volume (or count, when per-unit is count form)
operator fun Quantity.div(c: Concentration): Quantity
  // Requires this.unit family == c.amount.unit family.
  // Result value = this.value / c.amount.value (converted to compatible base units).
  // Result unit  = c.per.unit.
  // Throws IllegalArgumentException on family mismatch.

// Same-unit arithmetic
operator fun Quantity.plus(o: Quantity): Quantity   // requires same unit
operator fun Quantity.minus(o: Quantity): Quantity  // requires same unit
operator fun Quantity.times(scalar: Decimal): Quantity
```

Worked examples:
- `0.25 mg / (2.5 mg / 1 mL)` → `0.10 mL`
- `1 IU / (100 IU / 1 mL)` → `0.01 mL`
- `1 capsule / (50 mg / 1 capsule)` → not allowed; capsules are the atomic unit. Use `actualDose = 1 capsule` directly.

### 3.1 CompoundSupply
```
id: Long
name: String                            // required, ≥1 char
category: Peptide | Supplement | Hormone | Medication
form: Injectable | Capsule | Tablet | Powder | Liquid | Topical
containerType: Vial | Bottle | Blister | Packet | Tub | Ampoule
primaryUnit: UnitCode                    // mcg | mg | g | IU | mL | capsule | tablet | scoop | drop
amountPerContainer: Quantity             // e.g. 5 mg, 60 capsules
concentration: Concentration?            // e.g. 2.5 mg / 1 mL, 1 IU / 1 tablet
numberOfContainers: Int                 // unopened containers; total containers = numberOfContainers + 1 if currentOpened != null
currentOpened: OpenedContainer?         // at most one
batchExpiryDate: LocalDate?
expiryAfterOpeningDays: Int?            // template; copies into OpenedContainer on open
storageLocation: Fridge | RoomTemp | Freezer
batchNumber: String?
supplier: String?
notes: String?
deletedAt: Instant?                     // soft-delete
createdAt: Instant
updatedAt: Instant
```

### 3.1.1 OpenedContainer
```
openedAt: Instant                       // required
remainingAmount: Quantity                // required, same unit family as compound.primaryUnit
expiryAfterOpeningDays: Int?            // copy of compound's, mutable per-container
userDefinedExpiryDate: LocalDate?       // manual override; wins if set
predictedExpiryDate: LocalDate?         // derived: openedAt.date + expiryAfterOpeningDays
```

**Rules**:
- One open container max per compound.
- `numberOfContainers` means unopened containers only.
- Opening a container decrements `numberOfContainers` by 1 and creates `OpenedContainer`.
- When `remainingAmount` reaches ≤0 via dose deduction: remove `OpenedContainer`. Do not decrement `numberOfContainers` again.
- If `numberOfContainers > 0` after depletion, prompt "Open new container?" via snackbar action (default: auto-open).
- Delete OpenedContainer (via §4.5 Edit Opened Container sheet) = lost/discarded path. Removes the OpenedContainer record but does not change `numberOfContainers`.
- Effective expiry display: `userDefinedExpiryDate ?? predictedExpiryDate ?? null`. If both null, hide expiry from UI.

### 3.2 Protocol
```
id: Long
name: String                            // required
compoundSupplyId: Long                  // required
plannedDose: Quantity                    // same family as compound.primaryUnit
route: Subcutaneous | Intramuscular | Oral | Topical
schedule: Schedule
dosageTimes: List<LocalTime>            // empty = no specific time
escalation: Escalation?
protocolBreak: ProtocolBreak?
startDate: LocalDate                    // required, may be past
endDate: LocalDate?                     // null = open-ended
reminderEnabled: Boolean
reminderOffsetMinutes: Int              // 0 = at scheduled time; -ve = before
reminderBucket: Morning | Afternoon | Evening | null   // used when dosageTimes empty
injectionSiteRestriction: BodyRegion?
notes: String?
status: Active | Paused | Completed     // "InBreak" is derived from protocolBreak
deletedAt: Instant?
createdAt: Instant
updatedAt: Instant
```

```
Schedule:
type: Daily | EveryXDays | XTimesPerDay | SpecificWeekdays
| XTimesPerWeek | XTimesPerMonth
interval: Int?                        // for EveryXDays (≥1)
timesPerDay: Int?                     // for XTimesPerDay (≥1)
selectedWeekdays: Set<DayOfWeek>?     // for SpecificWeekdays (≥1)
timesPerWeek: Int?                    // for XTimesPerWeek (≥1)
timesPerMonth: Int?                   // for XTimesPerMonth (≥1)
```

```
Escalation:
startDose: Quantity
targetDose: Quantity
increaseAmount: Quantity              // > 0
increaseEvery: EveryXDays | EveryXWeeks | AfterXDoses
increaseEveryValue: Int               // ≥ 1
maxDose: Quantity?
stopAtTarget: Boolean
```

**Current dose** (computed, not stored — the escalation rule engine):
```
counter = AfterXDoses ? doses the schedule placed since startDate : days since startDate
steps   = counter / increaseEveryValue            // integer division; EveryXWeeks divides by value × 7
dose    = min(startDose + increaseAmount × steps, ceiling)
ceiling = min(maxDose, targetDose if stopAtTarget)   // whichever are set; neither set = no clamp
```
- The dose is expressed in `startDose`'s unit; `increaseAmount`, `maxDose` and `targetDose` are converted into that unit before they are added or compared, so a rule mixing `mg` and `mcg` still clamps correctly.
- A date before `startDate` reads as day 0, i.e. `startDose`.
- `stopAtTarget == false` means the escalation keeps climbing past `targetDose` — only `maxDose` stops it.
- `AfterXDoses` counts the doses the protocol's schedule actually places, break off-days excluded, which is what makes a date's dose independent of the horizon that generated it (§5.2).

```
ProtocolBreak:
daysOn: Int                           // ≥ 1
daysOff: Int                          // ≥ 0; e.g. 5/2 or 56/28 cycles
```

**In-break derivation** (computed, not stored):
```
daysSinceStart = today - protocol.startDate
cyclePos       = daysSinceStart mod (daysOn + daysOff)
inBreak        = cyclePos >= daysOn
```
When `inBreak == true`, protocol status reads as "In break" in UI but stored `status` remains `Active`. `ScheduledDose` generation skips off-days (see §5.2).

Equivalent-dose display (`0.10 mL · 10 insulin units`) derived at display time from `compoundSupply.concentration`. Not stored.

**Protocol additions for site cooldown** (see §5.3 / §5.8.5):
```
siteCooldownDays: Int?                // null = global default (5d SC, 7d IM)
```

### 3.3 ScheduledDose
```
id: Long
protocolId: Long
compoundSupplyId: Long
scheduledAt: Instant                    // when no time-of-day, start of day in user TZ
hasTimeOfDay: Boolean                   // derived from protocol.dosageTimes at gen time
plannedDose: Quantity                   // captured at gen time (after escalation)
route: Route
status: Pending | Taken | Skipped | Missed | Partial
administrationEventId: Long?            // set when logged
createdAt: Instant
```

- Snoozing updates `scheduledAt`; status stays Pending.
- If `hasTimeOfDay == false`, UI hides clock; sort key for that day = end-of-day.

### 3.4 AdministrationEvent
```
id: Long
loggedAt: Instant                       // wall-clock when logged
route: Subcutaneous | Intramuscular | Oral | Topical
status: Taken | Skipped | Partial      // Missed is a ScheduledDose-only state; no event row is created when a dose is missed
injectionSiteId: Long?                  // required when route in {SC, IM}
notes: String?
components: List<DoseComponent>         // ≥ 1
createdAt: Instant
updatedAt: Instant
```

### 3.5 DoseComponent
```
id: Long
administrationEventId: Long
scheduledDoseId: Long?                  // null for manual logs
protocolId: Long?                       // null for manual off-protocol logs
compoundSupplyId: Long
plannedDose: Quantity?                  // snapshot from protocol at log time; null if manual
actualDose: Quantity
concentrationAtLog: Concentration?      // snapshot from compound.concentration at log time; nullable for unit-based forms or manual w/o concentration
notes: String?
inventoryDeducted: Quantity             // computed at save, stored for audit
```

**Inventory-deducted quantity**:
```
deduction.value = actualDose.value / concentrationAtLog.amount.value
deduction.unit  = concentrationAtLog.per.unit
```
- Unit-family check: `actualDose.unit` family must match `concentrationAtLog.amount.unit` family. Conversion within family applied if needed (e.g. mg → mcg).
- For unit-based forms with no concentration (`concentrationAtLog == null`): `deduction = actualDose` directly (count form, e.g. 1 capsule).
- `concentrationAtLog` is captured at log time so edits + reversals stay correct when the user later re-reconstitutes the compound (changing `compound.concentration`).

### 3.6 InjectionSite
```
id: Long
name: String                            // user-defined or preset
bodyRegion: Abdomen | Quadriceps | Glute | Delt | Forearm | Hamstring | LowerBack | …
side: Left | Right | Center | NotApplicable
sublocation: Upper | Lower | Inner | Outer | null
lastUsedAt: Instant?
avoidUntil: Instant?
notes: String?
isAvailable: Boolean                    // user can mark unavailable (bruise, scar, etc.)
```

### 3.7 InventoryTransaction
```
id: Long
compoundSupplyId: Long
delta: Quantity                         // signed quantity in compound stock unit
type: InitialStock | Manual | DoseDeduction | ContainerOpen | ContainerClose
sourceEventId: Long?                    // AdministrationEvent.id when type=DoseDeduction
reason: String?                         // user-provided note for manual
at: Instant
```

**`InitialStock` semantics**: emitted **exactly once** when a compound is created, with `delta` equal to the total quantity the user entered as initial stock (i.e. `amountPerContainer × totalContainers`, plus any opened-container remaining). Required so `sum(delta)` across the ledger equals current absolute state for §5.8.0 reconciliation. If a user adds containers later via the Adjust flow (§4.3.9), each addition emits its own `Manual` transaction with the added quantity.

### 3.8 Settings
Singleton row, `id = 1`.
```
Settings:
id: Long                                 // always 1
theme: System | Light | Dark             // default System
dynamicColor: Boolean                    // default true
notificationStyle: Silent | Normal | Persistent   // default Normal
timeZoneOverride: String?                // IANA name; null = use device zone
missedDoseWindowMinutes: Int             // 5..60, default 60
onboardingCompleted: Boolean             // default false
exactAlarmDegraded: Boolean              // cached result of AlarmManager.canScheduleExactAlarms(); refreshed on app start + permission-change broadcast
defaultSiteCooldownDaysSC: Int           // default 5
defaultSiteCooldownDaysIM: Int           // default 7
createdAt: Instant
updatedAt: Instant
```

Used by §4.13 Settings screen, §5.1 reminder fallback path, §5.3 site cooldown source order, §5.7 timezone resolution.

**Storage**: Room singleton is authoritative. A small DataStore preference file mirrors only the two theme-critical fields (`theme`, `dynamicColor`) for sub-frame access during cold start (§2.3.4 ThemeInitializer). Repository write-through keeps the mirror in sync — never read or write theme from anywhere except via the Settings repository.

---

## 4. Features

### 4.0 Global navigation + layout

**Bottom navigation bar** (compact breakpoint): 5 vertical-item destinations.
- Home (icon `home` Material Symbols) → §4.1 Dashboard
- Compounds (`medication`) → §4.2
- Protocols (`calendar_month`) → §4.7
- Sites (`person_pin_circle`) → §4.12
- Settings (`settings`) → §4.13

**Side rail** (medium breakpoint and unfolded foldables): same 5 destinations vertically.

Detail screens (`Compound Detail`, `Protocol Detail`, `Edit dose`, `Administration Event detail`, `Create/Edit Compound`, `Create/Edit Protocol`, `Reconstitution Helper`, `Site picker`) push onto current destination's back stack. Bottom nav stays unless explicitly hidden.

**App bar pattern**
- Leading icon: contextual (back-arrow for stacked screens, nothing or something relevant for top-level destinations, close × for full-screen forms/sheets).
- Optional supporting text below headline (e.g. context: "Sema weekly titration").

#### 4.0.1 Search overlay (reusable)

Full-screen modal. Used by §4.2.1 Compounds, §4.7.1 Protocols, §4.12.1 Sites.

- App bar: leading `arrow_back` (closes overlay) · M3 `SearchBar` with autofocused text field · trailing `close` clears text.
- Result list below: same row layout as the host list (compound row / protocol row / site row), but with matched-substring highlighted via `SpanStyle(background = primaryContainer)`.
- Empty result state: centered `search_off` icon + "No matches" + supporting text.
- Search is case-insensitive substring on `name` field; FTS not required at v1 scale.

#### 4.0.2 Picker bottom sheet (reusable)

Reusable pattern for **Compound picker** (§4.9.3 Create Protocol, §4.10.3 grouped event add-component, §4.13 inventory adjust), **Route picker** (§4.10.3), **Body region picker** (§4.9.3).

- Modal bottom sheet. Drag handle. Scrim.
- Header row: title (e.g. "Pick compound") + trailing `close`.
- M3 `SearchBar` (only when item count >5).
- List rows: avatar/icon + name + supporting meta + `chevron_right`. Selection on tap closes sheet + returns selected item to caller.
- Empty state: "Nothing to pick" + CTA to navigate to creation flow (e.g. "Add compound" → §4.4).

Site picker (§4.12.7) is a full-screen flow, not this pattern.

### 4.1 Dashboard

**Primary goal**: Interact with dose card, log next dose w/ minimum friction.

**States**:
1. **Default** (`01 · Dashboard`) — has Pending doses today.
2. **Empty** (`01d · Dashboard (empty)`) — no doses today. Big illustrated empty hero (blob composition + center `add` icon), title "No doses today", subtitle "Tap to log your first dose or create a protocol.", primary CTA "Log dose" + tonal "Protocol". FAB hidden.
3. **All done** (`01e · Dashboard (all done)`) — has active protocols but zero Pending today (all Taken/Skipped). Hero `primary-container` card: round `primary` avatar w/ `done_all` icon + "All done today" headline-small + subtitle "N doses logged · 100% adherence". Keep Inventory + Recent activity sections below.
4. **Grouped administration suggestion** (`01b · Dashboard (grouped administration)`) — when ≥2 injectable Pending doses share same route within these grouping windows:
   - All components have `dosageTimes` set → 30-min window.
   - All components have `dosageTimes` empty → same calendar day.
   - **Mixed** (some timed, some not) → fall back to same calendar day (the looser window wins).
   Hero card replaces individual dose cards w/ grouped suggestion card (see §4.10.3).
5. **Overflow menu open** (`01c · Dashboard (overflow menu open)`) — anchored to tapped `more_vert` button on dose row, only on Grouped administration card. Items: Take dose · Snooze (submenu, see §4.1.2) · Skip.

#### 4.1.1 Day chip strip

Horizontal scrollable row at top of content. ±N days from today (default N = 3, so 7 day chips total). Centered on today initially.

**Chip composition**:
- Top: day letter (NARROW `DayOfWeek`, e.g. "M").
- Middle: day number.
- Bottom: dose indicator dot when `ScheduledDose` exists for that date.

**Selected chip** (today by default; user can tap any):
- Fill: `primary`. Text: `on-primary`.

**Interactions**:
- Tap chip → filter Today's schedule.
- Long-press chip → open Material Date Picker for arbitrary date.
- Swipe horizontally to navigate weeks/months.

**Lazy load**: render initial ±3 days. On horizontal scroll edge, append 7 chips on the leading edge and recycle 7 chips off the trailing edge. Window stays constant at ~14 chips in memory regardless of scroll distance.

#### 4.1.2 Dose cards

One per Pending `ScheduledDose` matching selected date. Sorted by `scheduledAt` ascending; doses w/ `hasTimeOfDay == false` sorted last.

**Default card layout**:
- Top row: compound name + ETA badge pill (right).
- ETA badge:
  - When `hasTimeOfDay == true` AND scheduled in future: "in Xh Ym" or "in N min"
  - When overdue: red `error-container` w/ "Overdue Xm"
  - When `hasTimeOfDay == false`: "Today" pill (no time)
- Detail row, formatted as:
  - With time: `0.25 mg · 0.10 mL · 8:00 PM`
  - Without time: `0.25 mg · 0.10 mL`
- Action row: 3 buttons inline.
  - **Take** (filled button) → opens §4.10.1 Take Dose bottom sheet.
  - **Snooze** (outlined button) → opens snooze submenu. **Standard submenu**: 1h / 3h / 1d when `hasTimeOfDay == true`; only 1d when `hasTimeOfDay == false`. Same submenu used by overflow menu in §4.1 state 5.
  - **Skip** (text button) → confirmation snackbar "Skip dose? [Skip] [Cancel]" — on confirm sets `ScheduledDose.status = Skipped`, no inventory deduction.

**Swipe gestures** (preferred for one-handed daily use):
- Swipe right (LTR) → equivalent to Take button: opens §4.10.1 Take Dose sheet prefilled.
- Swipe left → equivalent to Skip: sets `Skipped` with undo snackbar (5s).
- Drag distance threshold: 40% of card width; haptic on threshold cross.
- Disabled when card is the grouped suggestion card (must use overflow menu for per-component actions).

**First dose card** (the absolute next one due) uses `primary-container` fill instead of `surface-container` — hero visual emphasis.

**Grouped dose card** = tap `more_vert` icon on the sub-card (always visible right) → opens §4.1 state 5 menu.

#### 4.1.3 Section labels

Section label on dashboard:
- Leading icon (e.g. `inventory_2`, `history`)
- Inventory section label below dose cards
- Recent activity section label below inventory

#### 4.1.4 Inventory warnings

Below dose cards. Each warning = `error-container` fill, leading `warning` icon, 2-line content (title + supporting), trailing `chevron_right`. Tap → navigates to Compound Detail of the affected compound.

Warning triggers (per spec §5.3 inventory math):
- `dosesRemaining < 7` → "BPC-157 — 3 doses left · Reorder before {date}"
- Opened container expires within 14 days → "Opened Tirzepatide vial expires in 9 days"
- Active protocol requires more than available → "Protocol requires 1.5 mL until end date · Only 0.8 mL available"
- Batch expires before estimated run-out → "Batch expires Jul 14 — before run-out Jul 28"

#### 4.1.5 Recent activity

Below inventory section. Last 5 `AdministrationEvent` rows (Missed is a `ScheduledDose`-only state per §3.4 and never appears here). Each row: status dot (avatar circle, color by status), compound name, supporting text (e.g."Yesterday 8:14 PM · Taken"). Status colors:
- Taken: `secondary-container` w/ `check` icon
- Partial: `tertiary-container` w/ `schedule` icon
- Skipped: `error-container` w/ `close` icon

Tap row → §4.11 Administration Event detail.

#### 4.1.6 FAB

Bottom right.

`add` icon.

**Tap behavior**:
- If at least one Pending dose exists today → **direct** to §4.10.1 Take Dose sheet for the next due dose. Single tap = one of the most common flows lands instantly.
- If no Pending dose today → opens §4.1.7 FAB menu.

**Long-press**: always opens §4.1.7 FAB menu, regardless of Pending state. Lets power users get manual log / create flows even when next dose exists.

#### 4.1.7 FAB menu (`24 · FAB menu (open)`)

Items (top to bottom):
1. **Log scheduled** (`done` icon) → navigates to §4.10.2-a Log dose (Dashboard) preselected to next Pending dose.
2. **Log manual** (`edit` icon) → §4.10.2-a Log dose (Dashboard) w/ compound picker open.
3. **Add compound** (`colorize` icon) → §4.4 Create Compound.
4. **Add protocol** (`calendar_month` icon) → §4.9 Create Protocol.

Tap outside scrim or tap FAB (which morphs to `close` icon) → dismiss menu.

---

### 4.2 Compounds list (`02 · Compounds`)

**Primary goal**: find + select a compound.

#### 4.2.1 App bar
Leading `search` icon → opens **Search overlay** (see §4.0.1). Title "Compounds".

#### 4.2.2 Filter chip row (horizontally scrollable)


Chips in order:
1. **All** (`Style=Outlined, Configuration=Label & leading icon, Selected=True`, leading `done` icon) — default selected, mutually exclusive w/ Low stock + Expiring soon.
2. **Low stock** — selects compounds where `dosesRemaining < 7`.
3. **Expiring soon** — selects compounds where any effective expiry < today+28d.
4. **Category** (`Show trailing icon=True`, trailing `expand_more`) — opens kit Menu for multi-select. Items: Peptide / Supplement / Hormone / Medication. Chip label updates to "Category · N" when ≥1 selected. See `02c · Compounds (Category menu open)`.
5. **Form** (`Show trailing icon=True`) — opens kit Menu for multi-select. Items: Injectable / Capsule / Tablet / Powder / Liquid / Topical.

Category + Form menu items use `check_circle` (selected) / `add_circle` (unselected) leading icons.

#### 4.2.3 Compound row

Layout left→right:
- **Avatar**: category-colored fill + form icon:
  - Peptide: `primary-container` + `colorize` icon
  - Supplement: `tertiary-container` + `medication` icon
  - Hormone: `secondary-container` + `science` icon
  - Medication: `surface-container-highest` + `pill` icon
  - Low-stock state (any category): `error-container` + `warning` icon (overrides default)
- **Content column**:
  - Name
  - Meta: `{category} · {remaining} · {N container(s)}` or `{category} · Low stock · 3 doses left`
- **Meta column** (right):
  - Effective expiry: "Exp Jul 14"
  - `chevron_right` icon

**Tap** → §4.3 Compound Detail.
**Long-press** → enter §4.2.4 multi-select mode.

#### 4.2.4 Multi-select mode (`02b · Compounds (multi-select)`)

Entry: long-press any row.

Exits: the contextual bar's `close`, the system back gesture, or unticking the last selected row —
the selection *is* the mode, so an empty selection is never multi-select with nothing in it.

App bar transforms into contextual app bar:
- Leading `close` → exits multi-select.
- Title: "N selected" (live count).

The **filter chip row (§4.2.2) is hidden** with it: narrowing the list mid-selection would hide rows
the dock is about to act on. The **§4.2.5 FAB is hidden** too (§6.4.6) — the dock carries the mode's
actions, and a screen with two primary actions on it has none.

**Row visual**: outlined leading checkbox circle before avatar(shifted to the right). Tapping a row
toggles it rather than opening its detail.

**Selected row visual**: fill = `secondary-container`, leading checkbox filled with `primary` and `check`.

Bottom nav **hidden** during multi-select mode. Bottom dock appears instead:
- **Duplicate** (tonal `secondary-container` button, equal-grow): creates copies with " (copy)" suffix, fresh IDs, no opened container, no batch number.
- **Archive** (`error-container` button, equal-grow): opens confirmation dialog "Archive N compounds? Logged history is kept." — confirm → sets `deletedAt = now()` for all selected.

After action completes, exits multi-select mode. No undo snackbar. A batch runs to the end even if one
compound fails, and reports the **first** failure only — the list updating under the user is the
confirmation, so only what did *not* happen needs saying.

#### 4.2.5 FAB

Extended FAB "Add" bottom-right, opens §4.4 Create Compound.

---

### 4.3 Compound Detail (`03 · Compound Detail`)

**Primary goal**: review and act on one compound.

#### 4.3.1 App bar
- Leading `arrow_back` → Compounds list.
- Headline: compound name. Supporting: category (e.g. "Peptide").

#### 4.3.2 Stat strip (top of content, horizontal row)

1. **Doses left**: `primary-container`: emphasized number + label "Doses left". Computed = `floor((openedRemaining + unopenedTotalAmount) / dosesPerActualInjection)`. `dosesPerActualInjection` = the `plannedDose` of the most-frequent active protocol using this compound (frequency = doses per week derived from `schedule`). Ties resolved by max `plannedDose`. If no active protocol uses this compound, tile renders `—`.
2. **Days left** `secondary-container`: emphasized number + label "Days left". Computed = `dosesLeft / dosesPerDayAcrossActiveProtocols` where the denominator sums per-day frequencies of all active protocols using this compound.
3. **Batch expiry** `tertiary-container`: emphasized short date "Jul 14" + label "Batch expiry" or "Container expiry" whichever has shorter date. **Hidden entirely if `batchExpiryDate` null and no opened expiry**.

If only 2 tiles relevant, render 2 across full width. If only 1, render full-width.

#### 4.3.3 Opened vial card

Visible only if `currentOpened != null`. Full-width card, `surface-container-high`.

Top row: icon + "Opened {container type}" title + outlined "Edit" button (tap → §4.5 Edit Opened Container bottom sheet).

**Segmented progress bar**: 10 segments. Computation: `filledSegments = floor((remainingAmount / amountPerContainer) × 10)`. Handle partial segment.

Meta row (space-between): "X / Y mg remaining" · "Opened N days ago".

#### 4.3.4 Active protocols card

Full-width, `surface-container`. Header: `calendar_month` icon + "Active protocols · N".

Sub-list: each active protocol uses this compound → sub-row card (`surface-container-low`):
- Protocol name
- Details: (e.g. "Mon, Thu · 0.25 mg sc"0
- Tag pill (`primary-container`): `schedule` icon + next planned dose (e.g. "Next: Today 8 PM")

Tap sub-row → §4.8 Protocol Detail.

#### 4.3.5 Notes card

Full-width, `surface-container`. Header row: `edit` icon + "Notes". Body: notes text, **truncated to 2 lines** with `textTruncation=ENDING`. Below body: "Show more" tappable link + `expand_more` icon → expands to full text in-place (`expand_less` once expanded).

#### 4.3.6 History section

Above history list:
"Dose History" to the left + count badge (total Taken+Partial DoseComponents all-time for this compound) to the right.

#### 4.3.7 Filter chips

Filter chip, single-select:
- All (selected default)
- Taken
- Partial
- Skipped

#### 4.3.8 History list

List (lazy load). Each row `surface-container`:
- Status dot with status icon (check / schedule / close)
- Date + time + dose + site supporting (e.g. "Today · 8:00 PM" then "0.25 mg · 0.10 mL · Taken · Abdomen R")

Tap row → §4.11 Administration Event detail.

**Paged (Paging 3).** A history has no upper bound, so the rows are read a page at a time from a Room
`PagingSource` over `dose_component` rather than loaded whole — that is what meets §2.3.2's scroll SLO
at a thousand rows. §4.3.7's chip is part of that query (`WHERE status = …`), not a pass over rows
already in memory: filtering in memory would mean loading everything to throw most of it away. The
§4.3.6 badge is a separate `COUNT`, which is also why it stays still when the chip moves.

#### 4.3.9 Bottom dock

Sticky `surface-container-low`. Two buttons:
- **Log dose** (filled `primary`): leading `add` icon. → §4.10.2-b Log Dose (from Compound).
- **Adjust** (tonal `secondary-container`): leading `inventory_2` icon. → Adjust Edit compound screen.

Bottom nav is hidden on this screen — while the detail *is* the screen. At Medium and above it is one pane of the list-detail Scene beside the Compounds list (§6.4.2), which is a top-level destination and keeps its rail; the dock then spans the detail pane alone and nothing collides.

---

### 4.4 Create Compound (`4 · Create Compound`) + 4b validation variant

Scrollable form. Bottom dock w/ Cancel + Save compound CTAs.

#### 4.4.1 App bar
- Leading `close` (×) → confirms discard if dirty, returns to caller.
- Title: "New compound" (Create) or "Editing {name}" (Edit, `5 · Edit Compound`).
- App bar **container transparent on scroll**; X button keeps `surface-container-low`  round fill for legibility — floats above content.

#### 4.4.2 Section header pattern

Section labels = `primary` color. No card wrap.

#### 4.4.3 Sections (top to bottom)

**Basics** (all required):
1. **Name** — single-line text field, surface-container fill, leading `edit` icon. Required validation; empty → red outline + supporting text `error` "Name is required" w/ `error` icon.
2. **Category** — dropdown row (chevron). Single-select from Peptide / Supplement / Hormone / Medication.
3. **Form** — dropdown. Injectable / Capsule / Tablet / Powder / Liquid / Topical.
4. **Container type** — dropdown. Vial / Bottle / Blister / Packet / Tub / Ampoule.

**Smart defaults** Need to have really advanced smart algorithm, simple example: Per Form selection:
- Injectable → Vial · mg · subcutaneous · 5 mg
- Capsule → Bottle · capsule · oral · 60 capsules
- Tablet → Blister · tablet · oral · 30 tablets
- Powder (non-injectable) → Tub · g · oral · 100 g
- Liquid → Bottle · mL · oral · 30 mL
- Topical → Tub · g · topical · 50 g

Only container type, unit and amount are this form's to fill — **route belongs to a Protocol** (§4.9), not to a `CompoundSupply`, so this screen has no route field to default. A default never overwrites a field the user has set by hand; the exception is the unit, which is replaced when the new Form does not offer it (the picker's options are per-form — a tablet has no millilitres — so keeping it would show a selection that is not in its own list).

**Stock**:
1. **# of containers** — numeric total owned, side-by-side w/ next. Persistence stores this as unopened count: if no opened container is added, `numberOfContainers = total`; if an opened container is added, `numberOfContainers = total - 1`.
2. **Amount per container** + unit picker.
3. **Concentration** (Optional) — numeric + unit picker inline ("{amount} / 1 {per}"). **The picker offers whole ratios, and which ones depends on the Form**: a concentration answers "how much active is in one of these", so the denominator is a millilitre for an Injectable or a Liquid, a gram or a scoop for a Powder, a gram or a millilitre for a Topical, and the pill itself for a Capsule or a Tablet — "mg/mL" on a blister of tablets is not a unit. Changing the Form re-picks it under the same rule as the other smart defaults. **Trailing "Helper" tonal button** (`secondary-container`, `calculate` icon) → §4.6 Reconstitution Helper — on Create there is no compound to pre-select yet, so it opens the standalone calculator. Required only when `Form == Injectable AND ContainerType != Ampoule` (pre-mixed); the `Optional` badge disappears exactly when that rule makes it required.

**Storage & batch**:
1. **Storage location** — dropdown: Fridge (4°C) / Room temp / Freezer.
2. **Batch expiry date** (Optional) — date field, opens Material Date Picker.
3. **Batch number** (Optional) — single-line text.
4. **Supplier** (Optional) — single-line text.
5. **Expiry after opening** (Optional) — numeric + "days" suffix. Drives auto-computed `predictedExpiryDate` when container opened.

**Opened container** section:
- Empty state: `surface-container` card w/ `inventory_2` badge + "No container opened yet" title + "Auto-opens on first dose, or add one now." supporting + tonal `secondary-container` CTA "Add already opened" (leading `add` icon).
- Tap CTA → §4.5 Create Already Opened Container bottom sheet.
- Once container added, this section shows summary card identical to §4.3.3 Opened vial card layout + "Edit" pencil → opens §4.5 Edit Opened Container sheet.

**Notes** (Optional) — multi-line text field, 3-line min height.

#### 4.4.4 Save validation

On tap "Save compound":
- Validate all required fields. If any empty → focus first error field, scroll into view, show inline error.
- Insert/update `CompoundSupply` row. Persist `numberOfContainers` as **unopened count**.
- If "Mark as already opened" was used: create `OpenedContainer` linked to the new compound and subtract one from the total-owned container input when storing `numberOfContainers`.

**Worked example** (prevents off-by-one bugs):
> User enters Total owned = `3` and adds an opened container with remaining = `5 mg`. Stored result:
> - `compound_supply.numberOfContainers = 2` (unopened)
> - `opened_container.remainingAmount = 5 mg` (the third container, opened)
> - Total physical containers in user's possession = `numberOfContainers + (currentOpened != null ? 1 : 0)` = `3`.

**Edit case — `amountPerContainer` shrunk below `OpenedContainer.remainingAmount`**:
On Save, if user reduced `amountPerContainer` below the current opened container's remaining, show dialog:
- Title: "Container size smaller than remaining"
- Body: "Opened container remaining = {old remaining} {unit}, new container size = {new amount} {unit}."
- Actions: **Keep remaining** (no clamp; remaining stays > new amount, allowed) · **Cap to new size** (clamps remaining to new amount, logs an `InventoryTransaction { type = Manual, delta = -(old - new), reason = "Compound size reduced" }`) · **Cancel** (revert form to previous value).

Returns to caller: Compounds list (default) or Onboarding step 2 progresses to step 3.

#### 4.4.5 Behaviors
- Auto-save draft on backgrounding (resume restores form state). The ViewModel already survives backgrounding, so the draft is written to its `SavedStateHandle` on every edit — that is what makes it survive the **process death** that can follow. In Edit mode a restored draft wins over the stored compound (the unsaved edits are the newer truth); it is dropped once the form is saved, discarded or skipped, so the next Create opens clean.
- Discard confirmation dialog when X pressed w/ dirty form: "Discard changes?" + Discard / Keep editing. Same for Cancel and the back gesture. "Dirty" means the fields differ from what the form was loaded with — typing and deleting back is not a change, and a failed validation is not one either.

---

### 4.5 Opened Container bottom sheets

Two variants:
- `6 · Edit Opened Container (bottom sheet)` — opened from Compound Detail or Edit Compound when container exists.
- `7 · Create Already Opened Container (bottom sheet)` — opened from Create/Edit Compound "Mark as already opened" CTA. Identical UI minus Delete button.

#### 4.5.1 Sheet structure
Bottom sheet modal. Drag handle + Scrim overlay. Adaptive per §6.4.2 "Other modal bottom sheets".

Only the fields scroll; the §4.5.4 action row is pinned below them. At Expanded the side sheet is as tall as the window, which on a landscape phone is `411dp` — less than the fields need — and a Save the user has to scroll to find is a Save they do not find.

**Date pickers**: both date fields open the Material date picker in its **calendar** display mode where there is room and its **text-input** mode where there is not (window height below the Medium breakpoint, `480dp`). The calendar needs ≈`500dp` of height; below that its grid overlaps its own weekday header and action row.

#### 4.5.2 Header
Inline at top of sheet content: title ("Add opened {container type}" / "Edit opened {container type}") + subtitle (compound name + size e.g. "Semaglutide · 5 mg vial"). Right side: `close` icon button.

#### 4.5.3 Fields
1. **Opened date** — date picker field, `surface-container` row. Leading `today`, value (e.g. "May 14, 2026"), supporting "12 days ago" (auto-computed), trailing `edit`. Default to today on Create.
2. **Remaining** — `surface-container` row. Leading `straighten`, value numeric, inherits compound's `primaryUnit`. Default on Create = `compound.amountPerContainer`.
3. **Container expiry** (Optional) — date picker. Leading `event_busy`, value or "Tap to set", supporting "N days after opening" (auto-computed when based on `expiryAfterOpeningDays`), trailing `edit`.
   - **Default value**: if `compound.expiryAfterOpeningDays` set → auto-compute = `openedDate + expiryAfterOpeningDays`, label as "auto" (greyed). If user taps trailing edit → switches to manual mode, sets `userDefinedExpiryDate` (manual override wins per §3.1.1).

#### 4.5.4 Actions (Edit variant only)
Bottom row: `Delete` (`error-container`, leading `delete` icon) + `Save` (filled `primary`).

**Delete behavior**: removes the `OpenedContainer` (lost/discarded path). Does not change `numberOfContainers`. Compound reverts to "no opened container" state. Snackbar "Opened container removed" (no undo).

**Delete during the New Compound flow** removes the staged container instead, and writes nothing.

#### 4.5.5 Save behavior
- Create for an existing compound: decrements `numberOfContainers`, creates `OpenedContainer` row linked to compound, and updates compound's `currentOpened` ref (`CompoundRepository.addOpenedContainer`).
- Create during New Compound flow: stages the opened-container fields until "Save compound"; final save stores `numberOfContainers` as total-owned input minus one.
- Edit: updates fields on existing `OpenedContainer`.
- If `remainingAmount == 0` after save: triggers natural depletion. Remove `OpenedContainer` without decrementing `numberOfContainers` again, then show dialog "Open new container?" w/ "Open new" (default) / "Leave closed" actions when unopened stock remains. Delete (§4.5.4) does **not** raise it: discarding a container is already a decision about that container.
- **The form's total-owned count follows the write.** A container that left the stock — discarded or emptied — is one the user no longer has, while `numberOfContainers` is untouched, so §4.4.3's "# of containers" field drops by one. For an existing compound the count is re-read from the stored row rather than computed, so the field and the row cannot drift apart.
- **A refused write is reported in the sheet**, not through the screen's snackbar: a modal sheet is a window of its own and the `SnackbarHost` draws behind it. The only refusal the user can act on is "no unopened container left to open" (§5.3 requires `numberOfContainers > 0`); everything else reads as a failed write.

**Ledger** (§5.8.0): each of these moves stock, so each books it. Adding an already-opened container books the difference between a full container and what is left in it; correcting Remaining books the difference; discarding a container with something still in it books what goes with it. `ContainerOpen` / `ContainerClose` stay the delta-0 audit markers of §5.3.

---

### 4.6 Reconstitution Helper (`19 · Reconstitution Helper`)

**Primary goal**: compute correct dose volume w/ confidence.

**Progressive disclosure**: by default open with the syringe hero (§4.6.2), equivalence chips (§4.6.3), and result tiles (§4.6.6) visible. Mix (§4.6.4) and Dose ladder (§4.6.5) sections are collapsed behind a single "Show calculation" expansion row (`expand_more` → `expand_less`). Most reconstitution events use the saved concentration; advanced users get full detail on demand.

#### 4.6.1 App bar
Leading `close`, title "Reconstitute", supporting "{compound name} · {container amount}{unit} vial".

#### 4.6.2 Syringe hero card

Top row: left column shows label "Draw to" + value row numeric + "units" (`primary` color). Right: size badge pill (`secondary-container`, leading `straighten` icon + "U-100 · 1 mL"). when tapping on size badge pill change syringe kind (insulin: U30, U50, U100; regular: 2mL, 3mL, 5mL)

**Syringe visualization**:
- Syringe with right graduation depending on size
- visual change if insulin or regular
- preview filled with the dose

#### 4.6.3 Equivalence chips row

2 or 3 chips below syringe (side-by-side):
- `primary-container` chip — value "0.25" / unit "mg" (mass)
- `secondary-container` chip — value "0.10" / unit "mL" (volume)
- `tertiary-container` chip — value "10" / unit "units" (insulin units, if insulin type)

Each chip: value above unit, center-aligned.

#### 4.6.4 Mix section (inputs)

Section header "Mix" (`primary` label color).

compact grid:
1. **Container** — value + unit (read-only from compound).
2. **Diluent** — editable numeric + "mL".
3. **Desired dose** — editable numeric + unit.
4. **Display** — dropdown: mL / Insulin units.

Each tile: `surface-container`, leading icon (colorize / water_drop / straighten / tune), label above value row + unit.

#### 4.6.5 Dose ladder

Section header "Dose ladder".

Horizontal scrollable row of dose rungs. Each rung: vertical pill. Selected rung = `primary` fill, corner 16dp (shape break vs unselected 999r). Unselected = outlined.

Default rungs computed: [0.1, currentDesired/2, currentDesired, currentDesired×2, currentDesired×3]. Each rung shows dose value + unit equivalent (e.g. "10 u").

Tap rung → previews syringe fill width at that dose (animated spring transition, see motion specs). Sets desired dose on confirm.

#### 4.6.6 Result tiles

Section header "Result".

2 horizontal tiles:
1. **Concentration**: `2.5 mg/mL` + label "Concentration", leading `calculate` icon.
2. **Doses / container**: `20` + label, leading `inventory_2`.

#### 4.6.7 Save dock

Sticky bottom dock, filled `primary` button: leading `check` + "Save & set concentration".

On tap:
- Update `CompoundSupply.concentration`.
- Regenerate displayed volume on all Pending `ScheduledDose` for this compound (plannedDose unit untouched).
- Return to caller (Create Compound → field filled, Edit Compound → field filled).

#### 4.6.8 Motion
Syringe fill width transitions on dose change: Material spring `MotionScheme.expressive().fastSpatialSpec()` over `width` modifier.

---

### 4.7 Protocols list (`08 · Protocols`)

**Primary goal**: review active protocols + create/edit.

#### 4.7.1 App bar
Leading `search` icon → opens **Search overlay** (§4.0.1). Title "Protocols".

#### 4.7.2 Filter chips

Chips: Active / Paused / Completed / Archived. Single select.

**Tab definitions**:
- **Active**: `status == Active && deletedAt == null`. Includes in-break protocols (in-break derived per §3.2; status stays Active).
- **Paused**: `status == Paused && deletedAt == null`.
- **Completed**: `status == Completed && deletedAt == null`.
- **Archived**: `deletedAt != null` (any status). `Archived` is not a `Protocol.status` enum value — it is derived from soft-delete.

Archived protocols never appear in Active/Paused/Completed tabs (visible only when Archived chip activated).

#### 4.7.3 Protocol card

Full-width card per protocol, `surface-container`.

Top row: 2-column.
- Left col: name + meta (`{compound} · {dose} {route}`).
- Right: status pill.
  - **Active**: `primary-container`, label "Active".
  - **In break**: `tertiary-container`, label "In break".
  - **Paused**: `surface-container-highest` outlined, label "Paused".
  - **Completed**: outline-only chip "Completed".

Schedule chips row (horizontal):
- Schedule chip: outlined pill, leading `calendar_month` + label "Weekly · Mon, Thu".
- Next dose chip: outlined, leading `schedule` + "Today 8 PM" or "Tomorrow 8 AM" or "In 5 d (break)".

**Titration progress bar** (only if `escalation != null`):
- Label row: "Titration" + value "0.25 / 1.0 mg" (right-aligned)
- bar `surface-container-highest`, fill `primary` at `currentDose / targetDose`.

Tap card → §4.8 Protocol Detail.
Long-press → §4.7.4 multi-select mode.

#### 4.7.4 Multi-select mode (`08b · Protocols (multi-select)`)

Entry: long-press row. The selection *is* the mode: emptying it leaves multi-select just as close ×
does. While the mode is on, a row tap toggles instead of opening §4.8.

Contextual app bar: close × · "N selected" · trailing `more_vert` (Select all / Invert). It replaces
the app bar **and** §4.7.2's chip row: both menu entries and every dock action work on the tab's
visible result list, and switching tabs mid-selection would swap those rows out from under them.

Selected card visual: fill = `secondary-container`, leading checkbox circle on left of card. Unselected cards keep default.

Bottom dock (replaces nav bar):
- **Pause** (`secondary-container`) — applies only to selected Active protocols. Disabled if no selected is Active.
- **Resume** (`secondary-container`) — applies only to selected Paused. Disabled if no selected is Paused.
- **Complete** (`secondary-container`) — sets `status=Completed`, no new ScheduledDoses generated. Disabled if every selected is already Completed.
- **Duplicate** (`secondary-container`) — creates copies w/ " (copy)" suffix, status=Active. Never disabled: the copy starts Active whatever the original was.
- **Archive** (`error-container`) — confirmation → soft-delete. Disabled on the Archived tab, where the selection is soft-deleted already.

**Incompatible selections narrow, they do not block.** Each button applies to the part of the
selection it is defined for and is disabled only when that part is empty — pausing a mixed selection
pauses what is running and leaves the rest untouched. An in-break protocol is Active (§3.2) and
pauses with the rest.

A batch attempts every protocol even after one write fails, and reports only the first failure.

#### 4.7.5 FAB

Extended FAB "New protocol" (leading `add`) → §4.9 Create Protocol.

---

### 4.8 Protocol Detail (`9 · Protocol Detail`)

**Primary goal**: review protocol state + log next dose.

#### 4.8.1 App bar
Leading `arrow_back`. Headline: protocol name. Supporting: `{status} · {compound name}` (e.g. "Active · Semaglutide").

#### 4.8.2 Quick action chips (inline below app bar)
3 outlined pill buttons: **Pause** (`pause`) / **Edit** (`edit`) / **Duplicate** (`add_circle`).

If protocol is Paused: Pause label morphs to "Resume" w/ `play_arrow` icon.

#### 4.8.3 Schedule card

Full-width, `surface-container`. Header: `calendar_month` icon + "Schedule" title.

Key-value table (each row: label left, value right)(example values):
- Frequency: e.g. "Weekly · Mon, Thu" (formatted from `schedule`)
- Times: "8:00 PM" or **omitted** if `dosageTimes` empty
- Titration: "0.25 → 1.0 mg · +0.25 / 4 wk" or **omitted** if no escalation
- Duration: "May 1 → open-ended" or "May 1 → Sep 30"
- Reminder: "10 min before" or "Off"

#### 4.8.4 Linked compound card

`surface-container`. Header: `inventory_2` + "Linked compound".

Sub-row showing compound (same layout as §4.2.3 row): avatar + name + meta (e.g. "0.25 mg = 0.10 mL · 2.5 mg/mL") + chevron. Tap → §4.3 Compound Detail.

#### 4.8.5 Inventory forecast

`surface-container`. Header: `inventory_2` + "Inventory forecast".

Key-value table(example values):
- Doses remaining: "18 doses"
- Run-out date: "Jul 28, 2026"
- Required until end: "Open-ended" or "0.5 mL"

**Warning row** (if applicable): `error-container` fill, `warning` icon + "Batch expires Jul 14 — before run-out".

#### 4.8.6 Site restrictions
`surface-container`. Header: `person_pin_circle` + "Site restrictions".
Chips for region (e.g. "Abdomen only") + rotation rule (e.g. "Rotate every 5 days").

The region chip is `injectionSiteRestriction`, or "Any site" when the protocol sets none. The
rotation chip is the **site cooldown**, resolved through §5.3's source order — `siteCooldownDays`
where the protocol overrides it, else the Settings default for its route — because that is the
rotation rule the app actually enforces on log. There is no left/right rotation field in §3.2, so
there is nothing behind a "Rotate L / R" chip to state; the cooldown is the rule that exists.

#### 4.8.7 Dose history
Same header and rows as §4.3.6/§4.3.8, filtered to this protocol — but **without §4.3.7's status
chips**: this list is already narrowed to one protocol, and a second filter over it buys nothing.
- Header w/ count pill "{N} logged" (Taken+Partial).
- List rows w/ status dot + date + dose + site + status.
- Paged like §4.3.8, since a protocol's history has no upper bound either.

Tap row → §4.11 Administration Event detail.

#### 4.8.8 Notes
Same as §4.3.5 (truncated 2 lines + Show more).

#### 4.8.9 Bottom dock
- **Log dose** (filled `primary`, leading `add`) → §4.10.2-c Log Dose (from Protocol), prefilled w/ this protocol context + next Pending dose.
- **Archive** (`error-container`, leading `delete`) → confirmation → soft-delete.

---

### 4.9 Create Protocol (`11 · Create Protocol`) + 11b forecast view + 10 Edit Protocol

#### 4.9.1 App bar
- Create: title "New protocol", leading `close`.
- Edit: title "Edit protocol", supporting (current protocol name), leading `arrow_back`.

#### 4.9.2 Edit-mode warning banner

`tertiary-container` banner at top of content: `warning` icon + "Saving regenerates pending doses" title + "Logged history (Taken / Skipped / Missed) stays intact." supporting. Always visible in Edit mode.

#### 4.9.3 Sections

**Name** — no field. `Protocol.name` (§3.2) is required but the form has no input for it: a created protocol is named after the compound it doses, which is what identifies it on the list (§4.7.3), and an edit keeps whatever the protocol is already called. Likewise, the form has no control for `escalation`, `protocolBreak` or `siteCooldownDays`, so an edit carries all three through untouched — saving here never flattens a titration.

**Compound** (required):
- Card `primary-container`, rounded-square avatar + compound name + meta (`{category} · {amountPerContainer}{unit} {containerType} · {concentration}`). Trailing `expand_more` → opens **Compound picker** (§4.0.2 reusable pattern).

**Route** (required):
- Kit `Segmented button` (`Segments=4`). 4 segments: SC / IM / Oral / Topical. Default = compound's typical route or protocol if different from compound.

**Planned dose** (required):
- Card `surface-container`. Leading `straighten` icon. Value column: numeric + tonal unit pill (`secondary-container`, mg/mcg/IU dropdown w/ chevron). Below: equivalence chip (`tertiary-container`, leading `calculate`) showing "Equivalent: 0.10 mL · 10 insulin units" only when `compound.concentration` set.

**Schedule** (required):
- Filter chips row (single-select): Daily / Every X days / Weekdays / Times/week / Times/day / Times/month.
- **Conditional inputs below** based on chip:
  - **Daily**: nothing extra; `dosageTimes` editable.
  - **Every X days**: numeric "every N days" input.
  - **Weekdays**: 7-day circle picker. Circle, selected = `primary` fill, unselected = outlined.
  - **Times/week**: numeric 1-7.
  - **Times/day**: numeric, opens N empty time slots in Times of day section.
  - **Times/month**: numeric 1-31.
- **Times of day** (optional list, below):
  - List of time pills (selected = `secondary-container`, leading `schedule`).
  - "Add time" outlined pill → opens Time Picker.
  - **Empty list allowed** = "no specific time" (dose appears as "Today" on dashboard).
- **Next-7-days preview** (`11b`, live-computed, below Times of day):
  - Card `surface-container`. Header: `calendar_month` + "Next 7 days · {n} doses".
  - 7 equal-grow day cells from today (or `startDate` if it is later): weekday initial + day of month, a `primary-container` fill and a `primary` dot on the days the schedule doses.
  - It reads the **same schedule rule the generator does** (`Protocol.dosingTimesOn`, §5.2) over the same 7-day horizon Save will write, so the count it shows is the number of Pending rows the save produces.

**Duration**:
- 2-column row: **Start** date box + **End** (Optional) date box. Each box: `surface-container`, "Start" / "End" label + value row w/ `today` icon. Tap → Material Date Picker.

**Reminder**:
- Card `surface-container`. Leading `notifications` icon. Content column: "Notify at dose time" + supporting "Offset: 0 min before · normal style".
- `Switch` on right.
- When switch ON AND `dosageTimes` non-empty: schedule alarms at `dosageTime - reminderOffsetMinutes`.
- When switch ON AND `dosageTimes` empty: reveal **Reminder bucket selector** (chips Morning 9am / Afternoon 1pm / Evening 7pm) — default Morning. Alarm scheduled at fixed daily time.

**Site restriction** (Optional):
- Card `surface-container`, leading `person_pin_circle`, value "Abdomen only" or "No restriction", trailing `expand_more` → **Body region picker** (§4.0.2 reusable pattern).

**Notes** (Optional): multi-line text.

**Forecast & warnings** (live-computed):
- Section header.
- Card `surface-container`. Header: `monitoring` + "Inventory forecast".
- 3 stat tiles (equal-grow): doses left (primary-container) / days left (secondary-container) / run-out date (tertiary-container).
- **Warning row** (`error-container`): "Batch expires before protocol end" + "Jul 14 expiry · Aug 02 run-out". Shown when `compound.batchExpiryDate < runOutDate`.
- **Reorder row** (`11b`, `secondary-container`, leading `inventory_2`): "Order {n} more {containerType} by {date}" + "Avoid shortage; covers protocol through {endDate}". Shown only for a protocol with an `endDate` its current stock cannot reach: `n = ceil((doses from runOutDate to endDate × plannedDose) / amountPerContainer)`, and the order-by date is `runOutDate − 7 d` (never before today) so the order has time to arrive.
- Derivation: `dosesLeft = floor(totalStock / plannedDose)` where `totalStock = (numberOfContainers × amountPerContainer) + currentOpened.remainingAmount`, the dose converted into the stock's unit first (§3.0.4). `runOutDate` is found by walking the schedule from today until those doses are spent; past a 2-year horizon the days-left and run-out tiles read "—" rather than a number nobody plans around.

#### 4.9.4 Bottom dock
- **Cancel** (text button) + **Save protocol** (filled `primary`, leading `check`, equal-grow).
- Edit mode: "Save changes" label.

#### 4.9.5 Lifecycle section (Edit mode only)

Below Forecast & warnings:
- Section header "Lifecycle".
- 3 buttons full-width, each with a trailing `chevron_right`:
  - **Pause protocol** (`secondary-container`, leading `pause`) → `status = Paused`, then leave the form.
  - **Duplicate protocol** (`surface-container`, leading `add_circle`) → inserts a copy of **what is on screen** (unsaved edits included) with a `" (copy)"` name suffix and `status = Active`, then leave the form. The user is looking at the form, so a duplicate that dropped their edits would be the surprising one.
  - **Archive protocol** (`error-container`, leading `delete`) → confirmation dialog → soft-delete (§5.5), then leave the form.

#### 4.9.6 Pause-with-unsaved-changes flow
If user taps Pause while form has unsaved changes → dialog "Save changes before pausing?" with **Save + Pause** (primary) / **Pause without saving** / **Cancel**. An untouched form skips the dialog and pauses straight away.

Each answer writes something different:
- **Save + Pause** — one update carrying `status = Paused`, so §4.9.7's edit path runs once: the edits land, §5.4's pending-regen purges the Pending rows, and regeneration yields nothing because the protocol it reads is already paused (§5.2). Two writes (update, then pause) would rebuild a horizon for a protocol about to stop dosing.
- **Pause without saving** — `status = Paused` only. The edits are dropped, and the Pending rows the protocol already had are left where they are, exactly as Pause from an untouched form leaves them.
- **Cancel** — nothing is written and the form stays open with its edits intact.

Save + Pause is still a save, so a form that fails validation (§4.9.3) marks its fields and stays open rather than pausing.

#### 4.9.7 Save behavior
- **Create**: insert Protocol row; generate ScheduledDoses for next 7 days (capped by endDate).
- **Edit**: update Protocol; regenerate all `ScheduledDose where status=Pending` (incl. snoozed in future). Logged history immutable. Cancel + re-schedule alarms.

---

### 4.10 Logging

5 entry points → 4 dose log flows + 1 edit flow:
- `15 · Take Dose (bottom sheet)` — from Dashboard dose card "Take" button
- `12 · Log Dose (from Dashboard)` — from FAB "Log scheduled" or "Log manual"
- `13 · Log Dose (from Compound)` — from Compound Detail "Log dose" CTA
- `14 · Log Dose (from Protocol)` — from Protocol Detail "Log dose" CTA
- `16 · Log Grouped Event (bottom sheet)` — from Dashboard grouped admin suggestion
- `17 · Edit dose` — from any dose history row tap

#### 4.10.1 Take Dose bottom sheet (`15`)

**Trigger**: Dashboard dose card "Take" button only.

Bottom sheet modal. Scrim. Drag handle.

**Header**: `primary` avatar w/ compound icon (`colorize`) + title col ("Take {compound name}" `headline-small-emphasized` + supporting "{protocol} · scheduled {time}" / "today").

**Dose to log hero card** (`primary-container`):
- Label row: "Dose to log" left + "Edit" pill right (leading `edit`, transparent fill).
- Value row: numeric + unit + equivalence "= 0.10 mL".
- **Adjust chips row** (4 pills): -0.05 / -0.01 / +0.01 / +0.05. Filled `primary`, `on-primary` text. Tap = mutate Actual dose ±.

**Site card** (`surface-container`):
- Header: `person_pin_circle` icon + "Injection site" label (grow) + "Suggested" tag pill (right, `secondary-container`, leading `flag` icon).
- Selected site row: site name + "Change" link `primary` + `chevron_right`. Tap → §4.12.5 Site picker.
- Alternative chips row (3 outlined pills): next-best sites per rotation. Tap = select that site.

**When field** (`surface-container`): `schedule` icon + "When" label + value "Now · Tue May 26, 9:30 AM" + trailing `edit` (opens time picker).

**Inventory deduction preview** (`tertiary-container`): `inventory_2` icon + "Will deduct 0.10 mL · 3.1 mL left".

**Action row**:
- **Confirm taken** (filled `primary`, leading `check`): saves AdministrationEvent, status=Taken (deduced from Actual==Planned) or Partial (if user edited Actual < Planned).

**No Skip option** in this sheet (per spec: Skip lives on dose card overflow menu only).

#### 4.10.1.1 Long-press Confirm (`15b · Take Dose (long-press confirm with note)`)
Long-press "Confirm taken" → contextual menu overlay (`surface-container-high`) above button:
- Confirm taken (`check` icon) — same as tap
- Confirm with note (`edit` icon) — opens inline note text field before save

#### 4.10.2 Log dose full-screen forms

Three variants, same skeleton, different context:

##### a) `12 · Log Dose (from Dashboard)` — generic
- App bar: leading `close`, title "Log dose".
- Compound/Protocol chip at top — selectable.
- Planned vs Actual side-by-side (Planned only if linked to protocol).
- ±chips + "Set planned" button (resets Actual to Planned).
- Route / When / Site.
- Inventory deduction preview.
- Dock: Save dose.

##### b) `13 · Log Dose (from Compound)` — manual, no protocol context
- App bar: leading `close`, title "Manual log", supporting compound name.
- Compound chip preselected ("No protocol · manual entry" sub-line).
- **Link to protocol** (Optional) row — when set, screen morphs into protocol-linked mode (planned/actual columns appear).
- Single **Actual dose** hero card (`secondary-container`) — no Planned column.
- "Set planned" chip **hidden** in manual mode.
- Same Route / When / Site / Status / Inventory / Save.

##### c) `14 · Log Dose (from Protocol)` — protocol context
- App bar: leading `close`, title "Log dose", supporting protocol name.
- Protocol hero card (`primary-container`): avatar + compound + protocol + dose info + meta chips (Scheduled time / Dose N of M).
- Planned vs Actual split (Planned shown).
- Adjust chips.
- Same other fields.

##### Common to all 3
- **Save dose** → creates AdministrationEvent + DoseComponent(s). Marks linked ScheduledDose (if any) with matching status.

#### 4.10.3 Log Grouped Event bottom sheet (`16`)

**Trigger**: Dashboard grouped admin suggestion "Log grouped event" CTA OR §4.10.2 "Add" → grouped mode.

Bottom sheet, modal. Scrim. Drag handle.

**Header**: `tertiary` rounded-square avatar w/ `vaccines` icon + title "Log grouped injection" + supporting "N compounds · same route + site + time".

**Shared context pills** (row of editable pills, `tertiary-container`):
- Route pill (e.g. "SC" + `science` icon) — tap opens **Route picker** (§4.0.2 reusable pattern)
- Site pill (e.g. "Abdomen R" + `person_pin_circle`) — tap opens **Site picker** (§4.12.7 full-screen)
- Time pill (e.g. "Now · 8:00 PM" + `schedule`) — tap opens Material Time Picker

**Component rows** (one per included DoseComponent):
- `surface-container` card.
- `tertiary-container` round avatar w/ `colorize` icon.
- Content col: compound name + "Planned {dose} · {volume}".
- Editable dose pill on right (`secondary-container`, value + `edit` icon).
- Long-press row → remove from group.

**Add another compound** dashed-outline row: leading `add` + label.

**Safety + summary** (`tertiary-container`):
- Leading `warning` icon
- "Only log together if injected together" title
- Supporting: "Will deduct: 0.10 mL BPC-157 + 0.20 mL TB-500"

**Validation**:
- All components must be Injectable form
- Route must be SC or IM
- Min 2 components

**Mixed protocol + manual components allowed**: each DoseComponent's `protocolId` / `scheduledDoseId` can be null independently.

**Actions**:
- **Cancel** (text)
- **Log injection** (filled `tertiary`, leading `vaccines`): creates single AdministrationEvent w/ N DoseComponents sharing route + injectionSiteId + loggedAt. Each ScheduledDose linked → marked Taken.

#### 4.10.4 Edit dose (`17 · Edit dose`)

**Trigger**: Administration Event detail's "Edit dose" button.

Stripped-down form:
- App bar: leading `close`, title "Editing dose".
- Planned + Actual fields (side-by-side, `surface-container` / `secondary-container`).
- Route field.
- When field.
- Injection site field.
- **Status segmented binary**: Taken / Skipped only. Partial is deduced.
- Bottom dock: Save dose (filled `primary`, full-width).

**Status change inventory side-effects**:
- Taken → Skipped: reverses inventory deduction.
- Skipped → Taken: applies inventory deduction.
- Dose value change while Taken: applies delta (positive or negative).

---

### 4.11 Administration Event detail (`23 · Administration Event detail`)

**Trigger**: dose history row tap from anywhere.

#### 4.11.1 App bar
Leading `arrow_back`, title "Dose detail", supporting timestamp (e.g. "Tue May 26 · 8:14 PM").

#### 4.11.2 Status hero card
Full-width, `primary-container`. `primary` round avatar w/ status icon (check / schedule / close). Title = status (Taken / Partial / Skipped). Supporting = "Logged Tue May 26 · 8:14 PM". Missed doses have no `AdministrationEvent` row and cannot reach this screen (per §3.4).

#### 4.11.3 Dose components

Section header "Dose components · N".

Per-component card (`surface-container`):
- Top row: `primary-container` avatar w/ `colorize` + compound name + protocol context "Sema weekly titration" (or "Manual entry" if no protocol).
- 3 stat tiles:
  - **Planned** (`surface-container-low`): dose value
  - **Actual** (`secondary-container`): dose value
  - **Volume** (`tertiary-container`): mL value

#### 4.11.4 Field rows

- **Route**: `surface-container`, leading `science`, value "Subcutaneous".
- **Injection site**: `surface-container`, leading `person_pin_circle`, value "Abdomen · Right (lower)", supporting "Marked cooling for 7 days".
- **Notes card** (`surface-container`): `edit` header + "Notes" label + body or "No notes for this dose.".

#### 4.11.5 Inventory effect

`tertiary-container` row. `inventory_2` icon + "Deducted 0.10 mL" + "From Semaglutide opened vial · 3.0 mL remaining".

For Skipped: "No inventory deducted" message.

#### 4.11.6 Bottom dock
- **Delete** (`error-container`, leading `delete`): confirmation → hard-delete AdministrationEvent + DoseComponents, reverses inventory deduction, sets linked ScheduledDose back to Pending.
- **Edit dose** (filled `primary`, leading `edit`) → §4.10.4 Edit dose.

---

### 4.12 Sites (`18 · Sites`)

**Primary goal**: pick next injection site w/ rotation confidence.

#### 4.12.1 App bar
Leading `history` icon = decorative only (no action) — an `Icon`, not an `IconButton`, so it offers no
target for the action it does not have. Title "Sites". **No trailing action.**

There is no search here. §4.0.1's overlay exists because the compound and protocol lists are
unbounded; the site list is the fourteen preset rows of §5.8.6, all of them on one screen already, and
a search field over them narrows nothing the eye has not already found.

#### 4.12.2 Route filter chips (top of content)
Kit filter chips: All routes / SC / IM. Filters all subsequent stats + body map + carousel by route.
Only the two injected routes get a chip — an oral or topical dose has no site to rotate.

**A site carries no route** (§3.6), so the chips filter on the routes its `bodyRegion` is given at:
muscle bellies (Delt, Glute) take an intramuscular dose, subcutaneous tissue takes a subcutaneous one,
and Quadriceps — the lateral thigh — takes both. §4.12.3's "This month" tile is the one thing filtered
on the *dose's* route rather than the site's, because it counts doses and each one recorded its own.

#### 4.12.3 Stats strip

3 tiles:
- **Ready**`secondary-container`: count of sites where `avoidUntil < now AND isAvailable`. Leading `check_circle`.
- **Cooling** `error-container`: count where `avoidUntil > now`. Leading `restart_alt`.
- **This month** `tertiary-container`: count of AdministrationEvents w/ site set in current calendar month. Leading `bolt`.

Each tile: label + leading icon row (top) + value (below).

#### 4.12.4 Body map hero

`surface-container-low`.

**Top tabs** (pill segmented inside hero):
- **Front** (selected default) / **Back** — switches body view.
- **View mode toggle** (small segmented at right): **Dots** / **Heat** — switches `18` ↔ `18b` heat-map mode.

**Body silhouette canvas**:
- Head
- Neck
- Torso
- Arms
- Legs

**Front-side sites** (dots):
- Abdomen UL · UR · LL · LR
- Anterior deltoid L · R
- Lateral thigh L · R
- Forearm L · R

**Back-side sites** (when Back tab):
- Glute upper-outer L · R
- Hamstring L · R
- Lower back L · R
- Posterior deltoid L · R

**Dot states** (size/color):
- **Suggested** (next-rotation): `primary` filled + `primary` 60%-opacity ring around it (focal accent).
- **Cooling** (`avoidUntil > now`): `error` filled.
- **Recent** (used <6d ago): `secondary` filled.
- **Available** (ready, used ≥6d ago or never): `outline` filled.

**The figure**: an anatomical body, drawn rather than an asset — path data in one fixed viewport,
of which only the right half is written down and the left is the same data mirrored. Four layers:

1. the **silhouette** — a traced half of a canonical eight-head standing figure plus one arm, each
   mirrored and unioned. The arm is its own shape because a hanging arm crosses the hip: traced as
   part of the trunk outline that crossing is a self-intersection, and the wedge between forearm and
   waist fills in solid.
2. the **muscle groups** of the current view, a shade off the body and clipped to it — pectorals,
   abdominals, obliques, deltoids, biceps, forearms, quadriceps, knees and shins on Front;
   trapezius, latissimus, lower back, triceps, glutes, hamstrings and calves on Back. They carry no
   data, but a deltoid the user can *find* is what separates a body map from a plain outline, and it
   is the reason Front and Back are two different drawings rather than the same one twice.
3. the **zone** each site injects into, washed in that site's dot colour — the patch of body a dose
   actually lands in, so the map answers "where on me" and not only "which of fourteen rows". The
   wash is weighted by state: a ready site is faint, suggested and cooling are what the eye finds,
   because with fourteen presets nearly every zone is tinted at once.
4. the **dot** at the middle of that zone, in the same colour at full strength.

Every coordinate — silhouette, muscle, zone, dot — is scaled from that one viewport, so a tap
resolves against the same geometry the canvas drew and the hit target scales with the map.
Overlapping targets — the four abdomen quadrants sit within a dot's width of each other — go to the
**nearest** dot, never the first listed, or one of them is unreachable at every size.

**Front / Back are mirrored**: on Front we face the body, so a site on its **left** is drawn on the
viewer's **right**; on Back we are behind it and the two agree.

**Tap target**: a dot answers a tap out to a radius that scales with it and never falls below a
finger's width, so the map stays usable in §6.4.2's narrow left pane. Each dot also carries a
TalkBack node labelled "{site name}, {status}" (§5.10) with no pointer input of its own.

**Heat map mode** (`18b`): replaces the zone wash and the dots with blurred ellipses (`error` fill,
varying opacity 0.05–0.7 by usage frequency, outside layer blur, `RenderEffect.createBlurEffect()`
per §2.3.7). Hotter = recently/frequently used. Used to visualize over-rotation.

The frequency is the site's share of the **last 30 days**, scaled against the busiest site the route
chip left — relative and not absolute, because a rotation of two doses a week and one of two a day
would both be flat on any fixed scale, and the question this mode answers is "which of these am I
leaning on". A site with no dose in the window sits at the ramp's floor. The share is derived once,
across both body views, so Front and Back agree on what "hot" means.

Each ellipse is the **zone's own bounds** rather than one radius for all — a hamstring takes a dose
across a hand's width of muscle and a forearm does not — and the blur is unbounded in both
directions, so a blob on a deltoid radiates past the edge of the map instead of being sliced flat
against it.

The two modes **cross-fade** rather than cut: they are the same fourteen sites in two inks. The one
mark that survives the fade is the suggested site's `primary` ring, because heat has no way of saying
"use this one next" and §4.12.5's answer must still be findable on the map.

**Legend** at bottom of hero (when Dots mode): Suggested / Cooling / Recent / Ready w/ swatch dots.

**Heat legend** (when Heat mode): Recent / Cooling / Older / Untouched w/ varied-opacity dots.

#### 4.12.5 Suggested site hero

`primary-container` card.
- Top row: round `primary` avatar w/ `person_pin_circle` icon + content col:
  - Tag pill (`primary` filled, leading `bolt`): "Best for now"
  - Headline: "Abdomen · Lower right"
- Fact chips row: "14 days rested" (`primary` filled, leading `schedule`) + "Cooling complete" (`primary` filled, leading `check_circle`).
- Action row:
  - **Use this site** (large filled, `on-primary-container` background w/ `primary-container` text = inverted contrast, leading `arrow_forward`) — returns selected site to caller flow.
  - **Pick another** (text button, `on-primary-container` text) → §4.12.7 Site picker full list.

#### 4.12.6 Recent activity carousel

Horizontal scrollable row of square site cards.
- **Cooling card**: `error-container`, leading `error` avatar w/ `restart_alt`.
- **Ready card**: `surface-container`, leading `secondary-container` avatar w/ `check`.
- Each card: avatar + site name  + "N days ago".

Tap card → §4.12.8 Site detail bottom sheet.

#### 4.12.7 Site picker (`22 · Site picker`)

Full-screen list. App bar title "Pick site", supporting "For {compound} · {route}". Leading `arrow_back`.

Filter chips at top: All / Ready / Cooling (single-select).

**Suggested** section: top row = suggested site (`primary-container` highlight, "Best" pill on right).

**All sites · N** section header. Rows for every site matching filter:
- Status dot: `error` w/ `restart_alt` (cooling), `secondary-container` w/ `check` (ready).
- Site name + meta (e.g. "Last used 2 days ago", "Never used").
- Cooling pill ("Cool 2d") if applicable.

Bottom dock: Cancel + Pick site (filled `primary`, requires selection).

#### 4.12.8 Site detail bottom sheet (`18c · Site detail (bottom sheet)`)

**Trigger**: tap any dot on body map OR tap site card in carousel.

Bottom sheet modal. Drag handle. Scrim. It opens **fully expanded**, not half: the sheet ends in its
actions, and a first frame showing half the content is a first frame with nothing to press on it.

- Header: avatar w/ status (cooling = `error-container` + `restart_alt`; ready = `secondary-container` + `check`; unavailable = `error-container` + `block`) + site name + supporting "{status} · {info}".
  Unavailable overrides the dot state in both: a site left out of the rotation is still ready or still
  cooling underneath, and neither is what the user needs told about it.
- Stats row (3 tiles): Times used · Route · Last used.
  - **Times used** counts *events*, all-time: a dose that stacked two compounds (§4.10.3) used this
    site once. It is blank (`—`) until the site's doses have been read — the sheet opens on what the
    map already knew, and "0 times used" for a site with eight is a worse first frame than an empty tile.
  - **Route** is derived from the body region (§4.12.2's rule), because a site carries none of its own
    (§3.6): "Subcut", "IM", or "Subcut · IM" for the lateral thigh.
- Recent uses list (last 3 doses at this site, newest first): "{compound} · {dose}" over
  "{n} days ago · {time}". A row per dose *component*, so a stacked dose shows both compounds — which is
  what went into the site. Skipped doses are not listed and not counted: nothing was administered.
- Actions row (wraps to a second line where both labels do not fit):
  - **View history** (`secondary-container`, leading `history`) → full site history list (filtered Compound Detail-like view scoped to site).
  - **Mark unavailable** (`error-container`, leading `block`) → toggles `isAvailable`, and reads
    "Mark available" once it is off. The sheet stays open on success: the state it shows is what says
    the change took. A failed write is stated *in the sheet* — the sheet is its own window, and the
    screen's `SnackbarHost` draws behind it.

No "Use this site" CTA here (that's on Site picker / Take Dose context). This sheet is informational/management.

---

### 4.13 Settings (`20 · Settings`)

#### 4.13.2 Section: Appearance
- **Theme** row (`surface-container`, leading `dark_mode`, value "System", trailing `chevron_right`) → opens **Theme picker dialog** (`20b · Settings (Theme picker dialog)`):
  - dialog, centered, `surface-container-high`.
  - Title "Theme".
  - 3 radio rows: System default (`phone_android`, selected default) / Light (`light_mode`) / Dark (`dark_mode`).
  - Buttons row right-aligned: Cancel (text, `primary`) + Save (filled `primary`).
- **Dynamic color** row w/ Switch on right.

#### 4.13.3 Section: Reminders
- **Notification style** (Silent / Normal / Persistent) — single-choice dialog.
- **Time zone** — opens searchable time zone list dialog. Default = device zone.
- **Missed dose window** (5–60 min, default 60) — numeric input sheet. After elapsed, Pending → Missed via WorkManager job.
- **Exact alarm degraded warning row** — shown only while `Settings.exactAlarmDegraded` is true. `error-container` fill, leading `warning` icon, "Exact reminders are off" + "Dose reminders may be delayed.", CTA "Enable exact reminders" → system Alarms & reminders screen for this app. Reads the persisted flag, never `AlarmManager` directly; see §5.1 for the flag's lifecycle.

#### 4.13.4 Section: Data
- **Export JSON** → file picker save → produces `stax-export-YYYY-MM-DD.json`.
- **Import JSON** → file picker open → preview dialog (row counts per entity) → confirm → ID remap import (see §5.6).
- **Reset all data** row — `error-container` fill, `error-container` text, leading `delete` icon in `error-container` themed style. Tap → typed-confirm dialog ("Type RESET to confirm") → executes reset operation per §5.11.

#### 4.13.5 Footer
App identity + version: "Stax" + "v 1.0.0" (both `on-surface-variant`). Centered.

---

### 4.14 Onboarding (`21 · Onboarding`)

**3 steps** (all skippable).

#### Step 1 — Welcome
- Blob illustration hero (3 overlapping shapes: `primary-container` + `tertiary-container` + `secondary-container`) + centered `primary` round logo w/ `vaccines` icon.
- Headline "Track your stack,\nstay on protocol." (2-line).
- Subtitle value-prop.
- **Notification permission row removed from onboarding** — handled by gate screen §4.15.
- CTA: "Continue" (filled `primary`, full-width).
- Skip text below.
- Step indicator pill row at top: 1st pill `primary` fill + 2 small pills `outline-variant`.

#### Step 2 — Add first compound (skippable)
- Reuses §4.4 Create Compound. App bar title "Add your first compound · 2 of 3".
- Skip button in app bar trailing.

#### Step 3 — Create first protocol (skippable)
- Reuses §4.9 Create Protocol. App bar title "Create your first protocol · 3 of 3".
- Skip button.

Completion → Dashboard. Persists `Settings.onboardingCompleted = true`.

---

### 4.15 Notification permission gate

**Trigger**: app launch (or resume) AND `POST_NOTIFICATIONS` not granted.

Layout: similar to Onboarding step 1 (blob illustration + headline) but:
- Headline: "Enable dose reminders"
- Subtitle: "Stax needs notification permission to remind you about scheduled doses. Without it, you'll only see reminders inside the app."
- Single CTA: "Allow notifications" (filled `primary`, full-width). Triggers system permission dialog.
- Secondary CTA (text): "Open system settings" — only shown if permission permanently denied (rationale check via `shouldShowRequestPermissionRationale = false` after previous denial).
- Continue button.

---

### 4.16 Home-screen widget

**Primary goal**: log next pending dose with zero in-app navigation.

Built with **Glance** (AndroidX `androidx.glance:glance-appwidget`). Renders Compose-style UI as RemoteViews.

#### 4.16.1 Sizes

| Size class       | Cells (w×h) | Layout                                           |
|------------------|-------------|--------------------------------------------------|
| `small`          | 2×2         | Compact: compound name + dose + Take button      |
| `medium`         | 4×2         | Compact + ETA badge + Snooze button              |
| `large`          | 4×3         | Up to 3 next pending doses (today), each w/ Take |

Use `GlanceAppWidget.sizeMode = SizeMode.Responsive` so the system picks the right layout from a `setOf` of size-keyed composables.

#### 4.16.2 Content states

1. **Next dose pending today**:
   - Compound name (`titleMedium`).
   - Dose detail row (`bodyMedium`): `0.25 mg · 0.10 mL · 8:00 PM`.
   - ETA pill (right): "in 2h 15m" / "Overdue 5m" / "Today" (no time).
   - Primary action button: **Take** (filled `primary`, `check` icon).
   - Secondary action (`medium` + `large` only): **Snooze 1h** (outlined).

2. **All done today** (no Pending but doses logged today):
   - Centered: `done_all` icon + "All done today" + "N doses · 100%".

3. **No doses today** (empty):
   - Centered: `event_available` icon + "No doses today" + tap-target → opens Dashboard.

4. **No notification permission** OR **exact-alarm degraded** (per §5.1):
   - Show small `warning` icon top-right corner of widget, tooltip = "Reminders may be delayed". Tap opens app to §4.15 / Settings.

#### 4.16.3 Theming

- Dynamic color via `GlanceTheme { ... }` — picks up Material You wallpaper colors when Android Material You is enabled, falls back to app brand colors otherwise.
- Surface = `surfaceContainer`. Take button = `primary`.
- Respects user dark/light theme set in §4.13.2.

#### 4.16.4 Interactions

- **Tap Take button** → fires `ActionRunCallback` that:
  1. Resolves the next pending `ScheduledDose` at click time (do not trust the cached one in the widget — may be stale).
  2. Opens an `Activity` deep-linked to §4.10.1 Take Dose sheet, prefilled.
  3. After save, broadcast `STAX_WIDGET_REFRESH` → widget re-fetches.
- **Tap Snooze 1h** → fires `ActionRunCallback` that updates `scheduledAt += 1h`, reschedules alarm (§5.1), refreshes widget. No activity launch.
- **Tap anywhere else on widget body** → opens Dashboard.
- **Tap warning icon** (state 4) → opens app at §4.15 or Settings Exact alarms row.

#### 4.16.5 Refresh

- Glance state is observed via `updateAppWidgetState`. Triggered by:
  - `AdministrationEvent` insert/update/delete (post-DB-commit broadcast).
  - `ScheduledDose` regeneration after protocol edit (§5.4).
  - TZ change (§5.7).
  - `GenerateScheduledDosesWorker` daily run.
- No periodic widget poll — purely event-driven.

---

### 4.17 Static app shortcuts

**Primary goal**: launch common flows from launcher long-press without entering Dashboard first.

Declared via `<shortcuts>` XML referenced from `AndroidManifest.xml` `<meta-data android:name="android.app.shortcuts" .../>` on the launcher Activity.

#### 4.17.1 Shortcut list

| ID                | Short label       | Long label                   | Icon            | Target                                                                 |
|-------------------|-------------------|------------------------------|-----------------|------------------------------------------------------------------------|
| `log_next_dose`   | "Log next dose"   | "Log next pending dose"      | `check`         | Deep-link to §4.10.1 Take Dose sheet for next Pending dose             |
| `log_manual`      | "Manual log"      | "Manually log a dose"        | `edit`          | Deep-link to §4.10.2-a Log Dose (Dashboard) w/ compound picker open    |
| `add_compound`    | "Add compound"    | "Add a new compound"         | `colorize`      | Deep-link to §4.4 Create Compound                                      |
| `reconstitute`    | "Reconstitute"    | "Open reconstitution helper" | `calculate`     | Deep-link to §4.6 Reconstitution Helper w/ compound picker first       |

#### 4.17.2 Deep-link routing

- Each shortcut launches the main `Activity` with an intent action `com.stax.app.action.SHORTCUT` + extra `shortcutId`.
- `Activity.onCreate` reads `shortcutId`, pushes the matching Nav 3 route onto the Dashboard stack (so back returns to Dashboard, not exits the app).
- `log_next_dose` resolves the next pending `ScheduledDose` at launch time. If none, fall back to §4.10.2-a Log Dose w/ manual entry.

#### 4.17.3 Dynamic shortcut (optional, v1.1)

If user logs the same compound at the same time-of-day ≥4 times in 14 days, pin a dynamic shortcut "Log {compound name}" via `ShortcutManager.pushDynamicShortcut`. Cap dynamic shortcuts at 2 to avoid clutter. Deferred to v1.1.

#### 4.17.4 Theming + accessibility

- Icons are 24×24dp Material Symbols Rounded rendered to adaptive shortcut icon w/ `primary-container` background.
- Each shortcut's `shortLabel` ≤ 10 chars, `longLabel` ≤ 25 chars (Android guidance).
- `disabledMessage`: "Shortcut unavailable in this state" (used if user later disables a flow — not expected at v1).

---

## 5. System Behaviors

### 5.1 Notifications and reminders

**Architecture**:
- `AlarmManager.setExactAndAllowWhileIdle` for dose reminders (medical use case justifies exact alarms). Declare `SCHEDULE_EXACT_ALARM` permission.
- **WorkManager** periodic jobs:
  - **`GenerateScheduledDosesWorker`**: runs daily device-local + on app start. Ensures every Active protocol has ScheduledDoses for next 7 days.
  - **`InventoryExpiryCheckWorker`**: runs daily. Marks Pending → Missed when `scheduledAt + missedDoseWindowMinutes < now`. Recomputes inventory warnings.
  - **`AlarmReconcileWorker`**: on boot via `BootReceiver`, re-schedules all pending alarms.
- **Notification channels**:
  - `dose_reminders` (high priority, badge, lights, vibration per user style setting)
  - `warnings` (default, no sound)
- **Widget refresh broadcast** (`STAX_WIDGET_REFRESH`, §4.16.5): emitted after every committed `AdministrationEvent` write, ScheduledDose regeneration, TZ re-anchor, and `GenerateScheduledDosesWorker` run. Glance widgets observe and re-render.

**Reminder lifecycle**:
- ScheduledDose created → if `protocol.reminderEnabled` AND `dosageTimes` non-empty: alarm scheduled at `scheduledAt - reminderOffsetMinutes`.
- ScheduledDose created AND `dosageTimes` empty AND `reminderBucket` set: alarm at bucket time for that day (Morning=09:00, Afternoon=13:00, Evening=19:00, device-local).
- **Bucket alarm aggregation**: if multiple Pending doses fall on the same date + same `reminderBucket`, schedule **one** grouped notification at the bucket time (not N). Body lists all due doses, e.g. "3 doses due this morning · tap to log". Tap → Dashboard filtered to today. The grouped notification is keyed by `(date, bucket)` so re-scheduling stays idempotent.
- Snoozing updates `scheduledAt`; reschedules alarm.
- Logging or skipping cancels.

#### Exact alarm permission handling

Dose reminders prefer exact alarms via `AlarmManager.setExactAndAllowWhileIdle`.

Before scheduling any exact reminder, the app must call
`AlarmManager.canScheduleExactAlarms()`.

If exact alarms are allowed:
- Schedule dose reminder alarms exactly at `scheduledAt - reminderOffsetMinutes`.
- On snooze, logging, skipping, protocol edit, or import, cancel and reschedule affected alarms.

If exact alarms are not allowed:
- Do not call `setExactAndAllowWhileIdle`.
- Mark reminder precision as degraded.
- Show a Settings warning: "Exact reminders are off. Dose reminders may be delayed."
- Provide CTA: "Enable exact reminders" → opens Android Alarms & reminders settings.
- Fall back to in-app due indicators and WorkManager reconciliation.
- Optionally schedule an inexact alarm/window reminder if notification permission is granted.

The app must listen for `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
When received:
- Re-check `canScheduleExactAlarms()`.
- If granted, reschedule all future Pending reminder alarms.
- If revoked, cancel exact-alarm state and mark reminders degraded.

If exact alarm scheduling throws `SecurityException`, treat it as permission revoked:
- Do not crash.
- Mark reminders degraded.
- Surface the Settings warning.

### 5.2 ScheduledDose generation

`GenerateScheduledDosesWorker` ensures 7-day horizon. Generation respects:
- **Protocol status**: only `Active`, non-archived (`deletedAt == null`) protocols generate. A paused or completed protocol produces nothing, so editing one never re-seeds the doses its pause removed.
- **Schedule type + dosageTimes**: empty `dosageTimes` → single dose per day w/ `hasTimeOfDay=false`, `scheduledAt = startOfDay(date)`. A non-empty `dosageTimes` yields one dose per time on *every* day the schedule selects, not just on `Daily` ones. `XTimesPerDay` without `dosageTimes` spreads `timesPerDay` doses evenly over the day, since `(protocolId, scheduledAt)` uniqueness needs them distinct.
- **Cycle spreading**: `XTimesPerWeek` / `XTimesPerMonth` place their `n` doses on the days of a 7- / 30-day cycle anchored at `startDate` where `(dayInCycle × n) mod cycle < n` — exactly `n` doses per cycle, spread as evenly as whole days allow (3×/week = days 0, 3, 5 — never clustered on days 0, 1, 2).
- **Active Escalation rule**: applies current dose at the time the rule fires (computed against `startDate` + accumulated doses). `AfterXDoses` counts the doses this schedule places between `startDate` and the dose being generated — breaks and off-days excluded — so a date's dose does not depend on which horizon generated it.
- **ProtocolBreak**: skip generation during off-days (compute on/off cycle from `startDate`).
- **endDate**: no generation past it; on `endDate` passing, set `Protocol.status = Completed`.

Generation is idempotent — uses `(protocolId, scheduledAt)` uniqueness; never duplicates.

### 5.3 Inventory deduction

**Compound creation (initial stock)**:
- On Create Compound (§4.4.4) save, after inserting `compound_supply` + any `opened_container`, emit exactly one `InventoryTransaction { type=InitialStock, delta=totalInitialStock }` where `totalInitialStock = (numberOfContainers × amountPerContainer) + (currentOpened?.remainingAmount ?? 0)`. Required so ledger `sum(delta)` equals current absolute state for §5.8.0 reconciliation.
- On Import (§5.6) of an existing dataset, do NOT emit `InitialStock` — imported ledgers already contain whatever history was exported.

**Container opening operation** (after first launch, when user opens a fresh closed container):
- Requires `numberOfContainers > 0`.
- Decrement `compound_supply.numberOfContainers` by 1.
- Create `OpenedContainer` with `openedAt=now` and `remainingAmount=amountPerContainer`.
- Insert `InventoryTransaction { type=ContainerOpen, delta=0 }`. `ContainerOpen` is an audit marker only; the underlying stock did not change (the unit moved from "unopened" to "opened" pool). Net stock delta = 0.
- **Already-opened variant** (§4.5.5 Create Already Opened): same operation, but `openedAt`, `remainingAmount` and the expiry come from the user. What the container has already lost never passed through the ledger, so it is booked as `InventoryTransaction { type=Manual, delta=remaining − amountPerContainer, reason="Already-opened container" }` — omitted when the container is still full.

**Closing a container that is not empty** (§4.5.4 lost/discarded): whatever is left leaves the stock with it, so it is booked as `InventoryTransaction { type=Manual, delta=−remaining }` alongside the delta-0 `ContainerClose`. An already-empty container books nothing extra — the deduction that emptied it is in the ledger already.

**Correcting an opened container's remaining** (§4.5.5 Edit): `InventoryTransaction { type=Manual, delta=new − old, reason="Remaining amount corrected" }`, read in the new unit since the same edit may have changed it.

**Manual stock addition** (Compound Detail → Adjust, §4.3.9):
- User-entered positive delta → insert `InventoryTransaction { type=Manual, delta=+x, reason="Added stock" }`.
- User-entered negative delta → insert `InventoryTransaction { type=Manual, delta=-x, reason="..." }`.

On AdministrationEvent save:
- **Taken / Partial**: for each DoseComponent:
  - Snapshot concentration: `dose_component.concentrationAtLog = compoundSupply.concentration` (may be null for unit-based forms).
  - Compute deduction:
    - If `concentrationAtLog != null` (concentration-based form):
      ```
      deduction.value = actualDose.value / concentrationAtLog.amount.value
      deduction.unit  = concentrationAtLog.per.unit
      ```
      Unit-family check applies (`actualDose.unit` family must match `concentrationAtLog.amount.unit` family; convert within family if needed).
    - Else (unit-based form, e.g. capsule/tablet count): `deduction = actualDose` directly.
  - Decrement `currentOpened.remainingAmount` by `deduction`.
  - Insert `InventoryTransaction { type=DoseDeduction, delta=-deduction, sourceEventId }`.
  - If `remainingAmount <= 0`:
    - Set `currentOpened = null`.
    - Insert `InventoryTransaction { type=ContainerClose, delta=0 }` (audit).
    - If `numberOfContainers > 0` → snackbar prompt: "Open new container?" → on confirm, run the container opening operation. Default action = auto-open after 5s timeout.
- **Skipped**: no deduction. (No Missed branch — Missed is a ScheduledDose-only state per §3.4.)

**Site cooldown on log** (route in SC / IM):
After deduction, update `injection_site.lastUsedAt = loggedAt` and `avoidUntil = loggedAt + cooldown.days`. Cooldown source order (first non-null wins):
1. `Protocol.siteCooldownDays` (per-protocol override)
2. `Settings.defaultSiteCooldownDaysSC` or `defaultSiteCooldownDaysIM` per route
3. Hardcoded fallback: 5d SC, 7d IM.

### 5.4 Protocol editing summary
- Edit save regenerates only `Pending` ScheduledDoses (incl. snoozed in future).
- Historical doses (Taken / Skipped / Missed / Partial) immutable.
- Alarms cancelled + re-scheduled.

### 5.5 Soft-delete and history preservation
- `CompoundSupply`, `Protocol` use soft-delete via `deletedAt: Instant?`.
- Active list queries filter `WHERE deletedAt IS NULL`.
- Soft-deleted entities remain readable from history screens (compound name + dose preserved on AdministrationEvent rows even after archive).
- Hard-delete reserved for Reset all data action only.

### 5.6 Export / Import

**Export** (single JSON file):
- All entities included (CompoundSupply, Protocol, ScheduledDose, AdministrationEvent, DoseComponent, InjectionSite, InventoryTransaction, Settings).
- IDs renumbered compactly per entity type (1, 2, 3…) before writing.
- Relationships preserved through renumbered IDs.
- Includes `schemaVersion: Int` for forward compatibility.
- Soft-deleted entities included with `deletedAt` intact.

**Import**:
- If database empty: imported IDs kept as-is.
- If database has data: every imported entity gets fresh auto-increment ID. Old→new ID map rewrites all FK references before insert. No conflicts.

### 5.7 Time zones
- All timestamps stored as UTC `Instant`.
- Wall-clock values (`dosageTimes`, `startDate`, `endDate`) stored as `LocalTime` / `LocalDate`, interpreted in user's current zone at display + scheduling time.
- Default zone = device. `Settings.timeZoneOverride` allows manual override (Travel mode).
- DST handled by `kotlinx.datetime` — 8:00 PM dose remains 8:00 PM local across DST transitions.

**Re-anchor mechanism**:
Each `ScheduledDose` row stores both the resolved `scheduledAt: Instant` AND the original wall-clock components used to compute it:
- `scheduled_dose.originalLocalDate: LocalDate` (always set)
- `scheduled_dose.originalLocalTime: LocalTime?` (null when `hasTimeOfDay = false`)
- `scheduled_dose.originalZone: String` (IANA zone name at generation time)

On TZ change (device zone or Settings override): for every Pending ScheduledDose, recompute `scheduledAt = originalLocalDate.atTime(originalLocalTime ?? endOfDay).atZone(newZone).toInstant()`. Re-cancel + re-schedule alarms. Logged history is never re-anchored.

### 5.8 Database implementation (Room)

Room is the single source of truth. ViewModels observe domain models mapped from Room `Flow` queries. Room entities are persistence models only; domain models are separate and created through mappers.

Room stores:
- `Instant` as epoch millis UTC.
- `LocalDate` as ISO-8601 text (`YYYY-MM-DD`).
- `LocalTime` as ISO-8601 text (`HH:mm[:ss]`).
- Enums as stable text names, never ordinals.
- `Decimal` values as canonical plain string (§3.0.1): `Decimal.toPlainString()`, no trailing zeros.
- `Quantity` as flattened columns:
  - `<field>Value: String` — canonical `Decimal` plain string, e.g. `"0.25"`, `"100"`, `"1.5"`.
  - `<field>Unit: String` — stable `UnitCode` name, e.g. `MG`, `MCG`, `ML`, `IU`, `CAPSULE`.
- `Concentration` as flattened columns:
  - `<field>AmountValue`, `<field>AmountUnit`
  - `<field>PerValue`, `<field>PerUnit`
- `Schedule`, `Escalation`, `ProtocolBreak`: flattened via `@Embedded(prefix = "schedule")` / `"escalation"` / `"break"` (see §5.8.1.1 for exact column names).
- `protocol.dosageTimes: List<LocalTime>` → child table `protocol_dosage_time(protocolId, time)`. Allows indexing by `time` for bucket-alarm aggregation queries.
- `protocol.selectedWeekdays: Set<DayOfWeek>` → packed `Int` bitmask column on `protocol` (bit 0 = Monday, bit 6 = Sunday). Single column; no child table needed.

Room must not store object references directly. Fields like `CompoundSupply.currentOpened` and `AdministrationEvent.components` are relation projections, not columns on the parent table.

#### 5.8.0 Source of truth — inventory state

`opened_container.remainingAmount` and `compound_supply.numberOfContainers` are **authoritative** mutable state. `inventory_transaction` is an **append-only audit ledger** for history and debugging — never read as truth at runtime.

Drift protection: `InventoryReconcileWorker` runs daily, sums the ledger per `compoundSupplyId`, and compares against `(numberOfContainers, currentOpened.remainingAmount)`. On mismatch:
- Debug builds: log to Logcat with structured tag `StaxInventoryDrift` (no network — see §2.1).
- Release builds: silent; set a `Settings`-side drift flag that surfaces a "Repair inventory" row in §4.13. Drift events are appended to a local rolling diagnostic file (`diagnostics.log`, ≤ 1 MB rotating) that can be exported via §4.13.4 Export JSON alongside the data export.
- **Repair flow** (user-initiated only): user taps "Repair inventory" → preview dialog shows (current state, ledger-derived state, delta per compound) → explicit "Apply repair" confirm → ledger-derived state is written back as the new mutable state, with one synthesizing `InventoryTransaction { type=Manual, reason="Ledger reconciliation" }`. The worker never auto-applies.

#### 5.8.1 Tables

| Table                   | Purpose                                                                |
|-------------------------|------------------------------------------------------------------------|
| `compound_supply`       | One compound/inventory item. Soft-deleted via `deletedAt`.             |
| `opened_container`      | Current opened container for a compound. At most one row per compound. |
| `protocol`              | Usage plan for one compound. Soft-deleted via `deletedAt`.             |
| `protocol_dosage_time`  | Child rows of `protocol.dosageTimes: List<LocalTime>`.                 |
| `scheduled_dose`        | Generated pending/history schedule rows.                               |
| `administration_event`  | One logged administration event.                                       |
| `dose_component`        | One compound inside an administration event.                           |
| `injection_site`        | Preset or user-created injection site.                                 |
| `inventory_transaction` | Append-only inventory audit ledger.                                    |
| `settings`              | Singleton row with app/user settings.                                  |

#### 5.8.1.1 Flattened column names (locked)

Devs must use these exact names — picking different ones causes migration churn.

`compound_supply`:
- `amountPerContainerValue`, `amountPerContainerUnit`
- `concentrationAmountValue`, `concentrationAmountUnit`, `concentrationPerValue`, `concentrationPerUnit`

`opened_container`:
- `remainingAmountValue`, `remainingAmountUnit`

`protocol`:
- `plannedDoseValue`, `plannedDoseUnit`
- `selectedWeekdaysBitmask: Int`
- Schedule embedded (`schedule` prefix): `scheduleType`, `scheduleInterval`, `scheduleTimesPerDay`, `scheduleTimesPerWeek`, `scheduleTimesPerMonth`
- Escalation embedded (`escalation` prefix): `escalationStartDoseValue`, `escalationStartDoseUnit`, `escalationTargetDoseValue`, `escalationTargetDoseUnit`, `escalationIncreaseAmountValue`, `escalationIncreaseAmountUnit`, `escalationIncreaseEvery`, `escalationIncreaseEveryValue`, `escalationMaxDoseValue`, `escalationMaxDoseUnit`, `escalationStopAtTarget`
- ProtocolBreak embedded (`break` prefix): `breakDaysOn`, `breakDaysOff`
- `siteCooldownDays: Int?`

`protocol_dosage_time`:
- `protocolId: Long` (FK)
- `time: String` (ISO LocalTime)
- Unique index `(protocolId, time)`

`scheduled_dose`:
- `plannedDoseValue`, `plannedDoseUnit`
- `originalLocalDate`, `originalLocalTime`, `originalZone` (§5.7 TZ re-anchor)

`dose_component`:
- `plannedDoseValue`, `plannedDoseUnit`
- `actualDoseValue`, `actualDoseUnit`
- `concentrationAmountValue`, `concentrationAmountUnit`, `concentrationPerValue`, `concentrationPerUnit` (snapshot, all nullable)
- `inventoryDeductedValue`, `inventoryDeductedUnit`

`inventory_transaction`:
- `deltaValue`, `deltaUnit`

#### 5.8.2 Relations + foreign keys

| Child                                    | Parent                    | Rule                                                                                    |
|------------------------------------------|---------------------------|-----------------------------------------------------------------------------------------|
| `opened_container.compoundSupplyId`      | `compound_supply.id`      | `CASCADE`; unique, enforces one opened container.                                       |
| `protocol.compoundSupplyId`              | `compound_supply.id`      | `NO ACTION`; compounds are archived, not hard-deleted.                                  |
| `protocol_dosage_time.protocolId`        | `protocol.id`             | `CASCADE`.                                                                              |
| `scheduled_dose.protocolId`              | `protocol.id`             | `CASCADE` only for hard reset/internal cleanup.                                         |
| `scheduled_dose.compoundSupplyId`        | `compound_supply.id`      | `NO ACTION`.                                                                            |
| `scheduled_dose.administrationEventId`   | `administration_event.id` | nullable; `SET NULL` if event is deleted.                                               |
| `administration_event.injectionSiteId`   | `injection_site.id`       | nullable; `SET NULL` if site is deleted.                                                |
| `dose_component.administrationEventId`   | `administration_event.id` | `CASCADE`.                                                                              |
| `dose_component.scheduledDoseId`         | `scheduled_dose.id`       | nullable; `NO ACTION`. Regeneration must not touch logged doses (see scope rule below). |
| `dose_component.protocolId`              | `protocol.id`             | nullable; `NO ACTION`.                                                                  |
| `dose_component.compoundSupplyId`        | `compound_supply.id`      | `NO ACTION`.                                                                            |
| `inventory_transaction.compoundSupplyId` | `compound_supply.id`      | `NO ACTION`.                                                                            |
| `inventory_transaction.sourceEventId`    | `administration_event.id` | nullable; `SET NULL` if event is deleted.                                               |

Hard-delete is only used by Reset all data or controlled cleanup. Normal user delete/archive uses `deletedAt`.

**Pending-regenerate scope rule** (used by §5.4 protocol edit, §5.6 import reconciliation):
> Regeneration deletes ONLY rows matching:
> `scheduled_dose WHERE protocolId = :id AND status = 'Pending' AND administrationEventId IS NULL`
>
> Linked-event scheduled doses (those with `administrationEventId` set, or any non-Pending status) must never be touched by regeneration. This preserves the dose history shown on Compound Detail and Protocol Detail.

#### 5.8.3 Unique constraints

- `opened_container.compoundSupplyId` is unique.
- `scheduled_dose(protocolId, scheduledAt)` is unique for idempotent generation.
- `dose_component.scheduledDoseId` is unique when non-null, so one scheduled dose cannot be logged twice.
- `protocol_dosage_time(protocolId, time)` is unique.
- `settings.id` is always `1`.

#### 5.8.4 Indexes

Required indexes:

- `compound_supply(deletedAt)`
- `compound_supply(category, form, deletedAt)`
- `compound_supply(name)`
- `compound_supply(batchExpiryDate)`
- `protocol(compoundSupplyId, status, deletedAt)`
- `protocol(status, startDate, endDate)`
- `scheduled_dose(protocolId, scheduledAt)`
- `scheduled_dose(status, scheduledAt)`
- `scheduled_dose(compoundSupplyId, status, scheduledAt)`
- `scheduled_dose(administrationEventId)` — supports regen scope query in §5.8.2
- `protocol_dosage_time(time)` — supports bucket-alarm aggregation query in §5.1
- `administration_event(loggedAt)`
- `administration_event(status, loggedAt)`
- `administration_event(injectionSiteId, loggedAt)`
- `dose_component(administrationEventId)`
- `dose_component(compoundSupplyId)`
- `dose_component(protocolId)`
- `dose_component(scheduledDoseId)`
- `injection_site(bodyRegion, side, isAvailable, avoidUntil)`
- `inventory_transaction(compoundSupplyId, at)`
- `inventory_transaction(sourceEventId)`

#### 5.8.5 Transaction boundaries

These operations must run inside one Room transaction:

- Create compound (with or without already-opened container):
  - insert `compound_supply`
  - if already-opened: insert `opened_container`
  - insert `InventoryTransaction(type=InitialStock, delta=totalInitialStock)` per §5.3
- Open container (first opening after create, or subsequent re-opens):
  - decrement `compound_supply.numberOfContainers`
  - insert `opened_container`
  - insert `InventoryTransaction(type=ContainerOpen, delta=0)` per §5.3
- Log administration event:
  - insert `administration_event`
  - insert all `dose_component` rows
  - update linked `scheduled_dose` rows
  - deduct opened inventory
  - insert inventory transactions
  - update injection site `lastUsedAt` / `avoidUntil`
- Edit administration event:
  - compute inventory delta
  - update event/components
  - update scheduled dose status
  - insert reversal/adjustment inventory transactions
- Delete administration event:
  - reverse inventory effects
  - set linked scheduled doses back to `Pending`
  - delete event/components
- Create/edit protocol:
  - insert/update protocol
  - delete/regenerate only future `Pending` scheduled doses
- Snooze dose:
  - update `scheduledAt`
  - reschedule reminder after transaction commit
- Import JSON.
- Reset all data.
- Time zone change re-anchoring of future pending doses.

Alarm scheduling happens only after the DB transaction succeeds.

#### 5.8.6 Migrations + first-launch seeding

Database starts at version `1`.

Rules:
- `exportSchema = true`.
- Schema JSON is committed to version control.
- No destructive migrations.
- Use Room auto-migrations for simple additive changes.
- Use manual migrations for renames, deletes, unit semantics changes, enum changes, or data backfills.
- Every migration from each previous version to latest must have a Room migration test.
- Export file `schemaVersion` is separate from Room DB version.

**First-launch seed** via `RoomDatabase.Callback.onCreate`:
1. Insert singleton `settings` row (`id = 1`) with defaults from §3.8.
2. Insert preset `injection_site` rows. 14 presets (each with `isAvailable = true`, no `avoidUntil`, no `notes`):

```
Abdomen Upper-Left      (bodyRegion=Abdomen,   side=Left,  sublocation=Upper)
Abdomen Upper-Right     (bodyRegion=Abdomen,   side=Right, sublocation=Upper)
Abdomen Lower-Left      (bodyRegion=Abdomen,   side=Left,  sublocation=Lower)
Abdomen Lower-Right     (bodyRegion=Abdomen,   side=Right, sublocation=Lower)
Anterior Deltoid Left   (bodyRegion=Delt,      side=Left,  sublocation=null)
Anterior Deltoid Right  (bodyRegion=Delt,      side=Right, sublocation=null)
Lateral Thigh Left      (bodyRegion=Quadriceps,side=Left,  sublocation=Outer)
Lateral Thigh Right     (bodyRegion=Quadriceps,side=Right, sublocation=Outer)
Glute Upper-Outer Left  (bodyRegion=Glute,     side=Left,  sublocation=Upper)
Glute Upper-Outer Right (bodyRegion=Glute,     side=Right, sublocation=Upper)
Hamstring Left          (bodyRegion=Hamstring, side=Left,  sublocation=null)
Hamstring Right         (bodyRegion=Hamstring, side=Right, sublocation=null)
Lower Back Left         (bodyRegion=LowerBack, side=Left,  sublocation=null)
Lower Back Right        (bodyRegion=LowerBack, side=Right, sublocation=null)
```
(Posterior Deltoid + Forearm presets may be added in v1.1 — keep seed minimal to match the body-map dots referenced in §4.12.4 today.)

#### 5.8.7 Import/export conflict behavior

Export:
- Export every table, including soft-deleted rows.
- Include `schemaVersion`, app version, exportedAt, and timezone.
- IDs are compacted per table in the JSON file.
- All foreign keys are rewritten to compact IDs.
- Rows are exported in deterministic order by table, then ID.

Import:
- Validate the entire file before writing anything.
- If `schemaVersion` is newer than supported, reject import.
- If required fields, enum values, units, or foreign keys are invalid, reject import with a preview error list.
- If database is empty, imported IDs may be preserved.
- If database has data, import appends as a separate dataset:
  - assign fresh local IDs
  - rewrite every FK through oldId -> newId maps
  - allow duplicate compound/protocol names
  - replace settings only after explicit confirmation
- Import is all-or-nothing in one transaction.
- After successful import:
  - run scheduled-dose reconciliation
  - run alarm reconciliation
  - refresh inventory/expiry warning state

### 5.9 Motion specs (M3 Expressive)

`StaxTheme` provides the expressive `MotionScheme` app-wide via `MaterialExpressiveTheme`, so every
Material 3 component animates expressively. Hand-written animations pull their specs from the
`StaxMotion` object (sourced from `MotionScheme.expressive()`); inline `tween(...)` is banned outside
`StaxMotion` (enforced by the `stax:NoInlineTween` detekt rule).

| Use                          | Spec                                                                                                |
|------------------------------|-----------------------------------------------------------------------------------------------------|
| Screen-to-screen navigation  | `MotionScheme.expressive().fastSpatialSpec()`                                                       |
| Bottom sheet enter/exit      | `MotionScheme.expressive().defaultSpatialSpec()`                                                    |
| Dose card "Take" tap → sheet | `MotionScheme.expressive().fastSpatialSpec()` + shape morph from card corner 24r → sheet corner 28r |
| Syringe fill width change    | spring damping 0.8, stiffness 380                                                                   |
| FAB → FAB menu open          | `defaultSpatialSpec()` w/ stagger                                                                   |
| Selected nav item indicator  | `fastSpatialSpec()` w/ shape morph (0r → 999r pill)                                                 |
| Day chip select              | shape morph 999r → 20r + color cross-fade 200ms                                                     |
| Theme change                 | `defaultEffectsSpec()` 300ms cross-fade                                                             |

### 5.10 Accessibility
- Status communicated through both icon + color (never color alone).
- Body map dots: each has content description "{site name}, {status}".

### 5.11 Reset all data

Hard-delete path. Triggered from §4.13.4 typed-confirm dialog only. Implemented as a single Room transaction; alarms cancelled and seed restored explicitly without relying on `RoomDatabase.Callback.onCreate` (which does not fire when rows are deleted but the DB file remains).

Sequence:
1. Cancel every scheduled alarm via `AlarmScheduler.cancelAll()`.
2. Cancel every pending WorkManager job: `GenerateScheduledDosesWorker`, `InventoryExpiryCheckWorker`, `InventoryReconcileWorker`, `AlarmReconcileWorker`.
3. Open one Room transaction:
   - `DELETE FROM` each table in FK-safe order (children before parents).
   - Re-insert the `settings` singleton row (`id = 1`) with default values per §3.8.
   - Re-insert the 14 preset `injection_site` rows per §5.8.6.
4. Reset the DataStore mirror to default `theme` + `dynamicColor` values (overwrite, do not merge).
5. Emit `STAX_WIDGET_REFRESH` so widgets repaint into their empty state.
6. Re-enqueue periodic workers as if first launch (idempotent via unique work names per §2.3.8).

Reset is irreversible. No undo snackbar; the typed-confirm dialog is the only barrier.

---

## 6. Screen inventory + navigation

### 6.1 Top-level destinations (bottom nav / side rail)
1. **Home / Dashboard** — start destination
2. **Compounds**
3. **Protocols**
4. **Sites**
5. **Settings**

### 6.2 Stacked screens (push on current destination's stack)
- Compound Detail · Create Compound · Edit Compound
- Protocol Detail · Create Protocol · Edit Protocol
- Site picker
- Log Dose (Dashboard / Compound / Protocol variants)
- Edit dose
- Administration Event detail
- Reconstitution Helper

### 6.3 Bottom sheets (modal overlays)
- Take Dose
- Log Grouped Event
- Edit Opened Container
- Create Already Opened Container
- Site detail
- FAB menu
- Compound picker / Route picker / Body region picker (§4.0.2 reusable pattern)
- Theme picker (basic dialog, not sheet)

### 6.3.1 Out-of-app surfaces
- **Home-screen widget** (§4.16) — Glance widget, small/medium/large sizes. Deep-links into Take Dose sheet or Dashboard.
- **App shortcuts** (§4.17) — static launcher shortcuts: Log next dose · Manual log · Add compound · Reconstitute.

### 6.4 Adaptive behavior

Goal: medium + expanded are first-class — UI rearranges meaningfully, not just wider columns. Follow the `adaptive` skill. Nav chrome via `NavigationSuiteScaffold`; **multi-pane via Nav3 Scene strategies** `androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy` + `SupportingPaneSceneStrategy` (passed to `NavDisplay.sceneStrategies`) — do **NOT** use `ListDetailPaneScaffold` / `SupportingPaneScaffold`. Capability queries via `WindowSizeClass`, `currentWindowAdaptiveInfo()`, and the Compose `MediaQuery` API.

#### 6.4.0 Breakpoints

| Class    | Width dp | Physical examples                                                    | Nav chrome                                |
|----------|----------|----------------------------------------------------------------------|-------------------------------------------|
| Compact  | <600     | Phone portrait                                                       | Bottom nav (5 dest)                       |
| Medium   | 600–839  | Tablet portrait · Foldable portrait unfolded · Phone large landscape | Navigation rail                           |
| Expanded | 840–1199 | Phone landscape · Tablet landscape · Foldable landscape unfolded     | Navigation rail (expanded labels visible) |

No Large / X-Large support — out of scope per §2 (no desktop, no ultra-wide).

All measurements `dp`, never `px`.

#### 6.4.1 Navigation chrome

Use `NavigationSuiteScaffold` (items supplied as `NavigationSuiteItem`s) so chrome swaps automatically across breakpoints. Drive show/hide via `rememberNavigationSuiteScaffoldState()` + `NavigationSuiteScaffoldState` (hide chrome on scroll-down / full-screen detail, restore on scroll-up) per §6.4.9.

- **Compact**: `NavigationBar` pinned bottom. 5 destinations, icon-only when selected label fits, icon+label otherwise.
- **Medium**: `NavigationRail` pinned start edge. Width `80dp`. Icon-only with active indicator pill; FAB anchored top of rail (above destinations) when current screen has a primary FAB.
- **Expanded**: `NavigationRail` pinned start edge. Width `96dp`. Icon + label below icon. FAB anchored top of rail. Optionally show short app-name header at rail top.

Rail (Medium + Expanded) takes the leading edge (LTR start). Detail/content fills remainder. Status bar + nav bar insets respected via §2.3.6 edge-to-edge.

**Every scaffold scene carries its own `sceneKey`.** `NavDisplay` keys its `AnimatedContent` by `(scene::class, scene.key)`, and both `ListDetailSceneStrategy` and `SupportingPaneSceneStrategy` build the same `ThreePaneScaffoldScene` class — so on the Material default (`sceneKey = Unit`) Compounds, Protocols, Settings and the Dashboard all collapse onto one content slot. The scaffold state remembered for the outgoing scene is then reused by the incoming scene's scaffold, whose new `Transition` finds the same `SeekableTransitionState` already in use, and the app crashes ("An instance of SeekableTransitionState has been used in different Transitions"). `StaxListDetailScene` / `StaxSupportingPaneScene` therefore make `sceneKey` a required parameter, and each feature declares its own constant.

#### 6.4.2 Per-screen layouts

##### Dashboard (§4.1)

- **Compact**: single-column scroll. Order: day chip strip → dose cards → inventory warnings → recent activity.
- **Medium**: supporting-pane Scene (`SupportingPaneSceneStrategy`) (two-pane).
  - **Main pane** (~60% width, min `400dp`): day chip strip + dose cards (primary actions live here).
  - **Supporting pane** (~40% width, min `320dp`): inventory warnings card + recent activity list. Sticky; does not scroll out.
  - Day chip strip widens; 14 chips visible without scroll.
  - FAB anchored to rail (§6.4.1), not floating over content.
- **Expanded**: three-region grid.
  - **Left** (`360dp`): "Up next" rail — vertical stack of next 5 pending dose cards (today + upcoming), each tappable to scroll the center pane.
  - **Center** (`fillRemainder`, min `560dp`): hero day chip strip (full week visible, no horizontal scroll until user navigates beyond) + active day's dose cards + grouped admin suggestion (when applicable).
  - **Right** (`360dp`): inventory warnings + recent activity (stacked, scrollable independently).
  - Swipe gestures on dose cards (§4.1.2) still active in the center pane.

##### Compounds list + Compound Detail (§4.2 / §4.3)

- **Compact**: list pushes to detail on tap.
- **Medium**: list-detail Scene (`ListDetailSceneStrategy`). List pane fixed `360dp`, detail pane fills, divider `outline-variant` 1dp. Selecting a row shows detail in the right pane without navigation push; empty selection shows the `detailPlaceholder`. Back press on detail returns focus to list (no pane swap on Medium).
- **Expanded**: list pane `400dp`. Detail pane internal layout switches to two-column:
  - Top stat strip stays full-width (§4.3.2).
  - Below: **left column** (`fillMaxWidth(0.55)`) = Opened vial card + Active protocols + Notes. **Right column** (`fillMaxWidth(0.45)`) = History section (filter chips + paginated history list).
  - Bottom dock (§4.3.9 Log dose / Adjust) spans only the detail pane, not the list pane.
  - The switch is measured against the **detail pane's own width** (`720dp`), not the window's. At the Expanded lower bound the pane is what is left after the `400dp` list pane and the navigation rail — under `350dp`, narrower than a Compact phone — and splitting that leaves history rows too narrow to read. Below the threshold the pane keeps the single-column layout above.

##### Protocols list + Protocol Detail (§4.7 / §4.8)

- **Compact**: list pushes to detail.
- **Medium**: list-detail Scene (`ListDetailSceneStrategy`) like Compounds. List `360dp`.
- **Expanded**: list pane `400dp`. Detail internal layout = two-column grid:
  - Quick action chips (§4.8.2) span full width.
  - **Left column**: Schedule card + Linked compound + Site restrictions + Notes.
  - **Right column**: Inventory forecast + Dose history (paginated).
  - Bottom dock (§4.8.9 Log dose / Archive) spans only detail pane.

##### Sites (§4.12)

- **Compact**: vertical scroll: stats strip → body map hero → suggested site → recent carousel.
- **Medium**: two-pane.
  - **Left** (~55%, min `420dp`): body map hero (Front/Back tabs + Dots/Heat toggle). Map scales up to the pane width; dots remain finger-friendly.
  - **Right** (~45%, min `320dp`): stats strip (vertical instead of horizontal — three tiles stacked) + suggested site card + recent activity carousel (now a vertical list when narrower than `360dp`).
- **Expanded**: three-region.
  - **Left** (`fillRemainder`, min `560dp`): body map hero, larger. Both Front + Back rendered side-by-side instead of tab-switched — user sees both at once. Dots/Heat toggle still applies to both. The Front/Back tabs go away with the switch: two silhouettes on screen is what they were for.
  - **Right** (`400dp`): stats + suggested + recent (same as Medium right pane).
  - Site detail bottom sheet (§4.12.8) opens as a **right-edge side sheet** instead of a bottom sheet at Expanded — `StaxAdaptiveSheet` with a `360dp` `sideSheetWidth`, which is the app's one modal sheet in all three of its shapes (§6.3). Not a `ModalNavigationDrawer`: a drawer is navigation chrome, and this sheet is content about the site the map was tapped on.
- **The arrangement is measured on the pane, not the window** — the same rule the Compound detail
  follows above, for the same reason. Sites opens beside the navigation rail, so a Medium window hands
  it about `580dp` and an Expanded one at its lower bound about `680dp`, the expanded rail taking
  ~`235dp` of it; the minimum widths above are sums no real window reaches once the rail has had its
  share. Two panes from `520dp` of pane width. Front + Back side by side is the one decision that
  reads the **window** class as well — it is §6.4.0's Expanded that §6.4.2 asks for — with a `640dp`
  pane floor under it, since two silhouettes narrower than the dots they carry are worse than tabs.
  The right pane takes the ~45% share, capped at the `400dp` above rather than pinned to it. The route
  chips span both panes rather than sitting in one, since §4.12.2 filters both.
- **The carousel reflows on the width of its own column**, not on the breakpoint: a vertical list
  under `360dp` of right pane, the horizontal row of square cards above it.

##### Settings (§4.13)

- **Compact**: vertical scrolling list of section rows.
- **Medium + Expanded**: list-detail Scene (`ListDetailSceneStrategy`). List pane `280dp` = section index (Appearance / Reminders / Data / About). Detail pane = chosen section's rows. Default selection on first open = Appearance. Dialogs (Theme picker etc.) keep their compact modal form — they're not section content.

##### Reconstitution Helper (§4.6)

- **Compact**: single-column scroll.
- **Medium**: two-column.
  - **Left** (`fillMaxWidth(0.5)`): syringe hero card + equivalence chips + dose ladder (sticky in left column as user scrolls right column).
  - **Right** (`fillMaxWidth(0.5)`): Mix inputs + Result tiles.
  - Progressive disclosure (§4.6 lead-in) collapses by default at Compact only; at Medium+ default to expanded, since horizontal space is cheap. It exists only in the single column: it is there to queue sections behind one another, and columns are the alternative to queueing them.
- **Expanded**: three-column.
  - **Left** (`360dp`): syringe hero + equivalence chips, sticky.
  - **Center** (`fillRemainder`): Mix inputs, full table layout (4 fields visible in one row instead of 2×2 grid).
  - **Right** (`320dp`): Result tiles + Dose ladder.
- **The Save dock spans the whole screen at every width**, pinned below the columns rather than sitting inside one of them — it is the screen's one commit, and a dock that only spans a half-width column reads as an action on that column.
- **The column count is measured on the pane, not the window** — the same rule the Compound detail follows above, for the same reason. This screen opens as a pane beside the navigation rail, so a Medium window hands it about `593dp` and an Expanded one at its lower bound about `744dp`. Thresholds: two columns from `520dp` of pane width, three from `1024dp`. The three-column figure is the sum of what the three columns need (`360` + `320` + `280dp` of centre, plus padding and gaps), not a breakpoint: below it the two fixed side columns would leave nothing between them, so the pane keeps the Medium halves — a phone in landscape is Expanded and gets two columns.
- **The Mix grid unfolds on the width of its own column**, not on the breakpoint (the same rule as §4.4.3's Stock row): four tiles in one row from `480dp` of column width, the 2×2 grid below it. The centre column is only as wide as the two fixed side columns leave it, and four tiles in a narrow one are four fields too narrow to read their own contents. On today's windows that centre column is around `320dp`, so the one-line table is what the layout grows into rather than what it usually shows — a narrower navigation rail or a wider window is what reaches it, and neither is this screen's to arrange.

##### Create / Edit Compound (§4.4)

- **Compact**: single-column form.
- **Medium**: two-column form.
  - **Left column**: Basics + Stock + Storage & batch.
  - **Right column**: Opened container section + Notes + Forecast preview (live) — "Stock preview": total stock entered (unopened containers × amount per container, plus whatever is left in the opened one), the container count, and the volume one container makes up to at the entered concentration. Derived from the form's own fields only; hidden while any of them is missing or half-typed. The volume line is shown **only when the concentration is per volume** — "once mixed" is reconstitution talk, and dividing a tablet count by a per-tablet strength answers a question nobody asked.
  - Section headers (§4.4.2) stay aligned across columns via shared grid.
- **Expanded**: same two-column layout but inputs wider — numeric + unit picker fits inline on a single row (not wrapped). Concentration row + "Helper" button fit on one line. The left column takes the extra width (roughly 55/45), since that is where the fields are.
- **The Stock row reflows on the width it is given, not on the breakpoint.** The Medium left column is *narrower than a Compact phone*, so the two counts stack there and sit side by side at Compact and Expanded; the "Helper" button leaves the concentration row on the same rule. Threshold: `360dp` of available column width.

##### Create / Edit Protocol (§4.9)

- **Compact**: single column.
- **Medium**: two-column.
  - **Left column**: Compound + Route + Planned dose + Schedule + Times of day + Duration.
  - **Right column**: Reminder + Site restriction + Notes + Forecast & warnings (live-updated as user changes left column).
- **Expanded**: same two-column, plus Forecast & warnings becomes a sticky inset card at top-right corner of the right column (visible while user scrolls left column). Expanded is a *width* class, so the pin also requires Medium height (≥480dp): phone landscape is 914 × 411dp, and a card that cannot scroll is clipped mid-tile once the app bar, the edit banner and the dock have taken their share of that. Below it the card goes back into the right column's scroll, i.e. the Medium arrangement.

##### Log Dose forms (§4.10.2 variants a/b/c)

- **Compact**: single-column full-screen.
- **Medium + Expanded**: two-column.
  - **Left** (`fillMaxWidth(0.55)`): Compound/Protocol context + Planned/Actual dose hero + adjust chips.
  - **Right** (`fillMaxWidth(0.45)`): Route + When + Site + Inventory deduction preview + Save dock spans full width pinned bottom.

##### Take Dose bottom sheet (§4.10.1)

- **Compact**: full-width modal bottom sheet (default).
- **Medium**: modal bottom sheet, width clamped to `560dp` centered horizontally. Still bottom-anchored; reachability from thumb on tablet held in portrait.
- **Expanded**: switches to **side sheet** anchored to the end edge, width `420dp`, full screen height. Content reflows vertically (Header → Dose hero → Site card → When → Inventory → Confirm). Easier to glance + tap from a landscape orientation.

##### Other modal bottom sheets

Take Dose pattern applies to: Log Grouped Event (§4.10.3), Edit Opened Container (§4.5), Create Already Opened Container (§4.5), Site detail (§4.12.8), Picker sheets (§4.0.2).

- Compact: bottom sheet.
- Medium: bottom sheet clamped to `560dp` centered.
- Expanded: side sheet, end edge, width `420dp` (`360dp` for Picker which has narrower content).

FAB menu (§4.1.7) stays as anchored sheet attached to the rail FAB at Medium + Expanded — no full-width modal.

##### Onboarding (§4.14)

- **Compact**: full-screen stepper.
- **Medium + Expanded**: hero illustration on left (`fillMaxWidth(0.5)`), step content + CTA on right. Step indicator at top of right column. Same skip-anywhere affordance.

##### Notification permission gate (§4.15)

Same adaptive treatment as Onboarding step 1.

##### Reconstitution syringe, body map, dose ladder

Vector renderers — scale to allocated bounds with `Modifier.aspectRatio`. Hit-test regions for body-map dots (§4.12.4) scale with the canvas.

#### 6.4.3 Foldable hinge handling

Use `WindowInfoTracker.windowLayoutInfo` to subscribe to `FoldingFeature` updates.

- **Vertical fold** (book posture):
  - If a two-pane layout is active, align the pane divider to the fold line. Allocate pane widths from the fold position, ignoring the matrix above. List pane gets the leading half, detail pane gets the trailing half.
  - If a single-pane layout is active, content is padded so no critical interactive element sits within `12dp` of the hinge.
- **Horizontal fold** (tabletop posture, e.g. Galaxy Z Fold flat-half):
  - Promote bottom-half to control surface, top-half to content. Specifically: Take Dose, Log Dose, Reconstitution Helper render the action dock + adjust chips in the bottom half, content above the fold. Treated as Medium even if width is Compact-class.
- **HALF_OPENED state**: dim the top half via `surfaceContainer @ 92% alpha` so user perceives the lower control surface.

#### 6.4.4 Orientation + window-size changes

- Use `rememberSaveable` for all form state so rotation does not lose drafts.
- Layout transitions across breakpoints animate via `AnimatedContent` w/ `MotionScheme.expressive().defaultSpatialSpec()` — no hard cut.
- Pane focus survives rotation: if Compounds list-detail was on a detail at Compact (pushed screen), rotating to Expanded preserves that detail in the right pane; the list pane re-renders with the same row selected. The `ListDetailSceneStrategy` resolves the same `NavBackStack` to one-pane vs two-pane across the size change — no manual pane bookkeeping.

#### 6.4.5 Predictive back + adaptive nav

- Predictive back enabled (Android 16 default). Back is driven by Nav3 `NavDisplay` popping the `NavBackStack`; the active Scene strategy decides pane behavior — at Compact back navigates list → detail → list, at Medium + Expanded back is consumed only if a stacked entry sits on top of the detail pane.
- Bottom-nav re-selection at Compact clears the destination's back stack to root. Same behavior at Medium + Expanded on rail re-tap.

#### 6.4.6 FAB placement

| Breakpoint | FAB position                                                                     |
|------------|----------------------------------------------------------------------------------|
| Compact    | Floating bottom-end of its pane, above the bottom nav (`16dp` inset)             |
| Medium     | Same — floating bottom-end of its pane (`16dp` inset)                            |
| Expanded   | Same — floating bottom-end of its pane (`16dp` inset)                            |

FAB icon, label and behavior unchanged across breakpoints (still §4.1.6 direct-log-or-menu). An extended FAB keeps its label at every width.

**Why not the navigation rail's FAB slot** (M5-06's original plan): that slot belongs to `NavigationSuiteScaffold` in `:app`, so a FAB placed there is chrome. It outlives the screen, cannot read the screen's state — Compounds hides its FAB in multi-select (§4.2.4), Dashboard's FAB changes behaviour with the day's doses (§4.1.6) — and would route its action around the screen's ViewModel, against §10.1. Placing it top-start of the pane instead (the first M7-02 build) is worse still: it lands on the app bar. The FAB is the screen's, so it stays in the screen's pane at every width; `AdaptiveFab` owns the placement.

#### 6.4.7 Type + density scaling

- M3 type styles unchanged; density does not multiply.
- Body type stays at `bodyLarge` for primary read at Medium + Expanded (not bumped to display); larger surfaces get more cards, not larger text. Avoids "stretched phone" feel.
- Hero card max-width clamped: dose card hero, suggested site hero, status hero — cap at `720dp` width. Beyond cap, center-anchor.

#### 6.4.8 Testing matrix

Adaptive layouts must be tested on at least these device profiles via `createComposeRule()` + `DeviceConfigurationOverride`:

| Profile                                 | Width dp | Height dp | Notes                                             |
|-----------------------------------------|----------|-----------|---------------------------------------------------|
| Pixel 10 portrait                       | 411      | 914       | Compact                                           |
| Pixel 10 landscape                      | 914      | 411       | Expanded                                          |
| Pixel 10 Pro Fold cover portrait        | 412      | 891       | Compact (folded); covers outer-screen daily use   |
| Pixel 10 Pro Fold inner portrait        | 673      | 841       | Medium, vertical hinge                            |
| Pixel 10 Pro Fold inner landscape       | 841      | 673       | Expanded, possible horizontal hinge (tabletop)    |
| Samsung Galaxy Z Fold 5 cover portrait  | 388      | 994       | Compact, narrower-than-typical Compact (test min) |
| Samsung Galaxy Z Fold 5 inner portrait  | 673      | 841       | Medium, vertical hinge                            |
| Samsung Galaxy Z Fold 5 inner landscape | 841      | 673       | Expanded, horizontal hinge (tabletop posture)     |
| Pixel Tablet portrait                   | 800      | 1280      | Medium                                            |
| Pixel Tablet landscape                  | 1280     | 800       | Expanded (clamped to 1199 by §6.4.0)              |

These profiles are driven by **Robolectric qualifiers** (`@Config(qualifiers = "w411dp-h914dp")`) on JVM Compose tests in `src/test`, not by `DeviceConfigurationOverride`: the window size class comes from `WindowMetricsCalculator` on the host Activity, which a `DeviceConfigurationOverride.ForcedSize` does not move. `DeviceConfigurationOverride` still applies to everything it does own (font scale, locale, layout direction, dark mode).

Samsung Z Fold 5 cover screen (`388dp`) is the narrowest realistic Compact target — UI must not clip there. Validate every Compact layout against this profile specifically.

`FoldingFeature` posture testing: parameterize hinge tests with `FoldingFeature.State.HALF_OPENED` + both `Orientation.VERTICAL` (book) and `Orientation.HORIZONTAL` (tabletop) using Jetpack `WindowLayoutInfoPublisherRule`.

CI runs one Compose UI test per major screen per profile = ~10 × 12 screens = 120 fast tests. Acceptable runtime via shared `ComposeTestRule` + parallel sharding.

Per the `adaptive` + `testing-setup` skills, each major screen also ships a `@PreviewTest` `@FormFactorPreviews` composable (Phone / Foldable / Tablet / Desktop) for the Compose Preview Screenshot Testing tool, so layout regressions across form factors are caught as golden-image diffs (see §10.5, M19-04). Reference images are regenerated only on intentional UI change.

#### 6.4.9 Adaptive lists + app bars

Per the `adaptive` skill:
- **Lazy lists** (`LazyColumn` / `LazyVerticalGrid`): use `GridCells.Adaptive(<minWidth>.dp)` so column count grows with width at Medium + Expanded instead of stretching rows. Choose a min width that keeps each item legible.
- **Non-lazy** fixed-count rows of the same item type: use the experimental `Grid` (`@OptIn(ExperimentalGridApi::class)`), reconfiguring rows/columns from `constraints` — not a lazy layout, not nested in a `Column`.
- **App bars** hide on scroll independently per top-level destination: `exitUntilCollapsedScrollBehavior` (stays hidden until offset 0) or `enterAlwaysScrollBehavior` (reappears immediately on scroll-up). Nav chrome visibility is coordinated via `NavigationSuiteScaffoldState` (§6.4.1).
- `Grid`, `FlexBox`, and `MediaQuery` are experimental (Compose `1.11.0-beta01`+) and require explicit opt-in.

---

## 7. Empty / error states

| Screen                                  | Empty state                                 | Error state                         |
|-----------------------------------------|---------------------------------------------|-------------------------------------|
| Dashboard                               | §4.1 state 2                                | Toast "Couldn't load doses · Retry" |
| Compounds                               | "No compounds yet · [Add compound]" hero    | —                                   |
| Protocols                               | "No protocols yet · [Create protocol]" hero | —                                   |
| Sites                                   | "No sites yet" hero w/ "Add site" CTA       | —                                   |
| Dose history (Compound/Protocol Detail) | "No doses logged yet" inline                | —                                   |
| Inventory tab                           | "No inventory adjustments" inline           | —                                   |
| Site detail · recent uses               | "Never used at this site"                   | —                                   |

---

## 8. Validation rules summary

| Field                               | Rule                                                            |
|-------------------------------------|-----------------------------------------------------------------|
| CompoundSupply.name                 | required, ≥1 char, ≤80 chars                                    |
| CompoundSupply.amountPerContainer   | > 0                                                             |
| CompoundSupply.numberOfContainers   | ≥ 0                                                             |
| CompoundSupply.concentration        | > 0 if set; required when Form=Injectable AND Container≠Ampoule |
| Protocol.name                       | required, ≥1 char, ≤80 chars                                    |
| Protocol.plannedDose                | > 0                                                             |
| Protocol.startDate                  | required                                                        |
| Protocol.endDate                    | if set, must be > startDate                                     |
| Schedule.interval (EveryXDays)      | ≥ 1                                                             |
| Schedule.timesPerDay                | ≥ 1                                                             |
| Schedule.selectedWeekdays           | ≥ 1 day                                                         |
| Escalation.targetDose               | > startDose                                                     |
| Escalation.increaseAmount           | > 0                                                             |
| Escalation.increaseEveryValue       | ≥ 1                                                             |
| ProtocolBreak.daysOn                | ≥ 1                                                             |
| ProtocolBreak.daysOff               | ≥ 0                                                             |
| OpenedContainer.remainingAmount     | ≥ 0, ≤ compound.amountPerContainer                              |
| OpenedContainer.openedAt            | ≤ now                                                           |
| DoseComponent.actualDose            | > 0 when status=Taken or Partial                                |
| AdministrationEvent.injectionSiteId | required when route in (SC, IM)                                 |

---

## 9. Design tokens reference

**Used roles** (non-exhaustive):
- `primary`, `on-primary`, `primary-container`, `on-primary-container`
- `secondary`, `on-secondary`, `secondary-container`, `on-secondary-container`
- `tertiary`, `on-tertiary`, `tertiary-container`, `on-tertiary-container`
- `error`, `on-error`, `error-container`, `on-error-container`
- `surface`, `on-surface`, `on-surface-variant`
- `surface-container-low`, `surface-container`, `surface-container-high`, `surface-container-highest`
- `outline`, `outline-variant`

These standard M3 roles are read **directly** from `MaterialTheme.colorScheme` (e.g. `MaterialTheme.colorScheme.surfaceContainerLow`) — they are **not** re-wrapped in a tokens file.

**Semantic color tokens** (`StaxColors` in `:core:design-system` `Tokens.kt`, M4-06): app-domain colors that M3 does **not** provide as a role — dose status (taken / missed / skipped / partial), low-stock vs `error`, success, the heat-map gradient ramp (§4.12.4), body-map dot + syringe-fill colors. Each is defined as an alias of an M3 `colorScheme` role where one fits (e.g. missed → `error`, skipped → `outline`), or a custom color **only** where M3 has no suitable role. `Tokens.kt` is the **single legal home for raw `Color(0xFF…)` literals** (including the fallback color-scheme seeds otherwise in `StaxTheme`); a raw color literal anywhere else fails lint (`stax:NoRawColorLiteral`, mirroring the motion/shape guards). Standard M3 roles are **not** duplicated here.

**Type scale**: pure M3 styles (`display`, `headline`, `title`, `body`, `label` with `-emphasized` variants where applicable). Font family = **Google Sans Flex**.

**Shape scale**: M3 Expressive shape scale, defined in `StaxShapes.material` (a `Shapes`) and wired to `MaterialTheme.shapes` by `StaxTheme` (via `MaterialExpressiveTheme`). Base slots: `extraSmall` 4dp · `small` 8dp · `medium` 12dp · `large` 16dp · `extraLarge` 28dp; the three M3 Expressive "increased" slots (`largeIncreased`, `extraLargeIncreased`, `extraExtraLarge`) keep their `ShapeDefaults` values. Plus a `StaxShapes.Pill` token (≈999r) for chips / status badges / the selected nav indicator. Components read shapes from `MaterialTheme.shapes.<slot>` or `StaxShapes.Pill` — **never** inline `RoundedCornerShape(...)`, which is banned outside `:core:design-system` by the `stax:NoInlineRoundedCornerShape` detekt rule.

**Components**: Material 3 components throughout, themed via **`MaterialExpressiveTheme`** (color / type / shape / **motion**) — `StaxTheme` wraps content in it so the expressive `MotionScheme` is provided app-wide and every M3 component animates expressively (§5.9), not just hand-written animations. Custom design-system components (syringe visualization §4.6, body-map renderer §4.12, dose card §4.1) are built on Compose primitives + the same `MaterialTheme` tokens — no separate styling system.

**Icons**: the `Icon` composable + hand-picked **Material Symbols Rounded** vector drawables, owned by `:core:design-system`. Rules:
- Assets live in `:core:design-system/src/main/res/drawable/`, named `ic_<name>.xml` (outlined) and `ic_<name>_filled.xml` (filled — e.g. the selected bottom-nav state). `<name>` is the Material Symbol's snake_case name (`ic_home`, `ic_calendar_month`).
- Exported from [fonts.google.com/icons](https://fonts.google.com/icons) as **Android Vector Drawable**, style **Rounded**, weight **400**, grade **0**, optical size **24dp**, single black fill. Color is applied at runtime via `Icon`'s `tint` / `LocalContentColor` — never bake color into the asset.
- A type-safe accessor `StaxIcons` maps each icon to its `painterResource`; feature code references `StaxIcons.X`, never raw `R.drawable` ids or string names. Every `Icon` gets a `contentDescription` (or `null` when purely decorative) per §5.10 / M17.
- **No icon font; do not add `androidx.compose.material.icons-extended`** (size + a11y + type-safety reasons).
- **Missing-icon policy (normative)**: if a screen needs an icon that is not already in `res/drawable`, **stop and request it** — state the exact Material Symbol name (Rounded; weight 400 / grade 0 / 24dp; plus the `_filled` variant if it is a selectable/nav icon). Do **not** invent an icon, substitute a different one, reuse an unrelated icon, or pull from `material-icons-extended`. The maintainer downloads the asset and drops it in.

---

## 10. Architecture conventions

Locked at scaffold time. Diverging from these creates churn.

### 10.1 MVI per screen

Every screen has a `ViewModel` exposing a `state: StateFlow<S>` + an `events: Channel<E>` (consumed via `receiveAsFlow()`), and a single `onAction(action: A)` entry point.

```kotlin
sealed interface DashboardAction {
  data object OnRefreshClick : DashboardAction
  data class OnSelectDay(val date: LocalDate) : DashboardAction
  data class OnTakeDoseClick(val scheduledDoseId: Long) : DashboardAction
  // ...
}

data class DashboardState(
  val pendingDoses: ImmutableList<DoseCardUi>,
  val selectedDate: LocalDate,
  val isLoading: Boolean,
  val error: UiText? = null,
  // ...
)

sealed interface DashboardEvent {
  data class NavigateToTakeDose(val scheduledDoseId: Long) : DashboardEvent
  data class ShowSnackbar(val message: UiText) : DashboardEvent
}

class DashboardViewModel(/* injected */) : ViewModel() {
  private val _state = MutableStateFlow(DashboardState(...))
  val state: StateFlow<DashboardState> = _state.asStateFlow()

  private val _events = Channel<DashboardEvent>()
  val events = _events.receiveAsFlow()

  fun onAction(action: DashboardAction) { ... }
}
```

Rules:
- `state` is `StateFlow<S>` exposed to Compose via `collectAsStateWithLifecycle()`. Mutate via `_state.update { it.copy(...) }`.
- `events` is `Channel<E>` (capacity = `Channel.BUFFERED` by default) exposed as a `Flow` via `receiveAsFlow()`. One-shot side effects only. Channel — not SharedFlow — so an emit is never dropped while the screen is paused.
- `onAction(action)` returns `Unit`; coroutine work goes to `viewModelScope`.
- State data classes contain only primitives + value classes + `ImmutableList` / `ImmutableSet` / `ImmutableMap` (kotlinx-collections-immutable). No Room entities, no domain models, no mutable collections — UI models only (see §2.3.1).
- Each screen ships a **Root** composable + **Screen** composable in the same file. Root holds the VM via `koinViewModel()` and observes `events` via `ObserveAsEvents`. Screen receives `state` + `onAction` only and is independently previewable.
- User-facing error strings flow through `UiText` (see `android-presentation-mvi` skill).

### 10.2 Repository layer

ViewModels never see Room or DataStore directly. Repositories own DAO access and entity → domain mapping.

- Module: every repository implementation lives in `:core:data`; its interface lives in `:core:domain`. Feature presentation modules depend on the interface only.
- Koin scope: bind impl via `singleOf(::RoomCompoundRepository) { bind<CompoundRepository>() }` per `android-di-koin`.
- DAO `Flow` queries collected inside repository, mapped to domain models, exposed back as `Flow<Domain>`.
- Mutating ops return `Result<T, DataError.Local>` or `EmptyResult<DataError.Local>` per `android-error-handling`. Validation failures use a feature-specific `Error` enum (e.g. `CompoundValidationError`).
- Transactions in §5.8.5 are implemented at the repository layer with `@Transaction` DAO methods.

**Naming convention** (Stax): distinguishes "data source" (single source) from "repository" (multi-source). Stax has no remote source — everything is local Room — so by the skill's strict definition all our accessors are data sources. We nevertheless use `Repository` for all of them because (a) most of them aggregate across multiple Room tables (compound + opened_container + inventory_transaction) which qualifies as multi-source in practice, and (b) the spec and ISSUES reference "repository" uniformly. No DTOs exist (no network).

### 10.3 Navigation 3 — typed routes

Follow the `navigation-3` skill. All routes are `@Serializable` Kotlin types implementing `NavKey`. No string routes, no `NavController` / `NavHost` / `NavGraphBuilder` (those are Navigation 2).

```kotlin
@Serializable data object DashboardRoute : NavKey
@Serializable data object CompoundsRoute : NavKey
@Serializable data class CompoundDetailRoute(val compoundId: Long) : NavKey
@Serializable data class CreateCompoundRoute(val templateForm: Form? = null) : NavKey
@Serializable data class EditCompoundRoute(val compoundId: Long) : NavKey
@Serializable data class ProtocolDetailRoute(val protocolId: Long) : NavKey
// ...
```

Rules:
- One file `Routes.kt` per feature presentation module exporting its `@Serializable` `NavKey` routes.
- `:app` hosts a single `NavDisplay` whose `entryProvider` is assembled from one `EntryProviderScope.<feature>Entries(onNavigateToX, onNavigateToY, ...)` extension per feature presentation module (the Nav3 equivalent of the old per-feature graph). Cross-feature navigation is wired as lambda callbacks in `:app`; feature modules never import another feature's route. Decouple per the `navigation-3` **modular (Koin)** recipe.
- Back stack is a `NavBackStack` (`rememberNavBackStack()`, a `SnapshotStateList<NavKey>`); the model is per top-level destination (Home / Compounds / Protocols / Sites / Settings each own a stack — Nav3 **multiple back stacks** recipe). `NavDisplay` is supplied the active stack + the Scene strategies (§6.4) + `NavigationSuiteScaffold` chrome.
- Save-state behaviour: the `NavBackStack` is saveable and survives process death; route params reach the ViewModel through the `NavKey` passed to its entry (Nav3 "passing arguments" recipe) — no `toRoute<T>()`.
- Bottom sheets and dialogs are NOT modeled as routes by default — they live in the parent screen's state (`state.showXSheet` flag; back press handled by parent). Nav3 offers built-in `Dialog` + custom `BottomSheet` Scenes; adopt those only where a sheet/dialog needs its own deep-linkable back-stack entry.

### 10.4 Module layout

Per the `android-module-structure` skill, with one Stax-specific shape: a single shared Room DB + heavily interlinked domain → domain + database + repository impls live in `:core:*`; only `presentation` is split per feature.

```
:app                               # wires modules, NavDisplay + entryProvider, Application class
:build-logic                       # Gradle convention plugins (stax.android.application, stax.android.feature, ...)

:core:domain                       # all domain models, repository interfaces, errors (Error/DataError), Result, Decimal/Quantity/Concentration
:core:database                     # Room @Database, all entities, all DAOs, migrations, seed callback
:core:data                         # repository impls, mappers, Settings + DataStore wiring
:core:presentation                 # UiText, ObserveAsEvents, shared UI utilities, error → UiText extensions
:core:design-system                # M3 Expressive theme, motion specs, Nav3 Scene-strategy wrappers (list-detail / supporting-pane), AdaptiveFab, NavigationSuiteScaffold chrome, icons, design tokens

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

| Layer                                            | May depend on                                               |
|--------------------------------------------------|-------------------------------------------------------------|
| `:core:domain`                                   | nothing                                                     |
| `:core:database`                                 | `:core:domain`                                              |
| `:core:data`                                     | `:core:domain`, `:core:database`                            |
| `:core:presentation`                             | `:core:domain`                                              |
| `:core:design-system`                            | nothing                                                     |
| `:feature:<x>:presentation`                      | `:core:domain`, `:core:presentation`, `:core:design-system` |
| `:widget`, `:shortcut`, `:work`, `:notification` | `:core:domain`, `:core:data`                                |
| `:app`                                           | everything                                                  |

Features never depend on each other. Cross-feature integration is the responsibility of `:app` (Navigation 3 callbacks per §10.3). Enforced by the `checkForbiddenModuleDependencies` Gradle task in root `build.gradle.kts` (allow-list mirrors the dependency-rules table; wired into `check`), introduced in M0-13.

Documentation: every module owns a top-level `CLAUDE.md` (with an `AGENT.md` symlink → `CLAUDE.md`) describing purpose + allowed dependencies + key types + applicable skills; every package within a module owns a `_Package.kt` with KDoc on its `package` declaration. Both are kept in sync with code changes per ISSUES X-05 / X-06. The repo root also owns a `CLAUDE.md` (+ `AGENT.md` symlink) — the entry point for any AI agent (§10.6).

### 10.5 Testing surface

Harness setup follows the `testing-setup` skill; ViewModel/UI patterns follow `android-testing`.

| Layer      | Tool                                                                                            | What                                                         |
|------------|-------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Domain     | JUnit5 + AssertK                                                                                | escalation math, in-break, dose math, validation             |
| Data       | Robolectric + Room in-memory + fakes (over mocks)                                               | DAO queries, transaction boundaries, FK rules                |
| Migration  | Room `MigrationTestHelper`                                                                      | every version-to-latest path                                 |
| ViewModel  | Turbine + `UnconfinedTestDispatcher` + `Dispatchers.setMain` + fake repositories                | action → state transitions, events                           |
| UI         | Compose `createAndroidComposeRule()` on Robolectric (`@Config(qualifiers)` for the breakpoint, `DeviceConfigurationOverride` / `WindowLayoutInfoPublisherRule` for the rest) | golden-path flows per screen, Nav3 scene + breakpoint matrix |
| Screenshot | Compose Preview Screenshot Testing (`@PreviewTest` + `@FormFactorPreviews`) / Roborazzi         | per-form-factor layout golden diffs (§6.4.9)                 |
| E2E        | Macrobenchmark + Baseline Profile                                                               | hot paths in §2.3.3                                          |

### 10.6 Codebase documentation mechanism

Goal: an AI agent (or human cold-reading) can orient inside any module in under 30 seconds before touching code. Three artifacts, all mandatory, all enforced in CI.

**Root `CLAUDE.md`** (repo root, with an `AGENT.md` symlink → `CLAUDE.md`). The single entry point for any AI agent: project overview, tech stack, architecture (§10.1–§10.5), module map + dependency rules, the normative skill set (ISSUES "Skill alignment" table) and which skill governs which work, build/test commands, the spec/issue workflow (`ISSUES.md` = source of truth, `scripts/sync-issues.py`, `start-oldest-feature.sh`), and project-wide gotchas. `CLAUDE.md` is auto-loaded by Claude Code when working anywhere in the tree; `AGENT.md` is the cross-tool alias for other agents.

**Per-module `CLAUDE.md`** (top of every Gradle module, each with an `AGENT.md` symlink → `CLAUDE.md`). Required sections: Purpose (one paragraph), Module coordinates (Gradle path + package namespace + convention plugins applied), Allowed dependencies (cite §10.4 dependency-rules table), Key types (bullet list w/ one-line roles), Applicable skills (which reference skills govern this module), Owned by (feature name or "shared"), Notes (Stax divergences, perf budgets, transactional boundaries).

**Per-package `_Package.kt`** (one per Kotlin package, KDoc-only file). Kotlin has no `package-info.java`; this is the idiomatic substitute. Required content (1–3 sentences): Purpose, Boundaries (what does NOT live here — e.g. "no Room imports"), Entry points (1–3 most-likely-touched public symbols).

Template:
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

**Sync rule**: any PR that adds/removes a public type in a module updates that module's `CLAUDE.md`. Any PR that introduces a new package adds a `_Package.kt`. Any PR that materially changes a package's public API updates the relevant `_Package.kt`. The `AGENT.md` symlink is created once per module and never edited directly (it resolves to `CLAUDE.md`). Enforced by `scripts/check_docs_drift.sh` (CLAUDE.md drift + presence of the `AGENT.md` symlink) + custom detekt rule `MissingPackageKDoc` (see ISSUES X-05 / X-06).
