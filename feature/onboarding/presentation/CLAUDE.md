# `:feature:onboarding:presentation` — Onboarding + permission gates

## Purpose
First-run flow: Welcome stepper, then steps that **reuse** Create Compound (§4.4) and Create Protocol
(§4.9), plus the notification-permission gate and exact-alarm rationale. Skip-anywhere.

## Module coordinates
- Gradle: `:feature:onboarding:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.onboarding.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only. **Note:** it reuses Create
Compound/Protocol *screens* — those are composed via `:app` wiring/callbacks, **not** by importing
the compounds/protocols feature modules (features never depend on features).

## Key types
- `OnboardingPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `onboardingEntries` (Nav3 entryProvider extension). Coming: `OnboardingViewModel` + State/Action/Event,
  Welcome + permission-gate composables.

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
