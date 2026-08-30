package com.stax.feature.sites.presentation.picker

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.stax.core.design.system.StaxTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The site picker across the §6.4.8 breakpoint profiles (§10.5): Compact (Pixel 10 portrait), Medium
 * (Fold inner portrait) and Expanded (Pixel 10 landscape).
 *
 * The picker keeps one arrangement at all three — the grid widens rather than the screen changing
 * shape (§4.12.7) — so what these assert is that every control still does what it does at each
 * width: the chips, the rows' selection, and both halves of the dock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SitePickerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SitePickerAction>()

    // -----------------------------------------------------------------------
    // §4.12.7 across the three breakpoints
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `every section renders at Compact`() {
        setScreen(state())

        assertScreenIsRendered()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `every section renders at Medium`() {
        setScreen(state())

        assertScreenIsRendered()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `every section renders at Expanded`() {
        setScreen(state())

        assertScreenIsRendered()
    }

    // -----------------------------------------------------------------------
    // App bar
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the app bar states what the caller is dosing`() {
        setScreen(state())

        composeRule.onNodeWithText("For Tirzepatide · Subcut").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a picker opened knowing nothing about the dose has no supporting line`() {
        setScreen(state().copy(compoundName = null, route = null))

        composeRule.onNodeWithText("For Tirzepatide · Subcut").assertDoesNotExist()
        pickButton().assertExists()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the back arrow leaves the picker with nothing`() {
        setScreen(state())

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnCancelClick)
    }

    // -----------------------------------------------------------------------
    // §4.12.7 filter chips
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the chips raise their filter at Compact`() {
        setScreen(state())

        composeRule.onNodeWithText("Cooling").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnFilterClick(PickerFilter.COOLING))
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the chips raise their filter at Medium`() {
        setScreen(state())

        composeRule.onNodeWithText("Ready").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnFilterClick(PickerFilter.READY))
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the chips raise their filter at Expanded`() {
        setScreen(state())

        composeRule.onNodeWithText("All").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnFilterClick(PickerFilter.ALL))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a filter that leaves nothing says so rather than showing an empty list`() {
        setScreen(
            state().copy(
                filter = PickerFilter.COOLING,
                sites = emptyList<PickerSiteUi>().toImmutableList(),
            ),
        )

        composeRule.onNodeWithText("All sites · 0").assertExists()
        composeRule.onNodeWithText("No site is in that state right now.").assertExists()
    }

    // -----------------------------------------------------------------------
    // Rows + selection
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `a row raises the site it names at Compact`() {
        setScreen(state())

        scrollTo("Lateral Thigh · Left")
        composeRule.onNodeWithText("Lateral Thigh · Left").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnSiteClick(siteId = 2))
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `a row raises the site it names at Expanded`() {
        setScreen(state())

        scrollTo("Lateral Thigh · Left")
        composeRule.onNodeWithText("Lateral Thigh · Left").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnSiteClick(siteId = 2))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the selected row reads as selected and the others do not`() {
        setScreen(state().copy(selectedSiteId = 2))

        scrollTo("Abdomen · Upper left")
        composeRule.onNodeWithText("Lateral Thigh · Left").assertIsSelected()
        composeRule.onNodeWithText("Abdomen · Upper left").assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a cooling row carries its countdown and a rested one its last use`() {
        setScreen(state())

        scrollTo("Cool 2d")
        composeRule.onNodeWithText("Cool 2d").assertExists()
        composeRule.onNodeWithText("Last used 2 days ago").assertExists()
        composeRule.onNodeWithText("Never used").assertExists()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the suggested row leads the list and carries its Best pill`() {
        setScreen(state())

        composeRule.onNodeWithText("Suggested").assertIsDisplayed()
        composeRule.onNodeWithText("Best").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // §4.12.7 dock
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `Pick site waits for a selection at Compact`() {
        setScreen(state().copy(selectedSiteId = null))

        pickButton().assertIsNotEnabled()
        assertThat(actions).isEmpty()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `Pick site waits for a selection at Expanded`() {
        setScreen(state().copy(selectedSiteId = null))

        pickButton().assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the dock's two actions raise theirs at Compact`() {
        setScreen(state().copy(selectedSiteId = 2))

        pickButton().assertIsEnabled().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnPickClick, SitePickerAction.OnCancelClick)
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the dock's two actions raise theirs at Medium`() {
        setScreen(state().copy(selectedSiteId = 2))

        pickButton().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnPickClick, SitePickerAction.OnCancelClick)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the dock's two actions raise theirs at Expanded`() {
        setScreen(state().copy(selectedSiteId = 2))

        pickButton().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(actions).containsExactly(SitePickerAction.OnPickClick, SitePickerAction.OnCancelClick)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** What §4.12.7 puts on screen at every width: the bar, the chips, both sections, the dock. */
    private fun assertScreenIsRendered() {
        pickButton().assertExists()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Suggested").assertIsDisplayed()
        composeRule.onNodeWithText("All sites · 3").assertExists()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    /** The dock's button. "Pick site" is also the app bar's title (§4.12.7), so it is matched by its role. */
    private fun pickButton() = composeRule.onNode(hasText("Pick site") and hasClickAction())

    /** Rows live in a lazy grid, so what is off screen has to be scrolled to before it exists. */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    private fun setScreen(state: SitePickerState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                SitePickerScreen(state = state, onAction = { actions += it })
            }
        }
    }

    private fun state(): SitePickerState {
        val sites = listOf(
            PickerSiteUi(id = 1, name = "Abdomen · Lower right", daysCoolingRemaining = null, daysSinceLastUse = 14),
            PickerSiteUi(id = 2, name = "Lateral Thigh · Left", daysCoolingRemaining = null, daysSinceLastUse = null),
            PickerSiteUi(id = 3, name = "Abdomen · Upper left", daysCoolingRemaining = 2, daysSinceLastUse = 2),
        )
        return SitePickerState(
            compoundName = "Tirzepatide",
            route = PickerRoute.SUBCUTANEOUS,
            suggested = sites.first(),
            sites = sites.toImmutableList(),
            isLoading = false,
        )
    }

    private companion object {
        /** Pixel 10 portrait (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Pixel 10 landscape (§6.4.8). */
        const val EXPANDED = "w914dp-h411dp"
    }
}
