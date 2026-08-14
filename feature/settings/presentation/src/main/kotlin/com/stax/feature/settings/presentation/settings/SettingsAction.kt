package com.stax.feature.settings.presentation.settings

/** Everything the user can do on the Settings screen (§4.13). */
sealed interface SettingsAction {
    /** "Enable exact reminders" on the degraded-reminders warning row (§5.1). */
    data object OnEnableExactRemindersClick : SettingsAction
}
