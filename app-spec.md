# Peptide / Supplement / Hormone Tracker — App Spec

## 1. Overview

A Material 3 Expressive Android app for tracking peptides, supplements, hormones, injectables, and topicals. Core features: compound inventory, protocol scheduling, dose logging (single and multi-compound injections), injection site rotation, reconstitution helper, expiry/stock warnings, JSON export/import. Local-first, fully offline.

This app will only be used by regular and foldable phone users, so the UI need to be optimized for those form factors (No large and extra large breakpoints).

## 2. Principles

### 2.1 Local-first
- Works fully offline. Room database. No accounts. No network.
- All user data exportable and importable as JSON.

### 2.2 Use simplicity
- Every common action reachable in ≤2 taps from the dashboard.
- Never ask for data the app already knows or can derive.
- Sensible defaults everywhere (actual dose = planned dose, suggested site from rotation, route prefilled from protocol, time prefilled to now).
- No multi-step wizards where a single screen with smart defaults works.

### 2.3 Performance
- Cold start to interactive: <400ms target. Use Baseline Profiles and the App Startup library.
- All UI interactions feel instant: 16ms frame budget, no blocking I/O on the main thread.
- Heavy work (export, ScheduledDose generation, inventory recalc) runs off-main via coroutines + WorkManager.
- Battery-aware: no foreground services. AlarmManager + WorkManager only.
- Beautiful UI is non-negotiable. Animations, Material 3 Expressive motion, dynamic color, and shaders stay. Performance is bought by doing them correctly (hardware-accelerated, hoisted state, stable composables), not by removing them.
- Use remember to minimize expensive calculations
- Use lazy layout keys
- Use derivedStateOf to limit recompositions
- Defer reads as long as possible
- Avoid backwards writes
- Flatten view hierarchy by reducing redundant or nested layouts.
- Keep the main thread unblocked at all times, and use threads strategically.
- Don't perform blocking or long-running operations on the app's main thread. Instead, create a worker thread and do most of the work there.
- Try to minimize any lock contention between the main thread and other threads.
- Activities must do as little as possible to set up in key lifecycle methods, such as onCreate() and onResume().
- Minimize slow or blocking operations in the app's startup code, such as methods run during dagger initialization.

### 2.4 Technology
- Kotlin, Jetpack Compose, Material 3 Expressive
- MVI architecture with Koin DI
- Google Sans Flex font
- Room (database), WorkManager (background), AlarmManager (exact reminders)
- Navigation 3, adaptive for foldables and tablets (can use all adaptive libs such as: androidx.compose.material3.adaptive.navigation3, androidx.compose.material3.adaptive.layout, androidx.compose.material3.adaptive)
- You must use the material-expressive-ui skill to think about the design of the app.

---

## 3. Domain Model

Five primary entities plus injection sites.

### 3.1 CompoundSupply
Something the user owns.

```
id: Long
name: String
category: Peptide | Supplement | Hormone | Medication
form: Injectable | Capsule | Tablet | Powder | Liquid | Topical
containerType: Vial | Bottle | Blister | Packet | Tub | Ampoule
primaryUnit: mcg | mg | g | IU | mL | capsule | tablet | scoop | drop
amountPerContainer: Double           // e.g. 5 (mg), 60 (capsules)
concentration: Double?               // mass per mL (or per dose form)
concentrationUnit: String?           // e.g. "mcg/mL", "IU/tablet"
numberOfContainers: Int              // total owned (editable any time)
currentOpened: OpenedContainer?      // see below; one at a time
batchExpiryDate: LocalDate?
storageLocation: Fridge | RoomTemp | Freezer
batchNumber: String?
supplier: String?
notes: String?
deletedAt: Instant?                  // soft-delete; see §5.5
```

```
OpenedContainer:
  openedAt: Instant
  remainingAmount: Double
  remainingUnit: String
  expiryAfterOpeningDays: Int?
  userDefinedExpiryDate: LocalDate?
  predictedExpiryDate: LocalDate     // derived
```

Only one container is open at a time per compound. When `remainingAmount` hits 0, `numberOfContainers` decrements by 1 and a new `OpenedContainer` is created (if any remain).

### 3.2 Protocol
How a compound is used.

