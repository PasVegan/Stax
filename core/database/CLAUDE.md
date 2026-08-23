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
  and the flat projection `CompoundHistoryRow` (`historyPagingSourceForCompound`, §4.3.8).
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
- **Unbounded lists return a Room `PagingSource`, not a `Flow<List<…>>`** — today that is
  `historyPagingSourceForCompound` (§4.3.8), whose status filter is `AND (:status IS NULL OR
  e.status = :status)` so §4.3.7's chip narrows the query instead of the result. Its companion
  `observeLoggedDoseCountForCompound` is §4.3.6's badge as a `COUNT`, which is the only way to
  answer "how many all-time" once the rows are no longer all in memory. Test them with
  `TestPager` (`paging-testing`), remembering that `PagingConfig.initialLoadSize` defaults to three
  pages.
- See spec §5.8 (Room implementation), §3; ISSUES M2-*.
