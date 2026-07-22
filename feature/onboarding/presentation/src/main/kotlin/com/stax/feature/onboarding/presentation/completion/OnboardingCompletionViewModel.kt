package com.stax.feature.onboarding.presentation.completion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Persists the end of the onboarding flow — `Settings.onboardingCompleted = true` (§4.14).
 *
 * Not a screen ViewModel, so it carries no state / action / event triple (§10.1): the last step
 * reuses the Create Protocol screen, which brings its own ViewModel, and completion is one write
 * with nothing to render. This type exists so the write stays behind a ViewModel and out of `:app`'s
 * composables (§10.2), and it is scoped to the activity rather than to a `NavEntry` so it outlives
 * the step that triggers it.
 *
 * A failed write leaves the flag false and the user is shown onboarding again on the next launch.
 * That is both the safe direction and the only recovery available: by the time the write lands the
 * flow is already off the back stack, and §4.14 has no error surface to report to.
 */
class OnboardingCompletionViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** Marks onboarding done. Called once, when the last step is finished or skipped. */
    fun complete() {
        viewModelScope.launch {
            val settings = settingsRepository.observe().first()
            settingsRepository.update(settings.copy(onboardingCompleted = true))
        }
    }
}
