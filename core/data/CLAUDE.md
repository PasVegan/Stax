# `:core:data` — repository implementations

## Purpose
Implements the repository interfaces from `:core:domain` on top of `:core:database` DAOs +
DataStore. Owns Entity↔Domain mapping, transactional write logic, the theme DataStore cache, the
scheduled-dose generator, and the Koin `coreDataModule`. ViewModels depend on the domain interfaces,
never on this module.

## Module coordinates
- Gradle: `:core:data` · plugins `com.stax.android.library` + `com.stax.koin` + `com.stax.testing`.
- Package: `com.stax.core.data` (`.di`, `.mapper`, `.preferences`, `.repository`, `.scheduler`).
- Deps: `:core:domain`, `:core:database`, DataStore.

## Allowed dependencies
`:core:domain`, `:core:database`.

## Key types
- `repository/Room*Repository` — impls (`RoomCompoundRepository`, `RoomProtocolRepository`,
  `RoomScheduledDoseRepository`, `RoomAdministrationEventRepository`, `RoomInjectionSiteRepository`,
  `RoomInventoryRepository`, `RoomSettingsRepository`).
- `mapper/*Mappers` — Entity↔Domain (+ `EnumMappers`). No DTOs (offline-only).
- `preferences/ThemePreferences` — DataStore theme cache read by `ThemeInitializer`.
- `scheduler/ScheduledDoseGenerator` — protocol → scheduled doses (§5.2).
- `di/CoreDataModule` — Koin bindings (`singleOf(::RoomX) { bind<XRepository>() }`).

## Applicable skills
`android-data-layer`, `android-error-handling`, `android-di-koin`.

## Owned by
Shared.

## Notes
- **Stax divergence**: no remote sources / DTOs. We call all accessors "Repository" even though they
  are single-source by the skill's strict definition (most aggregate ≥2 tables). See §10.2.
- Mutating ops return `Result<_, DataError.Local>` / `EmptyResult`; never throw on expected failures.
- Each repository method needs a Robolectric DAO test (one happy + one failure path).
- See spec §5.2–§5.5, §5.8.5, §10.2; ISSUES M3-*, M9-01.
