package com.stax.feature.settings.presentation.settings

/**
 * One-time effects of the Settings screen (§4.13, §10.1). The ViewModel names the intent;
 * [SettingsRoot] carries out the framework side.
 */
sealed interface SettingsEvent {
    /** Open the system "Alarms & reminders" screen so the user can grant exact alarms (§5.1). */
    data object OpenExactAlarmSettings : SettingsEvent
}
