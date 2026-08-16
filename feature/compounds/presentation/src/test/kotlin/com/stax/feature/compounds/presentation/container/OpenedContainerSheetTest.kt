package com.stax.feature.compounds.presentation.container

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.ContainerType
import com.stax.core.domain.UnitCode
import com.stax.feature.compounds.presentation.R
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The §4.5 opened-container sheet across the §6.4.8 breakpoint profiles (§10.5).
 *
 * Two things change with the width and nothing else does (§6.4.2): how wide the sheet is, and which
 * edge it is anchored to. The fields, the header and the actions are the same at every width, so the
 * tests that read them run at one breakpoint and the geometry gets its own three.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenedContainerSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<OpenedContainerSheetAction>()

    // -----------------------------------------------------------------------
    // §4.5.2 / §4.5.3 content
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the Edit variant names the compound and shows all three fields`() {
        setSheet(state())

        composeRule.onNodeWithText(string(R.string.container_sheet_title_edit, "Vial")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.container_sheet_subtitle, "Semaglutide", "5", "mg", "Vial"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.container_sheet_opened_date)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.container_sheet_remaining)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.container_sheet_expiry)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the opened date carries how long ago it was`() {
        setSheet(state())

        composeRule.onNodeWithText(plural(R.plurals.container_sheet_days_ago, 12)).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.container_sheet_expiry_auto, 28)).assertIsDisplayed()
    }

    /** §4.5.3: an expiry the user set is not "auto" any more, and stops saying so. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `an expiry set by hand drops the auto marker`() {
        setSheet(state().copy(isExpiryAuto = false))

        composeRule.onNodeWithText(plural(R.plurals.container_sheet_expiry_manual, 28)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a rejected remaining amount shows its reason`() {
        setSheet(state().copy(hasRemainingError = true))

        composeRule.onNodeWithText(string(R.string.container_sheet_remaining_error)).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // §4.5.4 actions
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the Edit variant offers Delete beside Save`() {
        setSheet(state())

        composeRule.onNodeWithText(string(R.string.container_sheet_delete)).performClick()
        composeRule.onNodeWithText(string(R.string.container_sheet_save)).performClick()

        assertThat(actions).containsExactly(
            OpenedContainerSheetAction.OnDeleteClick,
            OpenedContainerSheetAction.OnSaveClick,
        )
    }

    /** §4.5: Create Already Opened is the same sheet "minus Delete". */
    @Test
    @Config(qualifiers = COMPACT)
    fun `the Create variant is the same sheet without Delete`() {
        setSheet(state().copy(isEdit = false))

        composeRule.onNodeWithText(string(R.string.container_sheet_title_add, "Vial")).assertIsDisplayed()
        // Existence, not display: a bottom sheet opens partially expanded, so the action row may
        // still be below the fold on a phone until the user drags it up.
        composeRule.onNodeWithText(string(R.string.container_sheet_save)).assertExists()
        composeRule.onNodeWithText(string(R.string.container_sheet_delete)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `both date rows open their own picker`() {
        setSheet(state())

        composeRule.onNodeWithText(string(R.string.container_sheet_opened_date)).performClick()
        composeRule.onNodeWithText(string(R.string.container_sheet_expiry)).performClick()

        assertThat(actions).containsExactly(
            OpenedContainerSheetAction.OnDateFieldClick(OpenedContainerDateField.OPENED),
            OpenedContainerSheetAction.OnDateFieldClick(OpenedContainerDateField.EXPIRY),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the close icon dismisses the sheet`() {
        setSheet(state())

        composeRule.onNodeWithContentDescription(string(R.string.container_sheet_close)).performClick()

        assertThat(actions).containsExactly(OpenedContainerSheetAction.OnDismiss)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a saving sheet accepts nothing`() {
        setSheet(state().copy(isSaving = true))

        composeRule.onNodeWithText(string(R.string.container_sheet_save)).performClick()
        composeRule.onNodeWithText(string(R.string.container_sheet_delete)).performClick()

        assertThat(actions).isEmpty()
    }

    // -----------------------------------------------------------------------
    // §6.4.2 adaptive width
    // -----------------------------------------------------------------------

    /**
     * Compact: the sheet takes the window, so its content starts at the sheet's own gutter and
     * nowhere further in. The two tests below are the contrast — the same content, indented by the
     * margin each wider layout puts around the sheet.
     */
    @Test
    @Config(qualifiers = COMPACT)
    fun `the sheet is full-width at Compact`() {
        setSheet(state())

        assertThat(titleBounds().left.value).isBetween(SHEET_GUTTER - TOLERANCE, SHEET_GUTTER + TOLERANCE)
    }

    /** Medium: clamped to `560dp` and centred, so there is an equal margin on both sides. */
    @Test
    @Config(qualifiers = MEDIUM)
    fun `the sheet is clamped and centred at Medium`() {
        setSheet(state())

        val expected = (MEDIUM_WIDTH - MEDIUM_MAX_WIDTH) / 2 + SHEET_GUTTER
        val left = titleBounds().left.value

        assertThat(left).isBetween(expected - TOLERANCE, expected + TOLERANCE)
    }

    /** Expanded: a `420dp` side sheet on the end edge, so everything in it sits past the midpoint. */
    @Test
    @Config(qualifiers = EXPANDED)
    fun `the sheet moves to the end edge at Expanded`() {
        setSheet(state())

        val expected = EXPANDED_WIDTH - SIDE_SHEET_WIDTH + SHEET_GUTTER
        val left = titleBounds().left.value

        assertThat(left).isGreaterThan(EXPANDED_WIDTH / 2)
        assertThat(left).isBetween(expected - TOLERANCE, expected + TOLERANCE)
    }

    // -----------------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------------

    private fun setSheet(state: OpenedContainerSheetState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                OpenedContainerSheet(state = state, onAction = { actions += it })
            }
        }
    }

    /** The sheet is its own window, so the composition has two roots and the sheet's is the second. */
    private fun titleBounds() = composeRule
        .onNodeWithText(string(R.string.container_sheet_title_edit, "Vial"))
        .getUnclippedBoundsInRoot()

    private fun string(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    private fun plural(resId: Int, count: Int): String =
        composeRule.activity.resources.getQuantityString(resId, count, count)

    private fun state() = OpenedContainerSheetState(
        isEdit = true,
        containerType = ContainerType.VIAL,
        compoundName = "Semaglutide",
        containerAmount = "5",
        unit = UnitCode.MG,
        openedDate = LocalDate.parse("2026-05-14"),
        openedDaysAgo = 12,
        remaining = "3.2",
        expiryDate = LocalDate.parse("2026-06-11"),
        expiryDaysAfterOpening = 28,
    )

    private companion object {
        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait — Medium (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Pixel 10 landscape — Expanded (§6.4.8). */
        const val EXPANDED = "w914dp-h411dp"

        /** The widths those three qualifiers give the window (§6.4.8). */
        const val MEDIUM_WIDTH = 673f
        const val EXPANDED_WIDTH = 914f

        /** The sheet's own horizontal padding (§4.5.1). */
        const val SHEET_GUTTER = 16f

        /** §6.4.2 Medium / Expanded widths. */
        const val MEDIUM_MAX_WIDTH = 560f
        const val SIDE_SHEET_WIDTH = 420f

        /** Rounding and the drag handle's own insets; the geometry under test is tens of dp apart. */
        const val TOLERANCE = 8f
    }
}
