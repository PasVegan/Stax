package com.stax.feature.compounds.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Compounds list — top-level destination (Home › Compounds). */
@Serializable
data object CompoundsRoute : NavKey

/** Compound detail for the compound identified by [compoundId]. */
@Serializable
data class CompoundDetailRoute(val compoundId: Long) : NavKey

/**
 * Create-compound form (§4.4).
 *
 * [onboarding] marks the instance that onboarding step 2 reuses (§4.14 step 2): the same form, with
 * the app bar titled "Add your first compound · 2 of 3" and a Skip action in its trailing slot.
 * Onboarding cannot reach this route itself — `:app` builds it (§10.3).
 */
@Serializable
data class CreateCompoundRoute(val onboarding: Boolean = false) : NavKey

/** Edit the compound identified by [compoundId]. */
@Serializable
data class EditCompoundRoute(val compoundId: Long) : NavKey
