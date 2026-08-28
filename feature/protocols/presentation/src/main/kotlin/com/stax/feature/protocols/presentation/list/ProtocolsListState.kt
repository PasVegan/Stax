package com.stax.feature.protocols.presentation.list

import androidx.compose.runtime.Immutable
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * The four filter chips (§4.7.2). Single select, [ACTIVE] by default.
 *
 * [ARCHIVED] is not a `ProtocolStatus` value — it is `deletedAt != null`, whatever the status is —
 * which is why the tab is an enum of its own rather than a nullable status.
 */
enum class ProtocolFilter { ACTIVE, PAUSED, COMPLETED, ARCHIVED }

/**
 * The card's status pill (§4.7.3). [IN_BREAK] has no `ProtocolStatus` behind it either: a protocol
 * inside its `daysOff` window stays Active and only the pill changes (§3.2). Archived is not a
 * pill — a soft-deleted protocol keeps whichever status it held, and the tab is what says it is
 * archived.
 */
enum class ProtocolPill { ACTIVE, IN_BREAK, PAUSED, COMPLETED }

/**
 * The titration progress bar (§4.7.3), present only while the protocol escalates.
 *
 * [current] and [target] are pre-rendered because the bar's value label writes them as one string
 * ("0.25 / 1.0 mg"), and [progress] is the `current / target` fraction the bar fills to — a ratio
 * for a layout, not dose math, which is why it is the one `Float` here (§3.0.1).
 */
@Immutable
data class TitrationUi(val current: String, val target: String, val progress: Float)

/**
 * One protocol card (§4.7.3).
 *
 * The schedule chip is built from [scheduleType] / [scheduleValue] / [weekdays] / [dosageTimes]
 * rather than from a formatted string, because weekday names, plural forms and the 12h/24h clock
 * all come from the device at render time. [nextDoseAt] is the earliest still-pending generated dose
 * (§3.3) and is null once the horizon holds none — a paused or completed protocol generates nothing
 * at all (§5.2).
 *
 * `@Immutable` because [nextDoseAt] is an external type the Compose compiler cannot infer stability
 * for (§2.3.1); every field is in fact a read-only value.
 */
@Immutable
data class ProtocolListItemUi(
    val id: Long,
    val name: String,
    val compoundName: String?,
    val dose: String,
    val route: Route,
    val pill: ProtocolPill,
    val scheduleType: ScheduleType,
    val scheduleValue: Int?,
    val weekdays: ImmutableList<DayOfWeek>,
    val dosageTimes: ImmutableList<LocalTime>,
    val nextDoseAt: Instant?,
    val nextDoseHasTime: Boolean,
    val isInBreak: Boolean,
    val titration: TitrationUi?,
)

/**
 * UI state of the Protocols list (§4.7).
 *
 * [items] is the *result* list — what is left after [filter] has been applied. The unfiltered
 * protocols stay inside the ViewModel so the screen only ever sees what it renders.
 *
 * [hasAnyProtocol] separates §7's two empty states: a tab with nothing in it is not the same as an
 * app with no protocols at all, and only the second earns the "Create protocol" hero.
 */
data class ProtocolsListState(
    val items: ImmutableList<ProtocolListItemUi> = persistentListOf(),
    val filter: ProtocolFilter = ProtocolFilter.ACTIVE,
    val hasAnyProtocol: Boolean = false,
    val isLoading: Boolean = true,
)
