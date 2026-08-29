package com.stax.feature.sites.presentation.picker

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * §4.12.7's three chips. Single select, [ALL] by default.
 *
 * Ready and Cooling are the two states the picker's rows can be in — a site left out of the rotation
 * (§4.12.8) never reaches this screen at all, so there is no chip for it.
 */
enum class PickerFilter { ALL, READY, COOLING }

/**
 * One row of §4.12.7's list.
 *
 * [daysCoolingRemaining] is non-null exactly when the row is cooling, and is what the "Cool 2d" pill
 * counts down; [daysSinceLastUse] is null for a site never used, which is a different thing from
 * zero and is why the meta line says "Never used" rather than "Last used today".
 */
@Immutable
data class PickerSiteUi(val id: Long, val name: String, val daysCoolingRemaining: Int?, val daysSinceLastUse: Int?) {
    val isCooling: Boolean get() = daysCoolingRemaining != null
}

/**
 * UI state of the site picker (§4.12.7).
 *
 * [compoundName] and [route] come from the caller and only fill the app bar's supporting line;
 * [route] also narrows the list to the sites that route is given at, since a picker opened for an
 * intramuscular dose has no business offering the abdomen.
 *
 * [suggested] is the rotation's own pick and is **not** narrowed by [filter]: it is the answer the
 * screen leads with, and a chip that hid it would leave the user hunting for the one row that was
 * worth showing. [sites] is what the chip left, in rotation order.
 *
 * [selectedSiteId] is what the dock's "Pick site" acts on — null is the button disabled, which is
 * §4.12.7's "requires selection".
 */
data class SitePickerState(
    val compoundName: String? = null,
    val route: PickerRoute? = null,
    val filter: PickerFilter = PickerFilter.ALL,
    val suggested: PickerSiteUi? = null,
    val sites: ImmutableList<PickerSiteUi> = persistentListOf(),
    val selectedSiteId: Long? = null,
    val isLoading: Boolean = true,
)

/**
 * The route the picker was opened for, as the app bar writes it.
 *
 * A UI enum rather than the domain's `Route` (§2.3.1): only the two injected routes can reach this
 * screen — an oral or topical dose has no site to pick — so the state carries the two the bar can
 * actually name.
 */
enum class PickerRoute { SUBCUTANEOUS, INTRAMUSCULAR }
