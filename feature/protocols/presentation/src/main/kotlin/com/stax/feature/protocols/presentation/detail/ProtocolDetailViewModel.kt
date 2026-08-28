package com.stax.feature.protocols.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.Escalation
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.Settings
import com.stax.core.domain.dosesBetween
import com.stax.core.domain.isInBreak
import com.stax.core.domain.plannedDoseAt
import com.stax.core.domain.repository.AdministrationEventRepository
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InventoryRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.SettingsRepository
import com.stax.core.domain.valueIn
import com.stax.core.presentation.UiText
import com.stax.core.presentation.toUiText
import com.stax.feature.protocols.presentation.R
import com.stax.feature.protocols.presentation.form.convertedTo
import com.stax.feature.protocols.presentation.form.dividedBy
import com.stax.feature.protocols.presentation.form.totalStock
import com.stax.feature.protocols.presentation.list.ProtocolPill
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** What the route tells the screen: which protocol it is showing (§4.8). */
data class ProtocolDetailArgs(val protocolId: Long)

/**
 * MVI ViewModel for Protocol Detail (§4.8, §10.1).
 *
 * Five reads make one screen. The protocol itself drives the app bar, the Schedule card and the Site
 * restrictions; its compound (looked up from the protocol, so it re-subscribes only when the link
 * changes) fills §4.8.4 and the stock half of §4.8.5; the run-out date comes from `InventoryRepository`
 * (M3-09) so §4.8.5 and the Dashboard's warnings never disagree about the same protocol; the badge
 * count is §4.8.7's pill; and Settings supplies the site cooldown a protocol without an override
 * still runs under (§5.3).
 *
 * [history] is **not** part of that state (§4.8.7): a `PagingData` stream is neither a list nor a
 * value to hold, so it is its own flow, cached against configuration change. Unlike §4.3.8 it has no
 * filter to re-query on — §4.8.7 has no status chips.
 *
 * [today] and [timeZone] are parameters so the in-break pill, the titration step and the
 * required-until-end figure are testable without freezing the system clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtocolDetailViewModel(
    private val protocolRepository: ProtocolRepository,
    compoundRepository: CompoundRepository,
    inventoryRepository: InventoryRepository,
    administrationEventRepository: AdministrationEventRepository,
    settingsRepository: SettingsRepository,
    private val args: ProtocolDetailArgs,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(ProtocolDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<ProtocolDetailEvent>()
    val events = _events.receiveAsFlow()

    /** §4.8.7's history, paged and `cachedIn` so a pane resize does not throw away the loaded pages. */
    val history: Flow<PagingData<ProtocolHistoryEntryUi>> =
        administrationEventRepository.pagedHistoryForProtocol(args.protocolId)
            .map { page -> page.map { it.toUi() } }
            .cachedIn(viewModelScope)

    init {
        val protocols = protocolRepository.observeById(args.protocolId)
        combine(
            protocols,
            protocols.map { it?.compoundSupplyId }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(null) else compoundRepository.observeById(id) },
            inventoryRepository.observeRunOutDate(args.protocolId),
            administrationEventRepository.observeLoggedDoseCountForProtocol(args.protocolId),
            settingsRepository.observe(),
            ::Snapshot,
        )
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    fun onAction(action: ProtocolDetailAction) {
        when (action) {
            ProtocolDetailAction.OnBackClick -> send(ProtocolDetailEvent.NavigateBack)

            ProtocolDetailAction.OnPauseClick -> if (_state.value.isPaused) {
                write { protocolRepository.resume(args.protocolId) }
            } else {
                write { protocolRepository.pause(args.protocolId) }
            }

            ProtocolDetailAction.OnEditClick ->
                send(ProtocolDetailEvent.NavigateToEditProtocol(args.protocolId))

            ProtocolDetailAction.OnDuplicateClick -> write(
                message = UiText.StringResource(R.string.protocol_detail_duplicated),
            ) { protocolRepository.duplicate(args.protocolId) }

            ProtocolDetailAction.OnCompoundClick ->
                _state.value.compound
                    ?.let { send(ProtocolDetailEvent.NavigateToCompound(it.id)) }

            ProtocolDetailAction.OnToggleNotes ->
                _state.update { it.copy(isNotesExpanded = !it.isNotesExpanded) }

            is ProtocolDetailAction.OnHistoryEntryClick ->
                send(ProtocolDetailEvent.NavigateToAdministrationEvent(action.eventId))

            ProtocolDetailAction.OnLogDoseClick ->
                send(ProtocolDetailEvent.NavigateToLogDose(args.protocolId))

            ProtocolDetailAction.OnArchiveClick -> _state.update { it.copy(isArchiveDialogOpen = true) }

            ProtocolDetailAction.OnArchiveDismiss -> _state.update { it.copy(isArchiveDialogOpen = false) }

            // Archiving is a soft delete (§5.5), and `observeById` keeps emitting the row afterwards
            // — so leaving is this screen's job, not something the next emission will do for it.
            ProtocolDetailAction.OnArchiveConfirm -> {
                _state.update { it.copy(isArchiveDialogOpen = false) }
                write(then = ProtocolDetailEvent.NavigateBack) { protocolRepository.archive(args.protocolId) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Reading (§4.8.1 – §4.8.8)
    // -----------------------------------------------------------------------

    /** One emission of the five combined sources. */
    private data class Snapshot(
        val protocol: Protocol?,
        val compound: CompoundSupply?,
        val runOutDate: LocalDate?,
        val loggedDoseCount: Int,
        val settings: Settings,
    )

    private fun render(snapshot: Snapshot) {
        // The row is gone — hard-deleted, or never there. Leaving is the only honest answer; staying
        // would show the last state of something that no longer exists.
        val protocol = snapshot.protocol ?: run {
            send(ProtocolDetailEvent.NavigateBack)
            return
        }
        val date = today()
        val dose = protocol.currentDose(date)
        _state.update {
            it.copy(
                name = protocol.name,
                pill = protocol.pill(date),
                compoundName = snapshot.compound?.name,
                schedule = protocol.toScheduleCard(),
                compound = snapshot.compound?.toUi(dose),
                forecast = protocol.forecast(snapshot.compound, snapshot.runOutDate, dose, date),
                sites = SiteRestrictionsUi(
                    region = protocol.injectionSiteRestriction,
                    cooldownDays = protocol.cooldownDays(snapshot.settings),
                ),
                notes = protocol.notes?.takeIf(String::isNotBlank),
                loggedDoseCount = snapshot.loggedDoseCount,
            )
        }
    }

    /** §4.8.1's supporting line. Same rule as §4.7.3's pill: a break shows through, a status does not. */
    private fun Protocol.pill(date: LocalDate): ProtocolPill = when {
        status == ProtocolStatus.ACTIVE && isInBreak(date) -> ProtocolPill.IN_BREAK
        status == ProtocolStatus.PAUSED -> ProtocolPill.PAUSED
        status == ProtocolStatus.COMPLETED -> ProtocolPill.COMPLETED
        else -> ProtocolPill.ACTIVE
    }

    /** §4.8.3, as parts: the card writes the five rows, and drops the ones with nothing to say. */
    private fun Protocol.toScheduleCard() = ScheduleCardUi(
        scheduleType = schedule.type,
        scheduleValue = schedule.count(),
        weekdays = schedule.selectedWeekdays.orEmpty().sortedBy { it.isoDayNumber }.toImmutableList(),
        dosageTimes = dosageTimes.sorted().toImmutableList(),
        titration = escalation?.toUi(),
        startDate = startDate,
        endDate = endDate,
        reminderOffsetMinutes = reminderOffsetMinutes.takeIf { reminderEnabled },
    )

    /** §4.8.3's Titration row. The target is read in the start dose's unit so the arrow compares. */
    private fun Escalation.toUi() = TitrationRuleUi(
        startDose = startDose.value.toPlainString(),
        // Read in the start dose's unit so the two ends of the arrow compare; an escalation that
        // passed validation always converts (§3.2).
        targetDose = Quantity(targetDose.valueIn(startDose.unit), startDose.unit).toString(),
        increaseAmount = increaseAmount.value.toPlainString(),
        increaseEvery = increaseEvery,
        increaseEveryValue = increaseEveryValue,
    )

    /**
     * §4.8.4's sub-row: the compound, and what one dose of *this* protocol comes to in it —
     * "0.25 mg = 0.10 mL · 2.5 mg/mL". The volume is null when the compound carries no concentration
     * to divide by, and the row then quotes the dose alone.
     */
    private fun CompoundSupply.toUi(dose: Quantity) = LinkedCompoundUi(
        id = id,
        name = name,
        category = category,
        dose = dose.toString(),
        volume = concentration?.let { dose.dividedBy(it) }?.toString(),
        concentration = concentration?.amount?.value?.toPlainString(),
        concentrationUnit = concentration?.amount?.unit,
        concentrationPerUnit = concentration?.per?.unit,
    )

    /**
     * §4.8.5. The run-out date is the repository's (M3-09); the two figures beside it are this
     * protocol's own, computed against the same stock-per-dose rule the aggregation uses — the dose
     * divided out of the concentration where the stock is measured in volume, converted into the
     * stock's unit otherwise (§3.0.4).
     *
     * The warning row is §4.8.5's one condition: a batch whose shelf life ends before the stock does.
     */
    private fun Protocol.forecast(
        compound: CompoundSupply?,
        runOutDate: LocalDate?,
        dose: Quantity,
        date: LocalDate,
    ): ForecastUi {
        val perDose = compound?.stockPerDose(dose)?.takeIf { it.value > ZERO }
        val stock = compound?.totalStock()
        return ForecastUi(
            dosesRemaining = if (perDose != null && stock != null) {
                stock.value.raw.divideToIntegralValue(perDose.value.raw).toInt().coerceAtLeast(0)
            } else {
                null
            },
            runOutDate = runOutDate,
            // Open-ended protocols have no "until end" to require anything by (§4.8.5).
            requiredUntilEnd = endDate?.let { end ->
                perDose?.times(Decimal.parse(dosesBetween(date, end.plus(1, DateTimeUnit.DAY)).toString()))
                    ?.toString()
            },
            batchExpiry = compound?.batchExpiryDate?.takeIf { runOutDate != null && it < runOutDate },
        )
    }

    /** §5.3's cooldown source order, minus the hardcoded fallback Settings already carries. */
    private fun Protocol.cooldownDays(settings: Settings): Int = siteCooldownDays
        ?: when (route) {
            Route.INTRAMUSCULAR -> settings.defaultSiteCooldownDaysIM
            else -> settings.defaultSiteCooldownDaysSC
        }

    /**
     * The dose the protocol is on today — the escalated one where it titrates (§3.2), the flat
     * `plannedDose` where it does not. Same rule as §4.7.3's card, so the two never disagree.
     */
    private fun Protocol.currentDose(date: LocalDate): Quantity {
        val dosesBefore = if (escalation?.increaseEvery == EscalationIncreaseEvery.AFTER_X_DOSES) {
            dosesBetween(startDate, date)
        } else {
            0
        }
        return plannedDoseAt(date, dosesBefore)
    }

    private fun CompoundHistoryEntry.toUi() = ProtocolHistoryEntryUi(
        eventId = eventId,
        loggedAt = loggedAt,
        status = status,
        dose = dose.toString(),
        volume = volume?.toString(),
        siteName = injectionSiteName,
    )

    // -----------------------------------------------------------------------
    // Writing (§4.8.2, §4.8.9)
    // -----------------------------------------------------------------------

    /**
     * Runs one of §4.8.2's / §4.8.9's lifecycle operations. Nothing is re-read afterwards: everything
     * this screen shows is observed, so a pause arrives here by itself and the chip re-labels.
     */
    private fun write(
        message: UiText? = null,
        then: ProtocolDetailEvent? = null,
        operation: suspend () -> Result<*, DataError.Local>,
    ) {
        viewModelScope.launch {
            when (val result = operation()) {
                is Result.Success -> {
                    message?.let { _events.send(ProtocolDetailEvent.ShowMessage(it)) }
                    then?.let { _events.send(it) }
                }

                is Result.Error -> _events.send(ProtocolDetailEvent.ShowError(result.error.toUiText()))
            }
        }
    }

    private fun send(event: ProtocolDetailEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** A stock-per-dose of zero is a divide, not a case; the guard is against the former. */
        val ZERO = Decimal.parse("0")
    }
}

/**
 * How much stock one dose costs — the rule `InventoryRepository` divides by (§4.3.2, M3-09), so the
 * doses-remaining figure beside its run-out date is counted the same way.
 *
 * A concentration turns a mass dose into the volume actually drawn, which only helps when the stock
 * itself is measured in volume; where it is not, the conversion fails and the plain dose stands.
 */
private fun CompoundSupply.stockPerDose(dose: Quantity): Quantity? {
    val unit = amountPerContainer.unit
    return concentration?.let { dose.dividedBy(it) }?.convertedTo(unit) ?: dose.convertedTo(unit)
}

/** Whichever of the schedule's counts its own type uses (§3.2); null for the two that use none. */
private fun Schedule.count(): Int? = when (type) {
    ScheduleType.DAILY, ScheduleType.SPECIFIC_WEEKDAYS -> null
    ScheduleType.EVERY_X_DAYS -> interval
    ScheduleType.X_TIMES_PER_DAY -> timesPerDay
    ScheduleType.X_TIMES_PER_WEEK -> timesPerWeek
    ScheduleType.X_TIMES_PER_MONTH -> timesPerMonth
}
