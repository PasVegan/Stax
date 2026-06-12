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
- `SitesPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `sitesEntries` (Nav3 entryProvider extension). Coming: `SitesViewModel` + State/Action/Event,
  body-map vector renderer, heat-map mode, site-detail sheet, site-picker flow.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui` (Canvas/vector + hit-testing), `navigation-3`,
`adaptive`, `android-di-koin`.

## Owned by
Sites feature.

## Notes
- Body-map renderer is a Canvas vector that scales with `Modifier.aspectRatio`. Heat map uses `RenderEffect.createBlurEffect()` (§2.3.7).
- Rotation-suggestion logic is deterministic; keep computation testable (consider hoisting to domain).
- Site detail becomes an end-edge side sheet at Expanded (§6.4.2).
- See spec §4.12, §6.4.2 Sites; ISSUES M10-*.
