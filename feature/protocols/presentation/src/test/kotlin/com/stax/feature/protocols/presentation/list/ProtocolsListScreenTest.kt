package com.stax.feature.protocols.presentation.list

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.feature.protocols.presentation.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Protocols list screen across the §6.4.8 breakpoint profiles (§10.5): Compact (Pixel 10 portrait),
 * Medium (Fold inner portrait) and Expanded (Pixel 10 landscape).
 *
 * The screen is the list pane of the Protocols list-detail Scene (§6.4.2), so it keeps the same
 * one-card-per-line layout at every width — what the breakpoint changes is how much room the card's
 * own chips have, which is why the chip row wraps rather than scrolls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtocolsListScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<ProtocolsListAction>()

    // -----------------------------------------------------------------------
    // §4.7 across the three breakpoints
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the list renders its app bar, chips and cards at Compact`() {
        setScreen(state())

        assertListIsRendered()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the list renders its app bar, chips and cards at Medium`() {
        setScreen(state())

        assertListIsRendered()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the list renders its app bar, chips and cards at Expanded`() {
        setScreen(state())

        assertListIsRendered()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the New protocol FAB floats bottom-end with its label at Compact`() {
        setScreen(state())

        assertFabIsExtendedAtBottomEnd()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the New protocol FAB floats bottom-end with its label at Medium`() {
        setScreen(state())

        assertFabIsExtendedAtBottomEnd()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the New protocol FAB floats bottom-end with its label at Expanded`() {
        setScreen(state())

        assertFabIsExtendedAtBottomEnd()
    }

    // -----------------------------------------------------------------------
    // §4.7.2 filter chips
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `all four chips are offered and only the active one reads as selected`() {
        setScreen(state())

        chip(R.string.protocols_filter_active).assertIsSelected()
        chip(R.string.protocols_filter_paused).assertIsNotSelected()
        chip(R.string.protocols_filter_completed).assertIsNotSelected()
        chip(R.string.protocols_filter_archived).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping a chip files its filter action`() {
        setScreen(state())

        chip(R.string.protocols_filter_archived).performClick()

        assertThat(actions).containsExactly(ProtocolsListAction.OnFilterClick(ProtocolFilter.ARCHIVED))
    }

    // -----------------------------------------------------------------------
    // §4.7.3 card contents
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `a titrating card shows its pill, both chips and the titration bar`() {
        setScreen(state(items = listOf(SEMA)))

        // The chip row says "Active" too, so the pill is asserted on the card that carries it.
        card("Sema weekly titration", string(R.string.protocols_pill_active)).assertIsDisplayed()
        // "Semaglutide · 0.25 → 1 mg" — the meta line writes the range, not the flat dose (§4.7.3).
        composeRule.onNodeWithText("Semaglutide · 0.25 → 1 mg").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocols_titration)).assertIsDisplayed()
        composeRule.onNodeWithText("0.25 / 1 mg").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a flat card writes its dose and route and shows no titration bar`() {
        setScreen(state(items = listOf(TEST_CYP)))

        composeRule.onNodeWithText("Test Cyp · 100 mg " + string(R.string.protocol_form_route_im))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocols_titration)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an in-break card carries the In break pill and counts the days left`() {
        setScreen(state(items = listOf(BPC)))

        card("BPC-157 healing cycle", string(R.string.protocols_pill_in_break)).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.protocols_next_dose_break_in_days, BREAK_DAYS_LEFT))
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a protocol with nothing pending says so rather than showing an empty chip`() {
        setScreen(state(items = listOf(TEST_CYP)))

        composeRule.onNodeWithText(string(R.string.protocols_next_dose_none)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping a card opens its detail`() {
        setScreen(state())

        composeRule.onNodeWithText("Sema weekly titration").performClick()

        assertThat(actions).containsExactly(ProtocolsListAction.OnProtocolClick(protocolId = 1))
    }

    // -----------------------------------------------------------------------
    // §4.7.5 FAB / §7 empty states
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping the FAB opens Create Protocol`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocols_new)).performClick()

        assertThat(actions).containsExactly(ProtocolsListAction.OnCreateProtocolClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an app with no protocols at all gets the hero and its CTA`() {
        setScreen(state(items = emptyList(), hasAnyProtocol = false))

        composeRule.onNodeWithText(string(R.string.protocols_empty_title)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an empty tab says only that the tab is empty`() {
        setScreen(state(items = emptyList(), hasAnyProtocol = true, filter = ProtocolFilter.COMPLETED))

        composeRule.onNodeWithText(string(R.string.protocols_empty_filtered_completed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocols_empty_title)).assertDoesNotExist()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun assertListIsRendered() {
        composeRule.onNodeWithText(string(R.string.protocols_title)).assertIsDisplayed()
        chip(R.string.protocols_filter_active).assertIsDisplayed()
        chip(R.string.protocols_filter_archived).assertIsDisplayed()
        composeRule.onNodeWithText("Sema weekly titration").assertIsDisplayed()
        composeRule.onNodeWithText("Testosterone Cyp").assertIsDisplayed()
        assertThat(actions).isEmpty()
    }

    /**
     * §6.4.6: the FAB floats at the pane's bottom-end at every width, and it keeps the label that
     * names it — the rail's own FAB slot belongs to the chrome, not to a screen (see `AdaptiveFab`).
     */
    private fun assertFabIsExtendedAtBottomEnd() {
        val fab = composeRule.onNodeWithText(string(R.string.protocols_new))
        fab.assertIsDisplayed()
        val bounds = fab.getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertThat(bounds.left.value).isGreaterThan(root.width.value / 2)
        assertThat(bounds.top.value).isGreaterThan(root.height.value / 2)
    }

    private fun setScreen(state: ProtocolsListState) {
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                ProtocolsListScreen(state = state, onAction = { actions += it })
            }
        }
    }

    /**
     * A filter chip, told apart from the identically-labelled status pill by being selectable —
     * §4.7.2's chips and §4.7.3's pills deliberately share three of their four words.
     */
    private fun chip(labelRes: Int) = composeRule.onNode(hasText(string(labelRes)) and isSelectable())

    /** A card, found by its name and asserted on some other text it merges — its status pill. */
    private fun card(name: String, carries: String) = composeRule.onNode(hasText(name) and hasText(carries))

    private fun string(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    private fun plural(resId: Int, count: Int): String =
        composeRule.activity.resources.getQuantityString(resId, count, count)

    private fun state(
        items: List<ProtocolListItemUi> = listOf(SEMA, TEST_CYP),
        filter: ProtocolFilter = ProtocolFilter.ACTIVE,
        hasAnyProtocol: Boolean = true,
    ) = ProtocolsListState(
        items = items.toPersistentList(),
        filter = filter,
        hasAnyProtocol = hasAnyProtocol,
        isLoading = false,
    )

    private companion object {
        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait — Medium (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Pixel 10 landscape — Expanded (§6.4.8). */
        const val EXPANDED = "w914dp-h411dp"

        /** The screen resolves "In N d" against the real clock, so the fixture dates off it too. */
        const val BREAK_DAYS_LEFT = 5
        val NEXT_DOSE: Instant = Clock.System.now() + BREAK_DAYS_LEFT.days

        val SEMA = item(
            id = 1,
            name = "Sema weekly titration",
            compoundName = "Semaglutide",
            dose = "0.25 mg",
            scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
            weekdays = persistentListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dosageTimes = persistentListOf(LocalTime(20, 0)),
            nextDoseAt = NEXT_DOSE,
            titration = TitrationUi(current = "0.25", target = "1 mg", progress = 0.25f),
        )

        val TEST_CYP = item(
            id = 2,
            name = "Testosterone Cyp",
            compoundName = "Test Cyp",
            dose = "100 mg",
            route = Route.INTRAMUSCULAR,
            pill = ProtocolPill.PAUSED,
            scheduleType = ScheduleType.EVERY_X_DAYS,
            scheduleValue = 7,
        )

        val BPC = item(
            id = 3,
            name = "BPC-157 healing cycle",
            compoundName = "BPC-157",
            dose = "250 mcg",
            pill = ProtocolPill.IN_BREAK,
            dosageTimes = persistentListOf(LocalTime(8, 0)),
            nextDoseAt = NEXT_DOSE,
            isInBreak = true,
        )

        @Suppress("LongParameterList")
        fun item(
            id: Long,
            name: String,
            compoundName: String,
            dose: String,
            route: Route = Route.SUBCUTANEOUS,
            pill: ProtocolPill = ProtocolPill.ACTIVE,
            scheduleType: ScheduleType = ScheduleType.DAILY,
            scheduleValue: Int? = null,
            weekdays: ImmutableList<DayOfWeek> = persistentListOf(),
            dosageTimes: ImmutableList<LocalTime> = persistentListOf(),
            nextDoseAt: Instant? = null,
            isInBreak: Boolean = false,
            titration: TitrationUi? = null,
        ) = ProtocolListItemUi(
            id = id,
            name = name,
            compoundName = compoundName,
            dose = dose,
            route = route,
            pill = pill,
            scheduleType = scheduleType,
            scheduleValue = scheduleValue,
            weekdays = weekdays,
            dosageTimes = dosageTimes,
            nextDoseAt = nextDoseAt,
            nextDoseHasTime = nextDoseAt != null,
            isInBreak = isInBreak,
            titration = titration,
        )
    }
}
