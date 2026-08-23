# `:core:database` — Room persistence

## Purpose
The single shared Room database: `@Database`, all entities, all DAOs, type converters, migrations,
relation POJOs, and the first-launch seed callback. The only module that knows SQLite. Repository
impls in `:core:data` call these DAOs; nothing else touches Room.

## Module coordinates
- Gradle: `:core:database` · plugins `com.stax.android.library` + `com.stax.room` (KSP) + `com.stax.testing`.
- Package: `com.stax.core.database` (`.migration`).
- Schemas exported to `app/schemas/com.stax.core.database.StaxDatabase/`.

## Allowed dependencies
`:core:domain` only.

## Key types
- `StaxDatabase` — `@Database`, WAL journal mode, `foreign_keys=ON`.
- Entities: `CompoundSupplyEntity`, `OpenedContainerEntity`, `ProtocolEntity`,
  `ProtocolDosageTimeEntity`, `ScheduledDoseEntity`, `AdministrationEventEntity`,
  `DoseComponentEntity`, `InjectionSiteEntity`, `InventoryTransactionEntity`, `SettingsEntity`.
- DAOs: one per entity (`*Dao`) + relation POJOs `CompoundWithOpened`, `ProtocolWithDosageTimes`,
  and the flat projection `CompoundHistoryRow` (`observeHistoryForCompound`, §4.3.8).
- `RoomConverters` (TypeConverters), `DatabaseSeedCallback` (first-launch seed), `migration/`.

## Applicable skills
`android-data-layer` (Room entities/DAOs).

## Owned by
Shared.

## Notes
- Entities are persistence types, **not** domain models — mapping happens in `:core:data`.
- Every schema version needs a `MigrationTestHelper` test (`migration/MigrationTest.kt`, M19-01);
  bump version + add migration + add test together.
- Transactional boundaries (§5.8.5) are implemented with `@Transaction` DAO methods consumed by repos.
- See spec §5.8 (Room implementation), §3; ISSUES M2-*.
