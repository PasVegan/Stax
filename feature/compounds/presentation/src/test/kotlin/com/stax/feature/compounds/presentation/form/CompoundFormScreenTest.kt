package com.stax.feature.compounds.presentation.form

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isGreaterThan
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.ContainerType
import com.stax.feature.compounds.presentation.R
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Create / Edit Compound form across the §6.4.8 breakpoint profiles (§10.5).
 *
 * What the breakpoint moves is the column count (§6.4.2): one column at Compact, two from Medium up,
 * with the opened-container section and the live stock preview moving to the right of the fields
 * rather than below them. The dock and the app bar span the form at every width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompoundFormScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<CompoundFormAction>()

    @Test
    @Config(qualifiers = COMPACT)
    fun `every section renders in one column at Compact`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_form_title_create)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_section_basics)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_section_stock)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_save)).assertIsDisplayed()
    }

    /**
     * At Compact the right-column sections are below the fold, so they are composed but off-screen;
     * at Medium they share the viewport with Basics, which is the whole point of the split (§6.4.2).
     */
    @Test
    @Config(qualifiers = COMPACT)
    fun `the opened-container section is out of sight below the fields at Compact`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_form_section_opened)).assertIsNotDisplayed()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the second column brings the opened container and the preview alongside at Medium`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_form_section_basics)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_section_opened)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_section_forecast)).assertIsDisplayed()
    }

    /**
     * §6.4.2 Expanded is "the same two-column layout but inputs wider": the split moves past the
     * midpoint so the fields get the extra room, which is what separates it from the even Medium
     * split. Measured from where the right column starts rather than from any one field's box.
     */
    @Test
    @Config(qualifiers = EXPANDED)
    fun `Expanded moves the column split past the midpoint so the fields get the extra width`() {
        setScreen(state())

        val midpoint = composeRule.onRoot().getUnclippedBoundsInRoot().width.value / 2
        val rightColumn = composeRule.onNodeWithText(string(R.string.compound_form_section_opened))
            .getUnclippedBoundsInRoot()

        assertThat(rightColumn.left.value).isGreaterThan(midpoint)
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `Medium splits the two columns evenly`() {
        setScreen(state())

        val midpoint = composeRule.onRoot().getUnclippedBoundsInRoot().width.value / 2
        val rightColumn = composeRule.onNodeWithText(string(R.string.compound_form_section_opened))
            .getUnclippedBoundsInRoot()

        assertThat(rightColumn.left.value).isBetween(midpoint - EVEN_SPLIT_TOLERANCE, midpoint + EVEN_SPLIT_TOLERANCE)
    }

    // -----------------------------------------------------------------------
    // §4.4b validation + §4.4.5 discard
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `a rejected field shows its reason under the row`() {
        setScreen(
            state(errors = persistentMapOf(CompoundFormField.NAME to CompoundFormError.NAME_REQUIRED)),
        )

        composeRule.onNodeWithText(string(R.string.compound_form_error_name_required)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the discard dialog offers Discard and Keep editing`() {
        setScreen(state(isDiscardDialogOpen = true))

        composeRule.onNodeWithText(string(R.string.compound_form_discard_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_keep_editing)).performClick()

        assertThat(actions).containsExactly(CompoundFormAction.Overlay.OnDiscardDismiss)
    }

    // -----------------------------------------------------------------------
    // §4.4.4 Edit case — the shrink dialog
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the shrink dialog spells out both amounts and offers all three answers`() {
        setScreen(state(shrinkPrompt = SHRINK))

        composeRule.onNodeWithText(string(R.string.compound_form_shrink_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_shrink_body, "3.2 mg", "2 mg"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_shrink_keep)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_shrink_cap)).assertIsDisplayed()
        // Cancel is also the dock's label, so the dialog's is the last one composed.
        composeRule.onAllNodesWithText(string(R.string.compound_form_cancel)).onLast().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Keep remaining files its decision`() {
        setScreen(state(shrinkPrompt = SHRINK))

        composeRule.onNodeWithText(string(R.string.compound_form_shrink_keep)).performClick()

        assertThat(actions).containsExactly(
            CompoundFormAction.OnContainerShrinkDecision(ContainerShrinkDecision.KEEP),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Cap to new size files its decision`() {
        setScreen(state(shrinkPrompt = SHRINK))

        composeRule.onNodeWithText(string(R.string.compound_form_shrink_cap)).performClick()

        assertThat(actions).containsExactly(
            CompoundFormAction.OnContainerShrinkDecision(ContainerShrinkDecision.CAP),
        )
    }

    /**
     * The dialog's Cancel and the dock's share a label, so the dialog's is the one on top — which is
     * also what the scrim answers, since dismissing the question is Cancel (§4.4.4).
     */
    @Test
    @Config(qualifiers = COMPACT)
    fun `Cancel files its decision`() {
        setScreen(state(shrinkPrompt = SHRINK))

        composeRule.onAllNodesWithText(string(R.string.compound_form_cancel)).onLast().performClick()

        assertThat(actions).containsExactly(
            CompoundFormAction.OnContainerShrinkDecision(ContainerShrinkDecision.CANCEL),
        )
    }

    /** The labels are too long to share a line of a dialog that is 280dp at its narrowest. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `the three answers stack one above the other`() {
        setScreen(state(shrinkPrompt = SHRINK))

        val keep = composeRule.onNodeWithText(string(R.string.compound_form_shrink_keep))
            .getUnclippedBoundsInRoot()
        val cap = composeRule.onNodeWithText(string(R.string.compound_form_shrink_cap))
            .getUnclippedBoundsInRoot()
        val cancel = composeRule.onAllNodesWithText(string(R.string.compound_form_cancel)).onLast()
            .getUnclippedBoundsInRoot()

        assertThat(cap.top.value).isGreaterThan(keep.bottom.value - STACK_TOLERANCE)
        assertThat(cancel.top.value).isGreaterThan(cap.bottom.value - STACK_TOLERANCE)
    }

    // -----------------------------------------------------------------------
    // §4.4.1 app bar + §4.4 dock
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the app bar close button asks to leave`() {
        setScreen(state())

        composeRule.onNodeWithContentDescription(string(R.string.compound_form_close)).performClick()

        assertThat(actions).containsExactly(CompoundFormAction.OnCancelClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Save files its action`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_form_save)).performClick()

        assertThat(actions).containsExactly(CompoundFormAction.OnSaveClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Edit mode titles the bar after the compound`() {
        setScreen(state(isEdit = true, editedCompoundName = "Semaglutide"))

        composeRule.onNodeWithText(string(R.string.compound_form_title_edit, "Semaglutide")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `onboarding step 2 titles the bar and offers Skip`() {
        setScreen(state(isOnboarding = true))

        composeRule.onNodeWithText(string(R.string.compound_form_title_onboarding)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_skip)).performClick()

        assertThat(actions).containsExactly(CompoundFormAction.OnSkipClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a form without Skip is not onboarding`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_form_skip)).assertDoesNotExist()
    }

    // -----------------------------------------------------------------------
    // §4.4.3 opened container section
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = MEDIUM)
    fun `an empty opened-container section offers to add one`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_form_opened_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_form_opened_add)).performClick()

        assertThat(actions).containsExactly(CompoundFormAction.Overlay.OnOpenedContainerClick)
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `an opened container shows what is left of it`() {
        setScreen(
            state(
                opened = OpenedContainerUi(
                    containerType = ContainerType.VIAL,
                    remaining = "3.2",
                    capacity = "5",
                    unit = "mg",
                    fillFraction = 0.64f,
                    openedDaysAgo = 12,
                ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.compound_form_opened_remaining, "3.2", "5", "mg"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.compound_form_opened_days_ago, 12)).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // §4.5.5 natural depletion
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the depletion prompt offers Open new and Leave closed`() {
        setScreen(state(isDepletionPromptOpen = true))

        composeRule.onNodeWithText(string(R.string.container_depleted_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.container_depleted_open_new)).performClick()
        composeRule.onNodeWithText(string(R.string.container_depleted_leave_closed)).performClick()

        assertThat(actions).containsExactly(
            CompoundFormAction.OnNaturalDepletionDecision(openNew = true),
            CompoundFormAction.OnNaturalDepletionDecision(openNew = false),
        )
    }

    // -----------------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------------

    private fun setScreen(state: CompoundFormState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                CompoundFormScreen(state = state, onAction = { actions += it })
            }
        }
    }

    private fun string(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    private fun plural(resId: Int, count: Int): String =
        composeRule.activity.resources.getQuantityString(resId, count, count)

    @Suppress("LongParameterList")
    private fun state(
        isEdit: Boolean = false,
        isOnboarding: Boolean = false,
        editedCompoundName: String = "",
        opened: OpenedContainerUi? = null,
        errors: kotlinx.collections.immutable.ImmutableMap<CompoundFormField, CompoundFormError> = persistentMapOf(),
        isDiscardDialogOpen: Boolean = false,
        shrinkPrompt: ContainerShrinkPromptUi? = null,
        isDepletionPromptOpen: Boolean = false,
    ) = CompoundFormState(
        draft = CompoundFormDraft(
            name = "Retatrutide",
            totalContainers = "6",
            amountPerContainer = "10",
            concentrationAmount = "5",
        ),
        isEdit = isEdit,
        isOnboarding = isOnboarding,
        editedCompoundName = editedCompoundName,
        opened = opened,
        forecast = StockForecastUi(totalStock = "60 mg", containers = 6, volumePerContainer = "2 ml"),
        errors = errors,
        isDiscardDialogOpen = isDiscardDialogOpen,
        shrinkPrompt = shrinkPrompt,
        isDepletionPromptOpen = isDepletionPromptOpen,
    )

    private companion object {
        /** §4.4.4 Edit case: a 5 mg vial with 3.2 mg left, edited down to 2 mg. */
        val SHRINK = ContainerShrinkPromptUi(remaining = "3.2 mg", newAmount = "2 mg")

        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait — Medium (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Pixel 10 landscape — Expanded (§6.4.8). */
        const val EXPANDED = "w914dp-h411dp"

        /** Rounding + the gutter between the columns; an even split is not a pixel-exact half. */
        const val EVEN_SPLIT_TOLERANCE = 24f

        /** Text buttons overlap their neighbour's touch target slightly; stacking is about the rows. */
        const val STACK_TOLERANCE = 8f
    }
}
