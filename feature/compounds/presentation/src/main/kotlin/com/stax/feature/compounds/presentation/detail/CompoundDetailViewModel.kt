package com.stax.feature.compounds.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundDosesLeft
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.ScheduledDose
import com.stax.core.domain.ScheduledDoseStatus
import com.stax.core.domain.repository.AdministrationEventRepository
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InventoryRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.ScheduledDoseRepository
import com.stax.core.presentation.UiText
import com.stax.feature.compounds.presentation.R
import com.stax.feature.compounds.presentation.container.OpenedContainerDateField
import com.stax.feature.compounds.presentation.container.OpenedContainerSaveError
import com.stax.feature.compounds.presentation.container.OpenedContainerSheetAction
import com.stax.feature.compounds.presentation.container.OpenedContainerSheetState
import com.stax.feature.compounds.presentation.form.OpenedContainerUi
import com.stax.feature.compounds.presentation.form.ZERO
import com.stax.feature.compounds.presentation.form.fractionOf
import com.stax.feature.compounds.presentation.form.toDecimalOrNull
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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** What the route tells the screen: which compound it is showing (§4.3). */
data class CompoundDetailArgs(val compoundId: Long)

/**
 * MVI ViewModel for Compound Detail (§4.3, §10.1).
 *
 * Four sources make one screen and are combined once: the compound itself, the inventory aggregation
 * behind the stat strip (§4.3.2 — `dosesLeft` and `daysLeft` are protocol-weighted, which only
 * [InventoryRepository] can work out), the active protocols with the next dose generated for each
 * (§4.3.4), and §4.3.6's all-time dose count. Everything is *observed*, unlike the form, which reads
 * its compound once: nothing here is being typed into, so a row that changes underneath should show
 * through immediately.
 *
 * [history] is **not** part of that state (§4.3.8, M7-08): a `PagingData` stream is neither a list nor
 * a value to hold, so it is its own flow, driven by §4.3.7's chip and cached against configuration
 * change. The chip therefore re-queries rather than filtering rows already in memory — which is the
 * point of paging, since the rows it would filter were never all loaded.
 *
 * [now] and [timeZone] are parameters so "opened 12 days ago" is testable without freezing the system
 * clock; production resolves the defaults.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompoundDetailViewModel(
    private val compoundRepository: CompoundRepository,
    inventoryRepository: InventoryRepository,
    protocolRepository: ProtocolRepository,
    scheduledDoseRepository: ScheduledDoseRepository,
    administrationEventRepository: AdministrationEventRepository,
    private val args: CompoundDetailArgs,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(CompoundDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<CompoundDetailEvent>()
    val events = _events.receiveAsFlow()

    /**
     * §4.3.8's history, paged (M7-08). Re-queried whenever §4.3.7's chip moves — the filter is a SQL
     * predicate, not a pass over loaded rows — and `cachedIn` so a rotation or a pane resize does not
     * throw away the pages already read.
     */
    val history: Flow<PagingData<HistoryEntryUi>> = _state
        .map { it.historyFilter }
        .distinctUntilChanged()
        .flatMapLatest { filter ->
            administrationEventRepository.pagedHistoryForCompound(args.compoundId, filter.status())
        }
        .map { page -> page.map { it.toUi() } }
        .cachedIn(viewModelScope)

    /**
     * The compound as last observed. Kept whole because the §4.5 sheet needs fields §4.3 never
     * renders — the container size the remaining amount is a fraction of, and the expiry rule.
     */
    private var currentCompound: CompoundSupply? = null

    init {
        combine(
            compoundRepository.observeById(args.compoundId),
            inventoryRepository.observeDosesLeftPerCompound(),
            activeProtocols(protocolRepository, scheduledDoseRepository),
            administrationEventRepository.observeLoggedDoseCount(args.compoundId),
            ::Snapshot,
        )
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    fun onAction(action: CompoundDetailAction) {
        when (action) {
            CompoundDetailAction.OnBackClick -> send(CompoundDetailEvent.NavigateBack)

            CompoundDetailAction.OnOpenedContainerClick ->
                _state.update { it.copy(openedSheet = sheetState() ?: it.openedSheet) }

            is CompoundDetailAction.OnProtocolClick ->
                send(CompoundDetailEvent.NavigateToProtocol(action.protocolId))

            CompoundDetailAction.OnToggleNotes ->
                _state.update { it.copy(isNotesExpanded = !it.isNotesExpanded) }

            is CompoundDetailAction.OnHistoryFilterClick ->
                _state.update { it.copy(historyFilter = action.filter) }

            is CompoundDetailAction.OnHistoryEntryClick ->
                send(CompoundDetailEvent.NavigateToAdministrationEvent(action.eventId))

            CompoundDetailAction.OnLogDoseClick ->
                send(CompoundDetailEvent.NavigateToLogDose(args.compoundId))

            CompoundDetailAction.OnAdjustClick ->
                send(CompoundDetailEvent.NavigateToEditCompound(args.compoundId))

            is CompoundDetailAction.OpenedContainerSheet -> onSheetAction(action.action)

            is CompoundDetailAction.OnNaturalDepletionDecision -> onNaturalDepletionDecision(action.openNew)
        }
    }

    // -----------------------------------------------------------------------
    // Reading (§4.3.2 – §4.3.8)
    // -----------------------------------------------------------------------

    /** One emission of the four combined sources. */
    private data class Snapshot(
        val compound: CompoundSupply?,
        val dosesLeft: List<CompoundDosesLeft>,
        val protocols: List<ActiveProtocolUi>,
        val loggedDoseCount: Int,
    )

    private fun render(snapshot: Snapshot) {
        val compound = snapshot.compound
        // The row is gone — archived from the list pane beside us, or never there. Leaving is the only
        // honest answer; staying would show the last state of something that no longer exists.
        if (compound == null) {
            send(CompoundDetailEvent.NavigateBack)
            return
        }
        currentCompound = compound
        val supply = snapshot.dosesLeft.firstOrNull { it.compoundSupplyId == args.compoundId }
        _state.update {
            it.copy(
                name = compound.name,
                category = compound.category,
                stats = compound.statsWith(supply),
                opened = compound.currentOpened?.toUi(compound),
                protocols = snapshot.protocols.toImmutableList(),
                notes = compound.notes?.takeIf { it.isNotBlank() },
                // §4.3.6: Taken + Partial, all-time — so it does not move when the chip does.
                loggedDoseCount = snapshot.loggedDoseCount,
            )
        }
    }

    /**
     * §4.3.2. The two supply figures come from the inventory aggregation (M3-09) rather than being
     * recomputed here; the expiry tile is the earlier of the batch expiry and the opened container's
     * effective one (§3.1), labelled for whichever it turned out to be, and absent when there is
     * neither.
     */
    private fun CompoundSupply.statsWith(supply: CompoundDosesLeft?): CompoundStatsUi {
        val container = currentOpened?.let { it.userDefinedExpiryDate ?: it.predictedExpiryDate }
        val batch = batchExpiryDate
        val expiry = when {
            container != null && (batch == null || container < batch) ->
                ExpiryStatUi(container, isContainerExpiry = true)

            batch != null -> ExpiryStatUi(batch, isContainerExpiry = false)
            else -> null
        }
        return CompoundStatsUi(dosesLeft = supply?.dosesLeft, daysLeft = supply?.daysLeft, expiry = expiry)
    }

    /** §4.3.3: how much is left of how much, and how long it has been open. */
    private fun OpenedContainer.toUi(compound: CompoundSupply) = OpenedContainerUi(
        containerType = compound.containerType,
        remaining = remainingAmount.value.toPlainString(),
        capacity = compound.amountPerContainer.value.toPlainString(),
        unit = compound.amountPerContainer.unit.name.lowercase(),
        fillFraction = remainingAmount.fractionOf(compound.amountPerContainer),
        openedDaysAgo = openedAt.toLocalDateTime(timeZone).date.daysUntil(today()),
    )

    private fun CompoundHistoryEntry.toUi() = HistoryEntryUi(
        eventId = eventId,
        loggedAt = loggedAt,
        status = status,
        dose = dose.toString(),
        volume = volume?.toString(),
        siteName = injectionSiteName,
    )

    /**
     * §4.3.4's sub-rows, each with the next dose §3.3 generated for it.
     *
     * One `ScheduledDose` flow per active protocol, combined. The protocols are few — a compound with
     * more than a handful of live protocols is not a case this screen has — and the alternative, one
     * query across every generated dose in the database, reads far more rows to answer less.
     */
    private fun activeProtocols(
        protocolRepository: ProtocolRepository,
        scheduledDoseRepository: ScheduledDoseRepository,
    ): Flow<List<ActiveProtocolUi>> = protocolRepository.observeByCompoundSupplyId(args.compoundId)
        .map { protocols -> protocols.filter { it.status == ProtocolStatus.ACTIVE } }
        .flatMapLatest { protocols ->
            if (protocols.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    protocols.map { protocol ->
                        scheduledDoseRepository.observeForProtocol(protocol.id)
                            .map { doses -> protocol.toUi(doses.nextPending()) }
                    },
                ) { it.toList() }
            }
        }

    // -----------------------------------------------------------------------
    // The §4.5 opened-container sheet, hosted as a mode of this screen (§10.3)
    // -----------------------------------------------------------------------

    /**
     * The sheet as §4.5.3 opens it, or null before the compound has loaded — there is nothing to open
     * it on yet.
     *
     * Compound Detail always has a stored compound behind it, so Create Already Opened here means
     * "this compound has nothing open yet" rather than the form's "this compound does not exist yet".
     * Either way the defaults are today, a full container, and the expiry `expiryAfterOpeningDays`
     * implies.
     */
    private fun sheetState(): OpenedContainerSheetState? {
        val compound = currentCompound ?: return null
        val opened = compound.currentOpened
        val today = today()
        val openedDate = opened?.openedAt?.toLocalDateTime(timeZone)?.date ?: today
        val expiry = opened?.userDefinedExpiryDate
            ?: opened?.predictedExpiryDate
            ?: compound.expiryAfterOpeningDays?.let { openedDate.plus(it, DateTimeUnit.DAY) }
        return OpenedContainerSheetState(
            isEdit = opened != null,
            containerType = compound.containerType,
            compoundName = compound.name,
            containerAmount = compound.amountPerContainer.value.toPlainString(),
            // The container's own unit, not the compound's: reading `3.2 mg` back as `3.2 mcg` would
            // be a thousandfold error dressed up as a default.
            unit = opened?.remainingAmount?.unit ?: compound.amountPerContainer.unit,
            openedDate = openedDate,
            openedDaysAgo = openedDate.daysUntil(today),
            remaining = (opened?.remainingAmount ?: compound.amountPerContainer).value.toPlainString(),
            expiryDate = expiry,
            isExpiryAuto = opened?.userDefinedExpiryDate == null,
            expiryDaysAfterOpening = expiry?.let { openedDate.daysUntil(it) },
        )
    }

    private fun onSheetAction(action: OpenedContainerSheetAction) {
        val sheet = _state.value.openedSheet ?: return
        when (action) {
            OpenedContainerSheetAction.OnDismiss -> _state.update { it.copy(openedSheet = null) }

            is OpenedContainerSheetAction.OnDateFieldClick ->
                updateSheet { it.copy(openDatePicker = action.field) }

            OpenedContainerSheetAction.OnDatePickerDismiss -> updateSheet { it.copy(openDatePicker = null) }

            is OpenedContainerSheetAction.OnDateSelected -> onSheetDateSelected(sheet, action.date)

            is OpenedContainerSheetAction.OnRemainingChange ->
                updateSheet { it.copy(remaining = action.value, hasRemainingError = false, saveError = null) }

            OpenedContainerSheetAction.OnDeleteClick -> deleteOpenedContainer()

            OpenedContainerSheetAction.OnSaveClick -> saveOpenedContainer(sheet)
        }
    }

    /**
     * §4.5.3: moving the opened date drags an auto expiry along with it, since that expiry *is*
     * "this many days after opening"; setting an expiry by hand ends the link both ways.
     */
    private fun onSheetDateSelected(sheet: OpenedContainerSheetState, date: LocalDate?) {
        val field = sheet.openDatePicker
        val expiryDays = currentCompound?.expiryAfterOpeningDays
        updateSheet { current ->
            when {
                date == null -> current.copy(openDatePicker = null)

                field == OpenedContainerDateField.OPENED -> {
                    val expiry = if (current.isExpiryAuto) {
                        expiryDays?.let { date.plus(it, DateTimeUnit.DAY) }
                    } else {
                        current.expiryDate
                    }
                    current.copy(
                        openDatePicker = null,
                        openedDate = date,
                        openedDaysAgo = date.daysUntil(today()),
                        expiryDate = expiry,
                        expiryDaysAfterOpening = expiry?.let { date.daysUntil(it) },
                    )
                }

                else -> current.copy(
                    openDatePicker = null,
                    expiryDate = date,
                    isExpiryAuto = false,
                    expiryDaysAfterOpening = current.openedDate.daysUntil(date),
                )
            }
        }
    }

    /**
     * §4.5.5 for a compound that already exists — the only case this screen has. An empty container is
     * not rejected: it is natural depletion, which opens the container and closes it again in one go,
     * leaving the stock one container shorter.
     */
    private fun saveOpenedContainer(sheet: OpenedContainerSheetState) {
        val remaining = sheet.remaining.toDecimalOrNull()
        if (remaining == null || remaining < ZERO) {
            updateSheet { it.copy(hasRemainingError = true) }
            return
        }
        val compound = currentCompound ?: return
        val existing = compound.currentOpened
        val isDepleted = remaining <= ZERO
        val container = OpenedContainer(
            // An unchanged date keeps the instant it was opened at; only a date the user actually
            // moved is flattened to midnight, since a date field cannot say anything finer.
            openedAt = existing?.openedAt?.takeIf { it.toLocalDateTime(timeZone).date == sheet.openedDate }
                ?: sheet.openedDate.atStartOfDayIn(timeZone),
            remainingAmount = Quantity(remaining, sheet.unit),
            expiryAfterOpeningDays = compound.expiryAfterOpeningDays,
            userDefinedExpiryDate = sheet.expiryDate.takeIf { !sheet.isExpiryAuto },
            predictedExpiryDate = null,
        )
        runSheetWrite(promptOnEmpty = isDepleted) {
            val written = if (existing == null) {
                compoundRepository.addOpenedContainer(
                    compoundSupplyId = args.compoundId,
                    openedAt = container.openedAt,
                    remainingAmount = container.remainingAmount,
                    expiryAfterOpeningDays = container.expiryAfterOpeningDays,
                    userDefinedExpiryDate = container.userDefinedExpiryDate,
                )
            } else {
                compoundRepository.editOpenedContainer(
                    compoundSupplyId = args.compoundId,
                    openedAt = container.openedAt,
                    remainingAmount = container.remainingAmount,
                    expiryAfterOpeningDays = container.expiryAfterOpeningDays,
                    userDefinedExpiryDate = container.userDefinedExpiryDate,
                )
            }
            // Natural depletion (§4.5.5): the container is removed without `numberOfContainers` being
            // decremented a second time, which is exactly what closing it does.
            if (written is Result.Success && isDepleted) {
                compoundRepository.closeContainer(args.compoundId, null)
            } else {
                written
            }
        }
    }

    /**
     * §4.5.4: the lost / discarded path. No "open a new one?" follows — discarding a container is a
     * deliberate act, and §4.5.4 answers it with a snackbar stating what happened.
     */
    private fun deleteOpenedContainer() {
        val removed = CompoundDetailEvent.ShowMessage(UiText.StringResource(R.string.container_sheet_removed))
        runSheetWrite(message = removed) { compoundRepository.closeContainer(args.compoundId, null) }
    }

    /**
     * §4.5.5: "Open new" runs §5.3's container-opening operation — a fresh full container, dated now.
     * "Leave closed" is the whole of the other answer, so it only closes the prompt.
     */
    private fun onNaturalDepletionDecision(openNew: Boolean) {
        _state.update { it.copy(isDepletionPromptOpen = false) }
        if (openNew) runSheetWrite { compoundRepository.openContainer(args.compoundId) }
    }

    /**
     * Runs one of §4.5.5's writes and closes the sheet on success. Nothing is re-read afterwards, as
     * the form has to: everything this screen shows is observed, so the write arrives here by itself.
     */
    private fun runSheetWrite(
        promptOnEmpty: Boolean = false,
        message: CompoundDetailEvent? = null,
        write: suspend () -> EmptyResult<DataError.Local>,
    ) {
        viewModelScope.launch {
            updateSheet { it.copy(isSaving = true) }
            when (val result = write()) {
                is Result.Success -> {
                    // §4.5.5: with unopened stock left, an emptied container raises the offer to open
                    // the next one. Read from the last emission rather than after a re-read, which is
                    // sound here because none of these writes touches `numberOfContainers`: closing a
                    // container leaves the unopened tally alone (§5.3).
                    val hasStock = (currentCompound?.numberOfContainers ?: 0) > 0
                    _state.update {
                        it.copy(openedSheet = null, isDepletionPromptOpen = promptOnEmpty && hasStock)
                    }
                    message?.let { _events.send(it) }
                }

                // Reported in the sheet, not through the screen's snackbar: the sheet is a window of
                // its own and the `SnackbarHost` draws behind it, so a failure said that way is said
                // where the user cannot see it.
                is Result.Error -> updateSheet {
                    it.copy(isSaving = false, saveError = result.error.toSaveError())
                }
            }
        }
    }

    private fun updateSheet(transform: (OpenedContainerSheetState) -> OpenedContainerSheetState) {
        _state.update { it.copy(openedSheet = it.openedSheet?.let(transform)) }
    }

    /**
     * §5.3: the one refusal the sheet can explain in its own terms is "there is nothing left to
     * open" — every other failure is the write itself going wrong.
     */
    private fun DataError.Local.toSaveError(): OpenedContainerSaveError =
        if (this == DataError.Local.CONSTRAINT_VIOLATION) {
            OpenedContainerSaveError.NO_UNOPENED_STOCK
        } else {
            OpenedContainerSaveError.WRITE_FAILED
        }

    private fun send(event: CompoundDetailEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun today(): LocalDate = now().toLocalDateTime(timeZone).date
}

