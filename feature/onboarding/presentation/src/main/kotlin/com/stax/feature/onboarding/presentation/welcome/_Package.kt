/**
 * Onboarding step 1 — the Welcome screen (§4.14 step 1, §6.4.2 Onboarding).
 *
 * MVI per §10.1: `WelcomeState` / `WelcomeAction` / `WelcomeEvent` + `WelcomeViewModel`, with
 * `WelcomeRoot` (holds the ViewModel, observes events) and `WelcomeScreen` (state + `onAction`,
 * previewable) in `WelcomeScreen.kt`.
 *
 * The step reads and writes nothing — completion is persisted at the end of the flow, in step 3
 * (M6-03). Steps 2 and 3 reuse the Create Compound / Create Protocol screens and bring their own
 * ViewModels with them.
 */
package com.stax.feature.onboarding.presentation.welcome
