/**
 * The notification-permission gate (§4.15): the screen shown after onboarding when
 * `POST_NOTIFICATIONS` has not been granted.
 *
 * MVI per §10.1 — `NotificationGateState` / `NotificationGateAction` / `NotificationGateEvent` /
 * `NotificationGateViewModel`, with `NotificationGateRoot` + `NotificationGateScreen`. The Root owns
 * the framework side (the permission-request launcher, the permanent-denial check, and the jump to
 * system settings); the ViewModel maps taps and the dialog result to intents. `:app` supplies the
 * `onProceed` destination (§10.3).
 *
 * Entry points: `NotificationGateRoot`.
 */
package com.stax.feature.onboarding.presentation.notificationgate
