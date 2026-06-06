# `:shortcut` — static app shortcuts

## Purpose
Static launcher shortcuts (`<shortcuts>` XML) and the deep-link router that maps each shortcut to an
in-app destination: Log next dose · Manual log · Add compound · Reconstitute.

## Module coordinates
- Gradle: `:shortcut` · plugin `com.stax.android.library`.
- Package: `com.stax.shortcut`.

## Allowed dependencies
`:core:domain`, `:core:data`.

## Key types
- Shortcut deep-link router (intent → route), `log_next_dose` fallback logic.
- Static `shortcuts.xml` (merged via `:app` manifest).

## Applicable skills
`navigation-3` (deep-link targets), `android-data-layer` (fallback queries).

## Owned by
Shared (out-of-app surface).

## Notes
- Static shortcuts only at v1 — no `androidx.sharetarget`.
- `log_next_dose` needs a fallback when no next dose exists (M16-03).
- Deep-link contract shared with `:widget` and `:app`.
- See spec §4.17, §6.3.1; ISSUES M16-*.
