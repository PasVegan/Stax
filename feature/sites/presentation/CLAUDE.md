# `:feature:sites:presentation` — Injection sites

## Purpose
Injection-site rotation: the Sites screen with a body-map vector renderer (Front/Back), dots + heat-map
modes, suggested-next-site card, recent-site activity, the site-detail sheet, and the full-screen site
picker. Includes the rotation-suggestion algorithm surface.

## Module coordinates
- Gradle: `:feature:sites:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.sites.presentation` (`.di`, `.navigation`, `.picker`).
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
  viewport once, so the map and its targets scale together. Heat mode swaps the last two layers for
  one blurred ellipse per site on a second `Canvas`, cross-faded with them; `heatAlpha()` is the ramp
  both it and the legend read.
- `SitesAction.OnSiteClick(siteId)` — a dot tap (§4.12.4) **or** a carousel card (§4.12.6); both open
  §4.12.8's sheet, since a card naming a site is the other way into that site.
- `SiteDetailSheet.kt` — §4.12.8's sheet on `StaxAdaptiveSheet` (`360dp` side sheet at Expanded,
  §6.4.2): status header, the Times used / Route / Last used tiles, the last three doses given here,
  and the View full history / availability-toggle actions. `SiteDetailSheet` is the sheet;
  `SiteDetailContent` is its body, which is what the previews render — a modal sheet is its own
  window and no `@Preview` draws one.
- `SitesState.SiteDetailUi` / `SiteDoseUi` — the sheet's state. `SitesState.detail` non-null is the
  sheet being open.
- `picker/` — §4.12.7's full-screen picker: `SitePickerViewModel` + `SitePickerState` /
  `SitePickerAction` / `SitePickerEvent`, `SitePickerScreen` (Root + Screen split), and
  `SitePickerArgs` (the compound + route the caller is dosing, both optional). `PickerFilter` is the
  screen's own All / Ready / Cooling chip — not §4.12.2's route chip — and `PickerRoute` is the two
  injected routes the app bar can name.
- `SitesPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` routes:
  `SitesRoute`, `SitePickerRoute`) + `sitesEntries(onUseSite, onPickAnotherSite, onViewSiteHistory,
  onSitePicked, onPickerDismiss)` (Nav3 entryProvider extension).

## Applicable skills
`android-presentation-mvi`, `android-compose-ui` (Canvas/vector + hit-testing), `navigation-3`,
`adaptive`, `android-di-koin`.

## Owned by
Sites feature.

## Notes
- Body-map renderer is a Canvas vector sized **height-first** — `heightIn` plus an `aspectRatio` with
  `matchHeightConstraintsFirst = true`. Inside a scrolling column a width-first ratio has no height
  to be capped by, and the map grows until it pushes §4.12.5's hero off the screen.
- Heat mode (§4.12.4, §2.3.7) blurs with `Modifier.blur(…, BlurredEdgeTreatment.Unbounded)` —
  `RenderEffect.createBlurEffect()` under a `graphicsLayer`. It has to be its **own** `Canvas`: the
  blur applies to the whole layer, so drawing it with the silhouette puts the body out of focus. The
  blob is the site's zone bounds, not one radius for all, and the blur radius scales with the dot.
- `SiteUi.heat` is a **share of the busiest visible site**, not an absolute count: the ViewModel
  normalises it so Front and Back agree on what "hot" means and §4.12.2's chip re-scales with the map
  it narrowed. Heat is `error` at `heatAlpha()`, 0.05 → 0.7; the legend samples that same ramp.
- Dots ↔ Heat is a cross-fade (`StaxMotion.defaultEffectsSpec`), and the suggested site's `primary`
  ring survives it — heat has no way of saying "use this one next".
- Front and Back mirror each other: facing the body its **left** is the viewer's **right**, and from
  behind the two agree. `SitesBodyMap.placeOn` owns that rule — invert it and the whole map is wrong.
- Zone alpha is per state, not one value: with fourteen presets nearly every zone is tinted at once,
  so ready sites stay a faint wash and suggested / cooling are what the eye finds.
