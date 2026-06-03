package io.stax.health.features.dashboard.presentation

import androidx.navigation3.runtime.NavKey
import io.stax.health.core.presentation.MviAction
import io.stax.health.core.presentation.MviEvent
import io.stax.health.core.presentation.MviState
import io.stax.health.core.presentation.UiText
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute : NavKey

data class DashboardState(
    val title: String = "Stax Dashboard",
    val isLoading: Boolean = false,
    val error: UiText? = null,
) : MviState

sealed interface DashboardAction : MviAction {
    data object OnRefreshClick : DashboardAction
}

sealed interface DashboardEvent : MviEvent {
    data class ShowSnackbar(
        val message: UiText,
    ) : DashboardEvent
}
