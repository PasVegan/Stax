package com.stax.feature.sites.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.Protocol
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.SiteDose
import com.stax.core.domain.SiteUse
import com.stax.core.domain.Sublocation
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.AdministrationEventRepository
import com.stax.core.domain.repository.InjectionSiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The Sites ViewModel (§4.12): the two counts and the month tally, §4.12.2's route chip, §4.12.4's
 * dot states, §4.12.5's suggestion and §4.12.6's carousel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SitesViewModelTest {

    private lateinit var sites: FakeInjectionSiteRepository
    private lateinit var events: FakeAdministrationEventRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sites = FakeInjectionSiteRepository()
        events = FakeAdministrationEventRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // §4.12.3 Stats strip
    // -----------------------------------------------------------------------

    @Test
    fun `Ready counts available sites past their cooldown and Cooling counts the rest`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 30.days),
            site(id = 2, avoidUntil = NOW + 2.days),
            site(id = 3, avoidUntil = NOW - 1.days),
            site(id = 4, isAvailable = false),
        )

        val state = viewModel().state.value

        assertThat(state.readyCount).isEqualTo(2)
        assertThat(state.coolingCount).isEqualTo(1)
    }

    @Test
    fun `This month counts the site-bearing doses the chip keeps`() = runTest {
        sites.stored.value = listOf(site(id = 1))
        events.uses.value = listOf(
            use(siteId = 1, route = Route.SUBCUTANEOUS),
            use(siteId = 1, route = Route.SUBCUTANEOUS),
            use(siteId = 1, route = Route.INTRAMUSCULAR),
        )
        val viewModel = viewModel()

        assertThat(viewModel.state.value.usesThisMonth).isEqualTo(3)

        viewModel.onAction(SitesAction.OnRouteFilterClick(RouteFilter.INTRAMUSCULAR))

        assertThat(viewModel.state.value.usesThisMonth).isEqualTo(1)
    }

    // -----------------------------------------------------------------------
    // §4.12.2 Route filter
    // -----------------------------------------------------------------------

    @Test
    fun `the route chip narrows the map, the counts and the carousel together`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, region = BodyRegion.ABDOMEN, lastUsedAt = NOW - 3.days),
            site(id = 2, region = BodyRegion.DELT, lastUsedAt = NOW - 4.days),
            site(id = 3, region = BodyRegion.QUADRICEPS, lastUsedAt = NOW - 5.days),
        )
        val viewModel = viewModel()

        viewModel.onAction(SitesAction.OnRouteFilterClick(RouteFilter.INTRAMUSCULAR))

        val state = viewModel.state.value
        // The lateral thigh takes both routes, the deltoid only IM, the abdomen only SC.
        assertThat(state.frontSites.map { it.id }).containsExactly(2L, 3L)
        assertThat(state.readyCount).isEqualTo(2)
        assertThat(state.recent.map { it.id }).containsExactly(2L, 3L)
    }

    // -----------------------------------------------------------------------
    // §4.12.4 Dot states + body views
    // -----------------------------------------------------------------------

    @Test
    fun `each site carries the dot state its cooldown and last use imply`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 30.days),
            site(id = 2, lastUsedAt = NOW - 2.days, avoidUntil = NOW + 3.days),
            site(id = 3, lastUsedAt = NOW - 4.days),
            site(id = 4, lastUsedAt = NOW - 10.days),
        )

        val byId = viewModel().state.value.frontSites.associateBy { it.id }

        // Never mind the ids: the oldest ready site is the suggestion, so it takes the ring.
        assertThat(byId.getValue(1L).status).isEqualTo(SiteStatus.SUGGESTED)
        assertThat(byId.getValue(2L).status).isEqualTo(SiteStatus.COOLING)
        assertThat(byId.getValue(3L).status).isEqualTo(SiteStatus.RECENT)
        assertThat(byId.getValue(4L).status).isEqualTo(SiteStatus.READY)
    }

    @Test
    fun `sites are split across the Front and Back views`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, region = BodyRegion.ABDOMEN),
            site(id = 2, region = BodyRegion.GLUTE),
            site(id = 3, region = BodyRegion.HAMSTRING),
            site(id = 4, region = BodyRegion.LOWER_BACK),
        )

        val state = viewModel().state.value

        assertThat(state.sitesOn(BodyView.FRONT).map { it.id }).containsExactly(1L)
        assertThat(state.sitesOn(BodyView.BACK).map { it.id }).containsExactly(2L, 3L, 4L)
    }

    @Test
    fun `the Front-Back tab and the Dots-Heat toggle are plain state`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SitesAction.OnBodyViewClick(BodyView.BACK))
        viewModel.onAction(SitesAction.OnMapModeClick(MapMode.HEAT))

        assertThat(viewModel.state.value.bodyView).isEqualTo(BodyView.BACK)
        assertThat(viewModel.state.value.mapMode).isEqualTo(MapMode.HEAT)
    }

    @Test
    fun `heat is each site's share of the doses the busiest one took in the last 30 days`() = runTest {
        sites.stored.value = listOf(site(id = 1), site(id = 2), site(id = 3))
        events.uses.value = List(4) { use(siteId = 1) } + List(2) { use(siteId = 2) }

        val byId = viewModel().state.value.frontSites.associateBy { it.id }

        assertThat(byId.getValue(1L).heat).isEqualTo(1f)
        assertThat(byId.getValue(2L).heat).isEqualTo(0.5f)
        // Used before the window or not at all — either way this site is cold (§4.12.4 "Untouched").
        assertThat(byId.getValue(3L).heat).isEqualTo(0f)
    }

    @Test
    fun `a dose older than 30 days carries no heat`() = runTest {
        sites.stored.value = listOf(site(id = 1), site(id = 2))
        events.uses.value = listOf(
            use(siteId = 1, loggedAt = NOW - 10.days),
            use(siteId = 2, loggedAt = NOW - 40.days),
        )

        val byId = viewModel().state.value.frontSites.associateBy { it.id }

        assertThat(byId.getValue(1L).heat).isEqualTo(1f)
        assertThat(byId.getValue(2L).heat).isEqualTo(0f)
    }

    @Test
    fun `heat is scaled against the busiest site the chip left, not the busiest of all`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, region = BodyRegion.ABDOMEN),
            site(id = 2, region = BodyRegion.DELT),
        )
        events.uses.value = List(6) { use(siteId = 1) } + List(3) { use(siteId = 2) }
        val viewModel = viewModel()

        assertThat(viewModel.state.value.frontSites.single { it.id == 2L }.heat).isEqualTo(0.5f)

        viewModel.onAction(SitesAction.OnRouteFilterClick(RouteFilter.INTRAMUSCULAR))

        // The abdomen is filtered off the map, so the deltoid is now the hottest thing on it.
        assertThat(viewModel.state.value.frontSites.single { it.id == 2L }.heat).isEqualTo(1f)
    }

    @Test
    fun `a month with no site-bearing dose leaves every site cold rather than dividing by zero`() = runTest {
        sites.stored.value = listOf(site(id = 1))

        assertThat(viewModel().state.value.frontSites.single().heat).isEqualTo(0f)
    }

    // -----------------------------------------------------------------------
    // §4.12.5 Suggested site
    // -----------------------------------------------------------------------

    @Test
    fun `the suggestion is the least recently used ready site, never-used first`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 30.days),
            site(id = 2, lastUsedAt = null),
            site(id = 3, lastUsedAt = NOW - 60.days),
        )

        val suggested = viewModel().state.value.suggested

        assertThat(suggested?.id).isEqualTo(2L)
        assertThat(suggested?.daysRested).isNull()
    }

    @Test
    fun `a cooling or unavailable site is never suggested`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 90.days, avoidUntil = NOW + 1.days),
            site(id = 2, lastUsedAt = NOW - 80.days, isAvailable = false),
            site(id = 3, lastUsedAt = NOW - 14.days, avoidUntil = NOW - 7.days),
        )

        val suggested = viewModel().state.value.suggested

        assertThat(suggested?.id).isEqualTo(3L)
        assertThat(suggested?.daysRested).isEqualTo(14)
        // Its `avoidUntil` has passed, which is exactly what the second chip claims.
        assertThat(suggested?.isCoolingComplete).isEqualTo(true)
    }

    @Test
    fun `a site that never cooled makes no claim to have finished cooling`() = runTest {
        sites.stored.value = listOf(site(id = 1, lastUsedAt = null))

        assertThat(viewModel().state.value.suggested?.isCoolingComplete).isEqualTo(false)
    }

    @Test
    fun `nothing ready leaves the hero without a suggestion`() = runTest {
        sites.stored.value = listOf(site(id = 1, avoidUntil = NOW + 1.days))

        assertThat(viewModel().state.value.suggested).isNull()
    }

    @Test
    fun `Use this site hands the suggestion back and Pick another opens the picker`() = runTest {
        sites.stored.value = listOf(site(id = 7, lastUsedAt = NOW - 9.days))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SitesAction.OnUseSuggestedSiteClick)
            assertThat(awaitItem()).isEqualTo(SitesEvent.UseSite(siteId = 7))

            viewModel.onAction(SitesAction.OnPickAnotherSiteClick)
            assertThat(awaitItem()).isEqualTo(SitesEvent.PickAnotherSite)
        }
    }

    @Test
    fun `Use this site does nothing while nothing is ready`() = runTest {
        sites.stored.value = listOf(site(id = 1, avoidUntil = NOW + 1.days))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SitesAction.OnUseSuggestedSiteClick)
            expectNoEvents()
        }
    }

    // -----------------------------------------------------------------------
    // §4.12.6 Recent activity
    // -----------------------------------------------------------------------

    @Test
    fun `the carousel holds the most recently used sites, newest first`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 8.days),
            site(id = 2, lastUsedAt = NOW - 2.days),
            site(id = 3, lastUsedAt = null),
            site(id = 4, lastUsedAt = NOW - 5.days),
        )

        val recent = viewModel().state.value.recent

        assertThat(recent.map { it.id }).containsExactly(2L, 4L, 1L)
        assertThat(recent.map { it.daysSinceLastUse }).containsExactly(2, 5, 8)
    }

    // -----------------------------------------------------------------------
    // §4.12.8 Site detail sheet
    // -----------------------------------------------------------------------

    @Test
    fun `tapping a dot opens the sheet on that site with its uses`() = runTest {
        sites.stored.value = listOf(site(id = 1), site(id = 2, lastUsedAt = NOW - 2.days))
        events.dosesBySite.value = mapOf(
            // One event, two compounds (§4.10.3): two lines, one use of the site.
            2L to listOf(dose(eventId = 9), dose(eventId = 9, compound = "Tirzepatide"), dose(eventId = 8)),
        )
        val viewModel = viewModel()

        viewModel.onAction(SitesAction.OnSiteClick(2))

        val detail = viewModel.state.value.detail!!
        assertThat(detail.site.id).isEqualTo(2L)
        assertThat(detail.timesUsed).isEqualTo(2)
        assertThat(detail.recentUses.map { it.compoundName })
            .containsExactly("Semaglutide", "Tirzepatide", "Semaglutide")
        assertThat(detail.isAvailable).isTrue()
    }

    @Test
    fun `the sheet counts down the cooldown it is still serving`() = runTest {
        sites.stored.value = listOf(site(id = 1, avoidUntil = NOW + 2.days))
        val viewModel = viewModel()

        viewModel.onAction(SitesAction.OnSiteClick(1))

        assertThat(viewModel.state.value.detail!!.daysCoolingRemaining).isEqualTo(2)
    }

    @Test
    fun `the chip narrowing the map does not empty the open sheet`() = runTest {
        sites.stored.value = listOf(site(id = 1, region = BodyRegion.ABDOMEN))
        val viewModel = viewModel()
        viewModel.onAction(SitesAction.OnSiteClick(1))

        // The abdomen is subcutaneous only, so IM leaves the map empty.
        viewModel.onAction(SitesAction.OnRouteFilterClick(RouteFilter.INTRAMUSCULAR))

        assertThat(viewModel.state.value.frontSites).isEmpty()
        assertThat(viewModel.state.value.detail?.site?.id).isEqualTo(1L)
    }

    @Test
    fun `Mark unavailable toggles isAvailable and the site leaves the rotation`() = runTest {
        sites.stored.value = listOf(site(id = 1, lastUsedAt = NOW - 30.days), site(id = 2, lastUsedAt = NOW - 3.days))
        val viewModel = viewModel()
        viewModel.onAction(SitesAction.OnSiteClick(1))

        viewModel.onAction(SitesAction.OnToggleSiteAvailabilityClick)

        assertThat(sites.stored.value.single { it.id == 1L }.isAvailable).isFalse()
        assertThat(viewModel.state.value.detail!!.isAvailable).isFalse()
        // §4.12.3 and §4.12.5 both stop counting it: an unavailable site is neither ready nor next.
        assertThat(viewModel.state.value.readyCount).isEqualTo(1)
        assertThat(viewModel.state.value.suggested?.id).isEqualTo(2L)

        viewModel.onAction(SitesAction.OnToggleSiteAvailabilityClick)

        assertThat(sites.stored.value.single { it.id == 1L }.isAvailable).isTrue()
        assertThat(viewModel.state.value.suggested?.id).isEqualTo(1L)
    }

    @Test
    fun `a failed write is reported in the sheet and leaves the site alone`() = runTest {
        sites.stored.value = listOf(site(id = 1))
        sites.updateResult = Result.Error(DataError.Local.DISK_FULL)
        val viewModel = viewModel()
        viewModel.onAction(SitesAction.OnSiteClick(1))

        viewModel.onAction(SitesAction.OnToggleSiteAvailabilityClick)

        assertThat(viewModel.state.value.detail!!.hasWriteError).isTrue()
        assertThat(sites.stored.value.single().isAvailable).isTrue()
    }

    @Test
    fun `View full history names the site the sheet is open on`() = runTest {
        sites.stored.value = listOf(site(id = 7))
        val viewModel = viewModel()
        viewModel.onAction(SitesAction.OnSiteClick(7))

        viewModel.events.test {
            viewModel.onAction(SitesAction.OnViewSiteHistoryClick)

            assertThat(awaitItem()).isEqualTo(SitesEvent.ViewSiteHistory(7))
        }
    }

    @Test
    fun `dismissing the sheet drops the site and its uses`() = runTest {
        sites.stored.value = listOf(site(id = 1))
        events.dosesBySite.value = mapOf(1L to listOf(dose(eventId = 1)))
        val viewModel = viewModel()
        viewModel.onAction(SitesAction.OnSiteClick(1))

        viewModel.onAction(SitesAction.OnSiteDetailDismiss)

        assertThat(viewModel.state.value.detail).isNull()
    }

    private fun viewModel() = SitesViewModel(
        siteRepository = sites,
        administrationEventRepository = events,
        now = { NOW },
        timeZone = TimeZone.UTC,
    )

    @Suppress("LongParameterList")
    private fun site(
        id: Long,
        region: BodyRegion = BodyRegion.ABDOMEN,
        lastUsedAt: Instant? = null,
        avoidUntil: Instant? = null,
        isAvailable: Boolean = true,
    ) = InjectionSite(
        id = id,
        name = "Site $id",
        bodyRegion = region,
        side = InjectionSide.LEFT,
        sublocation = Sublocation.UPPER,
        lastUsedAt = lastUsedAt,
        avoidUntil = avoidUntil,
        notes = null,
        isAvailable = isAvailable,
    )

    private fun use(siteId: Long, route: Route = Route.SUBCUTANEOUS, loggedAt: Instant = NOW - 1.days) =
        SiteUse(injectionSiteId = siteId, route = route, loggedAt = loggedAt)

    private fun dose(eventId: Long, compound: String = "Semaglutide", loggedAt: Instant = NOW - 1.days) = SiteDose(
        eventId = eventId,
        loggedAt = loggedAt,
        compoundName = compound,
        dose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
    )

    private class FakeInjectionSiteRepository : InjectionSiteRepository {
        val stored = MutableStateFlow<List<InjectionSite>>(emptyList())
        var updateResult: EmptyResult<DataError.Local> = Result.Success(Unit)

        override fun observeAll(): Flow<List<InjectionSite>> = stored

        override fun observeById(id: Long) = throw NotImplementedError()

        override fun observeReady() = throw NotImplementedError()

        override fun observeCooling() = throw NotImplementedError()

        override suspend fun create(site: InjectionSite) = throw NotImplementedError()

        override suspend fun update(site: InjectionSite): EmptyResult<DataError.Local> {
            if (updateResult is Result.Success) {
                stored.value = stored.value.map { if (it.id == site.id) site else it }
            }
            return updateResult
        }

        override suspend fun delete(id: Long) = throw NotImplementedError()

        override suspend fun suggestNext(protocol: Protocol, route: Route) = throw NotImplementedError()
    }

    private class FakeAdministrationEventRepository : AdministrationEventRepository {
        val uses = MutableStateFlow<List<SiteUse>>(emptyList())
        val dosesBySite = MutableStateFlow<Map<Long, List<SiteDose>>>(emptyMap())

        override fun observeSiteUsesBetween(from: Instant, until: Instant): Flow<List<SiteUse>> = uses

        override fun observeSiteDoses(injectionSiteId: Long): Flow<List<SiteDose>> =
            dosesBySite.map { it[injectionSiteId].orEmpty() }

        override fun pagedHistoryForCompound(
            compoundSupplyId: Long,
            status: com.stax.core.domain.AdministrationEventStatus?,
        ) = throw NotImplementedError()

        override fun observeLoggedDoseCount(compoundSupplyId: Long) = throw NotImplementedError()

        override fun pagedHistoryForProtocol(protocolId: Long) = throw NotImplementedError()

        override fun observeLoggedDoseCountForProtocol(protocolId: Long) = throw NotImplementedError()

        override suspend fun log(
            event: com.stax.core.domain.AdministrationEvent,
            components: List<com.stax.core.domain.DoseComponent>,
        ) = throw NotImplementedError()

        override suspend fun edit(id: Long, edits: com.stax.core.domain.repository.AdministrationEventEdit) =
            throw NotImplementedError()

        override suspend fun delete(id: Long) = throw NotImplementedError()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-15T12:00:00Z")
    }
}
