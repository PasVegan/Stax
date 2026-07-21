package com.stax.feature.onboarding.presentation.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the Welcome step (§4.14 step 1, §10.1).
 *
 * The step reads nothing and writes nothing — it introduces the app — so [state] is the flow
 * position and the work is turning the two taps into navigation intents. Onboarding completion is
 * persisted at the end of the flow, in step 3 (M6-03).
 */
class WelcomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(WelcomeState())
    val state = _state.asStateFlow()

    private val _events = Channel<WelcomeEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: WelcomeAction) {
        val event = when (action) {
            WelcomeAction.OnContinueClick -> WelcomeEvent.NavigateToNextStep
            WelcomeAction.OnSkipClick -> WelcomeEvent.SkipOnboarding
        }
        viewModelScope.launch { _events.send(event) }
    }
}
