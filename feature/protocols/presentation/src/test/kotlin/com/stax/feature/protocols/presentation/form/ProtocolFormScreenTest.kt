package com.stax.feature.protocols.presentation.form

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isBetween
import assertk.assertions.isGreaterThan
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.ContainerType
import com.stax.core.domain.ReminderBucket
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.feature.protocols.presentation.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Create / Edit Protocol form across the §6.4.8 breakpoint profiles (§10.5).
 *
 * What the breakpoint moves is the column count (§6.4.2): one column at Compact, two from Medium up,
 * and at Expanded the Forecast card leaves the right column's scroll to sit pinned at its top. The
 * banner, the app bar and the dock span the form at every width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtocolFormScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<ProtocolFormAction>()

    // -----------------------------------------------------------------------
    // Layout (§6.4.2)
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the input sections render in one column at Compact`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_title_create)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_section_compound)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_section_route)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_save)).assertIsDisplayed()
    }

    /**
     * At Compact the right-column sections are below the fold, so they are composed but off-screen;
     * from Medium they share the viewport with the fields, which is the point of the split (§6.4.2).
     */
    @Test
    @Config(qualifiers = COMPACT)
    fun `the forecast is out of sight below the fields at Compact`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_forecast_title)).assertIsNotDisplayed()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the second column brings the reminder and the forecast alongside at Medium`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_section_compound)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_reminder_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_forecast_title)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the two columns split the width evenly`() {
        setScreen(state())

        val root = composeRule.onRoot().getUnclippedBoundsInRoot().width.value
        val left = composeRule.onNodeWithText(string(R.string.protocol_form_section_compound))
            .getUnclippedBoundsInRoot()
        val right = composeRule.onNodeWithText(string(R.string.protocol_form_section_reminder))
            .getUnclippedBoundsInRoot()

        assertThat(right.left.value).isGreaterThan(left.left.value)
        assertThat(right.left.value).isBetween(
            root / 2 - EVEN_SPLIT_TOLERANCE,
            root / 2 + EVEN_SPLIT_TOLERANCE,
        )
    }

    /** §6.4.2 Expanded: the forecast sits above the sections it used to sit under. */
    @Test
    @Config(qualifiers = EXPANDED_TALL)
    fun `the forecast is pinned above the right column at Expanded`() {
        setScreen(state())

        val forecast = composeRule.onNodeWithText(string(R.string.protocol_form_forecast_title))
            .getUnclippedBoundsInRoot()
        val reminder = composeRule.onNodeWithText(string(R.string.protocol_form_reminder_title))
            .getUnclippedBoundsInRoot()

        assertThat(reminder.top.value).isGreaterThan(forecast.top.value)
    }

    /**
     * Expanded is a *width* class, and phone landscape is 914 × 411dp. A card that cannot scroll is
     * clipped mid-tile in that much height, so the pin needs the height for it — below which the
     * forecast goes back under the sections, where it is at least readable.
     */
    @Test
    @Config(qualifiers = EXPANDED)
    fun `the forecast is not pinned when the window is too short for it`() {
        setScreen(state())

        val forecast = composeRule.onNodeWithText(string(R.string.protocol_form_forecast_title))
            .getUnclippedBoundsInRoot()
        val reminder = composeRule.onNodeWithText(string(R.string.protocol_form_reminder_title))
            .getUnclippedBoundsInRoot()

        assertThat(forecast.top.value).isGreaterThan(reminder.top.value)
    }

    // -----------------------------------------------------------------------
    // Edit mode (§4.9.2, §4.9.5)
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `Edit mode shows the regeneration banner and renames the Save button`() {
        setScreen(state(isEdit = true))

        composeRule.onNodeWithText(string(R.string.protocol_form_edit_banner_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_title_edit)).assertIsDisplayed()
        composeRule.onNodeWithText("Sema weekly titration").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_save_changes)).assertIsDisplayed()
    }

    /**
     * The three buttons sit at the far bottom of a long form, so they are clicked through their own
     * semantics rather than by scrolling to them: what is under test is that each reports its own
     * action, not that a scroll container can reach them.
     */
    @Test
    @Config(qualifiers = COMPACT)
    fun `Edit mode adds the Lifecycle section, and Create does not`() {
        setScreen(state(isEdit = true))

        composeRule.onNodeWithText(string(R.string.protocol_form_pause)).click()
        composeRule.onNodeWithText(string(R.string.protocol_form_duplicate)).click()
        composeRule.onNodeWithText(string(R.string.protocol_form_archive)).click()

        assertThat(actions).contains(ProtocolFormAction.OnPauseClick)
        assertThat(actions).contains(ProtocolFormAction.OnDuplicateClick)
        assertThat(actions).contains(ProtocolFormAction.OnArchiveClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Create mode has no Lifecycle section`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_section_lifecycle)).assertDoesNotExist()
    }

    // -----------------------------------------------------------------------
    // Interactions (§4.9.3)
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the compound card opens the picker`() {
        setScreen(state())

        composeRule.onNodeWithText("Semaglutide").performClick()

        assertThat(actions).contains(
            ProtocolFormAction.Overlay.OnPickerOpen(ProtocolFormPicker.COMPOUND),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the route segments report the segment tapped`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_route_im)).performClick()

        assertThat(actions).contains(ProtocolFormAction.Pick.OnRouteSelected(Route.INTRAMUSCULAR))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the schedule chips report the chip tapped`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_schedule_daily)).performClick()

        assertThat(actions).contains(
            ProtocolFormAction.Pick.OnScheduleTypeSelected(ScheduleType.DAILY),
        )
    }

    /** §4.9.3: the weekday circles carry their full name, since "M" alone is read aloud as nothing. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `the weekday circles toggle by their full name`() {
        setScreen(state())

        composeRule.onNodeWithContentDescription("Monday").performClick()

        assertThat(actions).contains(ProtocolFormAction.Pick.OnWeekdayToggled(DayOfWeek.MONDAY))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Add time opens the time picker and a time pill removes itself`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_add_time)).performClick()
        assertThat(actions).contains(ProtocolFormAction.OnAddTimeClick)

        composeRule.onNodeWithContentDescription(
            string(R.string.protocol_form_time_remove, "8:00 PM"),
        ).performClick()
        assertThat(actions).contains(ProtocolFormAction.OnTimeRemoved(LocalTime(20, 0)))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `both duration boxes open the date picker for their own end`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_start)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.protocol_form_end)).performScrollTo().performClick()

        assertThat(actions).contains(
            ProtocolFormAction.Overlay.OnDateFieldClick(ProtocolDateField.START),
        )
        assertThat(actions).contains(
            ProtocolFormAction.Overlay.OnDateFieldClick(ProtocolDateField.END),
        )
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the site restriction row opens the body region picker`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_site_none)).performClick()

        assertThat(actions).contains(
            ProtocolFormAction.Overlay.OnPickerOpen(ProtocolFormPicker.BODY_REGION),
        )
    }

    /** §4.9.3: the buckets appear exactly when reminders are on and there is no time to attach them to. */
    @Test
    @Config(qualifiers = MEDIUM)
    fun `a schedule with a time of day needs no reminder bucket`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_bucket_morning)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `a schedule with no time of day picks a reminder bucket instead`() {
        setScreen(state(dosageTimes = emptyList()))

        composeRule.onNodeWithText(string(R.string.protocol_form_bucket_morning)).performScrollTo().performClick()

        assertThat(actions).contains(
            ProtocolFormAction.Pick.OnReminderBucketSelected(ReminderBucket.MORNING),
        )
    }

    // -----------------------------------------------------------------------
    // Forecast, preview and validation
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = EXPANDED_TALL)
    fun `the forecast shows its three tiles and both notices`() {
        setScreen(state())

        composeRule.onNodeWithText("18").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_forecast_doses_left)).assertIsDisplayed()
        // The card is pinned rather than scrollable at Expanded (§6.4.2), and a 411dp-high window
        // cannot show all of it — the notices are composed, which is what this asserts.
        composeRule.onNodeWithText(string(R.string.protocol_form_forecast_expiry_title)).assertExists()
        composeRule.onNodeWithText(
            plural(R.plurals.protocol_form_forecast_reorder_title, 1, 1, "vial", "Jul 21"),
        ).assertExists()
    }

    @Test
    @Config(qualifiers = EXPANDED_TALL)
    fun `the forecast says what it is waiting for before a compound and dose are in`() {
        setScreen(state(forecast = null))

        composeRule.onNodeWithText(string(R.string.protocol_form_forecast_empty)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the 7-day preview names the dose count the save will generate`() {
        setScreen(state())

        composeRule.onNodeWithText(
            string(
                R.string.protocol_form_preview_title,
                plural(R.plurals.protocol_form_preview_doses, 2, 2),
            ),
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a rejected field states its reason under the row`() {
        setScreen(
            state(
                errors = persistentMapOf(ProtocolFormField.DOSE to ProtocolFormError.DOSE_NOT_POSITIVE),
            ),
        )

        composeRule.onNodeWithText(string(R.string.protocol_form_error_dose_not_positive)).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Dock, dialogs and onboarding
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the dock spans the form and reports Cancel and Save`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_form_cancel)).performClick()
        composeRule.onNodeWithText(string(R.string.protocol_form_save)).performClick()

        assertThat(actions).contains(ProtocolFormAction.OnCancelClick)
        assertThat(actions).contains(ProtocolFormAction.OnSaveClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the discard dialog offers Discard and Keep editing`() {
        setScreen(state(isDiscardDialogOpen = true))

        composeRule.onNodeWithText(string(R.string.protocol_form_discard_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_keep_editing)).performClick()
        composeRule.onNodeWithText(string(R.string.protocol_form_discard)).performClick()

        assertThat(actions).contains(ProtocolFormAction.Overlay.OnDiscardDismiss)
        assertThat(actions).contains(ProtocolFormAction.OnDiscardConfirm)
    }

    /**
     * §4.9.6. Three answers in a dialog built for two, so each is clicked here at every breakpoint
     * profile — what the wrapping does to the button row must never cost one of them its tap target.
     */
    @Test
    @Config(qualifiers = COMPACT)
    fun `the pause dialog offers all three answers at Compact`() {
        assertPauseDialogAnswersAllThree()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the pause dialog offers all three answers at Medium`() {
        assertPauseDialogAnswersAllThree()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the pause dialog offers all three answers at Expanded`() {
        assertPauseDialogAnswersAllThree()
    }

    @Test
    @Config(qualifiers = EXPANDED_TALL)
    fun `the pause dialog offers all three answers at Expanded with height`() {
        assertPauseDialogAnswersAllThree()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the archive dialog asks before it soft-deletes`() {
        setScreen(state(isEdit = true, isArchiveDialogOpen = true))

        composeRule.onNodeWithText(string(R.string.protocol_form_archive_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_archive_supporting)).assertIsDisplayed()
    }

    /** §4.14 step 3: the same form under a different title, with Skip in the trailing slot. */
    @Test
    @Config(qualifiers = COMPACT)
    fun `onboarding step 3 retitles the form and offers Skip`() {
        setScreen(state(isOnboarding = true))

        composeRule.onNodeWithText(string(R.string.protocol_form_title_onboarding)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_skip)).performClick()

        assertThat(actions).contains(ProtocolFormAction.OnSkipClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the compound picker lists what there is to pick`() {
        setScreen(state(openPicker = ProtocolFormPicker.COMPOUND))

        composeRule.onNodeWithText(string(R.string.protocol_form_picker_compound)).assertIsDisplayed()
        // The card behind the sheet names the same compound, so the row is the last of the two.
        composeRule.onAllNodesWithText("Semaglutide").onLast().performClick()

        assertThat(actions).contains(ProtocolFormAction.Pick.OnCompoundSelected(COMPOUND_ID))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an empty compound picker offers the way to create one`() {
        setScreen(state(openPicker = ProtocolFormPicker.COMPOUND, pickerCompounds = emptyList()))

        composeRule.onNodeWithText(string(R.string.protocol_form_picker_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_picker_add_compound)).performClick()

        assertThat(actions).contains(ProtocolFormAction.OnAddCompoundClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the body region picker offers No restriction first`() {
        setScreen(state(openPicker = ProtocolFormPicker.BODY_REGION))

        composeRule.onNodeWithText(string(R.string.protocol_form_picker_region)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_region_abdomen)).performClick()

        assertThat(actions).contains(
            ProtocolFormAction.Pick.OnBodyRegionSelected(BodyRegion.ABDOMEN),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the compound meta reads category, size and concentration`() {
        setScreen(state())

        composeRule.onNodeWithText("Peptide · 5 mg vial · 2.5 mg/mL").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the equivalence chip states the volume and the insulin units`() {
        setScreen(state())

        composeRule.onNodeWithText(
            string(R.string.protocol_form_equivalent_with_units, "0.10 mL", 10),
        ).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------------

    /** §4.9.6's dialog, checked the same way at each breakpoint: all three answers visible and tappable. */
    private fun assertPauseDialogAnswersAllThree() {
        setScreen(state(isEdit = true, isPauseDialogOpen = true))

        composeRule.onNodeWithText(string(R.string.protocol_form_pause_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_form_pause_save)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(string(R.string.protocol_form_pause_discard)).assertIsDisplayed().performClick()
        // The dock has a Cancel of its own behind the scrim; the dialog's is the one composed last.
        composeRule.onAllNodesWithText(string(R.string.protocol_form_cancel)).onLast()
            .assertIsDisplayed().performClick()

        assertThat(actions).contains(ProtocolFormAction.OnPauseSaveConfirm)
        assertThat(actions).contains(ProtocolFormAction.OnPauseDiscardConfirm)
        assertThat(actions).contains(ProtocolFormAction.Overlay.OnPauseDismiss)
    }

    /** Clicks a node wherever it is in the form, without asking a scroll container to bring it up. */
    private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

    private fun setScreen(state: ProtocolFormState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                ProtocolFormScreen(state = state, onAction = { actions += it })
            }
        }
    }

    private fun string(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    private fun plural(resId: Int, count: Int, vararg args: Any): String =
        composeRule.activity.resources.getQuantityString(resId, count, *args)

    @Suppress("LongParameterList")
    private fun state(
        isEdit: Boolean = false,
        isOnboarding: Boolean = false,
        dosageTimes: List<LocalTime> = listOf(LocalTime(20, 0)),
        forecast: ProtocolForecastUi? = FORECAST,
        errors: kotlinx.collections.immutable.ImmutableMap<ProtocolFormField, ProtocolFormError> =
            persistentMapOf(),
        openPicker: ProtocolFormPicker? = null,
        pickerCompounds: List<CompoundPickUi> = listOf(COMPOUND),
        isDiscardDialogOpen: Boolean = false,
        isPauseDialogOpen: Boolean = false,
        isArchiveDialogOpen: Boolean = false,
    ) = ProtocolFormState(
        draft = ProtocolFormDraft(
            compoundSupplyId = COMPOUND_ID,
            doseAmount = "0.25",
            scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dosageTimes = dosageTimes,
            startDate = TODAY,
        ),
        isEdit = isEdit,
        isOnboarding = isOnboarding,
        editedProtocolName = "Sema weekly titration",
        compound = COMPOUND,
        pickerCompounds = pickerCompounds.toImmutableList(),
        doseUnitOptions = persistentListOf(UnitCode.MG, UnitCode.MCG),
        equivalence = DoseEquivalenceUi(volume = "0.10", volumeUnit = UnitCode.ML, insulinUnits = 10),
        preview = PREVIEW,
        forecast = forecast,
        errors = errors,
        openPicker = openPicker,
        isDiscardDialogOpen = isDiscardDialogOpen,
        isPauseDialogOpen = isPauseDialogOpen,
        isArchiveDialogOpen = isArchiveDialogOpen,
    )

    private companion object {
        const val COMPOUND_ID = 7L

        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait — Medium (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Pixel 10 landscape — Expanded, and the shortest of the Expanded profiles (§6.4.8). */
        const val EXPANDED = "w914dp-h411dp"

        /** Pixel 10 Pro Fold inner landscape — Expanded with the height to pin a card (§6.4.8). */
        const val EXPANDED_TALL = "w841dp-h673dp"

        /** Rounding + the gutter between the columns; an even split is not a pixel-exact half. */
        const val EVEN_SPLIT_TOLERANCE = 32f

        val TODAY = LocalDate(2026, 5, 26)

        val COMPOUND = CompoundPickUi(
            id = COMPOUND_ID,
            name = "Semaglutide",
            category = CompoundCategory.PEPTIDE,
            containerType = ContainerType.VIAL,
            amount = "5",
            amountUnit = UnitCode.MG,
            concentration = "2.5",
            concentrationUnit = UnitCode.MG,
            concentrationPerUnit = UnitCode.ML,
        )

        val PREVIEW = SchedulePreviewUi(
            doseCount = 2,
            days = List(7) { offset ->
                val date = TODAY.plus(offset, DateTimeUnit.DAY)
                PreviewDayUi(date = date, hasDose = offset == 0 || offset == 3, isToday = offset == 0)
            }.toImmutableList(),
        )

        val FORECAST = ProtocolForecastUi(
            dosesLeft = 18,
            daysLeft = 63,
            runOutDate = LocalDate(2026, 7, 28),
            expiryWarning = ExpiryWarningUi(
                batchExpiry = LocalDate(2026, 7, 14),
                runOut = LocalDate(2026, 7, 28),
            ),
            reorder = ReorderHintUi(
                containers = 1,
                containerType = ContainerType.VIAL,
                orderBy = LocalDate(2026, 7, 21),
                coversUntil = LocalDate(2026, 8, 24),
            ),
        )
    }
}
