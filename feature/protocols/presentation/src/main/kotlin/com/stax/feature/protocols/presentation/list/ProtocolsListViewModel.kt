package com.stax.feature.protocols.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.Decimal
import com.stax.core.domain.Escalation
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.ScheduledDose
import com.stax.core.domain.dosesBetween
import com.stax.core.domain.isInBreak
import com.stax.core.domain.plannedDoseAt
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.ScheduledDoseRepository
import com.stax.core.domain.valueIn
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * MVI ViewModel for the Protocols list (§4.7, §10.1).
 *
 * Four reads feed one card: the live protocols, the archived ones, the compound names the meta line
 * quotes, and the next pending dose of every protocol. The archived half arrives from its own query
 * — §4.7.2 defines Archived as `deletedAt != null` rather than as a [ProtocolStatus] — and stays in
 * [archived], which is what makes it structurally impossible for a soft-deleted protocol to surface
 * in Active, Paused or Completed: those three filter [live], which the query already excludes it
 * from.
 *
 * [today] is a parameter so the in-break window and the titration step are testable without freezing
 * the system clock; production resolves the default.
 */
class ProtocolsListViewModel(
    protocolRepository: ProtocolRepository,
    compoundRepository: CompoundRepository,
    scheduledDoseRepository: ScheduledDoseRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(ProtocolsListState())
    val state = _state.asStateFlow()

    private val _events = Channel<ProtocolsListEvent>()
    val events = _events.receiveAsFlow()

    /** The two halves of §4.7.2, unfiltered. The screen only ever sees what the active chip keeps. */
    private var live: List<ProtocolListItemUi> = emptyList()
    private var archived: List<ProtocolListItemUi> = emptyList()

    init {
        combine(
            protocolRepository.observeAll(),
            protocolRepository.observeArchived(),
            compoundRepository.observeAll(),
            scheduledDoseRepository.observeNextPendingPerProtocol(),
        ) { liveProtocols, archivedProtocols, compounds, nextDoses ->
            val names = compounds.associate { it.id to it.name }
            val next = nextDoses.associateBy { it.protocolId }
            liveProtocols.toListItems(names, next) to archivedProtocols.toListItems(names, next)
        }
            .onEach { (liveItems, archivedItems) ->
                live = liveItems
                archived = archivedItems
                _state.update {
                    it.copy(
                        items = it.results(),
                        hasAnyProtocol = liveItems.isNotEmpty() || archivedItems.isNotEmpty(),
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ProtocolsListAction) {
        when (action) {
            is ProtocolsListAction.OnFilterClick ->
                update { it.copy(filter = action.filter) }

            ProtocolsListAction.OnSearchClick -> _state.update { it.copy(isSearchOpen = true) }

            // Leaving the overlay drops the query with it: the list underneath is filtered by the
            // tab alone, so a query left behind would keep narrowing a list with nothing on screen
            // to explain why (§4.0.1).
            ProtocolsListAction.OnSearchDismiss -> update { it.copy(isSearchOpen = false, searchQuery = "") }

            is ProtocolsListAction.OnSearchQueryChange -> update { it.copy(searchQuery = action.query) }

            is ProtocolsListAction.OnProtocolClick -> send(
                ProtocolsListEvent.NavigateToProtocolDetail(action.protocolId),
            )

            ProtocolsListAction.OnCreateProtocolClick -> send(ProtocolsListEvent.NavigateToCreateProtocol)
        }
    }

    /** Applies a filter or query change and re-derives the result list from it in one step. */
    private fun update(transform: (ProtocolsListState) -> ProtocolsListState) {
        _state.update { current -> transform(current).let { it.copy(items = it.results()) } }
    }

    /**
     * The tab (§4.7.2) and the search query (§4.0.1) AND together. Active keeps in-break protocols:
     * the break is derived, and the protocol's status stays Active throughout it (§3.2).
     */
    private fun ProtocolsListState.results(): ImmutableList<ProtocolListItemUi> {
        val source = when (filter) {
            ProtocolFilter.ACTIVE ->
                live.filter { it.pill == ProtocolPill.ACTIVE || it.pill == ProtocolPill.IN_BREAK }
            ProtocolFilter.PAUSED -> live.filter { it.pill == ProtocolPill.PAUSED }
            ProtocolFilter.COMPLETED -> live.filter { it.pill == ProtocolPill.COMPLETED }
            ProtocolFilter.ARCHIVED -> archived
        }
        val query = searchQuery.trim()
        return source
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .toImmutableList()
    }

    private fun List<Protocol>.toListItems(
        compoundNames: Map<Long, String>,
        nextDoses: Map<Long, ScheduledDose>,
    ): List<ProtocolListItemUi> {
        val date = today()
        return map { protocol -> protocol.toUi(date, compoundNames[protocol.compoundSupplyId], nextDoses[protocol.id]) }
    }

    private fun Protocol.toUi(date: LocalDate, compoundName: String?, next: ScheduledDose?): ProtocolListItemUi {
        val inBreak = status == ProtocolStatus.ACTIVE && isInBreak(date)
        val dose = currentDose(date)
        return ProtocolListItemUi(
            id = id,
            name = name,
            // An archived protocol can outlive an archived compound, and the meta line then simply
            // drops the name rather than showing a placeholder for it.
            compoundName = compoundName,
            dose = dose.toString(),
            route = route,
            pill = when {
                inBreak -> ProtocolPill.IN_BREAK
                status == ProtocolStatus.PAUSED -> ProtocolPill.PAUSED
                status == ProtocolStatus.COMPLETED -> ProtocolPill.COMPLETED
                else -> ProtocolPill.ACTIVE
            },
            scheduleType = schedule.type,
            scheduleValue = schedule.count(),
            weekdays = schedule.selectedWeekdays.orEmpty().sortedBy { it.isoDayNumber }.toImmutableList(),
            dosageTimes = dosageTimes.sorted().toImmutableList(),
            nextDoseAt = next?.scheduledAt,
            nextDoseHasTime = next?.hasTimeOfDay == true,
            isInBreak = inBreak,
            titration = escalation?.toUi(dose),
        )
    }

    /**
     * The dose the protocol is on today — the escalated one where it titrates (§3.2), the flat
     * `plannedDose` where it does not.
     *
     * Only an `AfterXDoses` escalation reads the dose count, and counting it walks the schedule a day
     * at a time from `startDate`, so the other two kinds never pay for it.
     */
    private fun Protocol.currentDose(date: LocalDate): Quantity {
        val dosesBefore = if (escalation?.increaseEvery == EscalationIncreaseEvery.AFTER_X_DOSES) {
            dosesBetween(startDate, date)
        } else {
            0
        }
        return plannedDoseAt(date, dosesBefore)
    }

    /**
     * §4.7.3's titration bar. [current] is written without its unit because the bar's value label
     * puts the unit once, on the target ("0.25 / 1.0 mg"), and the target is read in the current
     * dose's unit so the two numbers are comparable — an escalation that passed validation always
     * converts (§3.2).
     */
    private fun Escalation.toUi(current: Quantity): TitrationUi {
        val target = Quantity(targetDose.valueIn(current.unit), current.unit)
        return TitrationUi(
            current = current.value.toPlainString(),
            target = target.toString(),
            progress = if (target.value > ZERO) {
                (current.value / target.value).raw.toFloat().coerceIn(0f, 1f)
            } else {
                0f
            },
        )
    }

    private fun send(event: ProtocolsListEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** A target dose is `> 0` by validation (§8); the guard is against a divide, not a case. */
        val ZERO = Decimal.parse("0")
    }
}

/** Whichever of the schedule's counts its own type uses (§3.2); null for the two that use none. */
private fun Schedule.count(): Int? = when (type) {
    ScheduleType.DAILY, ScheduleType.SPECIFIC_WEEKDAYS -> null
    ScheduleType.EVERY_X_DAYS -> interval
    ScheduleType.X_TIMES_PER_DAY -> timesPerDay
    ScheduleType.X_TIMES_PER_WEEK -> timesPerWeek
    ScheduleType.X_TIMES_PER_MONTH -> timesPerMonth
}
