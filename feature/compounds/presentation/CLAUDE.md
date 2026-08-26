# `:feature:compounds:presentation` — Compounds

## Purpose
Compound inventory: compounds list (+ multi-select + search), Compound Detail (stats, opened vial,
active protocols, paginated history), Create/Edit Compound form, and the opened-container bottom
sheets (edit / create-already-opened) + amount-per-container shrink dialog.

## Module coordinates
- Gradle: `:feature:compounds:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.compounds.presentation` (`.container`, `.detail`, `.di`, `.form`,
  `.list`, `.navigation`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `CompoundsPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` routes) +
  `compoundsEntries` (Nav3 entryProvider extension).
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
  menus/pickers/prompts family, dispatched as one branch. `ContainerShrinkPromptUi` /
  `ContainerShrinkDecision` are the §4.4.4 Edit-case dialog (M7-05).
  `Edit.OnConcentrationCalculated` is §4.6.7's return path: the Reconstitution Helper's mix, units and
  all, typed into the concentration row as an edit like any other (M8-04).
- `detail/` — the Compound Detail screen (§4.3, M7-07). `CompoundDetailViewModel` +
  `CompoundDetailState` / `Action` / `Event` / `CompoundDetailArgs`, rendered by
  `CompoundDetailRoot` / `CompoundDetailScreen` over the sections of `CompoundDetailSections.kt`.
  UI models: `CompoundStatsUi` + `ExpiryStatUi` (§4.3.2), `ActiveProtocolUi` (§4.3.4),
  `HistoryEntryUi` + `HistoryStatusFilter` (§4.3.7–§4.3.8). It reuses `form/`'s `OpenedContainerUi`,
  which §4.4.3's card already borrowed from §4.3.3. `CompoundDetailViewModel.history` is a
  `Flow<PagingData<HistoryEntryUi>>` **beside** the state, not in it (M7-08).
- `container/` — the §4.5 opened-container sheets (M7-06): `OpenedContainerSheet` +
  `OpenedContainerSheetState` / `Action`, `OpenedContainerDateField`, `OpenedContainerSaveError`, and
  `NaturalDepletionDialog` (§4.5.5). Stateless and ViewModel-free — the screen that opens the sheet
  owns its state and does its writes.
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
- Validation variant of the Create form per §4.4b.
- **List filtering** (§4.2.2) happens in `CompoundsListViewModel`, not in a DAO query: the Low stock
  chip needs `dosesLeft`, which only `InventoryRepository` can aggregate, so the VM combines it with
  `CompoundRepository.observeAll()` and keeps the unfiltered rows private. Status (All / Low stock /
  Expiring soon), Category, Form and the search query all AND together; an empty Category/Form
  selection means "no constraint". Row expiry is the earlier of `batchExpiryDate` and the opened
  container's `userDefinedExpiryDate ?? predictedExpiryDate` (§3.1, §4.3.2).
