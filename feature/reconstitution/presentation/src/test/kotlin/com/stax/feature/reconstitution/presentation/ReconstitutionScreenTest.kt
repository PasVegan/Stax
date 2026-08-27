package com.stax.feature.reconstitution.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

        composeRule.onNodeWithText(string(R.string.reconstitution_container)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_diluent)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_desired_dose)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.reconstitution_display)).performScrollTo().assertIsDisplayed()
        // The typed values survive the reflow whole — a field too narrow for its own content scrolls
        // and shows "0.25" as "5". The dose is on a §4.6.3 chip and a §4.6.5 rung too, so it is the
        // editable one that is asked for here.
        composeRule.onNode(hasText("0.25") and hasSetTextAction()).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("2") and hasSetTextAction()).performScrollTo().assertIsDisplayed()
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

    /** §4.6.3: the chips sit with the syringe, outside the disclosure, at every width. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `states the dose in every unit beside the syringe`() {
        setContent(state())

        composeRule.onNodeWithContentDescription("0.25 ${string(R.string.reconstitution_unit_mg)}")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("0.10 ${string(R.string.reconstitution_unit_ml)}")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("10 ${string(R.string.reconstitution_units)}")
            .assertIsDisplayed()
    }

    /** §4.6.3: the insulin chip is the third one, and a regular barrel has no U-100 figure to put on it. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `drops the insulin chip on a regular syringe`() {
        setContent(state().copy(syringeSize = SyringeSize.ML3).recalculated())

        composeRule.onNodeWithContentDescription("0.10 ${string(R.string.reconstitution_unit_ml)}")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("10 ${string(R.string.reconstitution_units)}")
            .assertDoesNotExist()
    }

    /** M8-03's acceptance, on screen: five rungs, the typed dose lit, and a tap that types the rung. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `picks a dose from the ladder`() {
        setContent(state().copy(isCalculationExpanded = true))

        scrollLadderIntoView()

        composeRule.onNodeWithText(string(R.string.reconstitution_dose_ladder)).assertIsDisplayed()
        // The typed dose is on §4.6.4's field as well as its rung, so the rung is asked for by the
        // selection only §4.6.5 carries.
        composeRule.onNode(hasText("0.25") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText("0.5").assertIsDisplayed().performClick()

        assertThat(actions).containsExactly(ReconstitutionAction.OnDesiredDoseChange("0.5"))
    }

    /** §4.6: the ladder folds away with Mix — it types into the same Desired dose field. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `folds the ladder away with the rest of the calculation`() {
        setContent(state())

        composeRule.onNodeWithText(string(R.string.reconstitution_dose_ladder)).assertDoesNotExist()
    }

    /**
     * Five rungs do not fit a narrow window, so §4.6.5's row keeps their width and scrolls sideways
     * rather than squeezing the figures — the rungs that do fit are still tappable where they are.
     */
    @Test
    @Config(qualifiers = NARROW)
    fun `keeps the ladder tappable in a narrow window`() {
        setContent(state().copy(isCalculationExpanded = true))

        scrollLadderIntoView()

        composeRule.onNodeWithText("0.1").assertIsDisplayed().performClick()

        assertThat(actions).containsExactly(ReconstitutionAction.OnDesiredDoseChange("0.1"))
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

    /** §4.6.7: one tap is one write — the dock goes dead for as long as the write is in flight. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `disables the save dock while the write is in flight`() {
        setContent(state().copy(isSaving = true))

        composeRule.onNodeWithText(string(R.string.reconstitution_save)).assertIsNotEnabled()
        assertThat(actions).isEmpty()
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

        composeRule.onNodeWithText(string(R.string.reconstitution_display)).performScrollTo().performClick()

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

    /**
     * A rung's closest scrollable ancestor is §4.6.5's own sideways row, so `performScrollTo` on one
     * never moves the page. Scrolling to the section *below* the ladder is what brings it into view.
     */
    private fun scrollLadderIntoView() {
        composeRule.onNodeWithText(string(R.string.reconstitution_result)).performScrollTo()
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
