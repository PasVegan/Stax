package com.stax.feature.protocols.presentation.detail

import androidx.compose.runtime.Immutable
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.feature.protocols.presentation.list.ProtocolPill
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * §4.8.3's Schedule card, as the parts each row is built from rather than as five sentences.
 *
 * Weekday names, the 12h/24h clock and every plural form resolve from the device at render time, so
 * the ViewModel hands over `DayOfWeek`s and `LocalTime`s and the card writes the line (§4.7.3 does
 * the same for its schedule chip).
 *
 * [reminderOffsetMinutes] is null when reminders are off, which §4.8.3 renders as "Off" rather than
 * as a zero-minute offset.
 */
@Immutable
data class ScheduleCardUi(
    val scheduleType: ScheduleType,
    val scheduleValue: Int?,
    val weekdays: ImmutableList<DayOfWeek>,
    val dosageTimes: ImmutableList<LocalTime>,
    val titration: TitrationRuleUi?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val reminderOffsetMinutes: Int?,
)

/**
 * §4.8.3's Titration row: "0.25 → 1.0 mg · +0.25 / 4 wk", split into the four numbers it quotes.
 *
 * The doses are pre-rendered because the screen never formats a `Quantity` (§3.0.1); [increaseEvery]
 * and [increaseEveryValue] stay raw because the step's unit is a plural the device writes ("4 wk" /
 * "4 doses").
 */
@Immutable
data class TitrationRuleUi(
    val startDose: String,
    val targetDose: String,
    val increaseAmount: String,
    val increaseEvery: EscalationIncreaseEvery,
    val increaseEveryValue: Int,
)

/**
 * §4.8.4's linked-compound sub-row: the compound's own name over what one dose of this protocol
 * costs it — "0.25 mg = 0.10 mL · 2.5 mg/mL".
 *
 * [volume] and the three concentration fields are null for a compound with no concentration to derive
 * them from (a tub of capsules), and the meta line then quotes the dose alone. The concentration
 * arrives as its parts because its units are written through `unitLabel`, which is a string resource.
 */
@Immutable
data class LinkedCompoundUi(
    val id: Long,
    val name: String,
    val category: CompoundCategory,
    val dose: String,
    val volume: String?,
    val concentration: String?,
    val concentrationUnit: UnitCode?,
    val concentrationPerUnit: UnitCode?,
)

/**
 * §4.8.5's Inventory forecast.
 *
 * [runOutDate] comes from `InventoryRepository` (M3-09), the same aggregation the Dashboard warnings
 * read, so the two never disagree about the same protocol. It is null while the protocol is not
 * dosing — a paused protocol consumes nothing, and there is no day it runs out on.
 *
 * [requiredUntilEnd] is null for an open-ended protocol: without an end date there is no "enough".
 * [batchExpiry] is set **only** when the batch expires before [runOutDate] — it is §4.8.5's warning
 * row, not the compound's expiry date.
 */
@Immutable
data class ForecastUi(
    val dosesRemaining: Int?,
    val runOutDate: LocalDate?,
    val requiredUntilEnd: String?,
    val batchExpiry: LocalDate?,
)

/**
 * §4.8.6's Site restrictions: the region the protocol is confined to, and the cooldown that spaces
 * its sites out.
 *
 * [region] is null when the protocol may use any site. [cooldownDays] is resolved through §5.3's
 * source order — the protocol's own override, else the Settings default for its route — because a
 * protocol that sets none is still subject to the default, and a card that said nothing there would
 * be describing a rule the app does not follow.
 */
@Immutable
data class SiteRestrictionsUi(val region: BodyRegion?, val cooldownDays: Int)

/**
 * One dose-history row (§4.8.7). Identical to Compound Detail's (§4.3.8) but for this protocol.
 *
 * [dose] and [volume] are pre-rendered — the screen never divides quantities (§3.0.1). The timestamp
 * stays a moment because "Today · 8:00 PM" needs the device locale and its 12/24-hour setting.
 */
@Immutable
data class ProtocolHistoryEntryUi(
    val eventId: Long,
    val loggedAt: Instant,
    val status: AdministrationEventStatus,
    val dose: String,
    val volume: String?,
    val siteName: String?,
)

/**
 * UI state of Protocol Detail (§4.8).
 *
 * The history rows are **not** here (§4.8.7): they arrive as a paged `LazyPagingItems` stream
 * alongside this state, exactly as Compound Detail's do. [loggedDoseCount] is §4.8.7's count pill and
 * counts Taken + Partial all-time.
 *
 * [schedule] and [compound] are null only until the first emission lands; a protocol always has a
 * schedule, and it always has a compound unless that compound was archived out from under it (§4.7.2),
 * which §4.8.4 renders as an absent card rather than as a placeholder row.
 *
 * [isNotesExpanded] and [isArchiveDialogOpen] are state rather than `remember`s — whether the notes
 * are unfolded and whether the dock's confirmation is up are both app state (§2.3.1).
 */
data class ProtocolDetailState(
    val name: String = "",
    val pill: ProtocolPill = ProtocolPill.ACTIVE,
    val compoundName: String? = null,
    val schedule: ScheduleCardUi? = null,
    val compound: LinkedCompoundUi? = null,
    val forecast: ForecastUi = EMPTY_FORECAST,
    val sites: SiteRestrictionsUi? = null,
    val notes: String? = null,
    val isNotesExpanded: Boolean = false,
    val loggedDoseCount: Int = 0,
    val isArchiveDialogOpen: Boolean = false,
) {
    /** §4.8.2: the one chip that changes label — "Pause" while running, "Resume" once paused. */
    val isPaused: Boolean get() = pill == ProtocolPill.PAUSED
}

private val EMPTY_FORECAST = ForecastUi(
    dosesRemaining = null,
    runOutDate = null,
    requiredUntilEnd = null,
    batchExpiry = null,
)
