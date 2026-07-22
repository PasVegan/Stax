package com.stax.feature.onboarding.presentation.notificationgate

/**
 * One-time effects of the notification gate (§4.15, §10.1). Each is a framework interaction the
 * composable owns: the ViewModel names the intent, [NotificationGateRoot] carries it out.
 */
sealed interface NotificationGateEvent {
    /** Launch the system `POST_NOTIFICATIONS` permission dialog. */
    data object RequestPermission : NotificationGateEvent

    /** Open the app's system notification settings (permanent-denial path). */
    data object OpenAppSettings : NotificationGateEvent

    /** Done with the gate — `:app` drops the flow and lands on Dashboard. */
    data object Proceed : NotificationGateEvent
}
