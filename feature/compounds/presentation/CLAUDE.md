# `:feature:compounds:presentation` — Compounds

## Purpose
Compound inventory: compounds list (+ multi-select + search), Compound Detail (stats, opened vial,
active protocols, paginated history), Create/Edit Compound form, and the opened-container bottom
sheets (edit / create-already-opened) + amount-per-container shrink dialog.

## Module coordinates
- Gradle: `:feature:compounds:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.compounds.presentation` (`.di`, `.form`, `.list`, `.navigation`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `CompoundsPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` routes) +
  `compoundsEntries` (Nav3 entryProvider extension). Coming: detail/create/edit ViewModels &
  State/Action/Event, Root/Screen composables, history paging.
- `list/` — `CompoundsListViewModel` + `CompoundsListState` / `Action` / `Event`, the
  `CompoundListItemUi` row model and the `CompoundStatusFilter` / `CompoundFilterMenu` enums (§4.2,
  M7-01), rendered by `CompoundsListRoot` / `CompoundsListScreen` with the internal `CompoundRow`,
  `CompoundsSearchOverlay` and `CompoundsSelectionMode` (§4.2, §4.0.1, §4.2.4, M7-02/M7-03).
  `CompoundsListAction.Selection` is the multi-select family, dispatched as one branch.
- `form/` — the Create / Edit Compound form (§4.4, M7-04). `CompoundFormViewModel` +
  `CompoundFormState` / `Action` / `Event` / `CompoundFormArgs`, rendered by `CompoundFormRoot` /
  `CompoundFormScreen` over the field primitives of `CompoundFormFields.kt` (`FormTextField`,
  `FormPickerField`, `UnitSuffix`) and the sections of `CompoundFormSections.kt`.
  `CompoundFormDraft` is the editable half of the state; `CompoundFormAction.Overlay` is the
  menus/pickers/prompts family, dispatched as one branch.
- `CreateCompoundRoute(onboarding)` — onboarding step 2 reuses this form (§4.14 step 2): same screen,
  app bar titled "Add your first compound · 2 of 3" with Skip in the trailing slot, driven by the
  route flag. `compoundsEntries(onOnboardingStepDone = …)` carries the end of that step back to
  `:app`, which owns the flow — this module still knows nothing about the onboarding feature (§10.3).
  Both Skip and a successful Save reach it: from here they are the same statement.

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
- **Multi-select** (§4.2.4) is a mode of the list screen, not a destination: `selectedIds` *is* the
  mode (empty = off, so unticking the last row leaves it, as `close` and the back gesture do), and
  while it is on the contextual bar replaces the app bar **and the chip row**, the dock replaces the
  bottom of the pane, and the FAB steps aside. Duplicate/Archive run the whole batch even after one
  fails and report only the first failure, through `ShowError` → a `SnackbarHost` the Root owns.
  The **bottom nav is `:app`'s chrome**, so the screen cannot hide it: `CompoundsListRoot` reports the
  mode through `onSelectionModeChange` (also on dispose, or navigating out would leave the bar hidden)
  and `MainScaffold` hides the nav suite. Same feature-names-intent / `:app`-acts rule as navigation
  (§10.3).
- **Which chip menu is open** (`openFilterMenu`) and **whether search is open** live in the state, not
  in a `remember` — app state belongs to the ViewModel (§2.3.1). The form says the same of
  `openPicker`, `isDatePickerOpen` and `isDiscardDialogOpen`.
- **The form's "auto-save draft on backgrounding"** (§4.4.5) is `CompoundFormDraft` mirrored into the
  ViewModel's `SavedStateHandle` on every edit (`androidx.lifecycle.serialization.saved`). A
  ViewModel already survives backgrounding, so only the handle makes the form survive the process
  death that can follow it — and a restored draft always beats the stored compound in Edit mode, or
  resuming would revert the user's unsaved edits. Round-tripped in `CompoundFormDraftPersistenceTest`,
  which runs on Robolectric because the draft serializes into a real `Bundle`.
- **`numberOfContainers` is stored as the unopened count** (§4.4.4): the form's "# of containers"
  field is the *total owned*, so Save subtracts the opened container and load adds it back. Total
  owned `3` with one opened stores `2`.
- **The form's Stock section sizes itself from its own width**, not the window's (`BoxWithConstraints`,
  360dp): the left column of §6.4.2's two-column layout is narrower than a Compact phone, so a
  breakpoint check would put two fields side by side exactly where they do not fit. Verified on
  device at all three breakpoints.
- The list pane takes `paneInsets(claimTop = false)` (§2.3.6): every branch of it opens with a bar of
  its own — app bar, contextual bar, search bar — so the status bar is theirs to claim and draw their
  container behind. None of them passes `windowInsets`; the Material defaults are what does the work.
- Reads/writes only through repository interfaces (injected); no Room here.
- See spec §4.2–§4.5, §6.4.2 Compounds; ISSUES M7-*.
