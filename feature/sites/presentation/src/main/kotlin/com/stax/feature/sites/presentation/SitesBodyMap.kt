package com.stax.feature.sites.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/**
 * §4.12.4's body silhouette with one dot per site, drawn into whatever bounds it is given.
 *
 * The renderer itself is M10-02 (the `Canvas` silhouette, the normalized dot coordinates and the
 * hit-testing that scales with it) and M10-03 (the blurred [MapMode.HEAT] ellipses). What lands here
 * with M10-01 is the seam the rest of the screen is built against: the hero sizes this slot, hands it
 * the sites of one [BodyView] already carrying their §4.12.4 dot state, and every layout in §6.4.2 —
 * including Expanded's two silhouettes side by side — is laid out and testable before a single line
 * is drawn.
 */
@Suppress("FunctionName", "UnusedParameter")
@Composable
internal fun BodyMap(view: BodyView, sites: ImmutableList<SiteUi>, mode: MapMode, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize())
}
