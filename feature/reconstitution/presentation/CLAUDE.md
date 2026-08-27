# `:feature:reconstitution:presentation` — Reconstitution Helper

## Purpose
The reconstitution calculator: mix inputs → concentration result, syringe visualization, equivalence
chips, dose ladder, and save-concentration action. Pure dosing-math UI on top of the domain value types.

## Module coordinates
- Gradle: `:feature:reconstitution:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.reconstitution.presentation` (`.di`, `.navigation`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `ReconstitutionViewModel` + `ReconstitutionArgs` / `State` / `Action` / `Event` — the MVI triad of
  §4.6. `recalculated()` is the whole derivation (concentration, draw-to volume, doses per container)
  and is `internal` so it can be driven straight from tests.
- `ReconstitutionRoot` / `ReconstitutionScreen` (+ `ReconstitutionSections.kt`: `DrawToHero`,
  `ShowCalculationRow`, `MixSection`, `DoseLadderSection`, `ResultSection`).
- `DoseEquivalence` / `DoseRung` (in `ReconstitutionState.kt`) — §4.6.3's chips and §4.6.5's rungs,
  both pre-rendered by `recalculated()`. A rung's `dose` is the string tapping it types into Desired
  dose, so the ladder is a shortcut for that field and never a second source of truth.
- `SyringeVisualization.kt` — §4.6.2's `Canvas` renderer (`SyringeVisualization`) and its size badge
  (`SyringeSizeBadge`). `SyringeSize` (in `ReconstitutionState.kt`) carries the capacity and the
  graduation of each syringe; `next()` is the cycle the badge walks.
- `reconstitutionPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `reconstitutionEntries(onBack, onSaved)` (Nav3 entryProvider extension).
- `concentrationOrNull()` (in `ReconstitutionViewModel.kt`) — the mix itself, normalized to one
  millilitre. `recalculated()` divides by it; §4.6.7's save stores it.
- `SingleColumn` / `TwoColumn` / `ThreeColumn` (in `ReconstitutionScreen.kt`) — §6.4.2's layouts, one
  per column count, picked in `ReconstitutionScreen`'s `BoxWithConstraints`.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui` (Canvas syringe), `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Reconstitution feature.

## Notes
- **All math via `Decimal`/`Quantity`/`Concentration`** from `:core:domain` — never `Double`/`Float`
  (§3.0.1). The screen orchestrates; the arithmetic is domain-owned.
- The syringe fill animates on `StaxMotion.syringeFillSpec()` (spring, damping 0.8 / stiffness 380,
  §4.6.8). `syringeFill` is a ratio the ViewModel computes in `Decimal`; the composable only draws it.
- Adaptive (§6.4.2): 1/2/3 columns, chosen on the **pane's** width — `TWO_COLUMN_MIN_WIDTH` (`520dp`)
  and `THREE_COLUMN_MIN_WIDTH` (`1024dp`) in `ReconstitutionScreen.kt`. The rail takes its side of the
  window before this screen sees any of it, so a window-measured breakpoint promises room the pane does
  not have; `1024dp` is what the three columns actually need (`360` + `320` + a centre worth having),
  which means an Expanded *window* at its lower bound still gets two columns. Each column scrolls on
  its own. §4.6's "Show calculation" disclosure belongs to the single column alone.
- The Mix grid becomes §6.4.2's one-line table at `MIX_ROW_MIN_WIDTH` (`480dp`) of its own column's
  width, never on the breakpoint — the centre column is only what the two fixed side columns leave.
- The dose ladder follows the field it types into as far as the room allows: inside the disclosure at
  one column, in the syringe's column at two, back beside the Result at three.
- §4.6.5's ladder is `[0.1, dose/2, dose, dose x2, dose x3]`, sorted and de-duplicated, recomputed
  around whichever rung was tapped. §4.6.5 speaks of a preview and a confirm; there is no confirm
  affordance on the screen, so a tap does both — it types the dose, and the syringe fill springs to
  it. The selected rung is the one equal to the typed dose, so no selection is stored.
- §4.6.7's save writes **only** `CompoundSupply.concentration`, and that is the whole of it: a
  `ScheduledDose` stores the dose it plans, never the volume that dose comes to, so every Pending row
  restates itself at the new mix without being touched. Logged history does not move with it — a
  `DoseComponent` carries the concentration it was logged at (§3.5). What is stored is the exact
  quotient, not §4.6.6's three rounded digits (§3.0.3).
- `ReconstitutionEvent.Saved` carries the concentration back to `:app`, which hands it to whichever
  Compound form opened the helper (§4.4.3) — the standalone calculator has no row to read it from.
- `compoundId == null` is §4.4.3's standalone calculator: the container amount and unit are typed
  instead of read. A compound picker (§9 `reconstitute` shortcut) is not built yet.
- See spec §4.6, §6.4.2 Reconstitution; ISSUES M8-*.
