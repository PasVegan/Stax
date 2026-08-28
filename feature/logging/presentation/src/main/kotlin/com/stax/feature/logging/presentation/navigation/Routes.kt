package com.stax.feature.logging.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Log-dose flow. [compoundId] pre-selects a compound when launched from Compound detail (§4.10.2-b);
 * [protocolId] carries the protocol Protocol Detail's dock came from, which §4.10.2-c prefills the
 * whole log from. Both null opens the picker first.
 */
@Serializable
data class LogDoseRoute(val compoundId: Long? = null, val protocolId: Long? = null) : NavKey

/**
 * Administration Event detail (§4.11) for the event identified by [eventId].
 *
 * Reached from any dose-history row — Compound Detail's (§4.3.8) is the first. The screen itself is
 * M11-07; the route exists ahead of it so the rows that lead here can be wired now.
 */
@Serializable
data class AdministrationEventDetailRoute(val eventId: Long) : NavKey
