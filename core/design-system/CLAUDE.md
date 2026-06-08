# `:core:design-system` — M3 Expressive design system

## Purpose
The visual foundation: `StaxTheme`, typography (Google Sans Flex), Material Symbols Rounded **icon
vector drawables** (`res/drawable/ic_*.xml`) + the `StaxIcons` accessor,
motion specs, shape scale, semantic design tokens, and the reusable adaptive scaffolding wrappers
(Nav3 Scene-strategy helpers, `NavigationSuiteScaffold` chrome, `AdaptiveFab`). Pure Compose,
depends on nothing — every feature consumes it.

## Module coordinates
- Gradle: `:core:design-system` · plugins `com.stax.android.library` + `com.stax.compose`.
- Package: `com.stax.core.design.system`.

## Allowed dependencies
**Nothing** (pure Compose + Material 3).

## Key types
- `StaxTheme` — M3 Expressive theme via `MaterialExpressiveTheme` (expressive `MotionScheme` app-wide → M3 components animate expressively); consumes `Settings.theme` + `dynamicColor`; light/dark + dynamic color.
- `StaxTypography` — Google Sans Flex type scale (+ preview).
- `StaxMotion` — centralized M3 Expressive motion specs from `MotionScheme.expressive()` + syringe
  spring / shape-morph corners / cross-fade durations (§5.9). **Inline `tween(...)` is banned outside
  this object** — enforced by the `checkForbiddenMotionApis` Gradle task (root `build.gradle.kts`,
  wired into `check`).
- `StaxIcons` — hand-picked Material Symbols Rounded vector drawables (`res/drawable/ic_*.xml`);
  **no icon font / no `material-icons-extended`** — missing icon = request it, never invent (spec §9).
- `StaxShapes` — M3 Expressive shape scale (`material: Shapes` wired to `MaterialTheme.shapes`) + the
  `Pill` (≈999r) token (§9). **Inline `RoundedCornerShape(...)` is banned outside `:core:design-system`**
  — features use `MaterialTheme.shapes.<slot>` / `StaxShapes.Pill` (enforced by `checkForbiddenShapeApis`).
- `Tokens.kt` / `StaxColors` — **semantic** color tokens (dose status, site status, low-stock, heat
  map, syringe) aliased to `colorScheme` roles (§9). Standard M3 roles are read from
  `MaterialTheme.colorScheme` directly (not re-wrapped). `Tokens.kt` is the **only** legal home for
  raw `Color(0xFF…)` literals (scheme seeds) — banned elsewhere by `checkForbiddenColorApis`.
- Coming per M4: Scene-strategy wrappers + `AdaptiveFab`.

## Applicable skills
`android-compose-ui` (design-system composables, stability), `adaptive` (Scene wrappers, nav chrome),
`material-expressive-ui`.

## Owned by
Shared.

## Notes
- **Material 3 components only** — this is where theming lives. Slot APIs (`@Composable () -> Unit`
  params) are allowed **here** but discouraged in feature modules (§2.3.1).
- **No raw colors** outside `Tokens.kt`; **no `tween(`** outside `StaxMotion` (lint-enforced).
- Multi-pane wrappers use Nav3 `ListDetailSceneStrategy` / `SupportingPaneSceneStrategy` — **never**
  `*PaneScaffold` (§6.4).
- See spec §9 (design tokens), §5.9 (motion), §6.4; ISSUES M4-*, M5-04/05/06.
