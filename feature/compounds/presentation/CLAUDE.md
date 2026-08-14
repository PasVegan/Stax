# `:feature:compounds:presentation` — Compounds

## Purpose
Compound inventory: compounds list (+ multi-select + search), Compound Detail (stats, opened vial,
active protocols, paginated history), Create/Edit Compound form, and the opened-container bottom
sheets (edit / create-already-opened) + amount-per-container shrink dialog.

## Module coordinates
- Gradle: `:feature:compounds:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.compounds.presentation` (`.di`, `.list`, `.navigation`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `CompoundsPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` routes) +
  `compoundsEntries` (Nav3 entryProvider extension). Coming: detail/create/edit ViewModels &
  State/Action/Event, Root/Screen composables, history paging.
- `list/` — `CompoundsListViewModel` + `CompoundsListState` / `Action` / `Event` and the
  `CompoundListItemUi` row model + `CompoundStatusFilter` enum (§4.2, M7-01). `CompoundsListRoot` /
  `CompoundsListScreen` land with M7-02; multi-select with M7-03.
- `CreateCompoundRoute(onboarding)` — onboarding step 2 reuses this form (§4.14 step 2): same screen,
  app bar titled "Add your first compound · 2 of 3" with Skip in the trailing slot, driven by the
  route flag. `compoundsEntries(onSkipOnboardingStep = …)` carries that Skip back to `:app`, which
  owns the flow — this module still knows nothing about the onboarding feature (§10.3).

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Compounds feature.

## Notes
- List+Detail uses the Nav3 **list-detail Scene** (`ListDetailSceneStrategy`) at Medium+ (§6.4.2),
  not `ListDetailPaneScaffold`.
- History is paginated (Paging); validation variant of Create form per §4.4b.
- **List filtering** (§4.2.2) happens in `CompoundsListViewModel`, not in a DAO query: the Low stock
  chip needs `dosesLeft`, which only `InventoryRepository` can aggregate, so the VM combines it with
  `CompoundRepository.observeAll()` and keeps the unfiltered rows private. Status (All / Low stock /
  Expiring soon), Category, Form and the search query all AND together; an empty Category/Form
  selection means "no constraint". Row expiry is the earlier of `batchExpiryDate` and the opened
  container's `userDefinedExpiryDate ?? predictedExpiryDate` (§3.1, §4.3.2).
- Reads/writes only through repository interfaces (injected); no Room here.
- See spec §4.2–§4.5, §6.4.2 Compounds; ISSUES M7-*.
