package com.stax.feature.onboarding.presentation.notificationgate

/**
 * UI state of the notification-permission gate (§4.15).
 *
 * [showOpenSettings] gates the secondary "Open system settings" action: it appears only once the
 * permission has been permanently denied — the system will no longer surface the request dialog, so
 * the app's settings screen is the only remaining path to granting it (§4.15).
 */
data class NotificationGateState(val showOpenSettings: Boolean = false)
