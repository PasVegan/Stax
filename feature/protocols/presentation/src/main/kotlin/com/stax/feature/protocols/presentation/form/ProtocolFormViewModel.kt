package com.stax.feature.protocols.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.SCHEDULE_HORIZON_DAYS
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.core.domain.UnitFamily
import com.stax.core.domain.ValidationError
import com.stax.core.domain.dosesBetween
import com.stax.core.domain.dosingTimesOn
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.SettingsRepository
import com.stax.core.domain.validateProtocolEndDate
import com.stax.core.domain.validateProtocolPlannedDose
import com.stax.core.domain.validateProtocolStartDate
import com.stax.core.domain.validateScheduleInterval
import com.stax.core.domain.validateScheduleSelectedWeekdays
import com.stax.core.domain.validateScheduleTimesPerDay
import com.stax.core.presentation.toUiText
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Clock
import kotlin.time.Instant

/** What the route tells the form: which protocol to edit (null = create), and whether this is onboarding step 3. */
data class ProtocolFormArgs(val protocolId: Long? = null, val isOnboarding: Boolean = false)

/**
 * MVI ViewModel for the Create / Edit Protocol form (§4.9, §10.1).
 *
 * One ViewModel serves both modes: [ProtocolFormArgs.protocolId] decides whether the form starts
 * empty and Saves through `create` — which generates the 7-day Pending horizon — or loads a protocol
 * and Saves through `update`, which runs §5.4's pending-regen scope rule. Both rules live in
 * `ProtocolRepository`; this class only decides which of the two calls to make.
 *
 * The protocol is read **once** rather than observed: a form that reloaded under the user's hands
 * would discard what they were typing the moment anything else touched the row. The compound list
 * *is* observed, because the picker has to offer a compound the user just created in another tab.
 *
 * Everything §4.9.3 has no control for — escalation, protocol break, site cooldown, status — is
 * carried through from [loaded] untouched, so editing a titrating protocol through this form never
 * silently flattens its titration.
 */
