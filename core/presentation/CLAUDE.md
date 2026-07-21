# `:core:presentation` — shared presentation utilities

## Purpose
Cross-feature presentation helpers that every feature reuses: the `UiText` abstraction for
resource-or-literal user strings, the `DataError → UiText` mapping, and shared Compose utilities
(e.g. `ObserveAsEvents`). Keeps feature modules from duplicating string/error plumbing.

## Module coordinates
- Gradle: `:core:presentation` · plugin `com.stax.android.library` (+ compose consumer).
- Package: `com.stax.core.presentation`.
- Deps: `:core:domain`.

## Allowed dependencies
`:core:domain`.

## Key types
- `UiText` — sealed type for literal vs. string-resource text resolved in Compose.
- `DataErrorUiText` — `DataError.toUiText()` extension (map typed errors → user strings).
- `ObserveAsEvents` — lifecycle-aware collector for a ViewModel's one-time event flow (§10.1).
  Collects between `STARTED` and `STOPPED` (the ViewModel's `Channel` holds events fired while the
  screen is backgrounded) and invokes the handler on `Dispatchers.Main.immediate` so navigation takes
  effect in the same frame. Every `<Screen>Root` uses it.

## Applicable skills
`android-presentation-mvi` (UiText, ObserveAsEvents), `android-error-handling`.

## Owned by
Shared.

## Notes
- All user-facing error strings flow through `UiText` — features map `DataError`/validation errors
  to `UiText` here or in their own presentation layer, never hardcode strings in ViewModels.
- No feature-specific logic here — if it's only used by one feature, it belongs in that feature.
- See spec §10.1; ISSUES M1-00c.
