package com.stax.feature.sites.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.SiteUse
import com.stax.core.domain.repository.AdministrationEventRepository
import com.stax.core.domain.repository.InjectionSiteRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * MVI ViewModel for the Sites screen (§4.12, §10.1).
 *
 * Two reads feed the whole screen: every injection site, and the site-bearing doses of a window wide
 * enough for both things that count them — §4.12.3's "This month" tile and §4.12.4's 30-day heat map.
 * Everything else — the counts, the dot states, the suggestion, the carousel — is derived from those,
 * because §4.12.2's chip re-derives all four together and a query per tile would have them disagree
 * while they arrived.
 *
 * One read rather than two overlapping ones: on the first of a month the two windows share all but a
 * day, and two flows of nearly the same rows would re-derive the screen twice per logged dose.
 *
 * Both windows are resolved once, at construction: a screen left open across midnight on the last of
 * the month is not worth a timer, and the sites flow re-emits on the next dose logged anyway.
 *
 * [now] and [timeZone] are parameters so cooldowns and "14 days rested" are testable without
 * freezing the system clock; production resolves the defaults.
 */
class SitesViewModel(
    siteRepository: InjectionSiteRepository,
    administrationEventRepository: AdministrationEventRepository,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(SitesState())
    val state = _state.asStateFlow()

    private val _events = Channel<SitesEvent>()
    val events = _events.receiveAsFlow()

    /** §4.12.3's calendar month, as the half-open instant range the tile counts over. */
    private val monthStartDate: LocalDate = now().toLocalDateTime(timeZone).date
        .let { LocalDate(year = it.year, month = it.month, day = 1) }
    private val monthStart: Instant = monthStartDate.atStartOfDayIn(timeZone)
    private val monthEnd: Instant = monthStartDate.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(timeZone)

    /** §4.12.4's heat window: rolling, so "how hard have I leaned on this" is not reset by a new month. */
    private val heatStart: Instant = now() - HEAT_WINDOW

    /** The two reads, unfiltered. The screen only ever sees what the active chip leaves (§4.12.2). */
    private var sites: List<InjectionSite> = emptyList()
    private var siteUses: List<SiteUse> = emptyList()

    init {
        combine(
            siteRepository.observeAll(),
            // Whichever window opened first: the month is the wider one for all but the first 30 days
            // of it, and each count narrows the rows it reads back down to its own.
            administrationEventRepository.observeSiteUsesBetween(
                from = minOf(monthStart, heatStart),
                until = monthEnd,
            ),
        ) { allSites, uses -> allSites to uses }
            .onEach { (allSites, uses) ->
                sites = allSites
                siteUses = uses
                update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SitesAction) {
        when (action) {
            is SitesAction.OnRouteFilterClick -> update { it.copy(routeFilter = action.filter) }

            // Neither tab nor toggle narrows anything, so both are a plain state write — the dots are
            // already in state for both halves of the body (§4.12.4).
            is SitesAction.OnBodyViewClick -> _state.update { it.copy(bodyView = action.view) }

            is SitesAction.OnMapModeClick -> _state.update { it.copy(mapMode = action.mode) }

            // The map resolves which dot was tapped (M10-02); what opens on top of it is §4.12.8's
            // site detail sheet, which M10-04 adds. Until then the tap has nowhere to go.
            is SitesAction.OnSiteClick -> Unit

            SitesAction.OnUseSuggestedSiteClick ->
                _state.value.suggested?.let { send(SitesEvent.UseSite(it.id)) }

            SitesAction.OnPickAnotherSiteClick -> send(SitesEvent.PickAnotherSite)
        }
    }

    /** Applies a chip change and re-derives everything it narrows, in one emission (§4.12.2). */
    private fun update(transform: (SitesState) -> SitesState) {
        _state.update { current -> transform(current).withResults() }
    }

    /**
     * §4.12.3–§4.12.6 from the two reads and the active chip.
     *
     * The suggestion is picked first because it is also a dot state: the site §4.12.5 names carries
     * §4.12.4's `primary` ring, and deriving it twice is how the hero and the map end up pointing at
     * different sites.
     */
    private fun SitesState.withResults(): SitesState {
        val instant = now()
        val visible = sites.filter { routeFilter.accepts(it.bodyRegion) }
        val suggestion = visible.filter { it.isReadyAt(instant) }.minWithOrNull(ROTATION_ORDER)
        val heatCounts = siteUses.filter { it.loggedAt >= heatStart }
            .groupingBy { it.injectionSiteId }
            .eachCount()
        // The busiest *visible* site, not the busiest of all: the chip narrows what the map draws, and
        // scaling an IM-only map against an abdomen nobody filtered for leaves every blob cold.
        val hottest = visible.maxOfOrNull { heatCounts[it.id] ?: 0 } ?: 0
        val uiSites = visible.map {
            it.toUi(
                instant = instant,
                isSuggested = it.id == suggestion?.id,
                heat = if (hottest == 0) 0f else (heatCounts[it.id] ?: 0).toFloat() / hottest,
            )
        }
        val (front, back) = uiSites.partition { it.bodyView == BodyView.FRONT }

        return copy(
            readyCount = visible.count { it.isReadyAt(instant) },
            coolingCount = visible.count { it.isCoolingAt(instant) },
            usesThisMonth = siteUses.count { it.loggedAt >= monthStart && routeFilter.accepts(it.route) },
            frontSites = front.toImmutableList(),
            backSites = back.toImmutableList(),
            suggested = suggestion?.toSuggestedUi(instant),
            // §4.12.6 is "recent", not "all": a site nobody has used yet has no activity to carry.
            recent = uiSites
                .filter { it.daysSinceLastUse != null }
                .sortedBy { it.daysSinceLastUse }
                .take(RECENT_LIMIT)
                .toImmutableList(),
        )
    }

    /** §4.12.3's Ready tile: past its cooldown and not marked unavailable (§4.12.8). */
    private fun InjectionSite.isReadyAt(instant: Instant): Boolean = isAvailable && !isCoolingAt(instant)

    /** §4.12.3's Cooling tile: §5.3 wrote an `avoidUntil` and it has not passed yet. */
    private fun InjectionSite.isCoolingAt(instant: Instant): Boolean = avoidUntil?.let { it > instant } == true

    private fun InjectionSite.toUi(instant: Instant, isSuggested: Boolean, heat: Float): SiteUi {
        val daysSinceLastUse = daysSinceLastUse(instant)
        return SiteUi(
            id = id,
            name = name,
            bodyRegion = bodyRegion,
            side = side,
            sublocation = sublocation,
            status = when {
                isSuggested -> SiteStatus.SUGGESTED
                isCoolingAt(instant) -> SiteStatus.COOLING
                daysSinceLastUse != null && daysSinceLastUse < RECENT_DAYS -> SiteStatus.RECENT
                else -> SiteStatus.READY
            },
            daysSinceLastUse = daysSinceLastUse,
            heat = heat,
        )
    }

    private fun InjectionSite.toSuggestedUi(instant: Instant): SuggestedSiteUi = SuggestedSiteUi(
        id = id,
        name = name,
        daysRested = daysSinceLastUse(instant),
        // The site is in this list because it is ready, so an `avoidUntil` it still carries is one it
        // has already served — which is exactly what §4.12.5's second chip claims.
        isCoolingComplete = avoidUntil != null,
    )

    /** Whole days between the last dose here and today, or null for a site never used (§4.12.6). */
    private fun InjectionSite.daysSinceLastUse(instant: Instant): Int? =
        lastUsedAt?.toLocalDateTime(timeZone)?.date?.daysUntil(instant.toLocalDateTime(timeZone).date)

    private fun send(event: SitesEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** §4.12.4's Recent state: used inside the last six days. */
        const val RECENT_DAYS = 6

        /** §4.12.4's heat map weighs the last 30 days of use. */
        val HEAT_WINDOW = 30.days

        /** How many cards §4.12.6's carousel holds before the rest is history the sheet shows. */
        const val RECENT_LIMIT = 8

        /**
         * §4.12.4's next-rotation pick, and the order M10-06 will hoist into the domain: a site never
         * used yet before one that has been, then the least recently used.
         */
        val ROTATION_ORDER: Comparator<InjectionSite> = compareBy<InjectionSite> { it.lastUsedAt != null }
            .thenBy { it.lastUsedAt }
            .thenBy { it.name }
    }
}
