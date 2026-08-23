package com.stax.feature.compounds.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.height
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.ContainerType
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.feature.compounds.presentation.R
import com.stax.feature.compounds.presentation.form.OpenedContainerUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Compound Detail across the §6.4.8 breakpoint profiles (§10.5): Compact (Pixel 10 portrait), Medium
 * (Fold inner portrait) and Expanded (a window wide enough for §6.4.2's two-column detail pane).
 *
 * The screen is the *detail pane* of the Compounds list-detail Scene, so these tests give it the
 * whole window: what the real pane gets is narrower, which is exactly why the internal two-column
 * switch is measured against the pane rather than the window (see `CompoundDetailScreen`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompoundDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<CompoundDetailAction>()

    // --- Every section renders, at every width -----------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `every section renders at Compact`() {
        setScreen(state())

        assertAllSectionsRender()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `every section renders at Medium`() {
        setScreen(state())

        assertAllSectionsRender()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `every section renders at Expanded, where the pane splits in two`() {
        setScreen(state())

        assertAllSectionsRender()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the two-column layout keeps the cards and the history side by side`() {
        setScreen(state())

        // Two columns means neither list starts below the other: the cards and the history both
        // begin under the stat strip, which is what one column could never do.
        val opened = composeRule.onNodeWithText(string(R.string.compound_detail_opened_edit))
            .getUnclippedBoundsInRoot()
        val history = composeRule.onNodeWithText(string(R.string.compound_detail_history))
            .getUnclippedBoundsInRoot()
        assertThat((history.top - opened.top).value).isLessThan(COLUMN_TOP_TOLERANCE_DP)
    }

    // --- §4.3.2 Stat strip -------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the stat strip drops the expiry tile when there is no expiry`() {
        setScreen(state(stats = CompoundStatsUi(dosesLeft = 18, daysLeft = 63, expiry = null)))

        composeRule.onNodeWithText(string(R.string.compound_detail_stat_doses_left)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_stat_batch_expiry)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.compound_detail_stat_container_expiry)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `unknown supply figures render as a dash rather than a zero`() {
        setScreen(state(stats = CompoundStatsUi(dosesLeft = null, daysLeft = null, expiry = null)))

        composeRule.onAllNodesWithText(string(R.string.compound_detail_stat_unknown))
            .assertCountEquals(2)
    }

    // --- Interactions ------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the app bar back arrow leaves the screen`() {
        setScreen(state())

        composeRule.onNodeWithContentDescription(string(R.string.compound_detail_back)).performClick()

        assertThat(actions).containsExactly(CompoundDetailAction.OnBackClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Edit on the opened card opens the container sheet`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_detail_opened_edit)).performClick()

        assertThat(actions).containsExactly(CompoundDetailAction.OnOpenedContainerClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `with nothing open the card offers to add one instead`() {
        setScreen(state(opened = null))

        composeRule.onNodeWithText(string(R.string.compound_detail_opened_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_opened_add)).performClick()

        assertThat(actions).containsExactly(CompoundDetailAction.OnOpenedContainerClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping a protocol sub-row opens that protocol`() {
        setScreen(state())

        composeRule.onNodeWithText("Sema weekly titration").performClick()

        assertThat(actions).containsExactly(CompoundDetailAction.OnProtocolClick(protocolId = 1))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `Show more unfolds the notes`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.compound_detail_show_more)).performClick()

        assertThat(actions).containsExactly(CompoundDetailAction.OnToggleNotes)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an unfolded notes card offers to fold back`() {
        setScreen(state(isNotesExpanded = true))

        composeRule.onNodeWithText(string(R.string.compound_detail_show_less)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping a status chip files its filter action`() {
        setScreen(state())

        scrollTo(string(R.string.compound_detail_filter_skipped))
        composeRule.onNodeWithText(string(R.string.compound_detail_filter_skipped)).performClick()

        assertThat(actions).containsExactly(
            CompoundDetailAction.OnHistoryFilterClick(HistoryStatusFilter.SKIPPED),
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `tapping a history row opens its dose detail`() {
        setScreen(state())

        scrollTo(TAKEN_ROW_SITE)
        composeRule.onNodeWithText(TAKEN_ROW_SITE, substring = true).performClick()

        assertThat(actions).containsExactly(CompoundDetailAction.OnHistoryEntryClick(eventId = 1))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an empty history says so`() {
        setScreen(state(), history = emptyList())

        scrollTo(string(R.string.compound_detail_history_empty))
        composeRule.onNodeWithText(string(R.string.compound_detail_history_empty)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an empty history says it differently when a chip is what emptied it`() {
        setScreen(state(historyFilter = HistoryStatusFilter.PARTIAL), history = emptyList())

        scrollTo(string(R.string.compound_detail_history_empty_filtered))
        composeRule.onNodeWithText(string(R.string.compound_detail_history_empty_filtered)).assertIsDisplayed()
    }

    // --- §4.3.9 Bottom dock ------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the dock sticks to the bottom and fires both of its actions at Compact`() {
        setScreen(state())

        assertDockIsAtTheBottom()
        assertDockActions()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `the dock sticks to the bottom and fires both of its actions at Medium`() {
        setScreen(state())

        assertDockIsAtTheBottom()
        assertDockActions()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `the dock sticks to the bottom and fires both of its actions at Expanded`() {
        setScreen(state())

        assertDockIsAtTheBottom()
        assertDockActions()
    }

    // -----------------------------------------------------------------------

    private fun assertAllSectionsRender() {
        composeRule.onNodeWithText("Semaglutide").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compounds_category_peptide)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_stat_doses_left)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_stat_days_left)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_stat_batch_expiry)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_opened_edit)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_protocols, 1)).assertIsDisplayed()
        composeRule.onNodeWithText("Sema weekly titration").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_notes)).assertIsDisplayed()
        // The history is below the fold on the narrower widths, so it is scrolled to rather than
        // looked for where it happens to land.
        scrollTo(TAKEN_ROW_SITE)
        composeRule.onNodeWithText(TAKEN_ROW_SITE, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.compound_detail_history)).assertExists()
        composeRule.onNodeWithText(plural(R.plurals.compound_detail_history_count, 24)).assertExists()
        composeRule.onNodeWithText(string(R.string.compound_detail_filter_all)).assertExists()
        assertThat(actions).isEmpty()
    }

    /** §4.3.9: the dock is sticky, so it sits in the bottom half of the pane whatever scrolled past. */
    private fun assertDockIsAtTheBottom() {
        val dock = composeRule.onNodeWithText(string(R.string.compound_detail_log_dose))
            .getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertThat(dock.top.value).isGreaterThan(root.height.value / 2)
    }

    private fun assertDockActions() {
        composeRule.onNodeWithText(string(R.string.compound_detail_log_dose)).performClick()
        composeRule.onNodeWithText(string(R.string.compound_detail_adjust)).performClick()

        assertThat(actions).containsExactly(
            CompoundDetailAction.OnLogDoseClick,
            CompoundDetailAction.OnAdjustClick,
        )
    }

