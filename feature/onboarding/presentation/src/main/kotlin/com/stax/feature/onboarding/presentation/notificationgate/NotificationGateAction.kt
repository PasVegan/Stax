package com.stax.feature.onboarding.presentation.notificationgate

/** Everything that can happen on the notification gate — the user's taps and the dialog result (§4.15). */
sealed interface NotificationGateAction {
    /** "Allow notifications" — triggers the `POST_NOTIFICATIONS` request flow. */
    data object OnAllowClick : NotificationGateAction

    /** "Open system settings" — the permanent-denial path to the app's notification settings. */
    data object OnOpenSettingsClick : NotificationGateAction

    /** "Continue" — proceed into the app without the permission (§4.15). */
    data object OnContinueClick : NotificationGateAction

    /**
     * Outcome of the system permission dialog, reported by the composable that owns the launcher.
     * [permanentlyDenied] mirrors `shouldShowRequestPermissionRationale = false` after a denial — the
     * signal that only the settings screen can grant it now, so the secondary action is offered.
     */
    data class OnPermissionResult(val granted: Boolean, val permanentlyDenied: Boolean) : NotificationGateAction
}
