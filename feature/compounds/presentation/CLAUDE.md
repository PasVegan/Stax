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
- `list/` — `CompoundsListViewModel` + `CompoundsListState` / `Action` / `Event`, the
  `CompoundListItemUi` row model and the `CompoundStatusFilter` / `CompoundFilterMenu` enums (§4.2,
  M7-01), rendered by `CompoundsListRoot` / `CompoundsListScreen` with the internal `CompoundRow` and
  `CompoundsSearchOverlay` (§4.2, §4.0.1, M7-02). Multi-select lands with M7-03.
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
- **Search overlay** (§4.0.1) is `CompoundsSearchOverlay`, a mode of the list screen driven by
  `CompoundsListState.isSearchOpen` — not a nav destination, which is why it needs its own
  `BackHandler` (hence the `activity-compose` dependency). Protocols (§4.7.1) and Sites (§4.12.1)
  reuse the same pattern; hoist it into `:core:design-system` when the second caller lands, not before.
- **Which chip menu is open** (`openFilterMenu`) and **whether search is open** live in the state, not
  in a `remember` — app state belongs to the ViewModel (§2.3.1).
- Reads/writes only through repository interfaces (injected); no Room here.
- See spec §4.2–§4.5, §6.4.2 Compounds; ISSUES M7-*.