```
id: Long
name: String
compoundSupplyId: Long
plannedDose: Double
plannedDoseUnit: String
route: Subcutaneous | Intramuscular | Oral | Topical
schedule: Schedule
dosageTimes: List<LocalTime>         // empty = no specific time
escalation: Escalation?
protocolBreak: ProtocolBreak?
startDate: LocalDate                 // can be past
endDate: LocalDate?                  // null = open-ended
reminderEnabled: Boolean
reminderOffsetMinutes: Int           // 0 = at scheduled time
injectionSiteRestriction: BodyRegion?
notes: String?
status: Active | Paused | Completed  // "InBreak" is derived from protocolBreak
deletedAt: Instant?                  // soft-delete
```

```
Schedule:
  type: Daily | EveryXDays | XTimesPerDay | SpecificWeekdays
      | XTimesPerWeek | XTimesPerMonth
  interval: Int?                     // for EveryXDays
  timesPerDay: Int?                  // for XTimesPerDay
  selectedWeekdays: Set<DayOfWeek>?
  timesPerWeek: Int?
  timesPerMonth: Int?

Escalation:
  startDose: Double
  targetDose: Double
  increaseAmount: Double
  increaseEvery: EveryXDays | EveryXWeeks | AfterXDoses
  increaseEveryValue: Int
  maxDose: Double?
  stopAtTarget: Boolean

ProtocolBreak:
  daysOn: Int
  daysOff: Int                       // e.g. 5/2, 56/28
```

The equivalent-dose display (`0.1 mL / 200 mcg`) is derived at display time from `compoundSupply.concentration`. Not stored.

### 3.3 ScheduledDose
One planned occurrence generated from a protocol.

```
id: Long
protocolId: Long
compoundSupplyId: Long
scheduledAt: Instant                 // shifted directly by snooze
plannedDose: Double
plannedDoseUnit: String
route: Route
status: Pending | Taken | Skipped | Missed | Partial
administrationEventId: Long?         // set when logged
```

Snoozing updates `scheduledAt`; status remains `Pending`.

### 3.4 AdministrationEvent
What the user actually did. One event can contain multiple dose components if injected together.

```
id: Long
loggedAt: Instant
route: Subcutaneous | Intramuscular | Oral | Topical
status: Taken | Skipped | Partial
injectionSiteId: Long?               // required when route is injectable
notes: String?
components: List<DoseComponent>      // 1+
```

### 3.5 DoseComponent
One compound inside an event.

```
id: Long
administrationEventId: Long
scheduledDoseId: Long?               // null for manual logs
protocolId: Long?
compoundSupplyId: Long
plannedDose: Double
plannedDoseUnit: String
actualDose: Double
actualDoseUnit: String
notes: String?
```

Inventory-deducted quantity is computed from `actualDose × compoundSupply.concentration` at save time. Not stored.

### 3.6 InjectionSite
```
id: Long
name: String                         // user-defined or preset
bodyRegion: Abdomen | Quadriceps | Glute | Delt | …
side: Left | Right | Center | NotApplicable
lastUsedAt: Instant?
avoidUntil: Instant?
notes: String?
isAvailable: Boolean
```

---

## 4. Features

### 4.1 Dashboard
Single-screen overview. Cards (shown only when relevant):

- **Today's schedule** — Pending doses for today with quick actions: Take · Snooze (1h / 1d) · Skip · Edit · Change site. When you snooze, if the dose was supposed to be taken at a particular time snooze by 1 hour; if it was a "no specific time" dose snooze by 1 day.
- **Next dose** — Soonest upcoming Pending dose. A dose can have a specific time (e.g. 8:00 PM) or no specific time (just "today"). When the next dose has no specific time, it appears in the dashboard at the end of the day, after all time-specific doses.
- **Possible grouped administration** — When 2+ injectable Pending doses share the same route and are scheduled within 30 minutes or same day if no specific time, suggest logging as one event.
- **Inventory warnings** — Compounds running low based on active protocols.
- **Expiry warnings** — Opened containers or batches within the warning window.
- **Recent activity** — Last 5 AdministrationEvents.

FAB menu: Log scheduled dose · Log manual dose(one off) · Add compound · Add protocol · Adjust inventory.

### 4.2 Compound management
**Inventory math (computed, never stored):**
- Doses remaining = `(openedRemaining + unopenedTotalAmount) / dosesPerContainer`
- Days remaining = `dosesRemaining / dosesPerDayAcrossActiveProtocols`
- Estimated run-out date = today + days remaining
- Required quantity until protocol end = `dosesPerDay × daysUntilEnd`
- Expiry risk = `batchExpiry < estimatedRunOut`

