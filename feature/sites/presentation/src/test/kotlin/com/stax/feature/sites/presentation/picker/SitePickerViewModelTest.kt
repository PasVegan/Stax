package com.stax.feature.sites.presentation.picker

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.Protocol
import com.stax.core.domain.Route
import com.stax.core.domain.repository.InjectionSiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The site picker's ViewModel (§4.12.7): what the caller's route leaves on offer, the All / Ready /
 * Cooling chip, the rotation's own pick, the selection the dock acts on, and the two ways out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SitePickerViewModelTest {

    private lateinit var sites: FakeInjectionSiteRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sites = FakeInjectionSiteRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // What the picker offers
    // -----------------------------------------------------------------------

    @Test
    fun `the caller's route narrows the list to the sites it can be given at`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, region = BodyRegion.ABDOMEN),
            site(id = 2, region = BodyRegion.DELT),
            site(id = 3, region = BodyRegion.QUADRICEPS),
        )

        val state = viewModel(args = SitePickerArgs(route = Route.INTRAMUSCULAR)).state.value

        // The lateral thigh takes both routes, the deltoid only IM, the abdomen only SC.
        assertThat(state.sites.map { it.id }).containsExactly(2L, 3L)
    }

    @Test
    fun `a picker opened without a route offers every site`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, region = BodyRegion.ABDOMEN),
            site(id = 2, region = BodyRegion.DELT),
        )

        assertThat(viewModel().state.value.sites.map { it.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `a site left out of the rotation is never offered`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 10.days),
            site(id = 2, isAvailable = false),
        )

        val state = viewModel().state.value

        assertThat(state.sites.map { it.id }).containsExactly(1L)
        assertThat(state.suggested?.id).isEqualTo(1L)
    }

    @Test
    fun `the app bar carries whatever the caller knew about the dose`() = runTest {
        val state = viewModel(
            args = SitePickerArgs(compoundName = "Tirzepatide", route = Route.SUBCUTANEOUS),
        ).state.value

        assertThat(state.compoundName).isEqualTo("Tirzepatide")
        assertThat(state.route).isEqualTo(PickerRoute.SUBCUTANEOUS)
    }

    // -----------------------------------------------------------------------
    // §4.12.7 filter chips
    // -----------------------------------------------------------------------

    @Test
    fun `the chip narrows the list to Ready or to Cooling`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 10.days),
            site(id = 2, lastUsedAt = NOW - 2.days, avoidUntil = NOW + 3.days),
        )
        val viewModel = viewModel()

        viewModel.onAction(SitePickerAction.OnFilterClick(PickerFilter.READY))
        assertThat(viewModel.state.value.sites.map { it.id }).containsExactly(1L)

        viewModel.onAction(SitePickerAction.OnFilterClick(PickerFilter.COOLING))
        assertThat(viewModel.state.value.sites.map { it.id }).containsExactly(2L)

        viewModel.onAction(SitePickerAction.OnFilterClick(PickerFilter.ALL))
        assertThat(viewModel.state.value.sites.map { it.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `the suggested row survives a chip that hides every other site`() = runTest {
        sites.stored.value = listOf(site(id = 1, lastUsedAt = NOW - 10.days))
        val viewModel = viewModel()

        viewModel.onAction(SitePickerAction.OnFilterClick(PickerFilter.COOLING))

        assertThat(viewModel.state.value.sites).isEmpty()
        assertThat(viewModel.state.value.suggested?.id).isEqualTo(1L)
    }

    // -----------------------------------------------------------------------
    // Suggested row + row content
    // -----------------------------------------------------------------------

    @Test
    fun `the suggested row is the rotation's next pick and skips the cooling sites`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 2.days),
            // Never used, so it comes first — but it is still cooling, so it cannot be suggested.
            site(id = 2, lastUsedAt = null, avoidUntil = NOW + 3.days),
            site(id = 3, lastUsedAt = NOW - 30.days),
        )

        val state = viewModel().state.value

        assertThat(state.suggested?.id).isEqualTo(3L)
        // The list itself keeps the rotation's order: never used, then least recently used.
        assertThat(state.sites.map { it.id }).containsExactly(2L, 3L, 1L)
    }

    @Test
    fun `a row states its cooldown and how long since it was last used`() = runTest {
        sites.stored.value = listOf(
            site(id = 1, lastUsedAt = NOW - 2.days, avoidUntil = NOW + 2.days),
            site(id = 2, lastUsedAt = null),
            // A cooldown that ends later today is still a cooldown, so it counts as a day.
            site(id = 3, lastUsedAt = NOW, avoidUntil = NOW + 6.hours),
        )

        val byId = viewModel().state.value.sites.associateBy { it.id }

        assertThat(byId.getValue(1L).daysCoolingRemaining).isEqualTo(2)
        assertThat(byId.getValue(1L).daysSinceLastUse).isEqualTo(2)
        assertThat(byId.getValue(2L).isCooling).isFalse()
        assertThat(byId.getValue(2L).daysSinceLastUse).isNull()
        assertThat(byId.getValue(3L).daysCoolingRemaining).isEqualTo(1)
        assertThat(byId.getValue(3L).daysSinceLastUse).isEqualTo(0)
    }

    // -----------------------------------------------------------------------
    // Selection + the dock's two ways out
    // -----------------------------------------------------------------------

    @Test
    fun `tapping a row selects it and Pick site hands it to the caller`() = runTest {
        sites.stored.value = listOf(site(id = 1), site(id = 2))
        val viewModel = viewModel()

        viewModel.onAction(SitePickerAction.OnSiteClick(siteId = 2))
        assertThat(viewModel.state.value.selectedSiteId).isEqualTo(2L)

        viewModel.events.test {
            viewModel.onAction(SitePickerAction.OnPickClick)
            assertThat(awaitItem()).isEqualTo(SitePickerEvent.SitePicked(siteId = 2))
        }
    }

    @Test
    fun `Pick site with nothing selected returns nothing`() = runTest {
        sites.stored.value = listOf(site(id = 1))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SitePickerAction.OnPickClick)
            expectNoEvents()
        }
    }

    @Test
    fun `Cancel leaves without a site`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SitePickerAction.OnCancelClick)
            assertThat(awaitItem()).isEqualTo(SitePickerEvent.Dismissed)
        }
    }

    @Test
    fun `the selection is kept in the SavedStateHandle, so process death does not lose the pick`() = runTest {
        sites.stored.value = listOf(site(id = 1), site(id = 2))
        val savedStateHandle = SavedStateHandle()

        viewModel(savedStateHandle = savedStateHandle).onAction(SitePickerAction.OnSiteClick(siteId = 2))

        // A second ViewModel over the same handle is what a process death and restore looks like.
        val restored = viewModel(savedStateHandle = savedStateHandle)

        assertThat(restored.state.value.selectedSiteId).isEqualTo(2L)
        restored.events.test {
            restored.onAction(SitePickerAction.OnPickClick)
            assertThat(awaitItem()).isEqualTo(SitePickerEvent.SitePicked(siteId = 2))
        }
    }

    @Test
    fun `a selection the picker stops offering is dropped`() = runTest {
        sites.stored.value = listOf(site(id = 1), site(id = 2))
        val viewModel = viewModel()
        viewModel.onAction(SitePickerAction.OnSiteClick(siteId = 2))

        // Marked unavailable elsewhere (§4.12.8) while the picker sat open.
        sites.stored.value = listOf(site(id = 1), site(id = 2, isAvailable = false))

        assertThat(viewModel.state.value.selectedSiteId).isNull()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun viewModel(
        args: SitePickerArgs = SitePickerArgs(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = SitePickerViewModel(
        savedStateHandle = savedStateHandle,
        siteRepository = sites,
        args = args,
        now = { NOW },
        timeZone = TimeZone.UTC,
    )

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
        sublocation = null,
        lastUsedAt = lastUsedAt,
        avoidUntil = avoidUntil,
        notes = null,
        isAvailable = isAvailable,
    )

    private class FakeInjectionSiteRepository : InjectionSiteRepository {
        val stored = MutableStateFlow<List<InjectionSite>>(emptyList())

        override fun observeAll(): Flow<List<InjectionSite>> = stored

        override fun observeById(id: Long) = throw NotImplementedError()

        override fun observeReady() = throw NotImplementedError()

        override fun observeCooling() = throw NotImplementedError()

        override suspend fun create(site: InjectionSite) = throw NotImplementedError()

        override suspend fun update(site: InjectionSite) = throw NotImplementedError()

        override suspend fun delete(id: Long) = throw NotImplementedError()

        override suspend fun suggestNext(protocol: Protocol, route: Route) = throw NotImplementedError()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-15T12:00:00Z")
    }
}
