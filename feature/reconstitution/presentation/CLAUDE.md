# `:feature:reconstitution:presentation` — Reconstitution Helper

## Purpose
The reconstitution calculator: mix inputs → concentration result, syringe visualization, equivalence
chips, dose ladder, and save-concentration action. Pure dosing-math UI on top of the domain value types.

## Module coordinates
- Gradle: `:feature:reconstitution:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.reconstitution.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `ReconstitutionPresentationModule` (Koin). Coming: `ReconstitutionViewModel` + State/Action/Event,
  syringe visualization composable, equivalence chips, dose ladder, `Routes.kt`, `ReconstitutionEntries`.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui` (Canvas syringe), `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Reconstitution feature.

## Notes
- **All math via `Decimal`/`Quantity`/`Concentration`** from `:core:domain` — never `Double`/`Float`
  (§3.0.1). The screen orchestrates; the arithmetic is domain-owned.
- Syringe visualization animates via `graphicsLayer` + spring (`StaxMotion`, damping 0.8 / stiffness 380).
- Adaptive: 1/2/3 columns Compact/Medium/Expanded (§6.4.2).
- See spec §4.6, §6.4.2 Reconstitution; ISSUES M8-*.
