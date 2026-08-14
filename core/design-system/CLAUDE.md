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
  The scheme change cross-fades **per color role** (`animateColorAsState`, `StaxMotion.defaultEffectsSpec`,
  §5.9) — **never** by wrapping `content` in `Crossfade`/`AnimatedContent`. A structural fade puts the
  whole app in a new composition group on every theme recompute, and `rememberSaveable` derives its
  registry key from the enclosing group: all auto-keyed saved state (the nav back stacks included) would
  become unreachable and reset on every configuration change.
- `StaxTypography` — Google Sans Flex type scale (+ preview).
- `StaxMotion` — centralized M3 Expressive motion specs from `MotionScheme.expressive()` + syringe
  spring / shape-morph corners / cross-fade durations (§5.9). **Inline `tween(...)` is banned outside
  this object** — enforced by the `stax:NoInlineTween` detekt rule (`:detekt-rules`; `StaxMotion.kt`
  is the only `excludes` entry in `detekt.yml`).
- `StaxIcons` — hand-picked Material Symbols Rounded vector drawables (`res/drawable/ic_*.xml`);
  **no icon font / no `material-icons-extended`** — missing icon = request it, never invent (spec §9).
- `StaxShapes` — M3 Expressive shape scale (`material: Shapes` wired to `MaterialTheme.shapes`) + the
  `Pill` (≈999r) token (§9). **Inline `RoundedCornerShape(...)` is banned outside `:core:design-system`**
  — features use `MaterialTheme.shapes.<slot>` / `StaxShapes.Pill` (enforced by the
  `stax:NoInlineRoundedCornerShape` detekt rule).
- `Tokens.kt` / `StaxColors` — **semantic** color tokens (dose status, site status, low-stock, heat
  map, syringe) aliased to `colorScheme` roles (§9). Standard M3 roles are read from
  `MaterialTheme.colorScheme` directly (not re-wrapped). `Tokens.kt` is the **only** legal home for
  raw `Color(0xFF…)` literals (scheme seeds) — banned elsewhere by the `stax:NoRawColorLiteral`
  detekt rule (hex-literal args only; `Color(r, g, b)` is a computed value and passes).
- `StaxListDetailScene` — reusable Nav3 **list-detail Scene** wrapper (§6.4.2): `rememberSceneStrategy`
  (Material `ListDetailSceneStrategy` with list pane `360dp` Medium / `400dp` Expanded + 1dp pane
  divider) + `listPane(sceneKey, detailPlaceholder)` / `detailPane(sceneKey)` metadata helpers. **Not**
  `ListDetailPaneScaffold` (§6.4). Used by Compounds / Protocols / Settings entries.
  **`sceneKey` is mandatory** (the Material API defaults it to `Unit`): `NavDisplay` keys its
  `AnimatedContent` by `(scene::class, scene.key)`, so every scaffold scene sharing the default key
  lands in one content slot and the reused scaffold state crashes with "An instance of
  SeekableTransitionState has been used in different Transitions".
- `StaxSupportingPaneScene` — reusable Nav3 **supporting-pane Scene** wrapper (§6.4.2 Dashboard):
  `rememberSceneStrategy` (Material `SupportingPaneSceneStrategy`, supporting pane `360dp` ≈40% / main
  fills ≈60%, 1dp divider) + `mainPane(sceneKey)` / `supportingPane(sceneKey)` metadata (`sceneKey`
  mandatory, same reason as above — it builds the same `ThreePaneScaffoldScene` class). **Not**
  `SupportingPaneScaffold` (§6.4). Used by the Dashboard Medium layout.
- `Modifier.paneInsets()` — the **single** inset method a Scene pane may use (§2.3.6, M5-09): ruler
  alignment (`fitInside(WindowInsetsRulers.SafeDrawing.current)`) covering system bars + cutout + IME.
  Applied once at the content root of every `NavDisplay` entry. Position-aware, so each pane gets only
  the slice it actually touches; idempotent, so double padding is impossible. Every other
  `WindowInsets` API is banned outside this module (`stax:NoWindowInsetsOutsideDesignSystem`).
- `NoWindowInsets` — empty `WindowInsets` for the Material components that inset themselves
  (`SearchBar`, `TopAppBar`, `ModalBottomSheet`) when they sit inside a pane that already claimed its
  slice via `paneInsets()`; without it the status bar lands twice, as a gap.
- `AdaptiveFab` — the app's primary FAB (§6.4.6): floating bottom-end of its pane with a `16dp`
  inset at **every** width, extended (icon + label, label kept at every width) when `label` is passed.
  Place as the last child of a `fillMaxSize` overlay over screen content. Deliberately **not** the
  navigation rail's FAB slot: that slot is `NavigationSuiteScaffold` chrome owned by `:app`, and a
  FAB there cannot read the screen's state (multi-select hides it, §4.2.4) or reach its ViewModel.
- `LocalFoldingFeature` / `ProvideFoldingFeature` — `WindowInfoTracker.windowLayoutInfo` collector +
  `CompositionLocal<FoldingFeature?>` (§6.4.3). `ProvideFoldingFeature` wraps the nav roots; the
  list-detail / supporting-pane Scenes read it (`verticalHingeBounds()` → directive `excludedBounds`)
  to snap a two-pane divider to a vertical hinge. Also available to screens for single-pane hinge
  padding / tabletop layouts.

## Applicable skills
`android-compose-ui` (design-system composables, stability), `adaptive` (Scene wrappers, nav chrome),
`material-expressive-ui`.

## Owned by
Shared.

## Notes
- **Material 3 components only** — this is where theming lives. Slot APIs (`@Composable () -> Unit`
  params) are allowed **here** but discouraged in feature modules (§2.3.1).
- **No raw colors** outside `Tokens.kt`; **no `tween(`** outside `StaxMotion`; **no `WindowInsets`
  API** outside this module — all four enforced by the `stax` detekt ruleset (`:detekt-rules`).
- Multi-pane wrappers use Nav3 `ListDetailSceneStrategy` / `SupportingPaneSceneStrategy` — **never**
  `*PaneScaffold` (§6.4).
- See spec §9 (design tokens), §5.9 (motion), §6.4; ISSUES M4-*, M5-04/05/06.
