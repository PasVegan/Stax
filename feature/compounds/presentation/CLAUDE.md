# `:feature:compounds:presentation` — Compounds

## Purpose
Compound inventory: compounds list (+ multi-select + search), Compound Detail (stats, opened vial,
active protocols, paginated history), Create/Edit Compound form, and the opened-container bottom
sheets (edit / create-already-opened) + amount-per-container shrink dialog.

## Module coordinates
- Gradle: `:feature:compounds:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.compounds.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `CompoundsPresentationModule` (Koin). Coming: list + detail + create/edit ViewModels & State/Action/Event,
  `Routes.kt` (NavKeys), `CompoundsEntries`, Root/Screen composables, history paging.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Compounds feature.

## Notes
- List+Detail uses the Nav3 **list-detail Scene** (`ListDetailSceneStrategy`) at Medium+ (§6.4.2),
  not `ListDetailPaneScaffold`.
- History is paginated (Paging); validation variant of Create form per §4.4b.
- Reads/writes only through repository interfaces (injected); no Room here.
- See spec §4.2–§4.5, §6.4.2 Compounds; ISSUES M7-*.
