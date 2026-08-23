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
  `ShowCalculationRow`, `MixSection`, `ResultSection`).
- `reconstitutionPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `reconstitutionEntries` (Nav3 entryProvider extension).
- Coming: syringe visualization (M8-02), equivalence chips + dose ladder (M8-03), the save write
  (M8-04), the 2/3-column layouts (M8-05).

## Applicable skills
`android-presentation-mvi`, `android-compose-ui` (Canvas syringe), `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Reconstitution feature.

## Notes
- **All math via `Decimal`/`Quantity`/`Concentration`** from `:core:domain` — never `Double`/`Float`
  (§3.0.1). The screen orchestrates; the arithmetic is domain-owned.
- Syringe visualization animates via `graphicsLayer` + spring (`StaxMotion`, damping 0.8 / stiffness 380).
- Adaptive: 1/2/3 columns Compact/Medium/Expanded (§6.4.2) — M8-05. Today every width is one
  scroll, and §4.6's "Show calculation" disclosure appears only below a `600dp` **pane** width,
  since §6.4.2 keeps the same sections open from Medium up.
- `compoundId == null` is §4.4.3's standalone calculator: the container amount and unit are typed
  instead of read. A compound picker (§9 `reconstitute` shortcut) is not built yet.
- See spec §4.6, §6.4.2 Reconstitution; ISSUES M8-*.