- **The Reconstitution Helper's result** (§4.6.7) reaches the form through `:app`, not through the
  compound row: `compoundsEntries(reconstitutionResult = …, onReconstitutionResultApplied = …)` holds
  what the helper computed until whichever form opened it composes again, and the form applies it once
  and hands it back. The row is not the channel because the form reads its compound **once** (a form
  that reloaded under the user's hands would discard what they were typing), and because §4.4.3's
  Create case has no row yet — the helper opened standalone and wrote nothing.
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
- **Shrinking the container below what is open in it is a question, not a validation error** (§4.4.4
  Edit case, M7-05): both answers are legal, so Save stops to ask instead of marking a field wrong.
  Keep and Cap both go on to write — they differ only in `CompoundRepository.update`'s
  `capOpenedContainer` flag, which is what makes the compound row and the clamped container one
  transaction — while Cancel puts `amountPerContainer` **and** `primaryUnit` back to the baseline and
  writes nothing (either of the two may be what shrank the container: 5 mg → 5 mcg is a
  thousandfold shrink with the number untouched). The prompt carries both amounts already unit-suffixed
  for the same reason. Units of different families are never compared, so a Form change that moves
  grams to millilitres raises no dialog. The dialog stacks its three actions in the `confirmButton`
  slot: `AlertDialog`'s flow row only wraps what it measures as too wide, and these labels squeeze
  onto one unreadable line of a 280dp dialog.
- **`numberOfContainers` is stored as the unopened count** (§4.4.4): the form's "# of containers"
  field is the *total owned*, so Save subtracts the opened container and load adds it back. Total
  owned `3` with one opened stores `2`.
- **The form's Stock section sizes itself from its own width**, not the window's (`BoxWithConstraints`,
  360dp): the left column of §6.4.2's two-column layout is narrower than a Compact phone, so a
  breakpoint check would put two fields side by side exactly where they do not fit. Below the
  threshold the two counts stack, the Helper button leaves the concentration row and that row drops
  its leading icon — all three buy the labels the width they need. Verified on device at all three
  breakpoints.
- **Concentration units follow the Form** (§4.4.3): `concentrationUnitOptions()` offers whole ratios
  (`ConcentrationUnits`), per mL for an injectable or a liquid, per g/scoop for a powder, per pill for
  a capsule or tablet. Changing the Form re-picks them under the same rule as the other smart
  defaults, and the Stock preview's "per container once mixed" line only appears when the denominator
  is a volume.
- **Field-row details that came from real devices, not the emulator**: the `Optional` badge is
  `surface-container-highest` (on `surface-container` it is invisible under a dynamic scheme — it
  vanished on a Samsung), never wraps, and the label ellipsises before it does; the unit suffix waives
  `LocalMinimumInteractiveComponentSize` (enforced at 48dp it is taller than the line it suffixes, and
  the field grew to fit it, stranding the value at the top); and a picker's `DropdownMenu` is anchored
  to its chevron, not to the row, or it opens a full field-width away from the control that summoned it.
- The list pane takes `paneInsets(claimTop = false)` (§2.3.6): every branch of it opens with a bar of
  its own — app bar, contextual bar, search bar — so the status bar is theirs to claim and draw their
  container behind. None of them passes `windowInsets`; the Material defaults are what does the work.
- **The opened-container sheet is a mode of whichever screen opened it** (§4.5, §10.3, M7-06), not a
  destination: `container/` holds the composable and its state/action types, and
  `CompoundFormViewModel` holds one `OpenedContainerSheetState` and performs §4.5.5's writes. Compound
  Detail (M7-07) hosts the same composable with its own copy; hoist the write logic when that lands,
  not before. One state for both variants — §4.5 defines Create Already Opened as the Edit sheet
  "minus Delete", so `isEdit` *is* the difference.
- **Which of §4.5.5's three writes runs depends on what the form is**: during New Compound there is no
  compound to write to, so the container is staged in the ViewModel and stored by "Save compound";
  for an existing compound the sheet writes on its own and the form then **re-reads** the compound,
  taking the total-owned count from the row that was written rather than computing it. That is what
  keeps §4.4.3's field and `numberOfContainers` in step across a container that was opened
  (count unchanged, one moves into the opened slot), discarded or emptied (count drops by one, the
  unopened tally untouched). The re-read updates the discard baseline too — a write that already
  happened is not an unsaved change to offer to discard.
- **A refused sheet write is shown in the sheet, never in a snackbar**: a modal sheet is its own
  window and the screen's `SnackbarHost` draws behind it, so a failure sent there is invisible.
  `CONSTRAINT_VIOLATION` is the one the user can act on ("no unopened container left to open"), since
  §5.3 requires `numberOfContainers > 0`; the rest read as a failed write. Found on device, not in a
  test — the test harness renders both windows at once.
- **The sheet's action row does not scroll and its date picker changes mode with the window height**:
  at Expanded the side sheet is as tall as a landscape phone (`411dp`), which is less than the three
  fields need and far less than the Material calendar's ≈`500dp`. Only the fields scroll, and below
  the Medium height breakpoint the picker opens in its text-input mode. The form's own batch-expiry
  picker (§4.4.3) still opens as a calendar at every height and has the same overlap there.
- **Compound Detail observes; the form reads once.** Nothing on the detail screen is being typed
  into, so a row that changes underneath should show through immediately — five repositories are
  combined into one state and a §4.5 write needs no re-read afterwards, unlike the form's
  `syncFromCompound`. The two supply figures of §4.3.2 come from `InventoryRepository`'s aggregation
  (M3-09) rather than being recomputed in the ViewModel: `dosesPerActualInjection` is protocol-
  weighted, and one definition of it is enough.
- **The §4.3.4 next-dose pill is one `ScheduledDose` flow per active protocol**, combined. A compound
  with more than a handful of live protocols is not a case this screen has, and the alternative —
  one query across every generated dose in the database — reads far more rows to answer less.
- **§6.4.2's two-column detail layout keys off the *pane* width, not the window's**
  (`BoxWithConstraints`, `720dp`). At an `840dp` Expanded window the detail pane is under `350dp`
  once the `400dp` list pane and the rail have taken theirs — narrower than a Compact phone — and a
  `0.55 / 0.45` split of that wraps every history row onto three lines. Same lesson as the form's
  Stock section, one level up. Below the threshold the content is the single scroll §6.4.2 gives
  Compact anyway.
- **The history rows are items of the page's own `LazyColumn`, not a list nested inside it.** A lazy
  list inside a scrolling parent has no height to measure against, and the point of §4.3.8's lazy
  loading is precisely that the rows are not all composed at once. At Expanded the right-hand column
  *is* that `LazyColumn`, so `historySection` is a `LazyListScope` extension both layouts place into
  whichever list they own.
- **§4.3.8's history is paged and therefore travels beside the state, not inside it** (M7-08).
  `PagingData` is a stream, not a value: `CompoundDetailState` keeps only `historyFilter`, the
  ViewModel exposes `history: Flow<PagingData<HistoryEntryUi>>` (`flatMapLatest` on that filter,
  `cachedIn(viewModelScope)`), and `CompoundDetailRoot` collects it with `collectAsLazyPagingItems()`
  and hands the `LazyPagingItems` to `CompoundDetailScreen` as a second parameter. The chip therefore
  **re-runs the query** instead of filtering loaded rows — with paging there are no loaded rows to
  filter — and §4.3.6's badge comes from its own `COUNT`, which is why it still does not move.
- **Measured on glass with 1000 history rows** (M7-08, release build, Fold emulator, `dumpsys
  gfxinfo`): flinging the history is `p50 16ms / p90 17ms`, 7% janky at Compact and 8.5% in the
  two-column right-hand list — 60fps, and unchanged between a 200-row and a 1000-row history, which
  is the whole point of paging. A max-speed fling all the way to row 1000, with page fetches in
  flight, costs `p50 31ms`; the same emulator scrolls the *system Settings* list at `p50 32ms`, so
  that is the machine, not us. The one slower gesture is the whole-page fling at Compact
  (`p50 46ms`), which re-lays out the stat strip and the cards on every frame — §4.3.2–§4.3.5, not
  the history, and identical at 200 rows. §2.3.2's SLO is not gateable before the Baseline Profile
  pass anyway.
- **A stand-in `PagingData` needs its load states spelled out.** `PagingData.from(list)` leaves the
  differ's states alone, so it sits on its initial `Loading` forever: §4.3.8's empty state never
  appears and `asSnapshot()` waits for a refresh that never finishes. Previews and both test doubles
  pass `sourceLoadStates = LoadStates(NotLoading(true), …)`. The flow behind
  `collectAsLazyPagingItems()` must also be `remember`ed — a fresh one per recomposition gives it a
  fresh differ each time, which is the same symptom by another route. Both cost an afternoon once.
- **§4.3.9's "bottom nav is hidden on this screen" holds only while the detail *is* the screen.**
  From Medium up it is one pane of the list-detail Scene beside the Compounds list, which is a
  top-level destination and keeps its rail; the dock then spans the detail pane alone, which is what
  §6.4.2 asks for. `:app` makes that call (`hidesChromeAsSolePane`), the same way it does for
  multi-select — the nav suite is chrome, not a screen's.
- **The §4.3.6 badge counts Taken + Partial all-time**, so it does not move when §4.3.7's chip does.
  The chip filters `CompoundDetailState.history` out of the ViewModel's unfiltered `allHistory`,
  exactly as the list's chips work on its own unfiltered rows.
- **The §4.5 sheet writes are simpler here than in the form**: the compound always exists, so only
  the persist path applies, and `numberOfContainers` is read from the last emission rather than after
  a re-read — sound because none of §4.5.5's writes touches it (closing a container leaves the
  unopened tally alone, §5.3).
- Reads/writes only through repository interfaces (injected); no Room here.
- See spec §4.2–§4.5, §6.4.2 Compounds; ISSUES M7-*.