**Warnings shown contextually:**
- *Only N doses left based on active protocols*
- *Opened container expires in N days*
- *Active protocol requires X mL until end date, only Y mL available*
- *Batch expires before protocol end date*

List filters: All · Low stock · Expiring soon · by category · by form.

FAB Action: Create new CompoundSupply.

Can long press a compound → enter multi-select mode to select multiple compounds, then have buttons to delete(Archive) or duplicate them.

#### Compound Detail
Sections: Overview · Active protocols · Stock · Opened container · Dose history · Notes.

For opened container if reconstituted and no expiryAfterOpeningDays, show a recomposed X days ago; if expiryAfterOpeningDays set show it otherwise leave empty.

Log dose(no protocol link, just a one-off but still linked to the current CompoundSupply) and Adjust(edit) buttons.

#### Create / Edit CompoundSupply
Inputs: name, category, form, container type, number of containers, amount per container + unit, concentration + unit (must have recomposition button to open the helper), batch expiry date, container expiry date after opening, storage location, batch number, supplier, notes.

Optionals are: concentration, batch expiry date, container expiry date after opening, batch number, supplier, notes.

When creating a new compound user should be able to add an already opened container right away, with opened date, remaining amount, and optional container expiry date. If not added at creation, the compound is considered unopened until the user log a dose or manually add an opened container.

User should be able to delete and edit current opened container: remaining amount, opened date and expiry date (if set).

This screen is big so it's scrollable. To focus more on body content, set the app bar container to be transparent on scroll. This allows the X buttons to float above the content.

Make sure icon buttons have a container fill. 

### 4.3 Reconstitution helper
Helps the user compute concentration for injectable powders.

**During compound creation:** If the user is creating an Injectable + Powder, the helper opens automatically. If the compound is pre-mixed, the user enters concentration directly without the helper.

**Later edits:** The user can re-open the helper from the compound detail screen to update concentration (e.g. when reconstituting a new vial with a different diluent volume). When concentration changes:
- The compound's `concentration` is updated.
- All Pending ScheduledDoses for this compound have their displayed equivalent volume recomputed. `plannedDose` in the protocol's unit (e.g. 200 mcg) stays the same — only the derived volume display changes.

**Inputs:** container amount, diluent amount, desired dose, optional syringe display unit (mL / insulin units).
**Outputs:** final concentration, dose volume, doses per container.

### 4.4 Protocols
List sections: Active · Paused · Completed · Archived.

Can long press a protocol → enter multi-select mode to select multiple protocols, then have buttons to delete(Archive)/duplicate/Pause/Complete them.

Detail: Schedule · Linked compound · Dose history · Inventory forecast · Site restrictions · Notes.

Actions: Pause · Resume · Edit · Duplicate · Archive · Create new.

**Pause behavior:** Pending future ScheduledDoses are hidden from the dashboard; their reminders are cancelled. No deletion. On Resume, the protocol regenerates ScheduledDoses from today forward.

**Edit behavior:** On save, the app regenerates all `ScheduledDose` rows where `status == Pending` (including snoozed ones, whose `scheduledAt` is still in the future). Taken / Skipped / Missed / Partial doses are immutable history. Reminder alarms are cancelled and re-scheduled.

**Creation:** Pick compound → route → planned dose (equivalent shown if concentration set) → schedule → start/end dates → reminder → site restriction → review with inventory forecast and any expiry warnings.

### 4.5 Logging
**Single dose:** Compound, planned dose (prefilled from protocol), actual dose (prefilled = planned), route (prefilled from protocol), time (prefilled = now), status, site (if injectable; prefilled from rotation), notes.

**Grouped injectable event:** When the dashboard suggests grouping or the user picks *Log grouped event*:
- One shared route, one shared injection site, one shared time
- 2+ components, each with its own compound + planned + actual dose
- Validation: all components must be injectable; route must match across components

**Before save:** show inventory deduction preview, e.g. *"Will deduct 1.0 mL from Test Cyp (opened vial), 1.0 mL from Boldenone (opened vial)."*

Inventory deducts only on `Taken` or `Partial`. `Skipped` and `Missed` do not deduct.

### 4.6 Injection sites
Views: Body map · Site list · Rotation history · Recently used · Avoided.

**Rotation recommendation priority:** least recently used → matches protocol restriction → outside `avoidUntil` window → `isAvailable == true`.

A grouped multi-compound injection uses one shared site. The site's history shows all compounds from that event.

