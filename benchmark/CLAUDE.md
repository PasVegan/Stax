# `:benchmark` — Macrobenchmark + Baseline Profile

## Purpose
Performance harness: Macrobenchmark tests and Baseline Profile generation for the hot paths in
§2.3.3. Measures cold-start + scroll + key flows against the SLOs in §2.3.2 and produces the
Baseline Profile bundled into the release build.

## Module coordinates
- Gradle: `:benchmark` · Android test/benchmark module (runs against `:app`).
- Package: `com.stax.benchmark`.

## Allowed dependencies
`:app` (as the target of instrumentation); Macrobenchmark + profile-installer tooling.

## Key types
- Macrobenchmark classes for: app start → Dashboard scroll, Dashboard → Compound Detail,
  Take Dose open + save, FAB menu → Log dose.
- Baseline Profile generator.

## Applicable skills
`testing-setup` (E2E / benchmark harness), `r8-analyzer` (release build under test).

## Owned by
Shared.

## Notes
- Runs on a device/emulator on release tags, not on every PR (E2E happy-path = M19-06).
- SLOs are targets, not gates, until the first Baseline Profile pass locks them (§2.3.2).
- See spec §2.3.2/§2.3.3, §10.5; ISSUES M18-04, M19-06.
