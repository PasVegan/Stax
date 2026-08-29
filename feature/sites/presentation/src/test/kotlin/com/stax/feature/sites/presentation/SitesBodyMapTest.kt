package com.stax.feature.sites.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.Sublocation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The §4.12.4 body map: where its dots land, and what a tap on one of them resolves to.
 *
 * The acceptance M10-02 is held to is that the hit test scales with the canvas, so every assertion
 * about a tap runs at two canvas sizes an order of magnitude apart — a target sized or positioned in
 * fixed pixels passes at one of them and fails at the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SitesBodyMapTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clicked = mutableListOf<Long>()

    // -----------------------------------------------------------------------
    // Hit testing
    // -----------------------------------------------------------------------

    @Test
    fun `a tap on a dot resolves that site on a small canvas`() {
        assertEverySiteIsTappable(width = 110.dp, height = 200.dp)
    }

    @Test
    fun `a tap on a dot resolves that site on a large canvas`() {
        assertEverySiteIsTappable(width = 330.dp, height = 600.dp)
    }

    @Test
    fun `a tap on bare body resolves nothing`() {
        setBodyMap(BodyView.FRONT, frontSites())

        // The top-left corner of the map: outside the silhouette entirely, and so outside every dot.
        composeRule.onRoot().performTouchInput { click(Offset(1f, 1f)) }

        assertThat(clicked).isEmpty()
    }

    @Test
    fun `the dots of the other body view are not on this one`() {
        setBodyMap(BodyView.FRONT, frontSites())

        composeRule.onNodeWithContentDescription("Glute Upper-Outer Left, Ready").assertDoesNotExist()
    }

    // -----------------------------------------------------------------------
    // §4.12.4 placement
    // -----------------------------------------------------------------------

    @Test
    fun `Front puts a left-side site on the viewer's right`() {
        setBodyMap(BodyView.FRONT, frontSites())

        // Facing the body, its left is the viewer's right — mirror this and the whole map is wrong.
        assertThat(centerXOf("Abdomen Upper-Left, Recent")).isGreaterThan(centerXOf("Abdomen Upper-Right, Cooling"))
    }

    @Test
    fun `Back puts a left-side site on the viewer's left`() {
        setBodyMap(BodyView.BACK, backSites())

        assertThat(centerXOf("Glute Upper-Outer Left, Ready")).isLessThan(centerXOf("Glute Upper-Outer Right, Ready"))
    }

    @Test
    fun `a dot sits at the height its region is on the body`() {
        setBodyMap(BodyView.FRONT, frontSites())

        // Shoulder above navel above thigh: the vertical order is what makes the map readable at all.
        assertThat(centerYOf("Anterior Deltoid Left, Ready")).isLessThan(centerYOf("Abdomen Lower-Left, Suggested"))
        assertThat(centerYOf("Abdomen Lower-Left, Suggested")).isLessThan(centerYOf("Lateral Thigh Left, Ready"))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Taps every dot of both body views at the given canvas size and asserts each one comes back.
     *
     * The tap is injected at the dot's own bounds, but resolved by the canvas underneath from the
     * fractions it drew with — so this passes only while the two agree at this size.
     */
    private fun assertEverySiteIsTappable(width: Dp, height: Dp) {
        val sites = frontSites()
        setBodyMap(BodyView.FRONT, sites, width, height)

        sites.forEach { site ->
            clicked.clear()
            composeRule.onNodeWithContentDescription(site.description()).performClick()
            assertThat(clicked).containsExactly(site.id)
        }
    }

    private fun setBodyMap(view: BodyView, sites: ImmutableList<SiteUi>, width: Dp = 200.dp, height: Dp = 364.dp) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                Box(modifier = Modifier.size(width, height)) {
                    BodyMap(
                        view = view,
                        sites = sites,
                        mode = MapMode.DOTS,
                        onSiteClick = { clicked += it },
                        modifier = Modifier.size(width, height),
                    )
                }
            }
        }
    }

    private fun centerXOf(description: String): Float =
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.center.x

    private fun centerYOf(description: String): Float =
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.center.y

    private fun SiteUi.description(): String = "$name, ${statusLabel(status)}"

    private fun statusLabel(status: SiteStatus): String = when (status) {
        SiteStatus.SUGGESTED -> "Suggested"
        SiteStatus.COOLING -> "Cooling"
        SiteStatus.RECENT -> "Recent"
        SiteStatus.READY -> "Ready"
    }

    /** §5.8.6's front-side presets, one per dot §4.12.4 draws on the Front tab. */
    private fun frontSites(): ImmutableList<SiteUi> = listOf(
        site(1, "Abdomen Upper-Left", BodyRegion.ABDOMEN, InjectionSide.LEFT, Sublocation.UPPER, SiteStatus.RECENT),
        site(2, "Abdomen Upper-Right", BodyRegion.ABDOMEN, InjectionSide.RIGHT, Sublocation.UPPER, SiteStatus.COOLING),
        site(3, "Abdomen Lower-Left", BodyRegion.ABDOMEN, InjectionSide.LEFT, Sublocation.LOWER, SiteStatus.SUGGESTED),
        site(4, "Abdomen Lower-Right", BodyRegion.ABDOMEN, InjectionSide.RIGHT, Sublocation.LOWER, SiteStatus.READY),
        site(5, "Anterior Deltoid Left", BodyRegion.DELT, InjectionSide.LEFT, null, SiteStatus.READY),
        site(6, "Anterior Deltoid Right", BodyRegion.DELT, InjectionSide.RIGHT, null, SiteStatus.READY),
        site(7, "Lateral Thigh Left", BodyRegion.QUADRICEPS, InjectionSide.LEFT, Sublocation.OUTER, SiteStatus.READY),
        site(8, "Lateral Thigh Right", BodyRegion.QUADRICEPS, InjectionSide.RIGHT, Sublocation.OUTER, SiteStatus.READY),
    ).toImmutableList()

    /** §5.8.6's back-side presets. */
    private fun backSites(): ImmutableList<SiteUi> = listOf(
        site(9, "Glute Upper-Outer Left", BodyRegion.GLUTE, InjectionSide.LEFT, Sublocation.UPPER, SiteStatus.READY),
        site(10, "Glute Upper-Outer Right", BodyRegion.GLUTE, InjectionSide.RIGHT, Sublocation.UPPER, SiteStatus.READY),
        site(11, "Hamstring Left", BodyRegion.HAMSTRING, InjectionSide.LEFT, null, SiteStatus.READY),
        site(12, "Hamstring Right", BodyRegion.HAMSTRING, InjectionSide.RIGHT, null, SiteStatus.READY),
        site(13, "Lower Back Left", BodyRegion.LOWER_BACK, InjectionSide.LEFT, null, SiteStatus.READY),
        site(14, "Lower Back Right", BodyRegion.LOWER_BACK, InjectionSide.RIGHT, null, SiteStatus.READY),
    ).toImmutableList()

    @Suppress("LongParameterList")
    private fun site(
        id: Long,
        name: String,
        region: BodyRegion,
        side: InjectionSide,
        sublocation: Sublocation?,
        status: SiteStatus,
    ) = SiteUi(
        id = id,
        name = name,
        bodyRegion = region,
        side = side,
        sublocation = sublocation,
        status = status,
        daysSinceLastUse = null,
    )
}
