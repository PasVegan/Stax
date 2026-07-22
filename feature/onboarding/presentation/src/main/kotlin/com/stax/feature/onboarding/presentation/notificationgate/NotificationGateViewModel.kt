package com.stax.feature.onboarding.presentation.notificationgate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the notification-permission gate (§4.15, §10.1).
 *
 * The permission itself is Android state the composable owns — the request launcher and the rationale
 * check both need an `Activity` — so this ViewModel only turns taps and the dialog result into the
 * navigation/framework intents ([events]) and holds the one derived bit of state: whether the
 * permanent-denial "Open system settings" path is offered.
 */
class NotificationGateViewModel : ViewModel() {

    private val _state = MutableStateFlow(NotificationGateState())
    val state = _state.asStateFlow()

    private val _events = Channel<NotificationGateEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: NotificationGateAction) {
        when (action) {
            NotificationGateAction.OnAllowClick -> sendEvent(NotificationGateEvent.RequestPermission)
            NotificationGateAction.OnOpenSettingsClick -> sendEvent(NotificationGateEvent.OpenAppSettings)
            NotificationGateAction.OnContinueClick -> sendEvent(NotificationGateEvent.Proceed)
            is NotificationGateAction.OnPermissionResult ->
                if (action.granted) {
                    sendEvent(NotificationGateEvent.Proceed)
                } else {
                    _state.update { it.copy(showOpenSettings = action.permanentlyDenied) }
                }
        }
    }

    private fun sendEvent(event: NotificationGateEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
