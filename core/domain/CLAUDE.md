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
- Result/errors: `Result<D, E : Error>`, `DataError` (`DataError.Local`), `EmptyResult`.
- Domain models: `CompoundSupply`, `Protocol`, `ScheduledDose`, `AdministrationEvent`,
  `DoseComponent`, `InjectionSite`, `InventoryTransaction`, `Settings`, `InventoryReadModels`.
- Read models: `CompoundHistoryEntry` (§4.3.8 — one dose-history row: the event joined to the one
  component that names the compound, with the volume its logged concentration implies). It arrives
  through `AdministrationEventRepository.pagedHistoryForCompound` as a `Flow<PagingData<…>>`, with
  §4.3.7's status filter as a query parameter rather than as a filter over the emitted rows.
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
- See spec §3 (domain model), §10.2; ISSUES M1-*, M3-01/M3-03..M3-09.
