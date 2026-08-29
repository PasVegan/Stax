# `:feature:sites:presentation` — Injection sites

## Purpose
Injection-site rotation: the Sites screen with a body-map vector renderer (Front/Back), dots + heat-map
modes, suggested-next-site card, recent-site activity, the site-detail sheet, and the full-screen site
picker. Includes the rotation-suggestion algorithm surface.

## Module coordinates
- Gradle: `:feature:sites:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.sites.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `SitesViewModel` + `SitesState` / `SitesAction` / `SitesEvent` — the Sites screen (§4.12).
  `SitesScreen` (Root + Screen split) and `SitesSections.kt` (chips, stats strip, body-map hero,
  suggested hero, recent carousel).
- `SitesState.kt` also holds the screen's enums: `RouteFilter`, `BodyView`, `MapMode`, `SiteStatus`,
  plus `BodyRegion.bodyView` / `BodyRegion.routes()` — a site carries no route (§3.6), so §4.12.2's
  SC / IM chips are derived from the region.
- `SitesBodyArt.kt` — `BodyArt`: every path the map draws, as SVG path data in one fixed viewport
  (`120 × 248`). Silhouette (torso + arm), the muscle groups of each view, and `zoneOf(region,
  sublocation)` — the patch of body a site injects into plus the point its dot sits at. Only the
  **right** half is written down; the left is the same data mirrored.
- `SitesBodyMap.kt` — `BodyMap`: the §4.12.4 renderer. Four layers on one `Canvas` — silhouette,
  muscle groups (clipped to it), each site's zone washed in its state colour, then the dots — and a
  tap resolved to the nearest site within a scaled hit radius. Everything is scaled from `BodyArt`'s
  viewport once, so the map and its targets scale together. Heat mode is M10-03; dots still draw there.
- `SitesAction.OnSiteClick(siteId)` — a dot tap (§4.12.4). The ViewModel drops it until M10-04 adds
  §4.12.8's detail sheet; §4.12.6's carousel cards raise the same action when they become tappable.
- `SitesPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `sitesEntries(onUseSite, onPickAnotherSite)` (Nav3 entryProvider extension). Coming: site-detail
  sheet (M10-04), site-picker flow (M10-05).

## Applicable skills
`android-presentation-mvi`, `android-compose-ui` (Canvas/vector + hit-testing), `navigation-3`,
`adaptive`, `android-di-koin`.

## Owned by
Sites feature.

## Notes
- Body-map renderer is a Canvas vector sized **height-first** — `heightIn` plus an `aspectRatio` with
  `matchHeightConstraintsFirst = true`. Inside a scrolling column a width-first ratio has no height
  to be capped by, and the map grows until it pushes §4.12.5's hero off the screen. Heat map uses
  `RenderEffect.createBlurEffect()` (§2.3.7).
- Front and Back mirror each other: facing the body its **left** is the viewer's **right**, and from
  behind the two agree. `SitesBodyMap.placeOn` owns that rule — invert it and the whole map is wrong.
- Zone alpha is per state, not one value: with fourteen presets nearly every zone is tinted at once,
  so ready sites stay a faint wash and suggested / cooling are what the eye finds.
- The figure's proportions are the canonical eight-head standing figure. Move one landmark in
  `BodyArt` (chin `31`, navel `96`, crotch `126`, knee `176`, sole `240`) and the rest has to move.
- Dots are pixels on a canvas, so each carries a semantics-only node above it ("{site}, {status}")
  for TalkBack (§5.10); the node has no pointer input, so taps fall through to the canvas.
- Rotation-suggestion logic is deterministic; keep computation testable (consider hoisting to domain).
  `SitesViewModel.ROTATION_ORDER` is today's version of it — never-used first, then least recently
  used, among the ready sites the chip left. M10-06 hoists the full rule (site restrictions, §5.3's
  cooldown order) into `:core:domain`.
- Layout thresholds are measured on the **pane**, not the window (`SitesScreen.kt`): one column under
  `520dp`, two panes to `720dp`, both body views side by side above it (§6.4.2).
- Site detail becomes an end-edge side sheet at Expanded (§6.4.2).
- See spec §4.12, §6.4.2 Sites; ISSUES M10-*.
