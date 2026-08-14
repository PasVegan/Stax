package com.stax.feature.settings.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the Settings screen (§4.13, §10.1).
 *
 * Reminder precision comes from the persisted `Settings` row, not from `AlarmManager`: revoking
 * "Alarms & reminders" moves the flag through `:notification`'s `ExactAlarmPermissionMonitor`, so the
 * warning row appears and disappears while the screen is open, including on the return trip from the
 * system settings the CTA opens (§5.1).
 */
class SettingsViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    init {
        settingsRepository.observe()
            .onEach { settings -> _state.update { it.copy(exactAlarmDegraded = settings.exactAlarmDegraded) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnEnableExactRemindersClick ->
                viewModelScope.launch { _events.send(SettingsEvent.OpenExactAlarmSettings) }
        }
    }
}
