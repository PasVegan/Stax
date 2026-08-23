package com.stax.feature.compounds.presentation.detail

import androidx.compose.runtime.Immutable
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.feature.compounds.presentation.container.OpenedContainerSheetState
import com.stax.feature.compounds.presentation.form.OpenedContainerUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The single-select history filter row of §4.3.7. [ALL] is the default and doubles as the reset. */
enum class HistoryStatusFilter { ALL, TAKEN, PARTIAL, SKIPPED }

/**
 * The stat strip of §4.3.2, as the three questions it answers rather than as three rendered tiles.
 *
 * [dosesLeft] and [daysLeft] are null when no active protocol uses this compound — there is then no
 * dose size to divide the stock by, which §4.3.2 renders as "—" rather than as a zero.
 * [expiry] is null when the compound carries neither a batch expiry nor an opened one, and §4.3.2
 * drops that tile entirely.
 */
@Immutable
data class CompoundStatsUi(val dosesLeft: Int?, val daysLeft: Int?, val expiry: ExpiryStatUi?)

/**
 * The third stat tile (§4.3.2): the earlier of the batch expiry and the opened container's, labelled
 * for whichever one it turned out to be.
 */
@Immutable
data class ExpiryStatUi(val date: LocalDate, val isContainerExpiry: Boolean)

/**
 * One active-protocol sub-row of §4.3.4.
 *
 * The schedule arrives as its parts rather than as a sentence because the sentence is localized —
 * "Mon, Thu" is `DayOfWeek` run through the device locale, and "Every 3 days" is a string resource.
 * [scheduleValue] is whichever count [scheduleType] uses (interval / times per day / week / month);
 * [weekdays] is empty unless the type is `SPECIFIC_WEEKDAYS`.
 *
 * [nextDoseAt] is the earliest still-pending generated dose (§3.3); null when the protocol has none
 * left in the horizon, and [nextDoseHasTime] is false for an all-day dose, which shows no clock time.
 */
@Immutable
data class ActiveProtocolUi(
    val id: Long,
    val name: String,
    val scheduleType: ScheduleType,
    val scheduleValue: Int?,
    val weekdays: ImmutableList<DayOfWeek>,
    val dose: String,
    val route: Route,
    val nextDoseAt: Instant?,
    val nextDoseHasTime: Boolean,
)

/**
 * One dose-history row (§4.3.8).
 *
 * [dose] and [volume] are pre-rendered — the screen never divides quantities (§3.0.1). The timestamp
 * stays a moment rather than a string because "Today · 8:00 PM" needs the device locale and the
 * user's 12/24-hour setting, both of which only the composable can read.
 */
@Immutable
data class HistoryEntryUi(
    val eventId: Long,
    val loggedAt: Instant,
    val status: AdministrationEventStatus,
    val dose: String,
    val volume: String?,
    val siteName: String?,
)

/**
 * UI state of Compound Detail (§4.3).
 *
 * The history rows themselves are **not** here (§4.3.8, M7-08): they arrive as a paged
 * `LazyPagingItems` stream alongside this state, which holds only [historyFilter] — the chip that
 * decides which query produces them. [loggedDoseCount] is the badge of §4.3.6 and counts Taken +
 * Partial **all-time**, so it does not move when the filter does.
 *
 * [isNotesExpanded], [historyFilter] and [openedSheet] are state rather than `remember`s: whether the
 * notes are unfolded, which chip is picked and whether the §4.5 sheet is up are all app state (§2.3.1).
 */
data class CompoundDetailState(
    val name: String = "",
    val category: CompoundCategory? = null,
    val stats: CompoundStatsUi = CompoundStatsUi(dosesLeft = null, daysLeft = null, expiry = null),
    val opened: OpenedContainerUi? = null,
    val protocols: ImmutableList<ActiveProtocolUi> = persistentListOf(),
    val notes: String? = null,
    val isNotesExpanded: Boolean = false,
    val loggedDoseCount: Int = 0,
    val historyFilter: HistoryStatusFilter = HistoryStatusFilter.ALL,
    val openedSheet: OpenedContainerSheetState? = null,
    val isDepletionPromptOpen: Boolean = false,
    val isLoading: Boolean = true,
)
