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
- **`AdministrationEventRepository.observeForCompound`** is §4.3.8's dose history. It is driven from
  `dose_component`, not from `administration_event`: the compound is named by the component, so an
  event logging two compounds at once (§4.10.3) belongs in both histories and shows only its own
  dose in each. The volume is derived in the mapper from the concentration snapshotted at log time
  (§3.5), and only when that concentration's units divide into the dose — `Quantity.div` throws on a
  cross-family divisor and on count units, both of which are reachable data, and a history row is
  not the place to raise them.
- Each repository method needs a Robolectric DAO test (one happy + one failure path).
- **`CompoundRepository.update(compound, capOpenedContainer)`** is §4.4.4's Edit case: the flag clamps
  the opened container to the compound's new `amountPerContainer` and books the difference as a
  `Manual` transaction (`reason = "Compound size reduced"`, a stored string and so deliberately not a
  resource, like `COPY_SUFFIX`). It converts before it subtracts, since the same edit may have changed
  the unit. Whether the container actually overflows is the caller's question; no opened container is
  a no-op, not a failure. The flag exists rather than a second method so the compound row and the
  clamped container are one transaction (§5.8.5).
- **The opened-container operations are one operation** (§5.3, M7-06): `openContainer` and
  `addOpenedContainer` share a body, so a container the user opened before the app knew about it
  decrements `numberOfContainers`, writes the delta-0 `ContainerOpen` marker and derives
  `predictedExpiryDate` exactly as a fresh one does — only `openedAt`, the remaining amount and the
  expiry come from the user. `editOpenedContainer` takes an `openedAt` too and re-derives
  `predictedExpiryDate` from it, because §3.1.1 makes that field derived and a moved opened date has
  to move it.
- **Every one of them books the stock it moves as a `Manual` transaction** (§5.8.0): a part-used
  container arriving (`remaining − amountPerContainer`), a corrected remaining (`new − old`, read in
  the new unit), a half-full container discarded (`−remaining`). `ContainerOpen` / `ContainerClose`
  stay the delta-0 audit markers §5.3 defines — they cannot carry it. Closing an already-empty
  container books nothing: the deduction that emptied it is in the ledger already, and booking it
  twice is exactly the drift §5.8.0's reconcile worker exists to catch.
- See spec §5.2–§5.5, §5.8.5, §10.2; ISSUES M3-*, M7-06, M9-01.
