package com.stax.feature.reconstitution.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.UnitCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Reconstitution Helper across the §6.4.8 breakpoint profiles (§10.5), plus the narrow window
 * §6.4.2's Medium columns are no wider than.
 *
 * These render the whole screen at each width, which is what makes them worth having beyond the
 * ViewModel's tests: §4.6.4's tiles reflow on the width they are given, and the first version of that
 * reflow measured itself with a `BoxWithConstraints` inside a row sized to its tallest tile — a
 * combination that cannot be measured and took the screen down on every device narrow enough to hit
 * it. Rendering at [NARROW] is the whole of that regression test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReconstitutionScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<ReconstitutionAction>()

    @Test
    @Config(qualifiers = NARROW)
    fun `renders the mix and result sections in a narrow window`() {
        setContent(state().copy(isCalculationExpanded = true))

        composeRule.onNodeWithText(string(R.string.reconstitution_container)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_diluent)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_desired_dose)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_display)).assertIsDisplayed()
        // The typed values survive the reflow whole — a field too narrow for its own content scrolls
        // and shows "0.25" as "5".
        composeRule.onNodeWithText("0.25").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `folds the calculation away at Compact until asked`() {
        setContent(state())

        composeRule.onNodeWithText(string(R.string.reconstitution_mix)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.reconstitution_show_calculation)).performClick()

        assertThat(actions).containsExactly(ReconstitutionAction.OnToggleCalculation)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `shows the mix once the calculation is unfolded at Compact`() {
        setContent(state().copy(isCalculationExpanded = true))

        composeRule.onNodeWithText(string(R.string.reconstitution_hide_calculation)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_mix)).assertIsDisplayed()
    }

    /** §6.4.2: from Medium up the room is there, so there is nothing for a disclosure row to hide. */
    @Test
    @Config(qualifiers = MEDIUM)
    fun `keeps the mix open at Medium with no disclosure row`() {
        setContent(state())

        composeRule.onNodeWithText(string(R.string.reconstitution_show_calculation)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.reconstitution_mix)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `keeps the mix open at Expanded with no disclosure row`() {
        setContent(state())

        composeRule.onNodeWithText(string(R.string.reconstitution_show_calculation)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.reconstitution_desired_dose)).assertIsDisplayed()
    }

    /** §4.6.2 and §4.6.6 are outside the disclosure — they are on screen at every width. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `shows the drawn dose and the result whether or not the calculation is unfolded`() {
        setContent(state())

        composeRule.onNodeWithText(string(R.string.reconstitution_draw_to)).assertIsDisplayed()
        composeRule.onNodeWithText("10").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_concentration)).assertIsDisplayed()
        composeRule.onNodeWithText("20").assertIsDisplayed()
    }

    /** §4.6.7: nothing to set before the mix produces a concentration. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `disables the save dock until there is a concentration`() {
        setContent(ReconstitutionState())

        composeRule.onNodeWithText(string(R.string.reconstitution_save)).assertIsNotEnabled()
        assertThat(actions).isEmpty()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `saves once there is a concentration`() {
        setContent(state())

        composeRule.onNodeWithText(string(R.string.reconstitution_save)).assertIsEnabled().performClick()

        assertThat(actions).containsExactly(ReconstitutionAction.OnSaveClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `closes from the app bar`() {
        setContent(state())

        composeRule.onNodeWithContentDescription(string(R.string.reconstitution_close)).performClick()

        assertThat(actions).containsExactly(ReconstitutionAction.OnCloseClick)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun `opens the display picker from its tile`() {
        setContent(state().copy(isCalculationExpanded = true))

        composeRule.onNodeWithText(string(R.string.reconstitution_display)).performClick()

        assertThat(actions).containsExactly(
            ReconstitutionAction.OnPickerClick(ReconstitutionPicker.DISPLAY),
        )
    }

    /** §4.6.2's acceptance: the size badge is a button, and one tap moves to the next syringe. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `cycles the syringe size from the badge`() {
        setContent(state())

        composeRule.onNodeWithText(insulinBadge()).assertIsDisplayed().performClick()

        assertThat(actions).containsExactly(ReconstitutionAction.OnCycleSyringeSize)
    }

    /** The barrel is a `Canvas` with no text nodes, so the description is all a screen reader gets. */
    @Test
    @Config(qualifiers = NARROW)
    fun `describes the drawn syringe`() {
        setContent(state())

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(
                    R.string.reconstitution_syringe_description,
                    insulinBadge(),
                    "10",
                    string(R.string.reconstitution_units),
                ),
            )
            .assertIsDisplayed()
    }

    /** §4.6.2: a regular syringe's badge drops the U-scale — there is none to state. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `labels a regular syringe by its capacity alone`() {
        setContent(state().copy(syringeSize = SyringeSize.ML3))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.reconstitution_syringe_regular, "3"))
            .assertIsDisplayed()
    }

    private fun insulinBadge(): String =
        composeRule.activity.getString(R.string.reconstitution_syringe_insulin, 100, "1")

    private fun setContent(state: ReconstitutionState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                ReconstitutionScreen(state = state, onAction = { actions += it })
            }
        }
    }

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    private fun state() = ReconstitutionState(
        compoundName = "Semaglutide",
        containerAmount = "5",
        containerUnit = UnitCode.MG,
        isContainerEditable = false,
        diluent = "2",
        desiredDose = "0.25",
        doseUnit = UnitCode.MG,
    ).recalculated()

    private companion object {
        /** Narrower than a Compact phone — a small device, a split screen, a §6.4.2 column. */
        const val NARROW = "w301dp-h772dp"

        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /**
         * What a Medium window leaves this screen: Pixel 10 Pro Fold inner portrait (`673dp`, §6.4.8)
         * less the navigation rail. The disclosure row is decided on the pane, not the window.
         */
        const val MEDIUM = "w593dp-h841dp"

        const val EXPANDED = "w1280dp-h800dp"
    }
}
