# `:core:domain` — domain models, value types, repository interfaces

## Purpose
The pure-Kotlin heart of the app: dose-math value types, domain models, typed errors, the `Result`
wrapper, and **repository interfaces** (implemented in `:core:data`). No Android, no framework, no
Compose. Everything else depends inward on this module.

## Module coordinates
- Gradle: `:core:domain` · plugin `com.stax.kotlin.library` (pure JVM Kotlin) + `com.stax.testing`.
- Package: `com.stax.core.domain` (`.repository`).
- Deps: kotlinx-datetime, kotlinx-coroutines-core, paging-common only.

## Allowed dependencies
**Nothing** (pure Kotlin). If you reach for an Android/Compose/Room import here, it belongs elsewhere.
`androidx.paging:paging-common` is the one exception: `PagingData` is the return type of the paged
repository reads (§4.3.8), it is a plain Kotlin/JVM artifact, and the alternative — a read model of
our own that `:core:data` converts to `PagingData` anyway — buys nothing.

## Key types
- Value types: `Decimal`, `UnitCode`, `UnitFamily`, `Quantity`, `Concentration`, `Validation`.
- Dose rules: `EscalationEngine` — `Escalation.doseAt(daysSinceStart, dosesBefore)` and
  `Protocol.plannedDoseAt(date, dosesBefore)` (§3.2).
- Schedule rule: `ScheduleEngine` — `Protocol.dosingTimesOn(date)` (which days dose, and at what
  times), `Protocol.dosesBetween(from, until)`, `Protocol.isInBreak(date)` and
  `SCHEDULE_HORIZON_DAYS` (§5.2, §3.2).
- Rotation rule: `SiteRotation` — `List<InjectionSite>.suggestNextSite(now, route, restriction,
  cooldownDays)`, `SITE_ROTATION_ORDER`, `InjectionSite.isCoolingAt(now, cooldownDays)`,
  `siteCooldownDays(route, protocolCooldownDays, settings)` (§5.3's source order) and
  `BodyRegion.routes()` (§4.12.2 — a site carries no route of its own, §3.6).
- Result/errors: `Result<D, E : Error>`, `DataError` (`DataError.Local`), `EmptyResult`.
- Domain models: `CompoundSupply`, `Protocol`, `ScheduledDose`, `AdministrationEvent`,
  `DoseComponent`, `InjectionSite`, `InventoryTransaction`, `Settings`, `InventoryReadModels`.
- Read models: `CompoundHistoryEntry` (§4.3.8 — one dose-history row: the event joined to the one
  component that names the compound, with the volume its logged concentration implies). It arrives
  through `AdministrationEventRepository.pagedHistoryForCompound` as a `Flow<PagingData<…>>`, with
  §4.3.7's status filter as a query parameter rather than as a filter over the emitted rows. The same
  read model serves §4.8.7 through `pagedHistoryForProtocol`, which takes no status — Protocol Detail
  has no filter chips.
  `SiteUse` (§4.12.3 — a dose that named a site, projected to the site, route and moment the tiles
  count over) and `SiteDose` (§4.12.8 — one dose given at a site, with the compound it named and the
  dose it delivered) are the Sites screen's two: the first is read over a window across every site,
  the second over one site's whole history.
- `repository/` interfaces: `CompoundRepository`, `ProtocolRepository`, `ScheduledDoseRepository`,
  `AdministrationEventRepository`, `InjectionSiteRepository`, `InventoryRepository`,
  `SettingsRepository`.

## Applicable skills
`android-error-handling` (Result/DataError), `android-data-layer` (repository interface shape).

## Owned by
Shared.

## Notes
- **No `Double`/`Float` for dose math** — `Decimal`/`Quantity`/`Concentration` only (§3.0.1). This is
  the module that defines them; treat them as `@Immutable`.
- **No `LocalDateTime`** — `Instant`/`LocalDate`/`LocalTime` (§5.7).
- Value types carry the unit families + arithmetic; tests (`*Test.kt`) live alongside and must stay
  green (≥90% coverage, M19-02).
- **The escalation and schedule rules live here, not in the generator** (§3.2, §5.2, M9-02/M9-03):
  both are rules over pure domain types, and a feature that has to show "what will I be taking on
  the 14th" or "how many doses does this place next week" may not import `:core:data`.
  `ScheduledDoseGenerator` keeps only what is genuinely its own — turning those days and times into
  rows, with time zones, the escalation counter and idempotence. The escalation rule converts before
  it adds or compares — `maxDose` in `mg` clamps a `mcg` escalation — and takes the cumulative dose
  count as an argument, since only the caller knows how many doses its schedule has placed by that
  date (`ScheduledDoseGenerator` counts them with `dosesBetween`, §5.2).
- **The rotation rule lives here too** (§4.12.5, §5.3, M10-06), for the same reason as the other
  two: four surfaces read it — §4.12.5's hero, §4.12.7's picker, §4.12.4's `primary` ring and
  `InjectionSiteRepository.suggestNext` — and a copy per surface is how they end up naming different
  sites. `isCoolingAt` takes the cooldown that applies to the dose being placed as well as the
  `avoidUntil` the last one stamped, and the later of the two wins: a protocol asking for ten days
  may not spend a site whose stamp cleared after five, and a stamp still running is not cleared by a
  protocol that asks for less. Passing no `cooldownDays` reads the stamp alone, which is what the
  screens that are not dosing do.
- See spec §3 (domain model), §4.12.5, §10.2; ISSUES M1-*, M3-01/M3-03..M3-09, M9-02, M9-03, M10-06.
