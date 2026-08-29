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
- `SitesBodyMap.kt` — the `BodyMap` seam the hero sizes and hands one body view's dots to. The
  renderer itself is M10-02, the heat mode M10-03.
- `SitesPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `sitesEntries(onUseSite, onPickAnotherSite)` (Nav3 entryProvider extension). Coming: site-detail
  sheet (M10-04), site-picker flow (M10-05).

## Applicable skills
`android-presentation-mvi`, `android-compose-ui` (Canvas/vector + hit-testing), `navigation-3`,
`adaptive`, `android-di-koin`.

## Owned by
Sites feature.

## Notes
- Body-map renderer is a Canvas vector that scales with `Modifier.aspectRatio`. Heat map uses `RenderEffect.createBlurEffect()` (§2.3.7).
- Rotation-suggestion logic is deterministic; keep computation testable (consider hoisting to domain).
  `SitesViewModel.ROTATION_ORDER` is today's version of it — never-used first, then least recently
  used, among the ready sites the chip left. M10-06 hoists the full rule (site restrictions, §5.3's
  cooldown order) into `:core:domain`.
- Layout thresholds are measured on the **pane**, not the window (`SitesScreen.kt`): one column under
  `520dp`, two panes to `720dp`, both body views side by side above it (§6.4.2).
- Site detail becomes an end-edge side sheet at Expanded (§6.4.2).
- See spec §4.12, §6.4.2 Sites; ISSUES M10-*.
