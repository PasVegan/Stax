package com.stax.feature.reconstitution.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Reconstitution calculator. [compoundId] pre-selects a compound when launched from Compound
 * detail; `null` opens the standalone calculator.
 */
@Serializable
data class ReconstitutionRoute(val compoundId: Long? = null) : NavKey
