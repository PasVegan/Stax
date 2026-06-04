# Stax — Peptide / Supplement / Hormone Tracker — App Spec v2

## 1. Overview

Material 3 Expressive Android app. Tracks peptides, supplements, hormones, injectables, topicals. Core features: compound inventory, protocol scheduling, dose logging (single + multi-compound injections), injection site rotation w/ heat map, reconstitution helper w/ syringe visualization, expiry/stock warnings, JSON export/import. Local-first, fully offline.

Target form factors: regular phones + foldables only. **No** large or extra-large breakpoints. Optimize for **compact** (<600dp) + **medium** (600-839dp) breakpoints.

---

## 2. Principles

### 2.1 Local-first
- Fully offline. Room database. No accounts, no network.
- All user data exportable + importable as JSON.

### 2.2 Use simplicity
- Every common action ≤2 taps from Dashboard.
- Never ask for data app already knows or can derive.
- Sensible defaults: actual dose = planned, suggested site from rotation, route from protocol, time = now.
- No multi-step wizards when single screen + smart defaults works.

### 2.3 Performance
- Cold start to interactive: <400ms. Use Baseline Profiles + App Startup library.
- 16ms frame budget. No blocking I/O on main thread.
- Heavy work (export, ScheduledDose generation, inventory recalc) → coroutines + WorkManager off-main.
- Battery-aware: no foreground services. AlarmManager + WorkManager only.
- Beautiful UI non-negotiable: animations, M3 Expressive motion, dynamic color, shaders stay. Performance = doing them correctly (hardware-accelerated, hoisted state, stable composables) not removing them.
- `remember` for expensive calcs. Lazy layout keys. `derivedStateOf` to limit recomposition. Defer reads. Avoid backwards writes. Flatten hierarchy.

### 2.4 Technology
- Kotlin + Jetpack Compose + Material 3 Expressive
- MVI architecture, Koin DI
- **Google Sans Flex** font family (Regular, Medium, SemiBold, Bold, Light)
- **Material Symbols Rounded** for icons (load font via App Startup; render via text glyphs)
- Room database, WorkManager (background), AlarmManager (exact reminders)
- Navigation 3, adaptive: bottom nav (compact), side rail (medium/foldables unfolded). Libs: `androidx.compose.material3.adaptive.navigation3`, `androidx.compose.material3.adaptive.layout`.

---

## 3. Domain Model

### 3.1 CompoundSupply
```
id: Long
name: String                            // required, ≥1 char
category: Peptide | Supplement | Hormone | Medication
form: Injectable | Capsule | Tablet | Powder | Liquid | Topical
containerType: Vial | Bottle | Blister | Packet | Tub | Ampoule
primaryUnit: mcg | mg | g | IU | mL | capsule | tablet | scoop | drop
amountPerContainer: Double              // e.g. 5 (mg), 60 (capsules)
concentration: Double?                  // mass per mL (or per dose form)
concentrationUnit: String?              // e.g. "mcg/mL", "IU/tablet"
numberOfContainers: Int                 // total owned, ≥0
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
remainingAmount: Double                 // required, in compound's primaryUnit
remainingUnit: String
expiryAfterOpeningDays: Int?            // copy of compound's, mutable per-container
userDefinedExpiryDate: LocalDate?       // manual override; wins if set
predictedExpiryDate: LocalDate?         // derived: openedAt.date + expiryAfterOpeningDays
```

**Rules**:
- One open container max per compound.
- When `remainingAmount` reaches ≤0 via dose deduction: decrement `numberOfContainers` by 1. If `numberOfContainers > 0` after decrement, prompt "Open new container?" via snackbar action (default: auto-open).
- Effective expiry display: `userDefinedExpiryDate ?? predictedExpiryDate ?? null`. If both null, hide expiry from UI.
- Delete OpenedContainer (via §4.5 Edit Opened Container sheet) = container lost/discarded path: removes the OpenedContainer record but does NOT decrement `numberOfContainers` (already counted as opened). Compound reverts to "no opened container" state. Next dose log auto-opens fresh container from unopened pool.

### 3.2 Protocol
```
id: Long
name: String                            // required
compoundSupplyId: Long                  // required
plannedDose: Double                     // in plannedDoseUnit
plannedDoseUnit: String                 // same family as compound.primaryUnit
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
startDose: Double
targetDose: Double
increaseAmount: Double                // > 0
increaseEvery: EveryXDays | EveryXWeeks | AfterXDoses
increaseEveryValue: Int               // ≥ 1
maxDose: Double?
stopAtTarget: Boolean
```

```
ProtocolBreak:
daysOn: Int                           // ≥ 1
daysOff: Int                          // ≥ 0; e.g. 5/2 or 56/28 cycles
```

Equivalent-dose display (`0.10 mL · 10 insulin units`) derived at display time from `compoundSupply.concentration`. Not stored.

### 3.3 ScheduledDose
```
id: Long
protocolId: Long
compoundSupplyId: Long
scheduledAt: Instant                    // when no time-of-day, start of day in user TZ
hasTimeOfDay: Boolean                   // derived from protocol.dosageTimes at gen time
plannedDose: Double                     // captured at gen time (after escalation)
plannedDoseUnit: String
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
status: Taken | Skipped | Partial
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
plannedDose: Double                     // snapshot from protocol at log time (0 if manual)
plannedDoseUnit: String
actualDose: Double
actualDoseUnit: String
notes: String?
inventoryDeductedAmount: Double         // computed at save, stored for audit (in compound's container unit)
inventoryDeductedUnit: String           // typically mL for injectables, count for orals
```