/**
 * The next dose still to come (§3.3): the earliest Pending row. One already in the past is overdue
 * rather than gone, and is still the next thing due — which is what §4.3.4's pill reports.
 */
private fun List<ScheduledDose>.nextPending(): ScheduledDose? =
    filter { it.status == ScheduledDoseStatus.PENDING }.minByOrNull { it.scheduledAt }

private fun Protocol.toUi(next: ScheduledDose?) = ActiveProtocolUi(
    id = id,
    name = name,
    scheduleType = schedule.type,
    scheduleValue = schedule.count(),
    weekdays = schedule.selectedWeekdays.orEmpty().sortedBy { it.isoDayNumber }.toImmutableList(),
    dose = plannedDose.toString(),
    route = route,
    nextDoseAt = next?.scheduledAt,
    nextDoseHasTime = next?.hasTimeOfDay == true,
)

/** Whichever of the schedule's counts its own type uses (§3.2); null for the two that use none. */
private fun Schedule.count(): Int? = when (type) {
    ScheduleType.DAILY, ScheduleType.SPECIFIC_WEEKDAYS -> null
    ScheduleType.EVERY_X_DAYS -> interval
    ScheduleType.X_TIMES_PER_DAY -> timesPerDay
    ScheduleType.X_TIMES_PER_WEEK -> timesPerWeek
    ScheduleType.X_TIMES_PER_MONTH -> timesPerMonth
}

private fun HistoryStatusFilter.status(): AdministrationEventStatus? = when (this) {
    HistoryStatusFilter.ALL -> null
    HistoryStatusFilter.TAKEN -> AdministrationEventStatus.TAKEN
    HistoryStatusFilter.PARTIAL -> AdministrationEventStatus.PARTIAL
    HistoryStatusFilter.SKIPPED -> AdministrationEventStatus.SKIPPED
}
