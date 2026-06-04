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
- Composables receive **UI models**, never domain models directly. Domain models live in `domain/` + repositories + ViewModels; ViewModels map domain → UI model when emitting state. UI models are `data class` with primitive fields + value classes + immutable collections only.
- `@Immutable` on shared read-only value types reused inside UI models: `Quantity`, `Concentration`, `Decimal`.
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
5. `FontPreloadInitializer` — measure cost before deciding eager vs async. If Google Sans Flex + Material Symbols Rounded combined >40ms on mid-tier device, load async with Compose `FontFamily` fallback for first frame, swap when ready

#### 2.3.5 Storage / SQLite
- Room journal mode = `WRITE_AHEAD_LOGGING`. Enables concurrent reads during background-worker writes.
- `PRAGMA foreign_keys=ON` (Room default).

#### 2.3.6 Edge-to-edge
- `enableEdgeToEdge()` in `Activity.onCreate`.
- All padding derived from `WindowInsets.statusBars`, `WindowInsets.navigationBars`, `WindowInsets.ime`. No hardcoded inset dimensions anywhere.

#### 2.3.7 RenderEffect blur
Heat-map (§4.12.4) uses `RenderEffect.createBlurEffect()` via `Modifier.graphicsLayer { renderEffect = ... }`. No CPU-blur fallback needed — app is Android 16+.

#### 2.3.8 Background contention
`GenerateScheduledDosesWorker` may run while user is in the Take Dose sheet. Serialize with `ExistingWorkPolicy.KEEP` + unique work name. DB transactions already isolated per §5.8.5; this prevents duplicate enqueue.

