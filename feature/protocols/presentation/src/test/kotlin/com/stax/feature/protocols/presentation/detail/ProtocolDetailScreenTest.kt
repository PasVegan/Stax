package com.stax.feature.protocols.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.feature.protocols.presentation.R
import com.stax.feature.protocols.presentation.list.ProtocolPill
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Protocol Detail across the §6.4.8 breakpoint profiles (§10.5).
 *
 * The screen is the **detail pane** of the Protocols list-detail Scene, so what the breakpoint
 * decides is the pane's internal layout: one column until the pane itself is `720dp` wide, two
 * columns above it (§6.4.2). Both must render every section of §4.8 — which is this issue's
 * acceptance — and the §4.8.5 warning row must appear exactly when the batch expires first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtocolDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<ProtocolDetailAction>()

    // -----------------------------------------------------------------------
    // §4.8: every section renders, at every breakpoint
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `every section renders at Compact`() {
        setScreen(state())

        assertEverySectionIsRendered()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `every section renders at Medium`() {
        setScreen(state())

        assertEverySectionIsRendered()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `every section renders at Expanded, where the pane splits in two`() {
        setScreen(state())

        assertEverySectionIsRendered()
    }

    // -----------------------------------------------------------------------
    // §4.8.5's warning row — this issue's acceptance
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the warning row shows when the batch expires before the run-out`() {
        setScreen(state())

        scrollTo(string(R.string.protocol_detail_expiry_warning, "Jul 14"))

        composeRule.onNodeWithText(string(R.string.protocol_detail_expiry_warning, "Jul 14"))
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `no warning row when the batch outlives the stock`() {
        setScreen(state(forecast = FORECAST.copy(batchExpiry = null)))

        composeRule.onNodeWithText(string(R.string.protocol_detail_expiry_warning, "Jul 14"))
            .assertDoesNotExist()
    }

    // -----------------------------------------------------------------------
    // §4.8.2 – §4.8.9 interactions
    // -----------------------------------------------------------------------

    @Test
    @Config(qualifiers = COMPACT)
    fun `the pause chip reads Pause while the protocol runs`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_detail_resume)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.protocol_detail_pause)).performClick()

        assertThat(actions).containsExactly(ProtocolDetailAction.OnPauseClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the same chip reads Resume once it is paused`() {
        setScreen(state(pill = ProtocolPill.PAUSED))

        composeRule.onNodeWithText(string(R.string.protocol_detail_pause)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.protocol_detail_resume)).performClick()

        assertThat(actions).containsExactly(ProtocolDetailAction.OnPauseClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the linked compound row leads to the compound`() {
        setScreen(state())

        composeRule.onNodeWithText("Semaglutide").performClick()

        assertThat(actions).containsExactly(ProtocolDetailAction.OnCompoundClick)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a history row leads to its administration event`() {
        setScreen(state())

        scrollTo(string(R.string.protocol_detail_status_taken))

        composeRule.onNodeWithText(string(R.string.protocol_detail_status_taken), substring = true)
            .performClick()

        assertThat(actions).containsExactly(ProtocolDetailAction.OnHistoryEntryClick(1))
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the dock offers Log dose and Archive`() {
        setScreen(state())

        composeRule.onNodeWithText(string(R.string.protocol_detail_log_dose)).performClick()
        composeRule.onNodeWithText(string(R.string.protocol_detail_archive)).performClick()

        assertThat(actions).containsExactly(
            ProtocolDetailAction.OnLogDoseClick,
            ProtocolDetailAction.OnArchiveClick,
        )
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `the archive confirmation states what it keeps and what it drops`() {
        setScreen(state(isArchiveDialogOpen = true))

        composeRule.onNodeWithText(string(R.string.protocol_detail_archive_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_detail_archive_supporting)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_detail_archive_cancel)).performClick()

        assertThat(actions).containsExactly(ProtocolDetailAction.OnArchiveDismiss)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an empty history says so rather than showing nothing`() {
        setScreen(state(), history = emptyList())
        scrollTo(string(R.string.protocol_detail_history_empty))

        composeRule.onNodeWithText(string(R.string.protocol_detail_history_empty)).assertIsDisplayed()
        assertThat(actions).isEmpty()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a protocol with no times, no titration and no restriction drops what it has nothing to say about`() {
        setScreen(
            state(
                schedule = SCHEDULE.copy(titration = null, dosageTimes = persistentListOf()),
                sites = SiteRestrictionsUi(region = null, cooldownDays = 5),
            ),
        )

        composeRule.onNodeWithText(string(R.string.protocol_detail_times)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.protocol_detail_titration)).assertDoesNotExist()
        scrollTo(string(R.string.protocol_detail_sites_any))
        composeRule.onNodeWithText(string(R.string.protocol_detail_sites_any)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `an archived compound leaves the card saying so instead of a row leading nowhere`() {
        setScreen(state(compound = null, compoundName = null))

        composeRule.onNodeWithText(string(R.string.protocol_detail_compound_missing)).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /**
     * Every §4.8 section, by the header each one carries.
     *
     * The later cards are below the fold at Compact, so each is scrolled to before it is asserted on
     * — being off screen in a lazy list is not the same as being absent, and this issue's acceptance
     * is that all of them render.
     */
    private fun assertEverySectionIsRendered() {
        composeRule.onNodeWithText("Sema weekly titration").assertIsDisplayed()
        listOf(
            R.string.protocol_detail_schedule,
            R.string.protocol_detail_compound,
            R.string.protocol_detail_forecast,
            R.string.protocol_detail_sites,
            R.string.protocol_detail_history,
            R.string.protocol_detail_notes,
        ).forEach { header ->
            scrollTo(string(header))
            composeRule.onNodeWithText(string(header)).assertIsDisplayed()
        }
        // The dock is sticky, so it is on screen whatever the list is scrolled to.
        composeRule.onNodeWithText(string(R.string.protocol_detail_log_dose)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protocol_detail_archive)).assertIsDisplayed()
    }

    /**
     * The forecast, the history and the notes are below the fold at Compact and Medium, and split
     * across two lists at Expanded — either way each is reached by scrolling whichever lazy list
     * holds it, so every scrollable is tried and the ones that do not hold the text are no-ops.
     */
    private fun scrollTo(text: String) {
        val matcher = hasText(text, substring = true)
        // A lazy list has to be asked to scroll to the node, which is not composed until it does.
        composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { index ->
            runCatching { composeRule.onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(matcher) }
        }
        // An ordinary scroll container composes its children up front, so the node scrolls itself in.
        runCatching { composeRule.onNode(matcher).performScrollTo() }
    }

    @Suppress("LongParameterList")
    private fun state(
        pill: ProtocolPill = ProtocolPill.ACTIVE,
        compoundName: String? = "Semaglutide",
        schedule: ScheduleCardUi? = SCHEDULE,
        compound: LinkedCompoundUi? = COMPOUND,
        forecast: ForecastUi = FORECAST,
        sites: SiteRestrictionsUi? = SITES,
        isArchiveDialogOpen: Boolean = false,
    ) = ProtocolDetailState(
        name = "Sema weekly titration",
        pill = pill,
        compoundName = compoundName,
        schedule = schedule,
        compound = compound,
        forecast = forecast,
        sites = sites,
        notes = "Titrating slowly to limit GI side effects.",
        loggedDoseCount = 16,
        isArchiveDialogOpen = isArchiveDialogOpen,
    )

    private fun setScreen(state: ProtocolDetailState, history: List<ProtocolHistoryEntryUi> = HISTORY) {
        actions.clear()
        composeRule.setContent {
            StaxTheme(dynamicColor = false) {
                ProtocolDetailScreen(
                    state = state,
                    history = history.asLazyPagingItems(),
                    onAction = { actions += it },
                )
            }
        }
    }

    /**
     * A fixed list as the paged stream the screen takes. The load states are spelled out because
     * `PagingData.from(list)` alone leaves them on their initial `Loading`, which would keep §4.8.7's
     * empty state from ever appearing.
     */
    @Composable
    private fun List<ProtocolHistoryEntryUi>.asLazyPagingItems(): LazyPagingItems<ProtocolHistoryEntryUi> =
        remember(this) {
            val loaded = LoadState.NotLoading(endOfPaginationReached = true)
            flowOf(PagingData.from(this, LoadStates(refresh = loaded, prepend = loaded, append = loaded)))
        }.collectAsLazyPagingItems()

    private fun string(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    private companion object {
        /** Pixel 10 portrait — Compact (§6.4.8). */
        const val COMPACT = "w411dp-h914dp"

        /** Pixel 10 Pro Fold inner portrait — Medium (§6.4.8). */
        const val MEDIUM = "w673dp-h841dp"

        /** Wide enough for the pane's own two-column threshold, which is what Expanded exercises. */
        const val EXPANDED = "w1024dp-h800dp"

        val SCHEDULE = ScheduleCardUi(
            scheduleType = ScheduleType.SPECIFIC_WEEKDAYS,
            scheduleValue = null,
            weekdays = persistentListOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            dosageTimes = persistentListOf(LocalTime(20, 0)),
            titration = TitrationRuleUi(
                startDose = "0.25",
                targetDose = "1 mg",
                increaseAmount = "0.25",
                increaseEvery = EscalationIncreaseEvery.EVERY_X_WEEKS,
                increaseEveryValue = 4,
            ),
            startDate = LocalDate.parse("2026-05-01"),
            endDate = null,
            reminderOffsetMinutes = 10,
        )

        val COMPOUND = LinkedCompoundUi(
            id = 42,
            name = "Semaglutide",
            category = CompoundCategory.PEPTIDE,
            dose = "0.25 mg",
            volume = "0.1 ml",
            concentration = "2.5",
            concentrationUnit = UnitCode.MG,
            concentrationPerUnit = UnitCode.ML,
        )

        val FORECAST = ForecastUi(
            dosesRemaining = 18,
            runOutDate = LocalDate.parse("2026-07-28"),
            requiredUntilEnd = null,
            batchExpiry = LocalDate.parse("2026-07-14"),
        )

        val SITES = SiteRestrictionsUi(region = BodyRegion.ABDOMEN, cooldownDays = 5)

        /** The rows resolve "Today · 8:00 PM" against the real clock, so the fixture dates off it. */
        val HISTORY = listOf(
            ProtocolHistoryEntryUi(
                eventId = 1,
                loggedAt = Clock.System.now(),
                status = AdministrationEventStatus.TAKEN,
                dose = "0.25 mg",
                volume = "0.1 ml",
                siteName = "Abdomen R",
            ),
            ProtocolHistoryEntryUi(
                eventId = 2,
                loggedAt = Clock.System.now() - 7.days,
                status = AdministrationEventStatus.SKIPPED,
                dose = "0.25 mg",
                volume = null,
                siteName = null,
            ),
        )
    }
}