/**
     * The history is below the fold at Compact and Medium and in a list of its own at Expanded —
     * either way it is reached by scrolling whichever lazy list holds it, which is the first one in
     * the tree in both layouts (the left column of the two-column layout is an ordinary scroll and
     * carries no `ScrollToIndex`).
     */
    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText(text, substring = true))
    }

    private fun setScreen(state: CompoundDetailState, history: List<HistoryEntryUi> = HISTORY) {
        actions.clear()
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                CompoundDetailScreen(
                    state = state,
                    history = remember { flowOf(pagingData(history)) }.collectAsLazyPagingItems(),
                    onAction = { actions += it },
                )
            }
        }
    }

    /**
     * A fixed list as one loaded page.
     *
     * The load states are spelled out: `PagingData.from(list)` alone leaves them untouched, so the
     * differ would stay on its initial `Loading` and §4.3.8's empty state — which asks whether the
     * refresh has finished — would never show.
     */
    private fun pagingData(rows: List<HistoryEntryUi>): PagingData<HistoryEntryUi> = PagingData.from(
        data = rows,
        sourceLoadStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        ),
    )

    private fun string(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    private fun plural(resId: Int, count: Int): String =
        composeRule.activity.resources.getQuantityString(resId, count, count)

    private fun state(
        stats: CompoundStatsUi = CompoundStatsUi(
            dosesLeft = 18,
            daysLeft = 63,
            expiry = ExpiryStatUi(LocalDate.parse("2026-07-14"), isContainerExpiry = false),
        ),
        opened: OpenedContainerUi? = OPENED,
        isNotesExpanded: Boolean = false,
        historyFilter: HistoryStatusFilter = HistoryStatusFilter.ALL,
    ) = CompoundDetailState(
        name = "Semaglutide",
        category = CompoundCategory.PEPTIDE,
        stats = stats,
        opened = opened,
        protocols = persistentListOf(PROTOCOL),
        notes = "Pre-mixed with 2 mL BAC water.",
        isNotesExpanded = isNotesExpanded,
        loggedDoseCount = 24,
        historyFilter = historyFilter,
    )

    private companion object {
        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait — Medium (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Wide enough for §6.4.2's two-column detail pane. */
        const val EXPANDED = "w1280dp-h800dp"

        /** Two columns start together; a few dp of padding between them is not a second row. */
        const val COLUMN_TOP_TOLERANCE_DP = 24f

        /** Identifies the Taken row by its site, which the filter chips do not also carry. */
        const val TAKEN_ROW_SITE = "Abdomen R"

        val NOW: Instant = Clock.System.now()

        val OPENED = OpenedContainerUi(
            containerType = ContainerType.VIAL,
            remaining = "3.2",
            capacity = "5.0",
            unit = "mg",
            fillFraction = 0.64f,
            openedDaysAgo = 12,
        )

        val PROTOCOL = ActiveProtocolUi(
            id = 1,
            name = "Sema weekly titration",
            scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
            scheduleValue = null,
            weekdays = persistentListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dose = "0.25 mg",
            route = Route.SUBCUTANEOUS,
            nextDoseAt = NOW,
            nextDoseHasTime = true,
        )

        val HISTORY = listOf(
            HistoryEntryUi(
                eventId = 1,
                loggedAt = NOW,
                status = AdministrationEventStatus.TAKEN,
                dose = "0.25 mg",
                volume = "0.10 ml",
                siteName = "Abdomen R",
            ),
            HistoryEntryUi(
                eventId = 2,
                loggedAt = NOW - 7.days,
                status = AdministrationEventStatus.SKIPPED,
                dose = "0.25 mg",
                volume = null,
                siteName = null,
            ),
        )
    }
}
