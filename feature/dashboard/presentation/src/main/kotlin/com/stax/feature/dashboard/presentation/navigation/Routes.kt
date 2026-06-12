package com.stax.feature.dashboard.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Dashboard — the Home top-level destination (main pane). */
@Serializable
data object DashboardRoute : NavKey

/**
 * Dashboard supporting pane (§6.4.2): inventory warnings + recent activity, shown beside
 * [DashboardRoute] at Medium+ via the supporting-pane Scene.
 */
@Serializable
data object DashboardSupportingRoute : NavKey