- The figure's proportions are the canonical eight-head standing figure. Move one landmark in
  `BodyArt` (chin `31`, navel `96`, crotch `126`, knee `176`, sole `240`) and the rest has to move.
- Dots are pixels on a canvas, so each carries a semantics-only node above it ("{site}, {status}")
  for TalkBack (§5.10); the node has no pointer input, so taps fall through to the canvas.
- Rotation-suggestion logic is deterministic; keep computation testable (consider hoisting to domain).
  `ROTATION_ORDER` (in `SitesState.kt`) is today's version of it — never-used first, then least
  recently used, among the ready sites the chip left. It is **shared** by §4.12.5's hero and
  §4.12.7's Suggested row: two copies of the rule is how the two end up naming different sites.
  M10-06 hoists the full rule (site restrictions, §5.3's cooldown order) into `:core:domain`.
- Layout thresholds are measured on the **pane**, not the window (`SitesScreen.kt`): one column under
  `520dp`, two panes to `720dp`, both body views side by side above it (§6.4.2).
- Site detail becomes an end-edge side sheet at Expanded (§6.4.2) — `StaxAdaptiveSheet` handles all
  three shapes, so the sheet passes only `sideSheetWidth = 360dp` and its content.
- **The sheet's own read is a third flow**, keyed on `openSiteId` through `flatMapLatest`: §4.12.8 asks
  about one site and only while its sheet is up, and `flatMapLatest` is what closes the previous site's
  query when a second dot is tapped without a dismiss in between. `SiteDetailUi.timesUsed` is null
  until that read lands, so the tile is blank rather than claiming zero for a site with eight uses.
- **The sheet is derived from the *unfiltered* sites**: §4.12.2's chip narrows the map, and a chip
  changed while the sheet is open would otherwise empty it.
- Mark unavailable writes the whole `InjectionSite` back (`update(site.copy(isAvailable = …))`) rather
  than through a field-level repository call — the ViewModel already holds the row, and this screen
  changes nothing else about it. The sheet stays open: `observeAll` re-emits, the button flips to
  "Mark available", and the site drops out of Ready (§4.12.3) and out of the rotation (§4.12.5).
- The sheet's action row uses **no weights** inside its `FlowRow`: weighted, the two full-sentence
  labels share the row and truncate at Compact ("View full hi…"). Sized to their own labels they wrap.
- The recent-use time is formatted locally (`DateFormat.is24HourFormat` + `getBestDateTimePattern`),
  the same eight lines Compound Detail carries — features never depend on features, and hoisting it
  to `:core:presentation` for a second caller is a bigger change than the duplication.
- **The picker's selection lives in its `SavedStateHandle`**, not in `_state` alone: it is a
  full-screen flow the user can leave and return to, and a pick lost to process death is a pick made
  twice. `withResults()` mirrors it back into the state and drops it if the site stops being offered
  (marked unavailable elsewhere while the picker sat open).
- The picker **never offers an unavailable site** (§4.12.8): it is out of the rotation, and handing
  the caller one would return a site the user has said not to use. Its `Suggested` row is likewise
  not narrowed by the chip — it is the answer the screen leads with.
- The picker is one layout at every width: a `LazyVerticalGrid` with `GridCells.Adaptive` (rows at
  least `320dp`), with the chips, both headers and the suggested row spanning `maxLineSpan`. §6.4.2
  names no arrangement of its own for this screen, and a list of one kind of thing has no second
  pane to become.
- "Pick site" is both the picker's app-bar title and its dock button (§4.12.7), so tests match the
  button by its click action rather than by text.
- §4.12.7 "returns to the caller" by popping back to it: the picker is stacked on whichever screen
  opened it (§6.2), and only `:app` knows which — so `SitePickerEvent.SitePicked` names the site and
  `MainScaffold.staxSitesEntries` pops and hands it on (`ON_USE_SITE`, inert until M11-01).
- See spec §4.12, §6.4.2 Sites; ISSUES M10-*.