Inventory-deducted quantity = `actualDose / compoundSupply.concentration` for injectables (mL); = `actualDose` for unit-based forms (capsule/tablet count).

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
delta: Double                           // +/- in primaryUnit
type: Manual | DoseDeduction | ContainerOpen | ContainerClose
sourceEventId: Long?                    // AdministrationEvent.id when type=DoseDeduction
reason: String?                         // user-provided note for manual
at: Instant
```

---

## 4. Features

### 4.0 Global navigation + layout

**Bottom navigation bar** (compact breakpoint): 5 vertical-item destinations.
- Home (icon `home` Material Symbols) → §4.1 Dashboard
- Compounds (`medication`) → §4.2
- Protocols (`calendar_month`) → §4.7
- Sites (`person_pin_circle`) → §4.12
- Settings (`settings`) → §4.13

Selected item: filled `primary-container` pill behind icon. Label `M3/label/small` 12sp Medium.

**Side rail** (medium breakpoint and unfolded foldables): same 5 destinations vertically.

Detail screens (`Compound Detail`, `Protocol Detail`, `Edit dose`, `Administration Event detail`, `Create/Edit Compound`, `Create/Edit Protocol`, `Reconstitution Helper`, `Site picker`) push onto current destination's back stack. Bottom nav stays unless explicitly hidden (multi-select modes).

**App bar pattern** (M3 Small variant `Configuration=Small`):
- Leading icon: contextual (back-arrow for stacked screens, hamburger for top-level destinations w/ navigation drawer, close × for full-screen forms/sheets).
- Headline: screen title, `M3/title/large-emphasized` 22sp.
- Optional supporting text below headline (e.g. context: "Sema weekly titration").
- Trailing actions: up to 3 icon buttons; overflow to `more_vert` menu when >3.

**Status bar**: standard Android system bar at top (height 52dp). Use kit `Building Blocks/status-bar` component (key `27a8167e9cc8442bdf3e74f5864c0a2aaf9ac4ed`).

**Gesture navigation indicator** at bottom (height 24dp). Use kit `Building Blocks/navigation`.

**Frame size for design**: 412 × 892 dp (Pixel 8 baseline).

### 4.1 Dashboard

**Primary goal**: log next dose w/ minimum friction.

**States** (mutually exclusive):
1. **Default** (`01 · Dashboard`) — has Pending doses today.
2. **Empty** (`01d · Dashboard (empty)`) — no doses today, no active protocols. Big illustrated empty hero (blob composition + center `add` icon), title "No doses today", subtitle "Tap to log your first dose or create a protocol.", primary CTA "Log dose" + tonal "Protocol". FAB hidden.
3. **All done** (`01e · Dashboard (all done)`) — has active protocols but zero Pending today (all Taken/Skipped). Hero `primary-container` card: round `primary` avatar w/ `done_all` icon + "All done today" headline-small + subtitle "N doses logged · 100% adherence". Keep Inventory + Recent activity sections below.
4. **Grouped administration suggestion** (`01b · Dashboard (grouped administration)`) — when ≥2 injectable Pending doses share same route + same day (with `dosageTimes` empty) OR same route within 30-min window (when `dosageTimes` set). Hero card replaces individual dose cards w/ grouped suggestion card (see §4.10.3).
5. **Overflow menu open** (`01c · Dashboard (overflow menu open)`) — kit `Scrim` + kit `Menu` anchored to tapped `more_vert` button on dose row. Items: Take dose · Snooze 1 hour · Skip.

#### 4.1.1 Day chip strip

Horizontal scrollable row at top of content. ±N days from today (default N = 14, so 29 day chips total). Centered on today initially via `LazyListState`.

**Chip composition** (each chip 46×78dp):
- Top: day letter (NARROW `DayOfWeek`, e.g. "M"), `M3/label/small-emphasized` 11sp, color = on-surface-variant.
- Middle: day number, **Google Sans Flex Bold 22sp** (custom override).
- Bottom: dose indicator dot (6×6dp ellipse) when `ScheduledDose` exists for that date+active protocols. Color = primary (or on-primary for selected chip).

**Selected chip** (today by default; user can tap any):
- Corner radius **20dp** (squircle) — distinct shape vs unselected pill.
- Fill: `primary`. Text: `on-primary`.

**Unselected chip**:
- Corner radius **999** (full pill).
- Fill: `surface-container`. Text: `on-surface-variant` (letter) + `on-surface` (number).

**Interactions**:
- Tap chip → filter Today's schedule + Recent activity to that date.
- Long-press chip → open Material Date Picker for arbitrary date.
- Swipe horizontally to navigate weeks/months.

#### 4.1.2 Dose cards

One per Pending `ScheduledDose` matching selected date. Sorted by `scheduledAt` ascending; doses w/ `hasTimeOfDay == false` sorted last.

**Default card layout** (full width, corner 24dp, `surface-container`):
- Top row: compound name `M3/title/medium-emphasized` (left) + ETA badge pill (right, `tertiary-container`).
- ETA badge:
  - When `hasTimeOfDay == true` AND scheduled in future: "in Xh Ym" or "in N min"
  - When overdue: red `error-container` w/ "Overdue Xm"
  - When `hasTimeOfDay == false`: "Today" pill (no time)
- Detail row: `M3/body/medium` formatted as:
  - With time: `0.25 mg · 0.10 mL · 8:00 PM`
  - Without time: `0.25 mg · 0.10 mL` (no time fragment)
- Action row: 3 buttons inline.
  - **Take** (filled `primary` button, hero CTA) → opens §4.10.1 Take Dose bottom sheet.
  - **Snooze** (outlined pill) → opens snooze submenu (chips: 1h / 3h / 1d when timed; just 1d when no time).
  - **Skip** (text button) → confirmation snackbar "Skip dose? [Skip] [Cancel]" — on confirm sets `ScheduledDose.status = Skipped`, no inventory deduction.

**First dose card** (the absolute next one due) uses `primary-container` fill instead of `surface-container` — hero visual emphasis.

**Card overflow** = tap `more_vert` icon on the card (always visible top-right) → opens §4.1 state 5 menu.

#### 4.1.3 Section cards (promoted from plain labels)

Section headers on dashboard rendered as **interactive surface-container-low pills** (corner 16dp, padding 14h × 10v):
- Leading icon (e.g. `inventory_2`, `history`)
- Title `M3/title/small-emphasized`
- Trailing `chevron_right`

Each section header is tappable → navigates to expanded view (e.g. all inventory warnings, full recent activity).

#### 4.1.4 Inventory warnings

Below dose cards. Each warning = full-width row, corner 20dp, `error-container` fill, leading `warning` icon, 2-line content (title + supporting), trailing `chevron_right`. Tap → navigates to Compound Detail of the affected compound.

Warning triggers (per spec §5.3 inventory math):
- `dosesRemaining < 7` → "BPC-157 — 3 doses left · Reorder before {date}"
- Opened container expires within 14 days → "Opened Tirzepatide vial expires in 9 days"
- Active protocol requires more than available → "Protocol requires 1.5 mL until end date · Only 0.8 mL available"
- Batch expires before estimated run-out → "Batch expires Jul 14 — before run-out Jul 28"

#### 4.1.5 Recent activity

Below inventory section. Last 5 `AdministrationEvent`. Each row: status dot (avatar circle, color by status), compound name `M3/body/large-emphasized`, "Yesterday 8:14 PM · Taken" supporting text `M3/body/small`. Status colors:
- Taken: `secondary-container` w/ `check` icon
- Partial: `tertiary-container` w/ `schedule` icon
- Skipped: `error-container` w/ `close` icon
- Missed: `error-container` w/ `error` icon

Tap row → §4.11 Administration Event detail.

#### 4.1.6 Extended FAB

Bottom right, 16dp from edges, **above bottom nav** (y = 892 − 24 − 64 − 16 − fabH).

Label "Log dose" + leading `add` icon. Tonal `primary-container` fill. Use kit `Extended FAB` (key `855f3576c2aa2b4a0f706b532d2516a9defeb581`).

**Tap behavior**: opens §4.1.7 FAB menu.

#### 4.1.7 FAB menu (`24 · FAB menu (open)`)

Kit `FAB menu` component (key `14f4af2768dc5b57bc0b482f59309f7f3c92ef4a`), `Color=Primary container, Segments=3` variant + `Show 3rd/4th/5th segment = true` for 5 items.

Items (top to bottom):
1. **Log scheduled** (`done` icon) → navigates to §4.10.2-a Log dose (Dashboard) preselected to next Pending dose.
2. **Log manual** (`edit` icon) → §4.10.2-a Log dose (Dashboard) w/ compound picker open.
3. **Add compound** (`colorize` icon) → §4.4 Create Compound.
4. **Add protocol** (`calendar_month` icon) → §4.9 Create Protocol.
5. **Adjust inventory** (`inventory_2` icon) → opens Adjust Inventory bottom sheet (compound picker → +/− amount + reason).

Tap outside scrim or tap FAB (which morphs to `close` icon) → dismiss menu.

---

### 4.2 Compounds list (`02 · Compounds`)

**Primary goal**: find + select a compound.

#### 4.2.1 App bar
Leading `search` icon → opens full-screen Search overlay (filter list as user types). Title "Compounds". Trailing `tune` icon → opens Sort menu (Name / Category / Expiry / Stock level / Form).

#### 4.2.2 Filter chip row (horizontally scrollable)

Use kit `Filter chip` (key `107892ebbd2b57f25ebebea2736f9c639847a943`).

Chips in order:
1. **All** (`Style=Outlined, Configuration=Label & leading icon, Selected=True`, leading `done` icon) — default selected, mutually exclusive w/ Low stock + Expiring soon.
2. **Low stock** — selects compounds where `dosesRemaining < 7`.
3. **Expiring soon** — selects compounds where any effective expiry < today+30d.
4. **Category** (`Show trailing icon=True`, trailing `expand_more`) — opens kit Menu for multi-select. Items: Peptide / Supplement / Hormone / Medication. Chip label updates to "Category · N" when ≥1 selected. See `02c · Compounds (Category menu open)`.
5. **Form** (`Show trailing icon=True`) — opens kit Menu for multi-select. Items: Injectable / Capsule / Tablet / Powder / Liquid / Topical.

Category + Form menu items use `check_circle` (selected) / `add_circle` (unselected) leading icons.

#### 4.2.3 Compound row

Full-width row, corner 20dp, fill `surface-container` (default) or `secondary-container` (selected in multi-select).

Layout left→right:
- **Avatar**: 48×48dp rounded square (corner 16dp), category-colored fill + form icon:
  - Peptide: `primary-container` + `colorize` icon
  - Supplement: `tertiary-container` + `medication` icon
  - Hormone: `secondary-container` + `science` icon
  - Low-stock state (any category): `error-container` + `warning` icon (overrides default)
- **Content column**:
  - Name `M3/title/medium-emphasized`
  - Meta `M3/body/small` `on-surface-variant`: `{category} · {remaining} · {N container(s)}` or `{category} · Low stock · 3 doses left`
- **Meta column** (right):
  - Effective expiry `M3/label/small`: "Exp Jul 14"
  - `chevron_right` icon

**Tap** → §4.3 Compound Detail.
**Long-press** → enter §4.2.4 multi-select mode.

#### 4.2.4 Multi-select mode (`02b · Compounds (multi-select)`)

Entry: long-press any row (no other entry point).

App bar transforms into contextual app bar:
- Leading `close` → exits multi-select.
- Title: "N selected" (live count).
- Trailing actions: `delete` (= Archive) · `add_circle` (= Duplicate) · `more_vert` (Select all / Invert selection).

**Selected row visual**: fill = `secondary-container`, leading checkbox circle (28dp) replaces avatar position (avatar shifts right or remains; current impl shows checkbox + avatar both — checkbox 28dp filled `primary` with `check` icon when selected, outlined when unselected).

Bottom nav **hidden** during multi-select mode. Bottom dock appears instead (surface-container-low, padding 16h × 12t/16b):
- **Duplicate** (tonal `secondary-container` button, equal-grow): creates copies with " (copy)" suffix, fresh IDs, no opened container, no batch number.
- **Archive** (`error-container` button, equal-grow): opens confirmation dialog "Archive N compounds? Logged history is kept." — confirm → sets `deletedAt = now()` for all selected.

After action completes, exits multi-select mode. No undo snackbar (per design decision).

#### 4.2.5 FAB

Extended FAB "Add" bottom-right, opens §4.4 Create Compound.

---

### 4.3 Compound Detail (`03 · Compound Detail`)

**Primary goal**: review and act on one compound.

#### 4.3.1 App bar
- Leading `arrow_back` → Compounds list.
- Headline: compound name. Supporting: category (e.g. "Peptide").
- Trailing `more_vert` → menu: Edit / Duplicate / Reconstitute / Archive.

#### 4.3.2 Stat strip (top of content, horizontal row of 3 tiles, equal-grow)

Variety of corner radii (M3 Expressive shape-variety tactic):
1. **Doses left** (24r) `primary-container`: numeric value `M3/headline/medium-emphasized` + label "Doses left". Computed = `floor((openedRemaining + unopenedTotalAmount) / dosesPerActualInjection)` aggregated over active protocols using this compound.
2. **Days left** (24r) `secondary-container`: number + "d" suffix. Computed = `dosesLeft / dosesPerDayAcrossActiveProtocols`.
3. **Batch expiry** (24r) `tertiary-container`: short date "Jul 14". **Hidden entirely if `batchExpiryDate` null and no opened expiry**.

If only 2 tiles relevant, render 2 across full width. If only 1, render full-width.

#### 4.3.3 Opened vial card

Visible only if `currentOpened != null`. Full-width card, corner 24dp, `surface-container-high`.

Top row: `colorize` icon + "Opened vial" title `M3/title/medium-emphasized` + outlined "Reconstitute" pill (right, leading `calculate` icon, tap → §4.6 Reconstitution Helper).

**Segmented progress bar**: 10 segments, 4dp gap, each `28×12dp` rounded (corner 4dp). Filled segments = `primary`. Partial segment (when remaining %% has fraction) uses 50%-width inner `primary` fill on `surface-container-highest` segment background. Computation: `filledSegments = floor((remainingAmount / amountPerContainer) × 10)`.

Meta row (space-between): "X / Y mg remaining" (left, `M3/body/medium`) · "Reconstituted N days ago" (right, `M3/body/small`).

Tap card → §4.5 Edit Opened Container bottom sheet.

#### 4.3.4 Active protocols card

Full-width, `surface-container`. Header: `calendar_month` icon + "Active protocols · N" `M3/title/medium-emphasized`.

Sub-list: each active protocol uses this compound → sub-row card (`surface-container-low`, corner 16dp):
- Protocol name `M3/body/large-emphasized`
- "Mon, Thu · 0.25 mg sc" `M3/body/small`
- Tag pill (`primary-container`, 999r): `schedule` icon + "Next: Today 8 PM" `M3/label/small-emphasized`

Tap sub-row → §4.8 Protocol Detail.

#### 4.3.5 Notes card

Full-width, `surface-container`, corner 24dp. Header row: `edit` icon + "Notes" + trailing edit pencil. Body: notes text `M3/body/medium`, **truncated to 2 lines** with `textTruncation=ENDING`. Below body: "Show more" tappable link `M3/label/medium-emphasized` `primary` + `expand_more` icon → expands to full text in-place (`expand_less` once expanded).

Tap edit pencil → opens inline text editor (multi-line text field).

#### 4.3.6 History tabs

Two tabs above history list:
1. **Doses** (count badge: total Taken+Partial DoseComponents all-time for this compound) — selected by default.
2. **Inventory** (count badge: total `InventoryTransaction` rows excluding `DoseDeduction`).

Tab pill (kit-style): selected = `secondary-container`, unselected = transparent. Count badge inline: 6h padding, 1v, 999r; `primary` fill when tab selected, `surface-container-highest` when not.

#### 4.3.7 Filter chips (Doses tab only)

Kit Filter chip, single-select OR multi-select:
- All (selected default)
- Taken
- Partial
- Skipped

#### 4.3.8 History list

Paged list (50 per page, infinite scroll). Each row, corner 16dp, `surface-container`:
- Status dot (36×36dp ellipse) with status icon (check / schedule / close)
- Date + time + dose + site supporting (e.g. "Today · 8:00 PM" then "0.25 mg · 0.10 mL · Taken · Abdomen R")

Tap row → §4.11 Administration Event detail.

**Inventory tab**: same row layout, status dot becomes `+` (manual adjustment) or `inventory_2` (container open/close).

#### 4.3.9 Bottom dock

Sticky `surface-container-low`, 80dp height, padding 16h × 12t/16b. Two buttons:
- **Log dose** (filled `primary`, equal-grow): leading `add` icon. → §4.10.2-b Log Dose (from Compound).
- **Adjust** (tonal `secondary-container`): leading `inventory_2` icon. → Adjust Inventory bottom sheet.

Bottom nav is hidden on this screen (detail screen). Gesture indicator visible.

---

### 4.4 Create Compound (`4 · Create Compound`) + 4b validation variant

Scrollable form. Bottom dock w/ Cancel + Save compound CTAs.

#### 4.4.1 App bar
- Leading `close` (×) → confirms discard if dirty, returns to caller.
- Title: "New compound" (Create) or "Editing {name}" (Edit, `5 · Edit Compound`).
- App bar **container transparent on scroll**; X button keeps `surface-container-low` 40×40 round fill for legibility — floats above content.

#### 4.4.2 Section header pattern

Section labels = `M3/label/medium-emphasized` `primary` color, padding-top 8 padding-left 4. No card wrap.

#### 4.4.3 Sections (top to bottom)

**Basics** (all required):
1. **Name** — single-line text field, surface-container fill, leading `edit` icon. Required validation; empty → red 2px outline + supporting text `error` "Name is required" w/ `error` icon, `M3/label/small-emphasized`.
2. **Category** — dropdown row (chevron). Single-select from Peptide / Supplement / Hormone / Medication.
3. **Form** — dropdown. Injectable / Capsule / Tablet / Powder / Liquid / Topical.
4. **Container type** — dropdown. Vial / Bottle / Blister / Packet / Tub / Ampoule.

**Smart defaults banner** (only shows when user just selected Form): floating `secondary-container` banner above Stock section: leading `auto_awesome` icon + "Smart defaults applied" title + supporting "{Container} · {unit} · {route} · {default amountPerContainer}". Trailing `close` icon (32dp circle) to dismiss. Per Form selection:
- Injectable → Vial · mg · subcutaneous · 5 mg
- Capsule → Bottle · capsule · oral · 60 capsules
- Tablet → Blister · tablet · oral · 30 tablets
- Powder (non-injectable) → Tub · g · oral · 100 g
- Liquid → Bottle · mL · oral · 30 mL
- Topical → Tub · g · topical · 50 g

**Stock**:
1. **# of containers** — numeric, side-by-side w/ next.
2. **Amount per container** + unit picker.
3. **Concentration** (Optional) — numeric + unit picker inline. **Trailing "Helper" tonal button** (`secondary-container`, `calculate` icon) → §4.6 Reconstitution Helper. Required only when `Form == Injectable AND ContainerType != Ampoule` (pre-mixed).

**Storage & batch**:
1. **Storage location** — dropdown: Fridge (4°C) / Room temp / Freezer.
2. **Batch expiry date** (Optional) — date field, opens Material Date Picker.
3. **Batch number** (Optional) — single-line text.
4. **Supplier** (Optional) — single-line text.
5. **Expiry after opening** (Optional) — numeric + "days" suffix. Drives auto-computed `predictedExpiryDate` when container opened.

**Opened container** section:
- Empty state: `surface-container` card w/ `inventory_2` badge + "No container opened yet" title + "Auto-opens on first dose, or add one now." supporting + tonal `secondary-container` CTA "Mark as already opened" (leading `add` icon).
- Tap CTA → §4.5 Create Already Opened Container bottom sheet.
- Once container added, this section shows summary card identical to §4.3.3 Opened vial card layout + "Edit" pencil → opens §4.5 Edit Opened Container sheet.

**Notes** (Optional) — multi-line text field, 3-line min height.

#### 4.4.4 Save validation

On tap "Save compound":
- Validate all required fields. If any empty → focus first error field, scroll into view, show inline error.
- Insert/update `CompoundSupply` row.
- If "Mark as already opened" was used: create `OpenedContainer` linked to the new compound.
- Returns to caller: Compounds list (default) or Onboarding step 2 progresses to step 3.

#### 4.4.5 Behaviors
- Auto-save draft on backgrounding (resume restores form state).
- Discard confirmation dialog when X pressed w/ dirty form: "Discard changes?" + Discard / Keep editing.

---

### 4.5 Opened Container bottom sheets

Two variants:
- `6 · Edit Opened Container (bottom sheet)` — opened from Compound Detail or Edit Compound when container exists.
- `7 · Create Already Opened Container (bottom sheet)` — opened from Create/Edit Compound "Mark as already opened" CTA. Identical UI minus Delete button.

#### 4.5.1 Sheet structure
Kit `Bottom sheet` (key `80574a8b55ac6f199415aaa015a30ffbf415afd7`), `Modal=True` variant. Drag handle + Scrim overlay.

#### 4.5.2 Header
Inline at top of sheet content: title `M3/headline/small-emphasized` ("Add opened vial" / "Edit opened vial") + subtitle `M3/body/small` (compound name + size e.g. "Semaglutide · 5 mg vial"). Right side: `close` icon button (40dp, no fill).

#### 4.5.3 Fields
1. **Opened date** — date picker field, `surface-container` row. Leading `today`, value (e.g. "May 14, 2026"), supporting "12 days ago" (auto-computed), trailing `edit`. Default to today on Create.
2. **Remaining** — `surface-container` row. Leading `straighten`, value numeric, inherits compound's `primaryUnit`. Default on Create = `compound.amountPerContainer`.
3. **Container expiry** (Optional) — date picker. Leading `event_busy`, value or "Tap to set", supporting "N days after opening" (auto-computed when based on `expiryAfterOpeningDays`), trailing `edit`.
   - **Default value**: if `compound.expiryAfterOpeningDays` set → auto-compute = `openedDate + expiryAfterOpeningDays`, label as "auto" (greyed). If user taps trailing edit → switches to manual mode, sets `userDefinedExpiryDate` (manual override wins per §3.1.1).

#### 4.5.4 Actions (Edit variant only)
Bottom row: `Delete` (`error-container`, leading `delete` icon, fixed width) + `Save` (filled `primary`, equal-grow).

**Delete behavior**: removes the `OpenedContainer` (lost/discarded path). Does NOT decrement `numberOfContainers`. Compound reverts to "no opened container" state. Snackbar "Opened container removed" (no undo).

#### 4.5.5 Save behavior
- Create: creates `OpenedContainer` row linked to compound. Updates compound's `currentOpened` ref.
- Edit: updates fields on existing `OpenedContainer`.
- If `remainingAmount == 0` after save: triggers natural depletion → decrement `numberOfContainers`, dialog "Open new container?" w/ "Open new" (default) / "Leave closed" actions.

---

### 4.6 Reconstitution Helper (`19 · Reconstitution Helper`)

**Primary goal**: compute correct dose volume w/ confidence.

#### 4.6.1 App bar
Leading `close`, title "Reconstitute", supporting "{compound name} · {container amount}{unit} vial".

#### 4.6.2 Syringe hero card (`surface-container-low`, corner 32dp)

Top row: left column shows label "Draw to" `M3/label/medium-emphasized` + value row `display-medium-emphasized` numeric + `title-large-emphasized` "units" (`primary` color). Right: size badge pill (`secondary-container`, leading `straighten` icon + "U-100 · 1 mL").

**Syringe visualization** (380×110dp, drawn programmatically with shapes):
- **Plunger flange** (left, 8×60dp, corner 4dp, `outline`).
- **Plunger rod** (50×12dp, corner 2dp, `outline-variant`).
- **Barrel** (220×36dp, corner 4/2/2/4dp asymmetric, fill white #FFFFFF + 1.5px `outline` stroke @ 50% opacity).
- **Fill rectangle** inside barrel, width = `(unitsPerDose / 100) × 220dp`, fill `primary`, corner 3/1/1/3.
- **Plunger stopper** (4×32dp, `on-primary`) at fluid edge.
- **Tick marks**: 11 ticks across barrel (every 10 units). Major (every 50): 1.5×10dp. Minor: 1.5×6dp. All `on-surface-variant`.
- **Tick labels** at 0 / 50 / 100: `Roboto Medium 10sp` `on-surface-variant`.
- **Fill label pill** above syringe at fluid edge: `primary` fill, white text, leading no-icon, value = unitsPerDose integer. Pointer line (1.5×6dp `primary`) from pill down to barrel.
- **Needle hub** (8×24dp, `outline`).
- **Needle** (50×3dp, `on-surface`).
- **Needle tip** (small polygon triangle, `on-surface`).

#### 4.6.3 Equivalence chips row

3 chips below syringe (equal-grow, side-by-side):
- `primary-container` chip — value "0.25" / unit "mg" (mass)
- `secondary-container` chip — value "0.10" / unit "mL" (volume)
- `tertiary-container` chip — value "10" / unit "units" (insulin units)

Each chip: `M3/title/large-emphasized` value above `M3/label/small` unit, center-aligned.

#### 4.6.4 Mix section (inputs)

Section header "Mix" (`primary` label color).

2×2 compact grid:
1. **Container** — value + unit (read-only from compound).
2. **Diluent** — editable numeric + "mL".
3. **Desired dose** — editable numeric + unit.
4. **Display** — dropdown: mL / U-100 (Insulin units) / Tuberculin.

Each tile: `surface-container`, corner 16dp, leading icon (colorize / water_drop / straighten / tune), label `M3/label/small` above value row `M3/headline/small-emphasized` + unit `M3/body/medium`.

#### 4.6.5 Dose ladder

Section header "Dose ladder".

Horizontal scrollable row of dose rungs. Each rung: vertical pill, padding 10h × 8v. Selected rung = `primary` fill, white text, corner 16dp (shape break vs unselected 999r). Unselected = outlined.

Default rungs computed: [0.1, currentDesired/2, currentDesired, currentDesired×2, currentDesired×3]. Each rung shows dose value `M3/title/small-emphasized` + unit equivalent (e.g. "10 u").

Tap rung → previews syringe fill width at that dose (animated spring transition, see motion specs). Sets desired dose on confirm.

#### 4.6.6 Result tiles

Section header "Result".

3 horizontal tiles, varied corners (24/8/24):
1. **Concentration** (24r): `2.5 mg/mL` + label "Concentration", leading `calculate` icon.
2. **Doses / container** (8r): `20` + label, leading `inventory_2`.
3. **Supply** (24r): `140 d` + label, leading `today`. Computed = `dosesPerContainer × numberOfContainers / dosesPerDayAcrossActiveProtocols`.

#### 4.6.7 Save dock

Sticky bottom dock, filled `primary` button full-width: leading `check` + "Save & set concentration".

On tap:
- Update `CompoundSupply.concentration` + `concentrationUnit`.
- Regenerate displayed volume on all Pending `ScheduledDose` for this compound (plannedDose unit untouched).
- Return to caller (Create Compound → field filled, Compound Detail → toast confirmation, Edit Compound → field filled).

#### 4.6.8 Motion
Syringe fill width transitions on dose change: Material spring `MotionScheme.expressive().fastSpatialSpec()` over `width` modifier.

#### 4.6.9 No reset path
No "pre-mixed reset" button. To clear concentration, user closes helper and edits concentration field directly in Create/Edit Compound.

---

### 4.7 Protocols list (`08 · Protocols`)

**Primary goal**: review active protocols + create/edit.

#### 4.7.1 App bar
Leading `tune` (filter) — opens future-extension filter sheet. Title "Protocols". Trailing `search`.

#### 4.7.2 Tabs

Primary tabs (kit `Primary tabs / Label only` set): Active / Paused / Completed.

**Tab definitions**:
- **Active**: `status == Active`. Includes in-break protocols (status=Active w/ derived in-break state from `protocolBreak`).
- **Paused**: `status == Paused`.
- **Completed**: `status == Completed`.

Archived protocols (`deletedAt != null`) never appear in tabs (visible only via Archive view if added later).

#### 4.7.3 Protocol card

Full-width card per protocol, `surface-container`, corner 24dp, padding 16.

Top row: 2-column.
- Left col: name `M3/title/medium-emphasized` + meta `M3/body/small` (`{compound} · {dose} {route}`).
- Right: status pill.
  - **Active**: `primary-container`, label "Active".
  - **In break**: `tertiary-container`, label "In break".
  - **Paused**: `surface-container-highest` outlined, label "Paused".
  - **Completed**: outline-only chip "Completed".

Schedule chips row (horizontal, gap 8):
- Schedule chip: outlined pill, leading `calendar_month` 14sp + label "Weekly · Mon, Thu".
- Next dose chip: outlined, leading `schedule` + "Today 8 PM" or "Tomorrow 8 AM" or "In 5 d (break)".

**Titration progress bar** (only if `escalation != null`):
- Label row: "Titration" + value "0.25 / 1.0 mg" (right-aligned)
- 6dp bar `surface-container-highest`, fill `primary` at `currentDose / targetDose`.

Tap card → §4.8 Protocol Detail.
Long-press → §4.7.4 multi-select mode.

#### 4.7.4 Multi-select mode (`08b · Protocols (multi-select)`)

Entry: long-press row.

Contextual app bar: close × · "N selected" · trailing `more_vert` (Select all / Invert / Toggle status filter).

Selected card visual: fill = `secondary-container`, leading checkbox circle (28dp) on left of card. Unselected cards keep default.

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

Full-width, `surface-container`, corner 20dp. Header: `calendar_month` icon + "Schedule" title.

Key-value table (each row: label left, value right):
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

Key-value table:
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
- **Log dose** (filled `primary`, equal-grow, leading `add`) → §4.10.2-c Log Dose (from Protocol), prefilled w/ this protocol context + next Pending dose.
- **Archive** (`error-container`, leading `delete`) → confirmation → soft-delete.

---

### 4.9 Create Protocol (`11 · Create Protocol`) + 11b forecast view + 10 Edit Protocol

#### 4.9.1 App bar
- Create: title "New protocol", leading `close`.
- Edit: title "Edit protocol", supporting (current protocol name), leading `arrow_back`, trailing `more_vert` (Duplicate / Archive).

#### 4.9.2 Edit-mode warning banner

`tertiary-container` banner at top of content: `warning` icon + "Saving regenerates pending doses" title + "Logged history (Taken / Skipped / Missed) stays intact." supporting. Always visible in Edit mode.

#### 4.9.3 Sections

**Compound** (required):
- Card `primary-container`, full-width: 44dp rounded-square avatar + compound name `M3/title/medium-emphasized` + meta `M3/body/small` (`{category} · {amountPerContainer}{unit} vial · {concentration}`). Trailing `expand_more` → opens compound picker bottom sheet.

**Route** (required):
- Kit `Segmented button` (`Segments=4`). 4 segments: SC / IM / Oral / Topical. Default = compound's typical route OR last-used.

**Planned dose** (required):
- Card `surface-container`, corner 16dp. Leading `straighten` icon. Value column: `display-small-emphasized` numeric + tonal unit pill (`secondary-container`, mg/mcg/IU dropdown w/ chevron). Below: equivalence chip (`tertiary-container`, leading `calculate`) showing "Equivalent: 0.10 mL · 10 insulin units" only when `compound.concentration` set.

**Schedule** (required):
- Filter chips row (single-select): Daily / Every X days / Weekdays / Times/week / Times/day / Times/month.
- **Conditional inputs below** based on chip:
  - **Daily**: nothing extra; `dosageTimes` editable.
  - **Every X days**: numeric "every N days" input.
  - **Weekdays**: 7-day circle picker. Each circle 44×44dp, selected = `primary` fill, unselected = outlined.
  - **Times/week**: numeric 1-7.
  - **Times/day**: numeric, opens N empty time slots in Times of day section.
  - **Times/month**: numeric 1-31.
- **Times of day** (optional list, below):
  - List of time pills (selected = `secondary-container`, leading `schedule`).
  - "Add time" outlined pill → opens kit Time Picker.
  - **Empty list allowed** = "no specific time" (dose appears as "Today" on dashboard).

**Duration**:
- 2-column row: **Start** date box + **End** (Optional) date box. Each box: `surface-container`, corner 16dp, "Start" / "End" label + value row w/ `today` icon. Tap → Material Date Picker.

**Reminder**:
- Card `surface-container`, corner 16dp. Leading `notifications` icon. Content column: "Notify at dose time" + supporting "Offset: 0 min before · normal style".
- Kit `Switch` on right.
- When switch ON AND `dosageTimes` non-empty: schedule alarms at `dosageTime - reminderOffsetMinutes`.
- When switch ON AND `dosageTimes` empty: reveal **Reminder bucket selector** (chips Morning 9am / Afternoon 1pm / Evening 7pm) — default Morning. Alarm scheduled at fixed daily time.

**7-day preview** (live-updated when schedule/duration changes):
- `surface-container` card, corner 16dp. Header: `calendar_month` + "Next 7 days · N doses".
- 7 day cells horizontal row, equal-grow. Each cell: letter (top) + day number + 5×5 dot at bottom if scheduled. Cells: `primary-container` (fires) vs `surface` (no fire).

**Site restriction** (Optional):
- Card `surface-container`, leading `person_pin_circle`, value "Abdomen only" or "No restriction", trailing `expand_more` → picker.

**Notes** (Optional): multi-line text.

**Forecast & warnings** (live-computed):
- Section header.
- Card `surface-container`. Header: `monitoring` + "Inventory forecast".
- 3 stat tiles (equal-grow): doses left (primary-container) / days left (secondary-container) / run-out date (tertiary-container).
- **Warning row** (`error-container`): "Batch expires before protocol end" + "Jul 14 expiry · Aug 02 run-out".
- **Suggested fix row** (`tertiary-container`, optional): `lightbulb` icon + "Order N more vials by {date}" + supporting + "Add" CTA pill (`tertiary` fill).

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

Kit Bottom sheet `Modal=True`. Scrim. Drag handle.

**Header**: round 40dp `primary` avatar w/ compound icon (`colorize`) + title col ("Take {compound name}" `headline-small-emphasized` + supporting "{protocol} · scheduled {time}" / "today").

**Dose to log hero card** (`primary-container`, corner 24dp):
- Label row: "Dose to log" left + "Edit" pill right (leading `edit`, transparent fill).
- Value row: `display-small-emphasized` numeric + `title-medium-emphasized` unit + equivalence "= 0.10 mL" `M3/body/medium`.
- **Adjust chips row** (4 pills, equal-grow optional): -0.05 / -0.01 / +0.01 / +0.05. Filled `primary`, `on-primary` text. Tap = mutate Actual dose ±.

**Site card** (`surface-container`, corner 20dp):
- Header: `person_pin_circle` icon + "Injection site" label (grow) + "Suggested" tag pill (right, `secondary-container`, leading `flag` icon).
- Selected site row: site name `title-medium-emphasized` + "Change" link `primary` + `chevron_right`. Tap → §4.12.5 Site picker.
- Alternative chips row (3 outlined pills): next-best sites per rotation. Tap = select that site.

**When field** (`surface-container`, corner 16dp): `schedule` icon + "When" label + value "Now · Tue May 26, 9:30 AM" + trailing `edit` (opens time picker).

**Inventory deduction preview** (`tertiary-container`, corner 12dp): `inventory_2` icon + "Will deduct 0.10 mL · 3.1 mL left" `M3/label/medium-emphasized`.

**Action row**:
- **Partial** (text button, `surface-container-highest` filled): switches mode → user must enter Actual < Planned, status = Partial on save.
- **Confirm taken** (filled `primary`, equal-grow, leading `check`): saves AdministrationEvent, status=Taken (deduced from Actual==Planned) or Partial (if user edited Actual < Planned).

**No Skip option** in this sheet (per spec: Skip lives on dose card overflow menu only).

#### 4.10.1.1 Long-press Confirm (`15b · Take Dose (long-press confirm with note)`)
Long-press "Confirm taken" → contextual menu overlay (240dp wide, `surface-container-high`, corner 16dp) above button:
- Confirm taken (`check` icon) — same as tap
- Confirm with note (`edit` icon) — opens inline note text field before save

#### 4.10.2 Log dose full-screen forms

Three variants, same skeleton, different context:

##### a) `12 · Log Dose (from Dashboard)` — generic
- App bar: leading `close`, title "Log dose".
- Compound chip at top — selectable.
- Planned vs Actual side-by-side (Planned only if linked to protocol).
- ±chips + "Set planned" button (resets Actual to Planned).
- Route / When / Site / Status segmented (Taken/Partial/Skip).
- Inventory deduction preview.
- Dock: Add (component, opens compound picker for grouping) + Save dose.

##### b) `13 · Log Dose (from Compound)` — manual, no protocol context
- App bar: leading `close`, title "Manual log", supporting compound name.
- Compound chip preselected ("No protocol · manual entry" sub-line).
- **Link to protocol** (Optional) row — when set, screen morphs into protocol-linked mode (planned/actual columns appear).
- Single **Actual dose** hero card (`secondary-container`) — no Planned column.
- "Set planned" chip **hidden** in manual mode.
- Same Route / When / Site / Status / Inventory / Add / Save.

##### c) `14 · Log Dose (from Protocol)` — protocol context
- App bar: leading `close`, title "Log dose", supporting protocol name.
- Protocol hero card (`primary-container`): avatar + compound + protocol + dose info + meta chips (Scheduled time / Dose N of M).
- Planned vs Actual split (Planned shown).
- Adjust chips.
- Same other fields.

##### Common to all 3
- **Status segmented** (3-segment kit-style buttons): Taken / Partial / Skip. Inventory deduction shown only when Taken or Partial.
- **Add** in dock → pivots into grouped event mode (compound picker for 2nd component, then UI shifts to grouped layout).
- **Save dose** → creates AdministrationEvent + DoseComponent(s). Marks linked ScheduledDose (if any) with matching status.

#### 4.10.3 Log Grouped Event bottom sheet (`16`)

**Trigger**: Dashboard grouped admin suggestion "Log grouped event" CTA OR §4.10.2 "Add" → grouped mode.

Kit Bottom sheet `Modal=True`. Scrim. Drag handle.

**Header**: 40dp `tertiary` rounded-square avatar w/ `vaccines` icon + title "Log grouped injection" `headline-small-emphasized` + supporting "N compounds · same route + site + time".

**Shared context pills** (row of editable pills, `tertiary-container`):
- Route pill (e.g. "SC" + `science` icon) — tap opens route picker
- Site pill (e.g. "Abdomen R" + `person_pin_circle`) — tap opens site picker
- Time pill (e.g. "Now · 8:00 PM" + `schedule`) — tap opens time picker

**Component rows** (one per included DoseComponent):
- `surface-container` card, corner 16dp.
- 32dp `tertiary-container` round avatar w/ `colorize` icon.
- Content col: compound name `body-large-emphasized` + "Planned {dose} · {volume}" `body-small`.
- Editable dose pill on right (`secondary-container`, value + `edit` icon).
- Long-press row → remove from group.

**Add another compound** dashed-outline row: leading `add` + label.

**Safety + summary** (`tertiary-container`, corner 12dp):
- Leading `warning` icon
- "Only log together if injected together" title `label-medium-emphasized`
- Supporting: "Will deduct: 0.10 mL BPC-157 + 0.20 mL TB-500"

**Validation**:
- All components must be Injectable form
- Route must be SC or IM
- Min 2 components

**Mixed protocol + manual components allowed**: each DoseComponent's `protocolId` / `scheduledDoseId` can be null independently.

**Actions**:
- **Cancel** (text)
- **Log injection** (filled `tertiary`, leading `vaccines`, equal-grow): creates single AdministrationEvent w/ N DoseComponents sharing route + injectionSiteId + loggedAt. Each ScheduledDose linked → marked Taken.

#### 4.10.4 Edit dose (`17 · Edit dose`)

**Trigger**: any dose history row tap (Compound Detail · Protocol Detail · Administration Event detail's "Edit dose" button).

Stripped-down form:
- App bar: leading `close`, title "Editing dose".
- Planned + Actual fields (side-by-side, `surface-container` / `secondary-container`).
- Route field.
- When field.
- Injection site field.
- **Status segmented binary**: Taken / Skipped only. Partial requires full §4.10.2 flow.
- Bottom dock: Save dose (filled `primary`, full-width).

**Status change inventory side-effects**:
- Taken → Skipped: reverses inventory deduction.
- Skipped → Taken: applies inventory deduction.
- Dose value change while Taken: applies delta (positive or negative).

---

### 4.11 Administration Event detail (`23 · Administration Event detail`)

**Trigger**: dose history row tap from anywhere.

#### 4.11.1 App bar
Leading `arrow_back`, title "Dose detail", supporting timestamp (e.g. "Tue May 26 · 8:14 PM"). Trailing `more_vert` (Edit / Delete).

#### 4.11.2 Status hero card
Full-width, `primary-container`, corner 24dp. 48dp `primary` round avatar w/ status icon (check / schedule / close). Title `headline-small-emphasized` = status (Taken / Partial / Skipped / Missed). Supporting `body-small` = "Logged Tue May 26 · 8:14 PM".

#### 4.11.3 Dose components

Section header "Dose components · N".

Per-component card (`surface-container`, corner 20dp):
- Top row: 40dp `primary-container` avatar w/ `colorize` + compound name + protocol context "Sema weekly titration" (or "Manual entry" if no protocol).
- 3 stat tiles (equal-grow, corner 12dp):
  - **Planned** (`surface-container-low`): dose value
  - **Actual** (`secondary-container`): dose value
  - **Volume** (`tertiary-container`): mL value

#### 4.11.4 Field rows

- **Route**: `surface-container`, leading `science`, value "Subcutaneous".
- **Injection site**: `surface-container`, leading `person_pin_circle`, value "Abdomen · Right (lower)", supporting "Marked cooling for 7 days".
- **Notes card** (`surface-container`): `edit` header + "Notes" label + body or "No notes for this dose.".

#### 4.11.5 Inventory effect

`tertiary-container` row, corner 16dp. `inventory_2` icon + "Deducted 0.10 mL" + "From Semaglutide opened vial · 3.0 mL remaining".

For Skipped: "No inventory deducted" message.

#### 4.11.6 Bottom dock
- **Delete** (`error-container`, leading `delete`): confirmation → hard-delete AdministrationEvent + DoseComponents, reverses inventory deduction, sets linked ScheduledDose back to Pending.
- **Edit dose** (filled `primary`, equal-grow, leading `edit`) → §4.10.4 Edit dose.

---

### 4.12 Sites (`18 · Sites`)

**Primary goal**: pick next injection site w/ rotation confidence.

#### 4.12.1 App bar
Leading `history` icon = decorative only (no action). Title "Sites". Trailing `search`.

#### 4.12.2 Route filter chips (top of content)
Kit filter chips: All routes / SC / IM. Filters all subsequent stats + body map + carousel by route.

#### 4.12.3 Stats strip

3 tiles (equal-grow), varied corner radii:
- **Ready** (24r) `secondary-container`: count of sites where `avoidUntil < now AND isAvailable`. Leading `check_circle`.
- **Cooling** (8r) `error-container`: count where `avoidUntil > now`. Leading `restart_alt`.
- **This month** (24r) `tertiary-container`: count of AdministrationEvents w/ site set in current calendar month. Leading `bolt`.

Each tile: label `M3/label/small` + leading icon row (top) + value `M3/headline/small-emphasized` (below).

#### 4.12.4 Body map hero

`surface-container-low`, corner 32dp.

**Top tabs** (kit-style pill segmented inside hero):
- **Front** (selected default) / **Back** — switches body view.
- **View mode toggle** (small segmented at right): **Dots** / **Heat** — switches `18` ↔ `18b` heat-map mode.

**Body silhouette canvas** (380×360dp, drawn programmatically):
- Head ellipse 46×56 at top-center.
- Neck 24×14 below head.
- Torso 160×200, top corners 56r, bottom corners 28r (`surface-container-highest` fill).
- Arms 36×180 each side, fully rounded (18r).
- Legs 54×96 each, top 16r / bottom 32r.

**Front-side sites** (dots):
- Abdomen UL · UR · LL · LR (4 quadrants around centerline y=180-220)
- Anterior deltoid L · R (y=110, x=cx±90)
- Lateral thigh L · R (y=320, x=cx±33)
- Forearm L · R (y=210, x=cx±115)

**Back-side sites** (when Back tab):
- Glute upper-outer L · R
- Hamstring L · R
- Lower back L · R
- Posterior deltoid L · R

**Dot states** (size/color):
- **Suggested** (next-rotation): 22dp `primary` filled + 40dp `primary` 60%-opacity ring around it (focal accent).
- **Cooling** (`avoidUntil > now`): 16dp `error` filled.
- **Recent** (used <14d ago): 14dp `secondary` filled.
- **Available** (ready, used ≥14d ago or never): 12dp `outline` filled.

**Heat map mode** (`18b`): replaces dots with blurred ellipses (`error` fill, varying opacity 0.05–0.7 by usage frequency, 8px layer blur). Hotter = recently/frequently used. Used to visualize over-rotation.

**Legend** at bottom of hero (when Dots mode): Suggested / Cooling / Recent / Ready w/ swatch dots.

**Heat legend** (when Heat mode): Recent / Cooling / Older / Untouched w/ varied-opacity dots.

#### 4.12.5 Suggested site hero

`primary-container` card, corner 28dp.
- Top row: 56dp round `primary` avatar w/ `person_pin_circle` icon + content col:
  - Tag pill (`primary` filled, leading `bolt`): "Best for now"
  - Headline `headline-small-emphasized`: "Abdomen · Lower right"
- Fact chips row: "14 days rested" (`primary` filled, leading `schedule`) + "Cooling complete" (`primary` filled, leading `check_circle`).
- Action row:
  - **Use this site** (large filled, `on-primary-container` background w/ `primary-container` text = inverted contrast, equal-grow, leading `arrow_forward`) — returns selected site to caller flow.
  - **Pick another** (text button, `on-primary-container` text) → §4.12.7 Site picker full list.

#### 4.12.6 Recent activity carousel

Horizontal scrollable row of square site cards (140×140dp).
- **Cooling card**: `error-container`, corner 28r, leading 32dp `error` avatar w/ `restart_alt`.
- **Ready card**: `surface-container`, corner 20r, leading 32dp `secondary-container` avatar w/ `check`.
- Each card: avatar + site name `title-small-emphasized` + "N days ago" `body-small`.

Tap card → §4.12.8 Site detail bottom sheet.

#### 4.12.7 Site picker (`22 · Site picker`)

Full-screen list. App bar title "Pick site", supporting "For {compound} · {route}". Leading `arrow_back`.

Filter chips at top: All / Ready / Cooling (single-select).

**Suggested** section: top row = suggested site (`primary-container` highlight, "Best" pill on right).

**All sites · N** section header. Rows for every site matching filter:
- Status dot (36dp): `error` w/ `restart_alt` (cooling), `secondary-container` w/ `check` (ready).
- Site name + meta (e.g. "Last used 2 days ago", "Never used").
- Cooling pill ("Cool 2d") if applicable.

Bottom dock: Cancel + Pick site (filled `primary`, requires selection).

#### 4.12.8 Site detail bottom sheet (`18c · Site detail (bottom sheet)`)

**Trigger**: tap any dot on body map OR tap site card in carousel.

Kit Bottom sheet `Modal=True`. Drag handle. Scrim.

- Header: 48dp avatar w/ status (cooling = `error-container` + `restart_alt`; ready = `secondary-container` + `check`) + site name `headline-small-emphasized` + supporting "{status} · {info}".
- Stats row (3 tiles): Times used · Route · Last used.
- Recent uses list (last 2-3 AdministrationEvents at this site).
- Actions row:
  - **View history** (`secondary-container`, leading `history`) → full site history list (filtered Compound Detail-like view scoped to site).
  - **Mark unavailable** (`error-container`, leading `block`) → toggles `isAvailable = false`.

No "Use this site" CTA here (that's on Site picker / Take Dose context). This sheet is informational/management.

---

### 4.13 Settings (`20 · Settings`)

#### 4.13.1 No profile card (single-user local app).

#### 4.13.2 Section: Appearance
- **Theme** row (`surface-container`, leading `dark_mode`, value "System", trailing `chevron_right`) → opens **Theme picker dialog** (`20b · Settings (Theme picker dialog)`):
  - Kit-style basic dialog, 312dp wide, centered, `surface-container-high`, corner 28dp.
  - Title "Theme" `headline-small-emphasized`.
  - 3 radio rows: System default (`phone_android`, selected default) / Light (`light_mode`) / Dark (`dark_mode`).
  - Buttons row right-aligned: Cancel (text, `primary`) + Save (filled `primary`).
- **Dynamic color** row w/ kit Switch on right.

#### 4.13.3 Section: Reminders
- **Notification style** (Silent / Normal / Persistent) — single-choice dialog.
- **Time zone** — opens searchable time zone list dialog. Default = device zone.
- **Missed dose window** (5–60 min, default 15) — numeric input sheet. After elapsed, Pending → Missed via WorkManager job.

#### 4.13.4 Section: Data
- **Export JSON** → file picker save → produces `stax-export-YYYY-MM-DD.json`.
- **Import JSON** → file picker open → preview dialog (row counts per entity) → confirm → ID remap import (see §5.6).
- **Reset all data** row — `error-container` fill, `error-container` text, leading `delete` icon in `error-container` themed style. Tap → typed-confirm dialog ("Type RESET to confirm") → hard-deletes all entities.

#### 4.13.5 Footer
App identity + version: "Stax" `label-medium-emphasized` + "v 1.0.0 · build N" `label-small` (both `on-surface-variant`). Centered, padding-top 24dp, padding-bottom 16dp.

---

### 4.14 Onboarding (`21 · Onboarding`)

**3 steps** (all skippable except notification permission gate which is separate, §4.15).

#### Step 1 — Welcome
- Blob illustration hero (3 overlapping shapes: `primary-container` + `tertiary-container` + `secondary-container`) + centered 96dp `primary` round logo w/ `vaccines` icon.
- Headline `display-small-emphasized` "Track your stack,\nstay on protocol." (2-line).
- Subtitle `body-large` value-prop.
- **Notification permission row removed from onboarding** — handled by gate screen §4.15.
- CTA: "Continue" (filled `primary`, full-width).
- Skip text below.
- Step indicator pill row at top: 1st pill 24×8dp `primary` fill + 2 small pills 8×8dp `outline-variant`.

#### Step 2 — Add first compound (skippable)
- Reuses §4.4 Create Compound. App bar title "Add your first compound · 2 of 3".
- Skip button in app bar trailing.

#### Step 3 — Create first protocol (skippable)
- Reuses §4.9 Create Protocol. App bar title "Create your first protocol · 3 of 3".
- Skip button.

Completion → Dashboard. Persists `Settings.onboardingCompleted = true`.

---

### 4.15 Notification permission gate (NEW)

**Trigger**: app launch (or resume) AND `POST_NOTIFICATIONS` not granted (Android 13+).

**Not skippable**. Blocks all navigation until granted OR user explicitly opts out via system Settings.

Layout: similar to Onboarding step 1 (blob illustration + headline) but:
- Headline: "Enable dose reminders"
- Subtitle: "Stax needs notification permission to remind you about scheduled doses. Without it, you'll only see reminders inside the app."
- Single CTA: "Allow notifications" (filled `primary`, full-width). Triggers system permission dialog.
- Secondary CTA (text): "Open system settings" — only shown if permission permanently denied (rationale check via `shouldShowRequestPermissionRationale = false` after previous denial).
- No close × or back button.

On grant → dismisses, navigates to Dashboard (or Onboarding next step if first launch).

---

## 5. System Behaviors

### 5.1 Notifications and reminders

**Architecture**:
- `AlarmManager.setExactAndAllowWhileIdle` for dose reminders (medical use case justifies exact alarms). Declare `SCHEDULE_EXACT_ALARM` permission.
- **WorkManager** periodic jobs:
  - **`GenerateScheduledDosesWorker`**: runs daily 03:00 device-local + on app start. Ensures every Active protocol has ScheduledDoses for next 7 days.
  - **`InventoryExpiryCheckWorker`**: runs daily 08:00. Marks Pending → Missed when `scheduledAt + missedDoseWindowMinutes < now`. Recomputes inventory warnings.
  - **`AlarmReconcileWorker`**: on boot via `BootReceiver`, re-schedules all pending alarms.
- **Notification channels**:
  - `dose_reminders` (high priority, badge, lights, vibration per user style setting)
  - `warnings` (default, no sound)

**Reminder lifecycle**:
- ScheduledDose created → if `protocol.reminderEnabled` AND `dosageTimes` non-empty: alarm scheduled at `scheduledAt - reminderOffsetMinutes`.
- ScheduledDose created AND `dosageTimes` empty AND `reminderBucket` set: alarm at bucket time for that day (Morning=09:00, Afternoon=13:00, Evening=19:00, device-local).
- Snoozing updates `scheduledAt`; reschedules alarm.
- Logging or skipping cancels.

### 5.2 ScheduledDose generation

`GenerateScheduledDosesWorker` ensures 7-day horizon. Generation respects:
- **Schedule type + dosageTimes**: empty `dosageTimes` → single dose per day w/ `hasTimeOfDay=false`, `scheduledAt = startOfDay(date)`.
- **Active Escalation rule**: applies current dose at the time the rule fires (computed against `startDate` + accumulated doses).
- **ProtocolBreak**: skip generation during off-days (compute on/off cycle from `startDate`).
- **endDate**: no generation past it; on `endDate` passing, set `Protocol.status = Completed`.

Generation is idempotent — uses `(protocolId, scheduledAt)` uniqueness; never duplicates.

### 5.3 Inventory deduction

On AdministrationEvent save:
- **Taken / Partial**: for each DoseComponent:
  - Compute deduction in compound's container unit. For injectables: `actualDose / compoundSupply.concentration` (in mL). For unit-based: `actualDose` (count).
  - Decrement `currentOpened.remainingAmount` by deduction.
  - Insert `InventoryTransaction { type=DoseDeduction, delta=-deduction, sourceEventId }`.
  - If `remainingAmount <= 0`:
    - Set `currentOpened = null`.
    - Decrement `numberOfContainers` by 1.
    - Insert `InventoryTransaction { type=ContainerClose, delta=0 }` (audit).
    - If `numberOfContainers > 0` → snackbar prompt: "Open new container?" → on confirm, create new OpenedContainer w/ `openedAt=now, remainingAmount=amountPerContainer`. Default action = auto-open after 5s timeout.
- **Skipped / Missed**: no deduction.
- **Manual adjustment** (Adjust Inventory bottom sheet): insert `InventoryTransaction { type=Manual, delta, reason }`. If positive delta: add to `currentOpened.remainingAmount` (or open new container if none open and delta ≥ amountPerContainer). If negative: subtract from opened first, then unopened.

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
- Default zone = device. Settings allows override (Travel mode).
- DST handled by `kotlinx.datetime` — 8:00 PM dose remains 8:00 PM local across DST transitions.
- On TZ change: future Pending ScheduledDoses re-anchored, alarms re-scheduled.

### 5.8 Motion specs (M3 Expressive)

| Use | Spec |
|-----|------|
| Screen-to-screen navigation | `MotionScheme.expressive().fastSpatialSpec()` |
| Bottom sheet enter/exit | `MotionScheme.expressive().defaultSpatialSpec()` |
| Dose card "Take" tap → sheet | `MotionScheme.expressive().fastSpatialSpec()` + shape morph from card corner 24r → sheet corner 28r |
| Syringe fill width change | spring damping 0.8, stiffness 380 |
| FAB → FAB menu open | `defaultSpatialSpec()` w/ stagger |
| Selected nav item indicator | `fastSpatialSpec()` w/ shape morph (0r → 999r pill) |
| Day chip select | shape morph 999r → 20r + color cross-fade 200ms |
| Theme change | `defaultEffectsSpec()` 300ms cross-fade |

### 5.9 Accessibility
- All tappable targets ≥ 48×48dp.
- TalkBack labels on every interactive element.
- Status communicated through both icon + color (never color alone).
- Reduce motion respected: `Settings.System.TRANSITION_ANIMATION_SCALE` honored, falls back to instant transitions.
- Body map dots: each has content description "{site name}, {status}".
- Syringe visualization: aria-label = "Syringe with N units, equivalent to X mg".

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
- Theme picker (basic dialog, not sheet)

### 6.4 Adaptive behavior
**Compact** (<600dp): single-pane, bottom nav, FABs at bottom-right.

**Medium** (600–839dp, foldable unfolded): switches to side rail. Compound list ↔ Compound Detail becomes list-detail two-pane. Protocols ↔ Protocol Detail same. Sites uses single-pane (body map is hero, splitting reduces impact). Dashboard stays single-pane.

Touch targets remain ≥ 48×48 regardless of breakpoint.

---

## 7. Empty / error states

| Screen | Empty state | Error state |
|--------|-------------|-------------|
| Dashboard | §4.1 state 2 | Toast "Couldn't load doses · Retry" |
| Compounds | "No compounds yet · [Add compound]" hero | — |
| Protocols | "No protocols yet · [Create protocol]" hero | — |
| Sites | "No sites yet" hero w/ "Add site" CTA | — |
| Dose history (Compound/Protocol Detail) | "No doses logged yet" inline | — |
| Inventory tab | "No inventory adjustments" inline | — |
| Site detail · recent uses | "Never used at this site" | — |

---

## 8. Validation rules summary

| Field | Rule |
|-------|------|
| CompoundSupply.name | required, ≥1 char, ≤80 chars |
| CompoundSupply.amountPerContainer | > 0 |
| CompoundSupply.numberOfContainers | ≥ 0 |
| CompoundSupply.concentration | > 0 if set; required when Form=Injectable AND Container≠Ampoule |
| Protocol.name | required, ≥1 char, ≤80 chars |
| Protocol.plannedDose | > 0 |
| Protocol.startDate | required |
| Protocol.endDate | if set, must be > startDate |
| Schedule.interval (EveryXDays) | ≥ 1 |
| Schedule.timesPerDay | ≥ 1 |
| Schedule.selectedWeekdays | ≥ 1 day |
| Escalation.targetDose | > startDose |
| Escalation.increaseAmount | > 0 |
| Escalation.increaseEveryValue | ≥ 1 |
| ProtocolBreak.daysOn | ≥ 1 |
| ProtocolBreak.daysOff | ≥ 0 |
| OpenedContainer.remainingAmount | ≥ 0, ≤ compound.amountPerContainer |
| OpenedContainer.openedAt | ≤ now |
| DoseComponent.actualDose | > 0 when status=Taken or Partial |
| AdministrationEvent.injectionSiteId | required when route in (SC, IM) |

---

## 9. Design tokens reference

Use M3 paint styles from kit (file key `bySPmbwlmkbMqS3HIfQWVm`, `M3/sys/light/...` family). User's chosen theme color drives the palette via Material Theme Builder; all surfaces and roles auto-update.

**Used roles** (non-exhaustive):
- `primary`, `on-primary`, `primary-container`, `on-primary-container`
- `secondary`, `on-secondary`, `secondary-container`, `on-secondary-container`
- `tertiary`, `on-tertiary`, `tertiary-container`, `on-tertiary-container`
- `error`, `on-error`, `error-container`, `on-error-container`
- `surface`, `on-surface`, `on-surface-variant`
- `surface-container-low`, `surface-container`, `surface-container-high`, `surface-container-highest`
- `outline`, `outline-variant`

**Type scale**: pure M3 styles (`M3/display/*`, `M3/headline/*`, `M3/title/*`, `M3/body/*`, `M3/label/*` with `-emphasized` variants where applicable). Font family override on all = **Google Sans Flex**.

**Shape scale**: emphasized via M3 Expressive — corner radii vary intentionally:
- Buttons / pills: 999dp (full)
- Cards: 16 / 20 / 24 / 28dp depending on hierarchy
- Selected day chip: 20dp (squircle, breaks pill rhythm)
- Cooling carousel card: 28dp (breaks ready cards' 20dp)
- Result tiles in Recon: alternating 24 / 8 / 24
- Hero containers: 28 / 32dp

---

## 10. Open / unresolved items

Items still to design or define:
- Adjust Inventory bottom sheet (referenced in §4.1.7 + §4.3.9 dock).
- AdministrationEvent search/filter UI (currently history scoped to compound or protocol only).
- Backup / restore-from-backup mechanism (§4.13.4 has Import but no incremental backup).
- Foldable two-pane layouts at medium breakpoint (out of scope for v1 per spec §1).