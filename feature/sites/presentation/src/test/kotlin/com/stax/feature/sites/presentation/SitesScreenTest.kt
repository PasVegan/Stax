package com.stax.feature.sites.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.Sublocation
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Sites screen across the §6.4.8 breakpoint profiles (§10.5): Compact (Pixel 10 portrait),
 * Medium (Fold inner portrait) and Expanded (Pixel 10 landscape).
 *
 * §6.4.2 changes the arrangement at each: one scroll, then the map beside the column that reads
 * against it, then both body views side by side with the tabs gone — which is what these assert,
 * along with every control on the screen still doing what it does at each width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SitesScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SitesAction>()

    // -----------------------------------------------------------------------
    // §4.12 across the three breakpoints
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `every section renders at Compact`() {
        setScreen(state())

        assertScreenIsRendered()
        // One column: the map is tabbed, so only the selected view is on screen.
        composeRule.onNodeWithText("Front").assertIsSelected()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `every section renders at Medium`() {
        setScreen(state())

        assertScreenIsRendered()
        composeRule.onNodeWithText("Front").assertIsSelected()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `Expanded draws both body views and drops the tabs`() {
        setScreen(state())

        assertScreenIsRendered()
        // §6.4.2: Front and Back are both on screen, so the tabs that swapped between them are gone.
        composeRule.onNodeWithText("Front").assertDoesNotExist()
        composeRule.onNodeWithText("Back").assertDoesNotExist()
        // The Dots / Heat toggle still applies to both and stays.
        composeRule.onNodeWithText("Dots").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // §4.12.2 / §4.12.4 controls
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the route chips raise their filter at Compact`() {
        setScreen(state())

        composeRule.onNodeWithText("IM").performClick()

        assertThat(actions).containsExactly(SitesAction.OnRouteFilterClick(RouteFilter.INTRAMUSCULAR))
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the route chips raise their filter at Medium`() {
        setScreen(state())

        composeRule.onNodeWithText("SC").performClick()

        assertThat(actions).containsExactly(SitesAction.OnRouteFilterClick(RouteFilter.SUBCUTANEOUS))
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the route chips raise their filter at Expanded`() {
        setScreen(state())

        composeRule.onNodeWithText("All routes").performClick()

        assertThat(actions).containsExactly(SitesAction.OnRouteFilterClick(RouteFilter.ALL))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the Back tab and the Heat toggle raise their actions`() {
        setScreen(state())

        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Heat").performClick()

        assertThat(actions).containsExactly(
            SitesAction.OnBodyViewClick(BodyView.BACK),
            SitesAction.OnMapModeClick(MapMode.HEAT),
        )
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the Heat toggle still raises its action with the tabs gone`() {
        setScreen(state())

        composeRule.onNodeWithText("Heat").performClick()

        assertThat(actions).containsExactly(SitesAction.OnMapModeClick(MapMode.HEAT))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Heat mode swaps the legend for the heat bands`() {
        setScreen(state().copy(mapMode = MapMode.HEAT))

        composeRule.onNodeWithText("Untouched").assertIsDisplayed()
        composeRule.onNodeWithText("Suggested").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a tap on a body-map dot raises the site it landed on`() {
        setScreen(state())

        composeRule.onNodeWithContentDescription("Abdomen Upper-Left, Cooling").performClick()

        assertThat(actions).containsExactly(SitesAction.OnSiteClick(siteId = 2))
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `a tap on a dot of either body view raises its site at Expanded`() {
        setScreen(state())

        // §6.4.2 draws Front and Back at once here, so a Back dot is on screen with the Front ones.
        composeRule.onNodeWithContentDescription("Hamstring Right, Ready").performScrollTo().performClick()

        assertThat(actions).containsExactly(SitesAction.OnSiteClick(siteId = 4))
    }

    // -----------------------------------------------------------------------
    // §4.12.5 Suggested site hero
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the hero states the suggestion and both of its facts`() {
        setScreen(state())

        composeRule.onNodeWithText("Best for now").assertIsDisplayed()
        composeRule.onNodeWithText("Abdomen · Lower right").assertIsDisplayed()
        composeRule.onNodeWithText("14 days rested").assertIsDisplayed()
        composeRule.onNodeWithText("Cooling complete").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the hero's two actions raise theirs at Medium`() {
        setScreen(state())

        composeRule.onNodeWithText("Use this site").performScrollTo().performClick()
        composeRule.onNodeWithText("Pick another").performScrollTo().performClick()

        assertThat(actions).containsExactly(
            SitesAction.OnUseSuggestedSiteClick,
            SitesAction.OnPickAnotherSiteClick,
        )
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the hero's two actions raise theirs at Expanded`() {
        setScreen(state())

        composeRule.onNodeWithText("Use this site").performScrollTo().performClick()
        composeRule.onNodeWithText("Pick another").performScrollTo().performClick()

        assertThat(actions).containsExactly(
            SitesAction.OnUseSuggestedSiteClick,
            SitesAction.OnPickAnotherSiteClick,
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `nothing ready states so in the hero's place`() {
        setScreen(SitesState(isLoading = false, coolingCount = 14))

        composeRule.onNodeWithText("Every site is still cooling").assertIsDisplayed()
        composeRule.onNodeWithText("Use this site").assertDoesNotExist()
        assertThat(actions).isEmpty()
    }

    // -----------------------------------------------------------------------
    // §4.12.6 Recent activity
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the right pane carries the recent sites at Medium`() {
        setScreen(state())

        composeRule.onNodeWithText("Abdomen Upper-Left").assertExists()
        composeRule.onNodeWithText("Hamstring Right").assertExists()
        composeRule.onNodeWithText("2 days ago").assertExists()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the right pane carries the recent sites at Expanded`() {
        setScreen(state())

        composeRule.onNodeWithText("Abdomen Upper-Left").assertExists()
        composeRule.onNodeWithText("2 days ago").assertExists()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an empty carousel says so rather than showing nothing`() {
        setScreen(state().copy(recent = emptyList<SiteUi>().toImmutableList()))

        composeRule.onNodeWithText("No dose has named a site yet.").assertExists()
    }

    private fun setScreen(state: SitesState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                SitesScreen(state = state, onAction = { actions += it })
            }
        }
    }

    /** What §4.12 puts on the screen at every width: the bar, the chips, the strip, the map, the hero. */
    private fun assertScreenIsRendered() {
        composeRule.onNodeWithText("Sites").assertIsDisplayed()
        composeRule.onNodeWithText("All routes").assertIsDisplayed()
        // "Ready" and "Cooling" name both a tile and a legend swatch (§4.12.3, §4.12.4), so the
        // strip is asserted through the counts only it carries.
        composeRule.onNodeWithText("This month").assertExists()
        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithText("42").assertExists()
        composeRule.onNodeWithText("Suggested").assertExists()
        composeRule.onNodeWithText("Abdomen · Lower right").assertExists()
        composeRule.onNodeWithText("Recent activity").assertExists()
    }

    private fun state(): SitesState {
        val front = listOf(
            siteUi(1, "Abdomen Lower-Right", BodyRegion.ABDOMEN, SiteStatus.SUGGESTED, 14, Sublocation.LOWER),
            siteUi(2, "Abdomen Upper-Left", BodyRegion.ABDOMEN, SiteStatus.COOLING, 2, Sublocation.UPPER),
            siteUi(3, "Lateral Thigh Left", BodyRegion.QUADRICEPS, SiteStatus.READY, 8, Sublocation.OUTER),
        )
        val back = listOf(siteUi(4, "Hamstring Right", BodyRegion.HAMSTRING, SiteStatus.READY, 21, null))
        return SitesState(
            readyCount = 12,
            coolingCount = 3,
            usesThisMonth = 42,
            frontSites = front.toImmutableList(),
            backSites = back.toImmutableList(),
            suggested = SuggestedSiteUi(
                id = 1,
                name = "Abdomen · Lower right",
                daysRested = 14,
                isCoolingComplete = true,
            ),
            recent = (front.drop(1) + back).toImmutableList(),
            isLoading = false,
        )
    }

    @Suppress("LongParameterList")
    private fun siteUi(
        id: Long,
        name: String,
        region: BodyRegion,
        status: SiteStatus,
        days: Int?,
        sublocation: Sublocation?,
    ) = SiteUi(
        id = id,
        name = name,
        bodyRegion = region,
        side = InjectionSide.LEFT,
        sublocation = sublocation,
        status = status,
        daysSinceLastUse = days,
    )

    private companion object {
        /** Pixel 10 portrait (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Pixel 10 landscape (§6.4.8). */
        const val EXPANDED = "w914dp-h411dp"
    }
}
