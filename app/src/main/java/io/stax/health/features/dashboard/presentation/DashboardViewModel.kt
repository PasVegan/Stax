package io.stax.health.features.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class DashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    private val _events = Channel<DashboardEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.OnRefreshClick -> {
                viewModelScope.launch {
                    _state.update { it.copy(isLoading = true) }
                    // Simulate loading
                    kotlinx.coroutines.delay(1000.milliseconds)
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }
}