### 2.4 Technology
- Kotlin + Jetpack Compose + Material 3 Expressive
- MVI architecture, Koin DI
- **Google Sans Flex** font family (Regular, Medium, SemiBold, Bold, Light)
- **Material Symbols Rounded** for icons (load font via App Startup; render via text glyphs)
- Room database, WorkManager (background), AlarmManager (exact reminders)
- Navigation 3, adaptive: bottom nav (compact), side rail (medium/foldables unfolded). Libs: `androidx.compose.material3.adaptive.navigation3`, `androidx.compose.material3.adaptive.layout`.
- Glance for home-screen widgets (§4.16): `androidx.glance:glance-appwidget`, `androidx.glance:glance-material3`.
- Static app shortcuts via `<shortcuts>` XML (§4.17). No `androidx.sharetarget` needed at v1.
- only support android 16 and above, to be able to use all the new features
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
type: Manual | DoseDeduction | ContainerOpen | ContainerClose
sourceEventId: Long?                    // AdministrationEvent.id when type=DoseDeduction
reason: String?                         // user-provided note for manual
at: Instant
```

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

App bar transforms into contextual app bar:
- Leading `close` → exits multi-select.
- Title: "N selected" (live count).

**Row visual**: outlined leading checkbox circle before avatar(shifted to the right).

**Selected row visual**: fill = `secondary-container`, leading checkbox filled with `primary` and `check`.

Bottom nav **hidden** during multi-select mode. Bottom dock appears instead:
- **Duplicate** (tonal `secondary-container` button, equal-grow): creates copies with " (copy)" suffix, fresh IDs, no opened container, no batch number.
- **Archive** (`error-container` button, equal-grow): opens confirmation dialog "Archive N compounds? Logged history is kept." — confirm → sets `deletedAt = now()` for all selected.

After action completes, exits multi-select mode. No undo snackbar.

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

#### 4.3.9 Bottom dock

Sticky `surface-container-low`. Two buttons:
- **Log dose** (filled `primary`): leading `add` icon. → §4.10.2-b Log Dose (from Compound).
- **Adjust** (tonal `secondary-container`): leading `inventory_2` icon. → Adjust Edit compound screen.

Bottom nav is hidden on this screen.

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

**Stock**:
1. **# of containers** — numeric total owned, side-by-side w/ next. Persistence stores this as unopened count: if no opened container is added, `numberOfContainers = total`; if an opened container is added, `numberOfContainers = total - 1`.
2. **Amount per container** + unit picker.
3. **Concentration** (Optional) — numeric + unit picker inline. **Trailing "Helper" tonal button** (`secondary-container`, `calculate` icon) → §4.6 Reconstitution Helper. Required only when `Form == Injectable AND ContainerType != Ampoule` (pre-mixed).

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
- Auto-save draft on backgrounding (resume restores form state).
- Discard confirmation dialog when X pressed w/ dirty form: "Discard changes?" + Discard / Keep editing.

---

### 4.5 Opened Container bottom sheets

Two variants:
- `6 · Edit Opened Container (bottom sheet)` — opened from Compound Detail or Edit Compound when container exists.
- `7 · Create Already Opened Container (bottom sheet)` — opened from Create/Edit Compound "Mark as already opened" CTA. Identical UI minus Delete button.

#### 4.5.1 Sheet structure
Bottom sheet modal. Drag handle + Scrim overlay.

#### 4.5.2 Header
Inline at top of sheet content: title ("Add opened {container type}" / "Edit {container type}") + subtitle (compound name + size e.g. "Semaglutide · 5 mg vial"). Right side: `close` icon button.

#### 4.5.3 Fields
1. **Opened date** — date picker field, `surface-container` row. Leading `today`, value (e.g. "May 14, 2026"), supporting "12 days ago" (auto-computed), trailing `edit`. Default to today on Create.
2. **Remaining** — `surface-container` row. Leading `straighten`, value numeric, inherits compound's `primaryUnit`. Default on Create = `compound.amountPerContainer`.
3. **Container expiry** (Optional) — date picker. Leading `event_busy`, value or "Tap to set", supporting "N days after opening" (auto-computed when based on `expiryAfterOpeningDays`), trailing `edit`.
   - **Default value**: if `compound.expiryAfterOpeningDays` set → auto-compute = `openedDate + expiryAfterOpeningDays`, label as "auto" (greyed). If user taps trailing edit → switches to manual mode, sets `userDefinedExpiryDate` (manual override wins per §3.1.1).

#### 4.5.4 Actions (Edit variant only)
Bottom row: `Delete` (`error-container`, leading `delete` icon) + `Save` (filled `primary`).

**Delete behavior**: removes the `OpenedContainer` (lost/discarded path). Does not change `numberOfContainers`. Compound reverts to "no opened container" state. Snackbar "Opened container removed" (no undo).

#### 4.5.5 Save behavior
- Create for an existing compound: decrements `numberOfContainers`, creates `OpenedContainer` row linked to compound, and updates compound's `currentOpened` ref.
- Create during New Compound flow: stages the opened-container fields until "Save compound"; final save stores `numberOfContainers` as total-owned input minus one.
- Edit: updates fields on existing `OpenedContainer`.
- If `remainingAmount == 0` after save: triggers natural depletion. Remove `OpenedContainer` without decrementing `numberOfContainers` again, then show dialog "Open new container?" w/ "Open new" (default) / "Leave closed" actions when unopened stock remains.

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

Entry: long-press row.

Contextual app bar: close × · "N selected" · trailing `more_vert` (Select all / Invert / Toggle status filter).

Selected card visual: fill = `secondary-container`, leading checkbox circle on left of card. Unselected cards keep default.

Bottom dock (replaces nav bar):
- **Pause** (`secondary-container`) — applies only to selected Active protocols. Disabled if no selected is Active.
- **Resume** (`secondary-container`) — applies only to selected Paused. Disabled if no selected is Paused.
- **Complete** (`secondary-container`) — sets `status=Completed`, no new ScheduledDoses generated.
- **Duplicate** (`secondary-container`) — creates copies w/ " (copy)" suffix, status=Active.
- **Archive** (`error-container`) — confirmation → soft-delete.

Buttons disabled when selection incompatible (e.g. Resume disabled when any selected is Active).

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
Chips for region (e.g. "Abdomen only") + rotation rule (e.g. "Rotate L / R").

#### 4.8.7 Dose history
Identical structure to §4.3.6/7/8 but filtered to this protocol.
- Header w/ count pill "{N} logged" (Taken+Partial).
- List rows w/ status dot + date + dose + site + status.

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
- **Warning row** (`error-container`): "Batch expires before protocol end" + "Jul 14 expiry · Aug 02 run-out".

#### 4.9.4 Bottom dock
- **Cancel** (text button) + **Save protocol** (filled `primary`, leading `check`, equal-grow).
- Edit mode: "Save changes" label.

#### 4.9.5 Lifecycle section (Edit mode only)

Below Forecast & warnings:
- Section header "Lifecycle".
- 3 buttons full-width:
  - **Pause protocol** (`secondary-container`, leading `pause`)
  - **Duplicate protocol** (`surface-container`, leading `add_circle`)
  - **Archive protocol** (`error-container`, leading `delete`)

#### 4.9.6 Pause-with-unsaved-changes flow
If user taps Pause while form has unsaved changes → dialog "Save changes before pausing?" with **Save + Pause** (primary) / **Pause without saving** / **Cancel**.

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
Leading `history` icon = decorative only (no action). Title "Sites". Trailing `search` → opens **Search overlay** (§4.0.1).

#### 4.12.2 Route filter chips (top of content)
Kit filter chips: All routes / SC / IM. Filters all subsequent stats + body map + carousel by route.

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

**Heat map mode** (`18b`): replaces dots with blurred ellipses (`error` fill, varying opacity 0.05–0.7 by usage frequency, outside layer blur). Hotter = recently/frequently used. Used to visualize over-rotation.

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

Bottom sheet modal. Drag handle. Scrim.

- Header: avatar w/ status (cooling = `error-container` + `restart_alt`; ready = `secondary-container` + `check`) + site name + supporting "{status} · {info}".
- Stats row (3 tiles): Times used · Route · Last used.
- Recent uses list (last 2-3 AdministrationEvents at this site).
- Actions row:
  - **View history** (`secondary-container`, leading `history`) → full site history list (filtered Compound Detail-like view scoped to site).
  - **Mark unavailable** (`error-container`, leading `block`) → toggles `isAvailable = false`.

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

#### 4.13.4 Section: Data
- **Export JSON** → file picker save → produces `stax-export-YYYY-MM-DD.json`.
- **Import JSON** → file picker open → preview dialog (row counts per entity) → confirm → ID remap import (see §5.6).
- **Reset all data** row — `error-container` fill, `error-container` text, leading `delete` icon in `error-container` themed style. Tap → typed-confirm dialog ("Type RESET to confirm") → hard-deletes all entities.

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

#### 4.16.6 Accessibility

- Each button has a `contentDescription`: `"Take {compound name} dose, {dose}"`, `"Snooze {compound name} dose by one hour"`.
- Min touch target 48×48dp inside the widget, even at `small` size — drop secondary actions before shrinking primary below threshold.

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
- **Schedule type + dosageTimes**: empty `dosageTimes` → single dose per day w/ `hasTimeOfDay=false`, `scheduledAt = startOfDay(date)`.
- **Active Escalation rule**: applies current dose at the time the rule fires (computed against `startDate` + accumulated doses).
- **ProtocolBreak**: skip generation during off-days (compute on/off cycle from `startDate`).
- **endDate**: no generation past it; on `endDate` passing, set `Protocol.status = Completed`.

Generation is idempotent — uses `(protocolId, scheduledAt)` uniqueness; never duplicates.

### 5.3 Inventory deduction

**Container opening operation**:
- Requires `numberOfContainers > 0`.
- Decrement `compound_supply.numberOfContainers` by 1.
- Create `OpenedContainer` with `openedAt=now` and `remainingAmount=amountPerContainer`.
- Insert `InventoryTransaction { type=ContainerOpen, delta=amountPerContainer }`.

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
- Debug builds: log + Crashlytics warning.
- Release builds: silent; set a `Settings`-side drift flag that surfaces a "Repair inventory" row in §4.13.
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

- Create compound with already-opened container.
- Open container:
  - decrement `compound_supply.numberOfContainers`
  - insert `opened_container`
  - insert `InventoryTransaction(type=ContainerOpen)`
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
**Compact** (<600dp): single-pane, bottom nav, FABs at bottom-right.

**Medium** (600–839dp, foldable unfolded): switches to side rail.
- Compounds list ↔ Compound Detail = list-detail two-pane. List pane fixed `360dp`, detail pane fills remainder, divider `outline-variant` 1dp.
- Protocols list ↔ Protocol Detail = same two-pane geometry.
- Sites = single-pane (body map is hero; splitting reduces impact).
- Dashboard = single-pane.

**Expanded** (840–1199dp): same as Medium plus increased horizontal padding (`24dp` → `32dp`) on hero cards; list pane widens to `400dp`.

Touch targets remain ≥ 48×48 regardless of breakpoint.

#### 6.4.1 Foldable seam handling
Use `WindowInfoTracker` to detect a vertical fold; if list pane width would land under the seam, push the divider to the seam line and widen the detail pane to the second half. No content crosses the fold.

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

**Type scale**: pure M3 styles (`display`, `headline`, `title`, `body`, `label` with `-emphasized` variants where applicable). Font family = **Google Sans Flex**.

**Shape scale**: must be emphasized via M3 Expressive guidelines

---

## 10. Architecture conventions

Locked at scaffold time. Diverging from these creates churn.

### 10.1 MVI per screen

Every screen has a `ViewModel` exposing one `state` flow + one `effect` channel, consuming one `intent` sealed hierarchy.

```kotlin
sealed interface DashboardIntent {
  data object Refresh : DashboardIntent
  data class SelectDay(val date: LocalDate) : DashboardIntent
  data class TakeDose(val scheduledDoseId: Long) : DashboardIntent
  // ...
}