### 4.7 Settings
- Theme: System / Light / Dark. Dynamic color toggle.
- Time zone: device default, overridable (see §5.7).
- Reminder style: Silent / Normal / Persistent.
- Export data (JSON).
- Import data (JSON).
- Reset all data (hard-delete everything).

---

## 5. System Behaviors

### 5.1 Notifications and reminders
**Architecture:**
- `AlarmManager.setExactAndAllowWhileIdle` for dose reminders — the medical use case justifies exact alarms. Declare `SCHEDULE_EXACT_ALARM` permission.
- `WorkManager` for periodic background tasks:
  - Regenerate ScheduledDoses 7 days ahead.
  - Daily inventory + expiry check.
  - Mark overdue Pending doses as `Missed` after a configurable window.
- Notification channels: `dose_reminders` (high priority), `warnings` (default).
- `BootReceiver` re-schedules pending alarms after device restart.
- Request `POST_NOTIFICATIONS` in onboarding (Android 13+).

**Reminder lifecycle:** When a ScheduledDose is created, an alarm is set at `scheduledAt - reminderOffsetMinutes`. Snoozing reschedules. Logging or skipping cancels.

### 5.2 ScheduledDose generation
A WorkManager job runs daily (and on app start) to ensure every Active protocol has ScheduledDoses generated 7 days ahead. Generation respects:
- Schedule type and `dosageTimes` (empty = dose has no specific time; appears in "today" sorted last).
- Active `Escalation` rule (uses the current dose at the time the rule fires).
- `ProtocolBreak` (skip generation during off-days; the protocol is considered "in break" by the UI during these windows).
- `endDate` (no generation past it; protocol auto-marks `Completed`).

### 5.3 Inventory deduction
- On AdministrationEvent save with status `Taken` or `Partial`:
  - For each DoseComponent, compute deduction in the compound's container unit (typically mL for injectables, count for orals).
  - Decrement `currentOpened.remainingAmount`.
  - If it reaches ≤0, decrement `numberOfContainers` and open a new container if any remain.
- `Skipped` / `Missed`: no deduction.
- **Manual adjustment** (FAB → Adjust inventory): the user can add or remove inventory with an optional reason note. Stored as an `InventoryTransaction` entry visible in the compound's inventory history.

### 5.4 Protocol editing summary
- Editing regenerates only `Pending` ScheduledDoses (including future snoozed ones).
- Historical doses (Taken / Skipped / Missed / Partial) are immutable.
- Alarms are cancelled and re-scheduled for the regenerated set.

### 5.5 Soft-delete and history preservation
- `CompoundSupply` and `Protocol` use soft-delete via `deletedAt: Instant?`.
- Active list queries filter `WHERE deletedAt IS NULL`.
- Soft-deleted entities remain readable from history screens, so old administration events keep showing the correct compound name and dosage even after the supply or protocol is archived.
- Hard-delete is reserved for the user's *Reset all data* action.

### 5.6 Export / Import
**Export (JSON):**
- One file containing every entity.
- IDs are renumbered compactly per entity type (1, 2, 3 …) before writing, so the file stays small and human-readable.
- All relationships are preserved through the renumbered IDs.
- Includes a `schemaVersion` field for forward compatibility.
- Soft-deleted entities are exported with their `deletedAt` intact.

**Import (JSON):**
- If the database is empty: imported IDs are kept as-is (already compact).
- If the database has existing data: every imported entity gets a fresh auto-increment ID. An old→new ID map rewrites every foreign key before insert. No conflicts possible.

### 5.7 Time zones
- All timestamps stored as UTC `Instant`.
- Wall-clock values (`dosageTimes`, protocol start/end dates) stored as `LocalTime` / `LocalDate` and interpreted in the user's current zone at display and scheduling time.
- Default zone = device zone. Settings allows override (useful for travel).
- DST is handled by `kotlinx.datetime` — an 8:00 PM dose remains 8:00 PM local across the DST change.
- On time zone change: future Pending ScheduledDoses are re-anchored to the new zone, and alarms are re-scheduled.

---

## 6. Screens and Navigation

Top-level destinations (Navigation 3, adaptive — bottom bar on phones, side rail on foldables and tablets):

1. **Dashboard** (start)
2. **Compounds**
3. **Protocols**
4. **Sites**
5. **Settings**

Detail screens (Compound, Protocol, Site, Log) push onto the current destination's stack.

---

## 7. Onboarding

Three steps. Every step is skippable. Final destination: Dashboard.

1. Welcome + request notification permission.
2. *(Skip)* Create first compound supply.
3. *(Skip)* Create first protocol.
