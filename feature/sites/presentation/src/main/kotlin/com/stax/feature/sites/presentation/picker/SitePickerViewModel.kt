package com.stax.feature.sites.presentation.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.Route
import com.stax.core.domain.SITE_ROTATION_ORDER
import com.stax.core.domain.isCoolingAt
import com.stax.core.domain.repository.InjectionSiteRepository
import com.stax.core.domain.routes
import com.stax.core.domain.suggestNextSite
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** What the route tells the picker: the dose a site is being picked for, as far as the caller knows it. */
data class SitePickerArgs(val compoundName: String? = null, val route: Route? = null)

/**
 * MVI ViewModel for the full-screen site picker (§4.12.7, §10.1).
 *
 * One read feeds the screen — every injection site — and the suggested row, the list and the counts
 * are all derived from it, so §4.12.7's chip re-derives them together rather than leaving a header
 * counting rows the list no longer holds.
 *
 * **The selection lives in the [SavedStateHandle]**, not in `_state` alone: the picker is a
 * full-screen flow the user may leave and come back to (a notification, a phone call), and a pick
 * lost to process death is a pick the user has to make twice. It is mirrored into the state on every
 * derivation, so the dock reads one value.
 *
 * The rotation's pick is `:core:domain`'s `SiteRotation` (M10-06), the same rule §4.12.5's hero and
 * §4.12.4's `primary` ring are derived from: two copies of it is how the picker's "Suggested" row and
 * the hero end up naming two different sites.
 *
 * [now] and [timeZone] are parameters so the cooldown counts are testable without freezing the
 * system clock; production resolves the defaults.
 */
class SitePickerViewModel(
    private val savedStateHandle: SavedStateHandle,
    siteRepository: InjectionSiteRepository,
    private val args: SitePickerArgs,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        SitePickerState(
            compoundName = args.compoundName,
            route = args.route.toPickerRoute(),
            selectedSiteId = savedStateHandle[SELECTED_SITE_ID],
        ),
    )
    val state = _state.asStateFlow()

    private val _events = Channel<SitePickerEvent>()
    val events = _events.receiveAsFlow()

    /**
     * Every site, unfiltered. The screen only ever sees what [args] and the active chip leave.
     *
     * Not `sites`: [SitePickerState] has a `sites` of its own, and inside its receiver the state's
     * one would win.
     */
    private var allSites: List<InjectionSite> = emptyList()

    init {
        siteRepository.observeAll()
            .onEach { all ->
                allSites = all
                update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SitePickerAction) {
        when (action) {
            is SitePickerAction.OnFilterClick -> update { it.copy(filter = action.filter) }

            is SitePickerAction.OnSiteClick -> {
                savedStateHandle[SELECTED_SITE_ID] = action.siteId
                update { it }
            }

            SitePickerAction.OnCancelClick -> send(SitePickerEvent.Dismissed)

            // Guarded rather than assumed: the dock's button is disabled without a selection, and a
            // selection the list no longer holds is one `withResults` has already dropped.
            SitePickerAction.OnPickClick ->
                _state.value.selectedSiteId?.let { send(SitePickerEvent.SitePicked(it)) }
        }
    }

    /** Applies a change and re-derives everything the chip and the selection narrow, in one emission. */
    private fun update(transform: (SitePickerState) -> SitePickerState) {
        _state.update { current -> transform(current).withResults() }
    }

    /**
     * §4.12.7's suggested row and its "All sites" list, from the one read and the active chip.
     *
     * Sites marked unavailable (§4.12.8) never appear: they are out of the rotation, and a picker
     * that offered one would hand the caller a site the user has said not to use.
     */
    private fun SitePickerState.withResults(): SitePickerState {
        val instant = now()
        val offered = allSites.filter { it.isAvailable && args.route.accepts(it.bodyRegion) }
        val suggestion = allSites.suggestNextSite(instant, route = args.route)
        return copy(
            suggested = suggestion?.toUi(instant),
            sites = offered
                .filter { filter.accepts(it.isCoolingAt(instant)) }
                .sortedWith(SITE_ROTATION_ORDER)
                .map { it.toUi(instant) }
                .toImmutableList(),
            // A selection that is no longer on offer — the site was marked unavailable from another
            // screen while the picker sat open — is not one the dock may hand back.
            selectedSiteId = savedStateHandle.get<Long>(SELECTED_SITE_ID)
                ?.takeIf { id -> offered.any { it.id == id } },
        )
    }

    private fun InjectionSite.toUi(instant: Instant): PickerSiteUi = PickerSiteUi(
        id = id,
        name = name,
        daysCoolingRemaining = avoidUntil
            ?.takeIf { it > instant }
            ?.let { instant.toLocalDateTime(timeZone).date.daysUntil(it.toLocalDateTime(timeZone).date) }
            // A cooldown ending later today is still a cooldown, so it counts as a day rather than
            // rounding down to a pill that says "Cool 0d".
            ?.coerceAtLeast(1),
        daysSinceLastUse = lastUsedAt
            ?.toLocalDateTime(timeZone)?.date
            ?.daysUntil(instant.toLocalDateTime(timeZone).date),
    )

    private fun send(event: SitePickerEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** Where the in-flight pick is kept, so leaving the screen and coming back does not lose it. */
        const val SELECTED_SITE_ID = "SitePicker.selectedSiteId"
    }
}

/** Whether the picker's chip keeps a row in the given state (§4.12.7). */
internal fun PickerFilter.accepts(isCooling: Boolean): Boolean = when (this) {
    PickerFilter.ALL -> true
    PickerFilter.READY -> !isCooling
    PickerFilter.COOLING -> isCooling
}

/**
 * Whether a site in [region] can take the route the picker was opened for (§4.12.2's rule).
 *
 * A picker opened without a route offers every site: §4.12.5's "Pick another" is choosing a site,
 * not yet a dose to give at it.
 */
private fun Route?.accepts(region: BodyRegion): Boolean = this == null || this in region.routes()

/** Only the injected routes reach this screen; an oral or topical dose has no site to pick. */
private fun Route?.toPickerRoute(): PickerRoute? = when (this) {
    Route.SUBCUTANEOUS -> PickerRoute.SUBCUTANEOUS
    Route.INTRAMUSCULAR -> PickerRoute.INTRAMUSCULAR
    else -> null
}