data class DashboardState(
  val pendingDoses: ImmutableList<DoseCardModel>,
  val selectedDate: LocalDate,
  val isLoading: Boolean,
  // ...
)

sealed interface DashboardEffect {
  data class NavigateToTakeDose(val scheduledDoseId: Long) : DashboardEffect
  data class ShowSnackbar(val text: String) : DashboardEffect
}

class DashboardViewModel(/* injected */) : ViewModel() {
  val state: StateFlow<DashboardState>
  val effect: SharedFlow<DashboardEffect>
  fun handle(intent: DashboardIntent)
}
```

Rules:
- `state` is `StateFlow<S>` exposed to Compose via `collectAsStateWithLifecycle()`.
- `effect` is `SharedFlow<E>` with `replay = 0`, `extraBufferCapacity = 1`, `BufferOverflow.DROP_OLDEST`. One-shot side effects only.
- `handle(intent)` returns `Unit`; coroutine work goes to `viewModelScope`.
- State data classes contain only primitives + value classes + `ImmutableList` / `ImmutableSet` / `ImmutableMap` (kotlinx-collections-immutable). No Room entities, no domain models, no mutable collections — UI models only (see §2.3.1).

### 10.2 Repository layer

ViewModels never see Room directly. Repositories own DAO access and entity → domain mapping.

- Koin scope: `single` per `Repository`. ViewModels get them via `get()` / constructor injection.
- DAO `Flow` queries collected inside repository, mapped to domain models, exposed back as `Flow<Domain>`.
- Mutating ops are `suspend` functions returning either `Unit`, the new ID, or a `Result<T>` for validation failures.
- Transactions in §5.8.5 are implemented at the repository layer with `@Transaction` DAO methods.

### 10.3 Navigation 3 — typed routes

All routes are `@Serializable` Kotlin types. No string routes.

```kotlin
@Serializable data object DashboardRoute
@Serializable data object CompoundsRoute
@Serializable data class CompoundDetailRoute(val compoundId: Long)
@Serializable data class CreateCompoundRoute(val templateForm: Form? = null)
@Serializable data class EditCompoundRoute(val compoundId: Long)
@Serializable data class ProtocolDetailRoute(val protocolId: Long)
// ...
```

Rules:
- One file `Routes.kt` per feature package exporting its routes.
- `NavBackStack` typed; back-stack model is per top-level destination (Home / Compounds / Protocols / Sites / Settings each have their own stack).
- Save-state behaviour: routes survive process death via `SavedStateHandle`; ViewModels receive route params through the SDK helper `toRoute<T>()`.
- Bottom sheets and dialogs are NOT routes — they live in the parent screen's state. Sheets show via `state.showXSheet` flag; back press handled by parent.

### 10.4 Module layout

Single Android app module at v1 (no multi-module). Internal package layout:

```
com.stax.app/
  core/                   // value classes, Decimal, Quantity, unit math
  data/                   // Room entities, DAOs, repositories, mappers
  domain/                 // domain models, business rules (escalation, in-break, dose math)
  feature/
    dashboard/            // screens + viewmodels + state
    compounds/
    protocols/
    sites/
    settings/
    onboarding/
    reconstitution/
  ui/                     // shared composables, theme, motion, icons
  startup/                // App Startup initializers (§2.3.4)
  work/                   // WorkManager workers
  notification/           // AlarmManager + channels
  widget/                 // Glance widget (§4.16) + size-keyed composables + action callbacks
  shortcut/               // static + dynamic shortcut registration + deep-link router (§4.17)
```

Promote to multi-module only if build time crosses 30s clean / 8s incremental.

### 10.5 Testing surface

| Layer     | Tool                              | What                                             |
|-----------|-----------------------------------|--------------------------------------------------|
| Domain    | JUnit5 + AssertK                  | escalation math, in-break, dose math, validation |
| Data      | Robolectric + Room in-memory      | DAO queries, transaction boundaries, FK rules    |
| Migration | Room `MigrationTestHelper`        | every version-to-latest path                     |
| ViewModel | Turbine                           | intent → state transitions                       |
| UI        | Compose `createComposeRule()`     | golden-path flows per screen                     |
| E2E       | Macrobenchmark + Baseline Profile | hot paths in §2.3.3                              |