@Suppress("TooManyFunctions")
class ProtocolFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val protocolRepository: ProtocolRepository,
    private val compoundRepository: CompoundRepository,
    private val settingsRepository: SettingsRepository,
    private val args: ProtocolFormArgs,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** The auto-saved draft (§4.4.5's rule, which §4.9 inherits). Cleared once the form is done with. */
    private var savedDraft: ProtocolFormDraft? by savedStateHandle.saved(key = DRAFT_KEY) { null }

    /**
     * What the form was loaded with. The discard prompt is "the draft differs from this".
     *
     * Create opens on today (§4.9.3 Duration), so today *is* the baseline — otherwise the form would
     * count as changed before the user had touched anything. Edit replaces this once it loads.
     */
    private var baseline = ProtocolFormDraft(startDate = today())

    /** The protocol being edited, kept whole for the fields §4.9.3 cannot edit but must not drop. */
    private var loaded: Protocol? = null

    /** Every compound the picker can offer, in domain form — the forecast reads their stock. */
    private var compounds: List<CompoundSupply> = emptyList()

    private val _state = MutableStateFlow(
        (savedDraft ?: baseline).let { draft ->
            ProtocolFormState(
                draft = draft,
                isEdit = args.protocolId != null,
                isOnboarding = args.isOnboarding,
                isLoading = args.protocolId != null,
                isDirty = draft.differsFrom(baseline),
            )
        },
    )
    val state = _state.asStateFlow()

    private val _events = Channel<ProtocolFormEvent>()
    val events = _events.receiveAsFlow()

    init {
        observeCompounds()
        observeSettings()
        args.protocolId?.let(::load)
    }

    fun onAction(action: ProtocolFormAction) {
        when (action) {
            is ProtocolFormAction.Edit -> onEdit(action)
            is ProtocolFormAction.Pick -> onPick(action)
            is ProtocolFormAction.Overlay -> onOverlay(action)

            ProtocolFormAction.OnAddTimeClick,
            ProtocolFormAction.OnTimePickerDismiss,
            is ProtocolFormAction.OnTimeSelected,
            is ProtocolFormAction.OnTimeRemoved,
            -> onDosageTime(action)

            ProtocolFormAction.OnPauseClick,
            ProtocolFormAction.OnDuplicateClick,
            ProtocolFormAction.OnArchiveClick,
            ProtocolFormAction.OnArchiveConfirm,
            -> onLifecycle(action)

            ProtocolFormAction.OnAddCompoundClick -> viewModelScope.launch {
                _state.update { it.copy(openPicker = null) }
                _events.send(ProtocolFormEvent.OpenCreateCompound)
            }

            ProtocolFormAction.OnErrorScrollHandled -> _state.update { it.copy(scrollToError = null) }

            ProtocolFormAction.OnSaveClick -> save()

            // Leaving a form the user has changed confirms first; an untouched one just closes.
            ProtocolFormAction.OnCancelClick -> if (_state.value.isDirty) {
                _state.update { it.copy(isDiscardDialogOpen = true) }
            } else {
                finish()
            }

            ProtocolFormAction.OnDiscardConfirm -> finish()

            // Skip is already an explicit "not now" (§4.14), so it does not ask again about discarding.
            ProtocolFormAction.OnSkipClick -> finish()
        }
    }

    /** §4.9.3 Times of day: the picker opening and closing, and the list it edits. */
    private fun onDosageTime(action: ProtocolFormAction) {
        when (action) {
            ProtocolFormAction.OnAddTimeClick -> _state.update { it.copy(isTimePickerOpen = true) }
            ProtocolFormAction.OnTimePickerDismiss -> _state.update { it.copy(isTimePickerOpen = false) }

            is ProtocolFormAction.OnTimeSelected -> {
                _state.update { it.copy(isTimePickerOpen = false) }
                // Sorted and de-duplicated: `protocol_dosage_time(protocolId, time)` is unique
                // (§5.8.3), and two identical pills would be one row anyway.
                updateDraft { it.copy(dosageTimes = (it.dosageTimes + action.time).distinct().sorted()) }
            }

            is ProtocolFormAction.OnTimeRemoved ->
                updateDraft { it.copy(dosageTimes = it.dosageTimes - action.time) }

            else -> Unit
        }
    }

    /** §4.9.5 Lifecycle: Pause and Duplicate act at once; Archive asks first (§5.5). */
    private fun onLifecycle(action: ProtocolFormAction) {
        when (action) {
            ProtocolFormAction.OnPauseClick -> lifecycle { protocolRepository.pause(it) }
            ProtocolFormAction.OnDuplicateClick -> duplicate()
            ProtocolFormAction.OnArchiveClick -> _state.update { it.copy(isArchiveDialogOpen = true) }
            ProtocolFormAction.OnArchiveConfirm -> {
                _state.update { it.copy(isArchiveDialogOpen = false) }
                lifecycle { protocolRepository.archive(it) }
            }

            else -> Unit
        }
    }

    private fun onEdit(action: ProtocolFormAction.Edit) = when (action) {
        is ProtocolFormAction.Edit.OnDoseChange ->
            updateDraft(ProtocolFormField.DOSE) { it.copy(doseAmount = action.value) }

        is ProtocolFormAction.Edit.OnScheduleCountChange ->
            updateDraft(ProtocolFormField.SCHEDULE_COUNT) { it.withScheduleCount(action.value) }

        is ProtocolFormAction.Edit.OnNotesChange -> updateDraft { it.copy(notes = action.value) }

        is ProtocolFormAction.Edit.OnReminderToggle ->
            updateDraft { it.copy(reminderEnabled = action.enabled) }
    }

    private fun onPick(action: ProtocolFormAction.Pick) {
        when (action) {
            is ProtocolFormAction.Pick.OnCompoundSelected -> {
                _state.update { it.copy(openPicker = null, pickerQuery = "") }
                selectCompound(action.compoundId)
            }

            is ProtocolFormAction.Pick.OnRouteSelected ->
                updateDraft(ProtocolFormField.ROUTE) { it.copy(route = action.route) }

            is ProtocolFormAction.Pick.OnDoseUnitSelected -> {
                _state.update { it.copy(openPicker = null) }
                updateDraft(ProtocolFormField.DOSE) { it.copy(doseUnit = action.unit) }
            }

            // Switching chips does not clear the other chips' counts (§4.9.3): each keeps its own.
            is ProtocolFormAction.Pick.OnScheduleTypeSelected ->
                updateDraft { it.copy(scheduleType = action.type) }

            is ProtocolFormAction.Pick.OnWeekdayToggled -> updateDraft(ProtocolFormField.WEEKDAYS) {
                val weekdays = if (action.day in it.weekdays) it.weekdays - action.day else it.weekdays + action.day
                it.copy(weekdays = weekdays)
            }

            is ProtocolFormAction.Pick.OnReminderBucketSelected ->
                updateDraft { it.copy(reminderBucket = action.bucket) }

            is ProtocolFormAction.Pick.OnBodyRegionSelected -> {
                _state.update { it.copy(openPicker = null) }
                updateDraft { it.copy(siteRestriction = action.region) }
            }
        }
    }

    private fun onOverlay(action: ProtocolFormAction.Overlay) {
        when (action) {
            is ProtocolFormAction.Overlay.OnPickerOpen -> _state.update { it.copy(openPicker = action.picker) }
            ProtocolFormAction.Overlay.OnPickerDismiss ->
                _state.update { it.copy(openPicker = null, pickerQuery = "") }

            is ProtocolFormAction.Overlay.OnPickerQueryChange -> _state.update {
                it.copy(pickerQuery = action.query, pickerCompounds = pickerRows(action.query))
            }

            is ProtocolFormAction.Overlay.OnDateFieldClick -> _state.update { it.copy(openDateField = action.field) }
            ProtocolFormAction.Overlay.OnDatePickerDismiss -> _state.update { it.copy(openDateField = null) }
            is ProtocolFormAction.Overlay.OnDateSelected -> onDateSelected(action.date)

            ProtocolFormAction.Overlay.OnDiscardDismiss -> _state.update { it.copy(isDiscardDialogOpen = false) }
            ProtocolFormAction.Overlay.OnArchiveDismiss -> _state.update { it.copy(isArchiveDialogOpen = false) }
        }
    }

    /**
     * §4.9.3 Duration. The End box clears to "Open-ended" on a null, which is what dismissing the
     * picker with nothing chosen means; the Start box cannot be cleared, since a protocol without a
     * start has no day 0 for its schedule to count from.
     */
    private fun onDateSelected(date: LocalDate?) {
        val field = _state.value.openDateField
        _state.update { it.copy(openDateField = null) }
        when (field) {
            ProtocolDateField.START -> date?.let { picked ->
                updateDraft(ProtocolFormField.START_DATE) { it.copy(startDate = picked) }
            }

            ProtocolDateField.END -> updateDraft(ProtocolFormField.END_DATE) { it.copy(endDate = date) }
            null -> Unit
        }
    }

    // -----------------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------------

    private fun observeCompounds() {
        viewModelScope.launch {
            compoundRepository.observeAll().collect { all ->
                compounds = all
                _state.update {
                    it.copy(
                        pickerCompounds = pickerRows(it.pickerQuery),
                        isPickerSearchable = all.size > PICKER_SEARCH_THRESHOLD,
                    )
                }
                // The card, the dose units and the whole forecast hang off the picked compound, and
                // it only becomes readable once this list arrives.
                refreshDerived()
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.observe().collect { settings ->
                _state.update { it.copy(notificationStyle = settings.notificationStyle) }
            }
        }
    }

    /**
     * Loads the protocol being edited and makes it the baseline. A draft restored from process death
     * still wins for the fields — the user's unsaved edits are the newer truth — while the app bar's
     * name and the carried-through domain fields come from the row either way.
     */
    private fun load(protocolId: Long) {
        viewModelScope.launch {
            val protocol = protocolRepository.observeById(protocolId).first()
            if (protocol == null) {
                _events.send(ProtocolFormEvent.ShowError(DataError.Local.NOT_FOUND.toUiText()))
                _events.send(ProtocolFormEvent.Done)
                return@launch
            }
            loaded = protocol
            baseline = protocol.toDraft()
            _state.update {
                it.copy(
                    draft = savedDraft ?: baseline,
                    editedProtocolName = protocol.name,
                    isLoading = false,
                    isDirty = (savedDraft ?: baseline).differsFrom(baseline),
                )
            }
            refreshDerived()
        }
    }

    /**
     * §4.9.3: picking a compound defaults the route to the one that compound is normally taken by,
     * and the dose unit to a unit of its own family — but only while the user has not set either by
     * hand. A dose typed in `mcg` is not silently reinterpreted as `mg` because the compound changed.
     */
    private fun selectCompound(compoundId: Long) {
        val compound = compounds.firstOrNull { it.id == compoundId } ?: return
        val units = compound.doseUnitOptions()
        updateDraft(ProtocolFormField.COMPOUND) { draft ->
            draft.copy(
                compoundSupplyId = compoundId,
                route = if (ProtocolFormField.ROUTE in draft.touched) {
                    draft.route
                } else {
                    compound.form.defaultRoute()
                },
                // The unit is replaced when the new compound does not offer it, even if the user
                // chose it: a selection missing from its own list is worse than a re-picked default.
                doseUnit = if (draft.doseUnit in units && ProtocolFormField.DOSE in draft.touched) {
                    draft.doseUnit
                } else {
                    units.first()
                },
            )
        }
    }

    // -----------------------------------------------------------------------
    // Derived state — the card, the chip, the preview and the forecast (§4.9.3)
    // -----------------------------------------------------------------------

    private fun updateDraft(field: ProtocolFormField? = null, transform: (ProtocolFormDraft) -> ProtocolFormDraft) {
        _state.update { current ->
            val edited = transform(current.draft)
            val draft = if (field == null) edited else edited.copy(touched = edited.touched + field)
            savedDraft = draft
            current.copy(
                draft = draft,
                // An error the user is busy fixing should stop shouting.
                errors = if (field == null) current.errors else (current.errors - field).toImmutableMap(),
                isDirty = draft.differsFrom(baseline),
            )
        }
        refreshDerived()
    }

    private fun refreshDerived() {
        _state.update { current ->
            val draft = current.draft
            val compound = compounds.firstOrNull { it.id == draft.compoundSupplyId }
            current.copy(
                compound = compound?.toPickUi(),
                doseUnitOptions = (compound?.doseUnitOptions() ?: listOf(draft.doseUnit)).toImmutableList(),
                equivalence = equivalenceOf(draft, compound),
                preview = previewOf(draft),
                forecast = forecastOf(draft, compound),
            )
        }
    }

    /** §4.9.3's equivalence chip — only offered when the compound states a concentration to derive it from. */
    private fun equivalenceOf(draft: ProtocolFormDraft, compound: CompoundSupply?): DoseEquivalenceUi? {
        val concentration = compound?.concentration ?: return null
        val dose = draft.plannedDoseOrNull()?.takeIf { it.value > ZERO } ?: return null
        val volume = dose.dividedBy(concentration) ?: return null
        return DoseEquivalenceUi(
            volume = volume.value.raw.setScale(VOLUME_SCALE, RoundingMode.HALF_UP).toPlainString(),
            volumeUnit = volume.unit,
            // Insulin syringes are graduated in hundredths of a millilitre; anything else has no
            // "units" to speak of, so the second half of the chip simply is not there.
            insulinUnits = volume.value.raw
                .takeIf { volume.unit == UnitCode.ML }
                ?.multiply(BigDecimal(INSULIN_UNITS_PER_ML))
                ?.setScale(0, RoundingMode.HALF_UP)
                ?.toInt(),
        )
    }

    /**
     * 11b's next-7-days strip. It walks the same horizon Save will generate and the same rule the
     * generator uses (§5.2), so the count it shows is the number of Pending rows the save produces.
     */
    private fun previewOf(draft: ProtocolFormDraft): SchedulePreviewUi? {
        val protocol = draft.toPreviewProtocol() ?: return null
        val today = today()
        val from = maxOf(today, protocol.startDate)
        val days = List(SCHEDULE_HORIZON_DAYS) { offset ->
            val date = from.plus(offset, DateTimeUnit.DAY)
            PreviewDayUi(
                date = date,
                hasDose = protocol.dosingTimesOn(date).isNotEmpty(),
                isToday = date == today,
            )
        }
        return SchedulePreviewUi(
            doseCount = protocol.dosesBetween(from, from.plus(SCHEDULE_HORIZON_DAYS, DateTimeUnit.DAY)),
            days = days.toImmutableList(),
        )
    }

    /**
     * §4.9.3's forecast, plus 11b's reorder row. Null while the numbers it needs are missing: a
     * forecast that guessed at a half-typed dose would flicker nonsense as the user types.
     *
     * "Doses left" is the compound's whole stock — sealed containers plus what is left in the opened
     * one — divided by one planned dose. The dose is converted into the stock's unit first, so a
     * `mcg` protocol against a `mg` vial still divides correctly (§3.0.4).
     */
    private fun forecastOf(draft: ProtocolFormDraft, compound: CompoundSupply?): ProtocolForecastUi? {
        val supply = compound ?: return null
        val protocol = draft.toPreviewProtocol() ?: return null
        val stock = supply.totalStock()
        val dose = draft.plannedDoseOrNull()
            ?.convertedTo(stock.unit)
            ?.takeIf { it.value > ZERO }
            ?: return null

        val dosesLeft = stock.value.raw
            .divideToIntegralValue(dose.value.raw)
            .min(BigDecimal(MAX_FORECAST_DOSES))
            .toInt()
            .coerceAtLeast(0)

        val today = today()
        val runOut = protocol.runOutDate(from = maxOf(today, protocol.startDate), doses = dosesLeft)

        return ProtocolForecastUi(
            dosesLeft = dosesLeft,
            daysLeft = runOut?.let { today.daysUntil(it) },
            runOutDate = runOut,
            expiryWarning = supply.batchExpiryDate
                ?.takeIf { expiry -> runOut != null && expiry < runOut }
                ?.let { ExpiryWarningUi(batchExpiry = it, runOut = requireNotNull(runOut)) },
            reorder = reorderHintOf(protocol, supply, dose, runOut, today),
        )
    }

    /**
     * 11b's "Order N more vials by …" row: the protocol has an end date it cannot reach on the stock
     * in hand. Null for an open-ended protocol — there is no "enough" to compute against — and null
     * when the stock already outlasts the end date.
     */
    private fun reorderHintOf(
        protocol: Protocol,
        supply: CompoundSupply,
        dose: Quantity,
        runOut: LocalDate?,
        today: LocalDate,
    ): ReorderHintUi? {
        val endDate = protocol.endDate ?: return null
        if (runOut == null || runOut > endDate) return null

        val shortfallDoses = protocol.dosesBetween(runOut, endDate.plus(1, DateTimeUnit.DAY))
        if (shortfallDoses <= 0) return null

        val shortfall = dose.value.raw.multiply(BigDecimal(shortfallDoses))
        val containers = shortfall
            .divide(supply.amountPerContainer.value.raw, 0, RoundingMode.CEILING)
            .toInt()
        if (containers <= 0) return null

        return ReorderHintUi(
            containers = containers,
            containerType = supply.containerType,
            // Ordering *on* the run-out day is ordering too late, so the row asks for it a week
            // earlier — the shipping slack a reorder actually needs.
            // ponytail: fixed lead time; make it a Settings field if users need per-supplier slack.
            orderBy = maxOf(today, runOut.plus(-REORDER_LEAD_DAYS, DateTimeUnit.DAY)),
            coversUntil = endDate,
        )
    }

    /**
     * The day the [doses]th dose is taken, walking this protocol's schedule from [from]. Null when
     * the stock outlives [FORECAST_MAX_DAYS] — or when the schedule places no doses at all, which is
     * a schedule Save will reject anyway.
     */
    private fun Protocol.runOutDate(from: LocalDate, doses: Int): LocalDate? {
        var remaining = doses
        var date = from
        var walked = 0
        while (walked <= FORECAST_MAX_DAYS) {
            remaining -= dosingTimesOn(date).size
            if (remaining <= 0) return date
            date = date.plus(1, DateTimeUnit.DAY)
            walked++
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Save (§4.9.7) + Lifecycle (§4.9.5)
    // -----------------------------------------------------------------------

    /**
     * Validates, then writes (§4.9.7). A failure marks every offending field at once but scrolls to
     * the first: the user is told everything that is wrong and taken to the first thing to fix.
     *
     * Create inserts and generates the 7-day Pending horizon; Edit updates and runs §5.4's
     * pending-regen scope rule. Both belong to `ProtocolRepository`, which is why neither appears here.
     */
    private fun save() {
        val draft = _state.value.draft
        val errors = validate(draft)
        if (errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    errors = errors.toImmutableMap(),
                    scrollToError = ProtocolFormField.entries.first { field -> field in errors },
                )
            }
            return
        }
        val protocol = draft.toProtocol(id = args.protocolId ?: 0L) ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errors = persistentMapOf()) }
            val result: Result<*, DataError.Local> = if (args.protocolId == null) {
                protocolRepository.create(protocol)
            } else {
                protocolRepository.update(protocol)
            }
            when (result) {
                is Result.Success -> finish()
                is Result.Error -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(ProtocolFormEvent.ShowError(result.error.toUiText()))
                }
            }
        }
    }

    /**
     * §4.9.5 Duplicate. It copies what is on screen rather than what is stored — the user is looking
     * at the form, and a duplicate that quietly dropped their unsaved edits would be the surprising
     * one. `create` is the whole of it: a new protocol with a new id generates its own horizon.
     */
    private fun duplicate() {
        val draft = _state.value.draft
        val original = _state.value.editedProtocolName
        val protocol = draft.toProtocol(id = 0L, name = "$original$COPY_SUFFIX") ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = protocolRepository.create(protocol)) {
                is Result.Success -> finish()
                is Result.Error -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(ProtocolFormEvent.ShowError(result.error.toUiText()))
                }
            }
        }
    }

    /** §4.9.5 Pause / Archive: one write against the stored protocol, then the form is done with. */
    private fun lifecycle(write: suspend (Long) -> EmptyResult<DataError.Local>) {
        val protocolId = args.protocolId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = write(protocolId)) {
                is Result.Success -> finish()
                is Result.Error -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(ProtocolFormEvent.ShowError(result.error.toUiText()))
                }
            }
        }
    }

    /** Drops the auto-saved draft and closes. Leaving it behind would resurrect it on the next Create. */
    private fun finish() {
        savedDraft = null
        _state.update { it.copy(isDiscardDialogOpen = false, isArchiveDialogOpen = false) }
        viewModelScope.launch { _events.send(ProtocolFormEvent.Done) }
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    /**
     * The required fields of §4.9.3 — Compound, Planned dose, Schedule and Start — plus the two
     * rules §3.2 puts on a schedule: its count is at least one, and a weekday schedule selects a day.
     */
    private fun validate(draft: ProtocolFormDraft): Map<ProtocolFormField, ProtocolFormError> = buildMap {
        if (draft.compoundSupplyId == null || compounds.none { it.id == draft.compoundSupplyId }) {
            put(ProtocolFormField.COMPOUND, ProtocolFormError.COMPOUND_REQUIRED)
        }

        val dose = draft.plannedDoseOrNull()
        if (dose == null || validateProtocolPlannedDose(dose).failure() != null) {
            put(ProtocolFormField.DOSE, ProtocolFormError.DOSE_NOT_POSITIVE)
        }

        scheduleCountError(draft)?.let { put(ProtocolFormField.SCHEDULE_COUNT, it) }

        if (draft.scheduleType == ScheduleType.SPECIFIC_WEEKDAYS &&
            validateScheduleSelectedWeekdays(draft.weekdays).failure() != null
        ) {
            put(ProtocolFormField.WEEKDAYS, ProtocolFormError.WEEKDAYS_REQUIRED)
        }

        val startDate = draft.startDate
        if (validateProtocolStartDate(startDate).failure() != null) {
            put(ProtocolFormField.START_DATE, ProtocolFormError.START_DATE_REQUIRED)
        } else if (validateProtocolEndDate(requireNotNull(startDate), draft.endDate).failure() != null) {
            put(ProtocolFormField.END_DATE, ProtocolFormError.END_DATE_NOT_AFTER_START)
        }
    }

    /**
     * The count the selected chip owns. `X×/week` and `X×/month` have no domain validator of their
     * own, so they are held to the same "at least one, and not more than the cycle" rule the
     * generator's cycle spreading assumes.
     */
    private fun scheduleCountError(draft: ProtocolFormDraft): ProtocolFormError? {
        val count = draft.scheduleCount()?.trim()?.toIntOrNull()
        val invalid = when (draft.scheduleType) {
            ScheduleType.DAILY, ScheduleType.SPECIFIC_WEEKDAYS -> false
            ScheduleType.EVERY_X_DAYS -> count == null || validateScheduleInterval(count).failure() != null
            ScheduleType.X_TIMES_PER_DAY -> count == null || validateScheduleTimesPerDay(count).failure() != null
            ScheduleType.X_TIMES_PER_WEEK -> count == null || count !in 1..DAYS_PER_WEEK
            ScheduleType.X_TIMES_PER_MONTH -> count == null || count !in 1..DAYS_PER_MONTH
        }
        return ProtocolFormError.SCHEDULE_COUNT_INVALID.takeIf { invalid }
    }

    // -----------------------------------------------------------------------
    // Domain ↔ draft
    // -----------------------------------------------------------------------

    /**
     * The protocol to write. Everything §4.9.3 has no control for is carried through from [loaded],
     * so an edit never drops a titration, a break cycle or a per-protocol site cooldown.
     *
     * §4.9.3 has no name field, so a created protocol is named after what identifies it on the list
     * (§4.7.3): its compound. An edit keeps whatever the protocol is already called.
     */
    private fun ProtocolFormDraft.toProtocol(id: Long, name: String? = null): Protocol? {
        val compound = compounds.firstOrNull { it.id == compoundSupplyId } ?: return null
        val plannedDose = plannedDoseOrNull() ?: return null
        val startDate = startDate ?: return null
        val timestamp = now()
        val existing = loaded
        return Protocol(
            id = id,
            name = name ?: existing?.name ?: compound.name,
            compoundSupplyId = compound.id,
            plannedDose = plannedDose,
            route = route,
            schedule = toSchedule(),
            dosageTimes = dosageTimes,
            escalation = existing?.escalation,
            protocolBreak = existing?.protocolBreak,
            startDate = startDate,
            endDate = endDate,
            reminderEnabled = reminderEnabled,
            reminderOffsetMinutes = reminderOffsetMinutes,
            // §3.2: the bucket is what an alarm hangs off when there is no time of day to use.
            reminderBucket = reminderBucket.takeIf { reminderEnabled && dosageTimes.isEmpty() },
            injectionSiteRestriction = siteRestriction,
            siteCooldownDays = existing?.siteCooldownDays,
            notes = notes.trim().ifBlank { null },
            // A duplicate starts Active however the original ended up (§4.7.4).
            status = if (id == 0L) ProtocolStatus.ACTIVE else existing?.status ?: ProtocolStatus.ACTIVE,
            deletedAt = null,
            // The repository stamps both on write (§5.8.5); these are the values it replaces.
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp,
        )
    }

    /**
     * The draft as a protocol good enough to read a schedule off — which is all the preview and the
     * forecast ask of it. It exists because those two answer questions about a schedule long before
     * the rest of the form is fillable, and `dosingTimesOn` reads none of the rest.
     */
    private fun ProtocolFormDraft.toPreviewProtocol(): Protocol? {
        val startDate = startDate ?: return null
        val timestamp = now()
        return Protocol(
            id = args.protocolId ?: 0L,
            name = "",
            compoundSupplyId = compoundSupplyId ?: 0L,
            plannedDose = plannedDoseOrNull() ?: Quantity(ZERO, doseUnit),
            route = route,
            schedule = toSchedule(),
            dosageTimes = dosageTimes,
            escalation = null,
            protocolBreak = loaded?.protocolBreak,
            startDate = startDate,
            endDate = endDate,
            reminderEnabled = reminderEnabled,
            reminderOffsetMinutes = reminderOffsetMinutes,
            reminderBucket = null,
            injectionSiteRestriction = siteRestriction,
            siteCooldownDays = null,
            notes = null,
            // The rule yields nothing for a non-Active protocol (§5.2), and a paused protocol being
            // edited still has a schedule to preview — so the preview always asks as an Active one.
            status = ProtocolStatus.ACTIVE,
            deletedAt = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    /** The stored protocol as form fields — the reverse of [toProtocol], and the discard baseline. */
    private fun Protocol.toDraft() = ProtocolFormDraft(
        compoundSupplyId = compoundSupplyId,
        route = route,
        doseAmount = plannedDose.value.toPlainString(),
        doseUnit = plannedDose.unit,
        dosageTimes = dosageTimes,
        startDate = startDate,
        endDate = endDate,
        reminderEnabled = reminderEnabled,
        reminderOffsetMinutes = reminderOffsetMinutes,
        reminderBucket = reminderBucket ?: ProtocolFormDraft().reminderBucket,
        siteRestriction = injectionSiteRestriction,
        notes = notes.orEmpty(),
    ).withSchedule(schedule)

    private fun pickerRows(query: String) = compounds
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .map { it.toPickUi() }
        .toImmutableList()

    private fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    /**
     * §4.9.3's Compound card and §4.0.2's picker rows show the same meta, so they read the same model.
     * The pieces stay pieces — the screen owns how "Peptide · 5 mg vial · 2.5 mg/mL" is worded.
     */
    private fun CompoundSupply.toPickUi() = CompoundPickUi(
        id = id,
        name = name,
        category = category,
        containerType = containerType,
        amount = amountPerContainer.value.toPlainString(),
        amountUnit = amountPerContainer.unit,
        concentration = concentration?.amount?.value?.toPlainString(),
        concentrationUnit = concentration?.amount?.unit,
        concentrationPerUnit = concentration?.per?.unit,
    )

    private companion object {
        const val DRAFT_KEY = "protocol-form-draft"
        const val COPY_SUFFIX = " (copy)"

        /** §4.0.2: the picker's search field appears only past this many rows. */
        const val PICKER_SEARCH_THRESHOLD = 5

        /** Beyond this the forecast reports "no run-out in sight" rather than a date nobody plans around. */
        const val FORECAST_MAX_DAYS = 730

        /** A dose count above this is not a forecast, it is an overflow waiting to happen. */
        const val MAX_FORECAST_DOSES = 99_999

        /** 11b's reorder row asks for the order this many days before the run-out. */
        const val REORDER_LEAD_DAYS = 7

        const val INSULIN_UNITS_PER_ML = 100
        const val VOLUME_SCALE = 2
        const val DAYS_PER_WEEK = 7
        const val DAYS_PER_MONTH = 31
    }
}

// ---------------------------------------------------------------------------
// Compound helpers — each of them a conversion that can legitimately fail
// ---------------------------------------------------------------------------

/** The failing code of a validator, or null when it passed. */
private fun EmptyResult<ValidationError>.failure(): ValidationError.Code? =
    (this as? Result.Error)?.error as? ValidationError.Code

/**
 * The units the dose pill offers (§4.9.3's "mg/mcg/IU dropdown"): the compound's own family, so a
 * dose is always convertible into the stock it is drawn from, and a vial of mg never offers tablets.
 */
internal fun CompoundSupply.doseUnitOptions(): List<UnitCode> = when (primaryUnit.family) {
    UnitFamily.MASS -> listOf(UnitCode.MG, UnitCode.MCG, UnitCode.G)
    else -> listOf(primaryUnit)
}

/**
 * Everything the user physically has: the sealed containers plus what is left in the opened one.
 * Counting the opened container as full would overstate the stock by however much is already used.
 */
internal fun CompoundSupply.totalStock(): Quantity {
    val sealed = amountPerContainer * Decimal.parse(numberOfContainers.coerceAtLeast(0).toString())
    val opened = currentOpened?.remainingAmount?.convertedTo(sealed.unit)
    return if (opened == null) sealed else sealed + opened
}

/** Null rather than a throw when the units are of different families (a tub of grams has no millilitres). */
internal fun Quantity.convertedTo(target: UnitCode): Quantity? = try {
    Quantity(unit.convertTo(target, value), target)
} catch (_: IllegalArgumentException) {
    null
}

/** Null rather than a throw when the dose and the concentration are not of the same family. */
internal fun Quantity.dividedBy(concentration: Concentration): Quantity? = try {
    this / concentration
} catch (_: IllegalArgumentException) {
    null
}
