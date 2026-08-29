package com.stax.feature.sites.presentation.navigation

import androidx.navigation3.runtime.NavKey
import com.stax.core.domain.Route
import kotlinx.serialization.Serializable

/** Injection-site rotation — top-level destination. */
@Serializable
data object SitesRoute : NavKey

/**
 * Full-screen site picker (§4.12.7), stacked on whichever destination opened it (§6.2).
 *
 * Both arguments are what the caller knows about the dose the site is being picked for, and both are
 * optional because §4.12.5's "Pick another" knows neither: the Sites screen is picking a site, not
 * dosing yet. They only fill the app bar's supporting line and narrow the list to the sites that
 * route is given at — a picker opened from Take Dose (§4.10.1) or a grouped log (§4.10.3) carries
 * both, and one opened from Sites carries neither.
 */
@Serializable
data class SitePickerRoute(val compoundName: String? = null, val route: Route? = null) : NavKey
