package com.stax.feature.logging.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Log-dose flow. [compoundId] pre-selects a compound when launched from Compound detail; `null`
 * opens the picker first.
 */
@Serializable
data class LogDoseRoute(val compoundId: Long? = null) : NavKey
