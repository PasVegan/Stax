# `:feature:onboarding:presentation` — Onboarding + permission gates

## Purpose
First-run flow: Welcome stepper, then steps that **reuse** Create Compound (§4.4) and Create Protocol
(§4.9), plus the notification-permission gate and exact-alarm rationale. Skip-anywhere.

## Module coordinates
- Gradle: `:feature:onboarding:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.onboarding.presentation` (`.di`, `.navigation`, `.components`, `.welcome`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only. **Note:** it reuses Create
Compound/Protocol *screens* — those are composed via `:app` wiring/callbacks, **not** by importing
the compounds/protocols feature modules (features never depend on features).

## Key types
- `OnboardingPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `onboardingEntries(onContinue, onSkip)` (Nav3 entryProvider extension — `:app` decides where each
  action leads).
- `welcome/` — step 1 (§4.14): hero + headline + subtitle + Continue + Skip + step pills. MVI per
  §10.1: `WelcomeState` (flow position) / `WelcomeAction` / `WelcomeEvent` (`NavigateToNextStep`,
  `SkipOnboarding` — intents, not destinations) / `WelcomeViewModel`, with `WelcomeRoot` +
  `WelcomeScreen` in `WelcomeScreen.kt`. The step reads and writes nothing; completion is persisted
  in step 3 (M6-03). Adaptive per §6.4.2 — private `CompactWelcome` (single centered column) /
  `WideWelcome` (hero-left, content-right at 600dp+), selected from `currentWindowAdaptiveInfoV2()`.
- `OnboardingHeroBlob` / `OnboardingStepIndicator` (`components/`) — the three-blob hero illustration
  (`Canvas`, every dimension a fraction of the square side so it scales to its bounds) with the
  round `primary` logo badge, and the progress pill row. Both are reused by the permission gate
  (§4.15, M6-04).
- Step 2 (§4.14) is the Create Compound form flagged as onboarding — `:app` answers `onContinue`
  with `CreateCompoundRoute(onboarding = true)` and the form's Skip with a pop. Nothing of step 2
  lives here: features never depend on features. The form itself is M7-04; until it lands the entry
  is a placeholder carrying the onboarding title + Skip.
- Coming: permission-gate composables (M6-04/M6-05).

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Onboarding feature.

## Notes
- Permission gates: `POST_NOTIFICATIONS` + exact-alarm rationale (§4.15, M6-04/M6-05).
- Reuse of Create forms is achieved by hoisting those forms as reusable entry points and wiring them
  in `:app` — do not duplicate the form logic here.
- Adaptive: hero illustration + step content split at Medium+ (§6.4.2 Onboarding).
- See spec §4.14, §4.15; ISSUES M6-*.
