package com.stax.feature.logging.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Log-dose flow. [compoundId] pre-selects a compound when launched from Compound detail; `null`
 * opens the picker first.
 */
@Serializable
data class LogDoseRoute(val compoundId: Long? = null) : NavKey

/**
 * Administration Event detail (§4.11) for the event identified by [eventId].
 *
 * Reached from any dose-history row — Compound Detail's (§4.3.8) is the first. The screen itself is
 * M11-07; the route exists ahead of it so the rows that lead here can be wired now.
 */
@Serializable
data class AdministrationEventDetailRoute(val eventId: Long) : NavKey
