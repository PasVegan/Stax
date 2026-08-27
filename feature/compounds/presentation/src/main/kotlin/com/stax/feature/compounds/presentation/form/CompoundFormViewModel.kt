package com.stax.feature.compounds.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.UnitCode
import com.stax.core.domain.UnitFamily
import com.stax.core.domain.ValidationError
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.validateCompoundSupplyAmountPerContainer
import com.stax.core.domain.validateCompoundSupplyConcentration
import com.stax.core.domain.validateCompoundSupplyName
import com.stax.core.domain.validateCompoundSupplyNumberOfContainers
import com.stax.core.presentation.UiText
import com.stax.core.presentation.toUiText
import com.stax.feature.compounds.presentation.R
import com.stax.feature.compounds.presentation.container.OpenedContainerDateField
import com.stax.feature.compounds.presentation.container.OpenedContainerSaveError
import com.stax.feature.compounds.presentation.container.OpenedContainerSheetAction
import com.stax.feature.compounds.presentation.container.OpenedContainerSheetState
import kotlinx.collections.immutable.persistentMapOf
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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** What the route tells the form: which compound to edit (null = create), and whether this is onboarding step 2. */
data class CompoundFormArgs(val compoundId: Long? = null, val isOnboarding: Boolean = false)

/**
 * MVI ViewModel for the Create / Edit Compound form (§4.4, §10.1).
 *
 * One ViewModel serves both modes: [CompoundFormArgs.compoundId] decides whether the form starts
 * empty and Saves through `create`, or loads a compound and Saves through `update`. The compound is
 * read **once** rather than observed — a form that reloaded under the user's hands would discard
 * what they were typing the moment anything else touched the row.
 *
 * The draft is mirrored into the `SavedStateHandle` on every edit, which is what §4.4.5's "auto-save
 * draft on backgrounding" has to mean in practice: a ViewModel already survives backgrounding, so
 * only a `SavedStateHandle` makes the form survive the process death that can follow it. A restored
 * draft always beats the stored compound, or resuming would silently revert the user's edits.
 *
 * [now] and [timeZone] are parameters so "opened 12 days ago" is testable without freezing the
 * system clock; production resolves the defaults.
 */
class CompoundFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val compoundRepository: CompoundRepository,
    private val args: CompoundFormArgs,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /**
     * The auto-saved draft (§4.4.5). Null means nothing has been drafted yet — the form starts from
     * its defaults in Create mode, or from the stored compound in Edit mode. Cleared once the form is
     * done with, so opening Create again is a clean form rather than the last one abandoned.
     */
    private var savedDraft: CompoundFormDraft? by savedStateHandle.saved(key = DRAFT_KEY) { null }

    /** What the form was loaded with. The discard prompt (§4.4.5) is "the draft differs from this". */
    private var baseline = CompoundFormDraft()

    /**
     * The compound's opened container, kept in domain form because it is read from three sides: Save
     * takes one off the total-owned count (§4.4.4) and passes it through, and the §4.5 sheet both
     * opens on it and replaces it. During the New Compound flow this is the staged container — the
     * one with nowhere to be written yet (§4.5.5).
     */
    private var openedContainer: OpenedContainer? = null

    private val _state = MutableStateFlow(
        (savedDraft ?: CompoundFormDraft()).let { draft ->
            CompoundFormState(
                draft = draft,
                isEdit = args.compoundId != null,
                isOnboarding = args.isOnboarding,
                isLoading = args.compoundId != null,
                // A draft that came back from process death carries the user's unsaved edits with
                // it, so the form is already dirty before they have touched anything (§4.4.5).
                isDirty = draft.differsFrom(baseline),
            )
        },
    )
    val state = _state.asStateFlow()

    private val _events = Channel<CompoundFormEvent>()
    val events = _events.receiveAsFlow()

    init {
        args.compoundId?.let(::load)
        refreshForecast()
    }

    fun onAction(action: CompoundFormAction) {
        when (action) {
            is CompoundFormAction.Edit -> onEdit(action)
            is CompoundFormAction.Pick -> onPick(action)

            CompoundFormAction.OnSaveClick -> save()

            is CompoundFormAction.OnContainerShrinkDecision -> onContainerShrinkDecision(action.decision)

            is CompoundFormAction.OpenedContainerSheet -> onSheetAction(action.action)

            is CompoundFormAction.OnNaturalDepletionDecision -> onNaturalDepletionDecision(action.openNew)

            // §4.4.5: leaving a form the user has changed confirms first; an untouched one just closes.
            CompoundFormAction.OnCancelClick -> if (_state.value.isDirty) {
                _state.update { it.copy(isDiscardDialogOpen = true) }
            } else {
                finish()
            }

            CompoundFormAction.OnDiscardConfirm -> finish()

            // Skip is already an explicit "not now" (§4.14), so it does not ask again about discarding.
            CompoundFormAction.OnSkipClick -> finish()

            CompoundFormAction.OnReconstitutionHelperClick -> viewModelScope.launch {
                _events.send(CompoundFormEvent.OpenReconstitutionHelper(args.compoundId))
            }

            CompoundFormAction.OnErrorScrollHandled -> _state.update { it.copy(scrollToError = null) }

            is CompoundFormAction.Overlay -> onOverlay(action)
        }
    }

    /** The menus, the date picker and the prompts — the parts of the form that open and close. */
    private fun onOverlay(action: CompoundFormAction.Overlay) {
        when (action) {
            is CompoundFormAction.Overlay.OnPickerOpen -> _state.update { it.copy(openPicker = action.picker) }
            CompoundFormAction.Overlay.OnPickerDismiss -> _state.update { it.copy(openPicker = null) }

            CompoundFormAction.Overlay.OnBatchExpiryClick -> _state.update { it.copy(isDatePickerOpen = true) }
            CompoundFormAction.Overlay.OnBatchExpiryDismiss -> _state.update { it.copy(isDatePickerOpen = false) }
            is CompoundFormAction.Overlay.OnBatchExpirySelected -> {
                _state.update { it.copy(isDatePickerOpen = false) }
                updateDraft { it.copy(batchExpiryDate = action.date) }
            }

            // The section and its CTA are §4.4.3's; what the CTA opens is §4.5's sheet, seeded from
            // the container if there is one and from the form's own fields if there is not.
            CompoundFormAction.Overlay.OnOpenedContainerClick -> _state.update { it.copy(openedSheet = sheetState()) }

            CompoundFormAction.Overlay.OnDiscardDismiss -> _state.update { it.copy(isDiscardDialogOpen = false) }
        }
    }

    private fun onEdit(action: CompoundFormAction.Edit) = when (action) {
        is CompoundFormAction.Edit.OnNameChange ->
            updateDraft(CompoundFormField.NAME) { it.copy(name = action.name) }

        is CompoundFormAction.Edit.OnTotalContainersChange ->
            updateDraft(CompoundFormField.TOTAL_CONTAINERS) { it.copy(totalContainers = action.value) }

        is CompoundFormAction.Edit.OnAmountPerContainerChange ->
            updateDraft(CompoundFormField.AMOUNT_PER_CONTAINER) { it.copy(amountPerContainer = action.value) }

        is CompoundFormAction.Edit.OnConcentrationChange ->
            updateDraft(CompoundFormField.CONCENTRATION) { it.copy(concentrationAmount = action.value) }

        // §4.6.7: the helper's units come with its figure, because "2.5" means nothing without them —
        // a vial measured in IU comes back as IU/mL, and typing the number alone into a mg/mL row
        // would be a thousandfold error dressed up as a default.
        is CompoundFormAction.Edit.OnConcentrationCalculated ->
            updateDraft(CompoundFormField.CONCENTRATION) {
                it.copy(
                    concentrationAmount = action.concentration.amount.value.toPlainString(),
                    concentrationUnit = action.concentration.amount.unit,
                    concentrationPerUnit = action.concentration.per.unit,
                )
            }

        is CompoundFormAction.Edit.OnBatchNumberChange -> updateDraft { it.copy(batchNumber = action.value) }
        is CompoundFormAction.Edit.OnSupplierChange -> updateDraft { it.copy(supplier = action.value) }
        is CompoundFormAction.Edit.OnNotesChange -> updateDraft { it.copy(notes = action.value) }

        is CompoundFormAction.Edit.OnExpiryAfterOpeningDaysChange ->
            updateDraft { it.copy(expiryAfterOpeningDays = action.value) }
    }

    private fun onPick(action: CompoundFormAction.Pick) {
        _state.update { it.copy(openPicker = null) }
        when (action) {
            is CompoundFormAction.Pick.OnCategorySelected -> updateDraft { it.copy(category = action.category) }

            // The one pick that fills other fields in (§4.4.3).
            is CompoundFormAction.Pick.OnFormSelected -> updateDraft { it.applySmartDefaults(action.form) }

            is CompoundFormAction.Pick.OnContainerTypeSelected ->
                updateDraft(CompoundFormField.CONTAINER_TYPE) { it.copy(containerType = action.containerType) }

            is CompoundFormAction.Pick.OnStorageLocationSelected ->
                updateDraft { it.copy(storageLocation = action.storageLocation) }

            is CompoundFormAction.Pick.OnPrimaryUnitSelected ->
                updateDraft(CompoundFormField.AMOUNT_PER_CONTAINER) { it.copy(primaryUnit = action.unit) }

            is CompoundFormAction.Pick.OnConcentrationUnitSelected -> updateDraft(CompoundFormField.CONCENTRATION) {
                it.copy(concentrationUnit = action.units.amount, concentrationPerUnit = action.units.per)
            }
        }
    }

    /**
     * Loads the compound being edited and makes it the baseline. A draft restored from process death
     * still wins for the *fields* — the user's unsaved edits are the newer truth — while the opened
     * container and the app bar's name come from the row either way, since neither is editable here.
     */
    private fun load(compoundId: Long) {
        viewModelScope.launch {
            val compound = compoundRepository.observeById(compoundId).first()
            if (compound == null) {
                _events.send(CompoundFormEvent.ShowError(DataError.Local.NOT_FOUND.toUiText()))
                _events.send(CompoundFormEvent.Done)
                return@launch
            }
            baseline = compound.toDraft()
            openedContainer = compound.currentOpened
            _state.update {
                it.copy(
                    draft = savedDraft ?: baseline,
                    editedCompoundName = compound.name,
                    opened = compound.currentOpened?.toUi(compound.amountPerContainer, compound.containerType),
                    isLoading = false,
                    isDirty = (savedDraft ?: baseline).differsFrom(baseline),
                )
            }
            refreshForecast()
        }
    }

    /**
     * Applies an edit, auto-saves it, and re-derives what follows from it.
     *
     * [field] marks the edit as the user's own: it clears that field's inline error (§4.4.4 — an
     * error the user is busy fixing should stop shouting) and protects the field from smart defaults
     * (§4.4.3).
     */
    private fun updateDraft(field: CompoundFormField? = null, transform: (CompoundFormDraft) -> CompoundFormDraft) {
        _state.update { current ->
            val edited = transform(current.draft)
            val draft = if (field == null) edited else edited.copy(touched = edited.touched + field)
            savedDraft = draft
            current.copy(
                draft = draft,
                errors = if (field == null) current.errors else (current.errors - field).toImmutableMap(),
                isDirty = draft.differsFrom(baseline),
            )
        }
        refreshForecast()
    }

    private fun refreshForecast() {
        _state.update { it.copy(forecast = forecastOf(it.draft)) }
    }

    /**
     * Validates, then writes (§4.4.4). A failure marks every offending field at once but scrolls to
     * the first: the user is told everything that is wrong and taken to the first thing to fix.
     *
     * A container that has been shrunk below what is still open in it is not a validation failure —
     * both answers are legal — so it stops the write to ask rather than to complain.
     */
    private fun save() {
        val current = _state.value
        val errors = validate(current.draft, isConcentrationRequired = current.isConcentrationRequired)
        if (errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    errors = errors.toImmutableMap(),
                    scrollToError = CompoundFormField.entries.first { field -> field in errors },
                )
            }
            return
        }

        val shrink = shrinkPromptFor(current.draft)
        if (shrink != null) {
            _state.update { it.copy(shrinkPrompt = shrink, errors = persistentMapOf()) }
            return
        }
        persist(current.draft, capOpenedContainer = false)
    }

    /**
     * §4.4.4 Edit case: the prompt to raise when the new container size no longer holds what is left
     * in the opened one.
     *
     * Null whenever there is nothing to ask about — no opened container, an amount that is not a
     * number yet, or a container that still fits. Also null when the two cannot be compared at all: a
     * tub of grams has no millilitres, so an edit that changed the family has not "shrunk" anything
     * this dialog could offer to cap.
     */
    private fun shrinkPromptFor(draft: CompoundFormDraft): ContainerShrinkPromptUi? {
        val remaining = openedContainer?.remainingAmount ?: return null
        val newAmount = draft.amountPerContainerOrNull() ?: return null
        val comparable = remaining.convertedTo(newAmount.unit) ?: return null
        if (comparable.value <= newAmount.value) return null
        return ContainerShrinkPromptUi(remaining = remaining.toString(), newAmount = newAmount.toString())
    }

    /**
     * §4.4.4 Edit case. Keep and Cap both go on to save — they differ only in what happens to the
     * opened container — while Cancel puts the amount back where it was and writes nothing.
     */
    private fun onContainerShrinkDecision(decision: ContainerShrinkDecision) {
        val draft = _state.value.draft
        _state.update { it.copy(shrinkPrompt = null) }
        when (decision) {
            ContainerShrinkDecision.KEEP -> persist(draft, capOpenedContainer = false)
            ContainerShrinkDecision.CAP -> persist(draft, capOpenedContainer = true)
            // The unit comes back too: the amount alone is meaningless without the unit it was typed
            // against, and either of the two may be what shrank the container.
            ContainerShrinkDecision.CANCEL -> updateDraft {
                it.copy(amountPerContainer = baseline.amountPerContainer, primaryUnit = baseline.primaryUnit)
            }
        }
    }

    /** The write itself, once §4.4.4 has nothing left to ask. */
    private fun persist(draft: CompoundFormDraft, capOpenedContainer: Boolean) {
        val compound = draft.toCompound()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errors = persistentMapOf()) }
            val result: Result<*, DataError.Local> = if (args.compoundId == null) {
                compoundRepository.create(compound)
            } else {
                compoundRepository.update(compound, capOpenedContainer)
            }
            when (result) {
                is Result.Success -> finish()
                is Result.Error -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(CompoundFormEvent.ShowError(result.error.toUiText()))
                }
            }
        }
    }

    /** Drops the auto-saved draft and closes. Leaving it behind would resurrect it on the next Create. */
    private fun finish() {
        savedDraft = null
        _state.update { it.copy(isDiscardDialogOpen = false) }
        viewModelScope.launch { _events.send(CompoundFormEvent.Done) }
    }

    // -----------------------------------------------------------------------
    // Opened container sheet (§4.5)
    // -----------------------------------------------------------------------

    /**
     * The sheet as §4.5.3 opens it: the stored container's own values in the Edit variant, and the
     * form's current fields as defaults in Create Already Opened — today, a full container, and the
     * expiry `expiryAfterOpeningDays` implies.
     *
     * The unit is the container's own rather than the form's whenever there is a container: the form
     * may hold an unsaved unit change, and reading `3.2 mg` back as `3.2 mcg` would be a
     * thousandfold error dressed up as a default.
     */
    private fun sheetState(): OpenedContainerSheetState {
        val draft = _state.value.draft
        val opened = openedContainer
        val today = today()
        val openedDate = opened?.openedAt?.toLocalDateTime(timeZone)?.date ?: today
        val expiry = opened?.userDefinedExpiryDate
            ?: opened?.predictedExpiryDate
            ?: draft.expiryAfterOpeningDaysOrNull()?.let { openedDate.plus(it, DateTimeUnit.DAY) }
        return OpenedContainerSheetState(
            isEdit = opened != null,
            containerType = draft.containerType,
            compoundName = draft.name.trim(),
            containerAmount = draft.amountPerContainer.trim(),
            unit = opened?.remainingAmount?.unit ?: draft.primaryUnit,
            openedDate = openedDate,
            openedDaysAgo = openedDate.daysUntil(today),
            remaining = opened?.remainingAmount?.value?.toPlainString() ?: draft.amountPerContainer,
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
        updateSheet { current ->
            when {
                date == null -> current.copy(openDatePicker = null)

                field == OpenedContainerDateField.OPENED -> {
                    val expiry = if (current.isExpiryAuto) {
                        _state.value.draft.expiryAfterOpeningDaysOrNull()?.let { date.plus(it, DateTimeUnit.DAY) }
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
     * §4.5.5. Which of the three writes it describes this is depends on what the form is: during New
     * Compound there is no compound to write to, so the fields are staged until "Save compound";
     * for a compound that already exists the sheet writes on its own, and the form's total-owned
     * count is re-read from what was written rather than guessed at.
     *
     * An empty container is not rejected — it is §4.5.5's natural depletion, which opens the
     * container and closes it again in one go, leaving the stock one container shorter.
     */
    private fun saveOpenedContainer(sheet: OpenedContainerSheetState) {
        val remaining = sheet.remaining.toDecimalOrNull()
        if (remaining == null || remaining < ZERO) {
            updateSheet { it.copy(hasRemainingError = true) }
            return
        }
        val expiryDays = _state.value.draft.expiryAfterOpeningDaysOrNull()
        val container = OpenedContainer(
            // An unchanged date keeps the instant it was opened at; only a date the user actually
            // moved is flattened to midnight, since a date field cannot say anything finer.
            openedAt = openedContainer?.openedAt?.takeIf { it.toLocalDateTime(timeZone).date == sheet.openedDate }
                ?: sheet.openedDate.atStartOfDayIn(timeZone),
            remainingAmount = Quantity(remaining, sheet.unit),
            expiryAfterOpeningDays = expiryDays,
            userDefinedExpiryDate = sheet.expiryDate.takeIf { !sheet.isExpiryAuto },
            predictedExpiryDate = expiryDays?.let { sheet.openedDate.plus(it, DateTimeUnit.DAY) },
        )
        val isDepleted = remaining <= ZERO
        if (args.compoundId == null) {
            stageOpenedContainer(container, isDepleted)
        } else {
            persistOpenedContainer(args.compoundId, container, isDepleted)
        }
    }

    /** §4.5.5 "Create during New Compound flow": nothing is written until the form itself saves. */
    private fun stageOpenedContainer(container: OpenedContainer, isDepleted: Boolean) {
        openedContainer = if (isDepleted) null else container
        _state.update { current ->
            // A container that was opened and emptied is one the user no longer has, so the
            // total-owned count loses it — the same arithmetic the persisted path reads back.
            val draft = if (isDepleted) current.draft.withOneFewerContainer() else current.draft
            savedDraft = draft
            current.copy(
                draft = draft,
                openedSheet = null,
                opened = openedContainer?.toUi(draft.amountPerContainerOrNull(), draft.containerType),
                // Staging a container is an unsaved change even when no field of the form moved.
                isDirty = true,
            )
        }
        refreshForecast()
        if (isDepleted) promptForNewContainer()
    }

    /** §4.5.5 for a compound that already exists: the sheet's Save is a write of its own. */
    private fun persistOpenedContainer(compoundId: Long, container: OpenedContainer, isDepleted: Boolean) {
        val existing = openedContainer
        runSheetWrite(compoundId, promptOnEmpty = isDepleted) {
            val opened = if (existing == null) {
                compoundRepository.addOpenedContainer(
                    compoundSupplyId = compoundId,
                    openedAt = container.openedAt,
                    remainingAmount = container.remainingAmount,
                    expiryAfterOpeningDays = container.expiryAfterOpeningDays,
                    userDefinedExpiryDate = container.userDefinedExpiryDate,
                )
            } else {
                compoundRepository.editOpenedContainer(
                    compoundSupplyId = compoundId,
                    openedAt = container.openedAt,
                    remainingAmount = container.remainingAmount,
                    expiryAfterOpeningDays = container.expiryAfterOpeningDays,
                    userDefinedExpiryDate = container.userDefinedExpiryDate,
                )
            }
            // Natural depletion (§4.5.5): the container is removed without `numberOfContainers` being
            // decremented a second time, which is exactly what closing it does.
            if (opened is Result.Success && isDepleted) {
                compoundRepository.closeContainer(compoundId, null)
            } else {
                opened
            }
        }
    }

    /** §4.5.4: the lost / discarded path. Staged or stored, the container simply stops existing. */
    private fun deleteOpenedContainer() {
        val compoundId = args.compoundId
        if (compoundId == null || openedContainer == null) {
            openedContainer = null
            _state.update { current ->
                val draft = current.draft.withOneFewerContainer()
                savedDraft = draft
                current.copy(draft = draft, openedSheet = null, opened = null, isDirty = true)
            }
            refreshForecast()
            return
        }
        // No "open a new one?" here: discarding a container is a deliberate act, and §4.5.4 answers
        // it with a snackbar stating what happened, not with a question about the next one.
        runSheetWrite(compoundId, message = CompoundFormEvent.ShowMessage(removedMessage())) {
            compoundRepository.closeContainer(compoundId, null)
        }
    }

    /** §4.5.5: with unopened stock left, an emptied container raises the offer to open the next one. */
    private fun promptForNewContainer() {
        val unopened = _state.value.draft.totalContainers.trim().toIntOrNull() ?: 0
        if (unopened > 0) _state.update { it.copy(isDepletionPromptOpen = true) }
    }

    /**
     * §4.5.5: "Open new" runs the container-opening operation of §5.3 — a fresh full container, dated
     * now. "Leave closed" is the whole of the other answer, so it only closes the prompt.
     */
    private fun onNaturalDepletionDecision(openNew: Boolean) {
        _state.update { it.copy(isDepletionPromptOpen = false) }
        if (!openNew) return

        val compoundId = args.compoundId
        if (compoundId == null) {
            val draft = _state.value.draft
            openedContainer = OpenedContainer(
                openedAt = now(),
                remainingAmount = draft.amountPerContainerOrNull() ?: return,
                expiryAfterOpeningDays = draft.expiryAfterOpeningDaysOrNull(),
                userDefinedExpiryDate = null,
                predictedExpiryDate = draft.expiryAfterOpeningDaysOrNull()
                    ?.let { today().plus(it, DateTimeUnit.DAY) },
            )
            _state.update {
                it.copy(opened = openedContainer?.toUi(draft.amountPerContainerOrNull(), draft.containerType))
            }
            refreshForecast()
            return
        }
        runSheetWrite(compoundId) { compoundRepository.openContainer(compoundId) }
    }

    /**
     * Runs one of §4.5.5's writes, then re-reads the compound rather than mirroring what was written.
     *
     * The reason is `numberOfContainers`: opening a container decrements it, discarding one leaves it
     * alone, and the form's field is the *total owned* (§4.4.4). Deriving that total from the row the
     * repository actually wrote keeps the two in step — and it is also the [baseline], because a
     * write that has already happened is not an unsaved change to discard.
     */
    private fun runSheetWrite(
        compoundId: Long,
        promptOnEmpty: Boolean = false,
        message: CompoundFormEvent? = null,
        write: suspend () -> EmptyResult<DataError.Local>,
    ) {
        viewModelScope.launch {
            updateSheet { it.copy(isSaving = true) }
            when (val result = write()) {
                is Result.Success -> {
                    syncFromCompound(compoundId)
                    message?.let { _events.send(it) }
                    if (promptOnEmpty) promptForNewContainer()
                }

                // Reported in the sheet, not through the screen's snackbar: the sheet is a window of
                // its own and the `SnackbarHost` draws behind it, so a failure said that way is said
                // where the user cannot see it. Closing the sheet to make room would throw away what
                // they typed, which is worse.
                is Result.Error -> updateSheet {
                    it.copy(isSaving = false, saveError = result.error.toSaveError())
                }
            }
        }
    }

    /** Re-reads the compound after a §4.5.5 write and re-derives everything the form shows from it. */
    private suspend fun syncFromCompound(compoundId: Long) {
        val compound = compoundRepository.observeById(compoundId).first() ?: return
        openedContainer = compound.currentOpened
        val total = (compound.numberOfContainers + if (compound.currentOpened != null) 1 else 0).toString()
        baseline = baseline.copy(totalContainers = total)
        _state.update { current ->
            val draft = current.draft.copy(totalContainers = total)
            savedDraft = draft
            current.copy(
                draft = draft,
                openedSheet = null,
                opened = compound.currentOpened?.toUi(compound.amountPerContainer, compound.containerType),
                isDirty = draft.differsFrom(baseline),
            )
        }
        refreshForecast()
    }

    private fun updateSheet(transform: (OpenedContainerSheetState) -> OpenedContainerSheetState) {
        _state.update { it.copy(openedSheet = it.openedSheet?.let(transform)) }
    }

    /**
     * §5.3: the one refusal the sheet can explain in its own terms is "there is nothing left to
     * open" — every other failure is the write itself going wrong, which nothing the user types will
     * fix.
     */
    private fun DataError.Local.toSaveError(): OpenedContainerSaveError =
        if (this == DataError.Local.CONSTRAINT_VIOLATION) {
            OpenedContainerSaveError.NO_UNOPENED_STOCK
        } else {
            OpenedContainerSaveError.WRITE_FAILED
        }

    private fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    private fun removedMessage() = UiText.StringResource(R.string.container_sheet_removed)

    // -----------------------------------------------------------------------
    // Validation (§4.4.4)
    // -----------------------------------------------------------------------

    private fun validate(
        draft: CompoundFormDraft,
        isConcentrationRequired: Boolean,
    ): Map<CompoundFormField, CompoundFormError> = buildMap {
        validateCompoundSupplyName(draft.name.trim()).failure()?.let { code ->
            put(
                CompoundFormField.NAME,
                if (code == ValidationError.Code.NAME_TOO_LONG) {
                    CompoundFormError.NAME_TOO_LONG
                } else {
                    CompoundFormError.NAME_REQUIRED
                },
            )
        }

        val containers = draft.totalContainers.trim().toIntOrNull()
        when {
            containers == null || validateCompoundSupplyNumberOfContainers(containers).failure() != null ->
                put(CompoundFormField.TOTAL_CONTAINERS, CompoundFormError.CONTAINERS_INVALID)

            // The stored count is the *unopened* one, so an opened container needs a total of at
            // least one for it to have come out of (§4.4.4 worked example).
            openedContainer != null && containers < 1 ->
                put(CompoundFormField.TOTAL_CONTAINERS, CompoundFormError.CONTAINERS_BELOW_OPENED)
        }

        val amount = draft.amountPerContainerOrNull()
        if (amount == null || validateCompoundSupplyAmountPerContainer(amount).failure() != null) {
            put(CompoundFormField.AMOUNT_PER_CONTAINER, CompoundFormError.AMOUNT_NOT_POSITIVE)
        }

        concentrationError(draft, isConcentrationRequired)?.let { put(CompoundFormField.CONCENTRATION, it) }
    }

    /**
     * §4.4.3: concentration is optional except on a non-ampoule injectable. Text that is present but
     * not a number is its own failure — `concentrationOrNull()` cannot tell "left blank" from
     * "typed nonsense", and only the first of those is allowed.
     */
    private fun concentrationError(draft: CompoundFormDraft, isRequired: Boolean): CompoundFormError? {
        val concentration = draft.concentrationOrNull()
        if (concentration == null && draft.concentrationAmount.isNotBlank()) {
            return CompoundFormError.CONCENTRATION_NOT_POSITIVE
        }
        return when (validateCompoundSupplyConcentration(concentration, isRequired).failure()) {
            ValidationError.Code.CONCENTRATION_REQUIRED -> CompoundFormError.CONCENTRATION_REQUIRED
            null -> null
            else -> CompoundFormError.CONCENTRATION_NOT_POSITIVE
        }
    }

    // -----------------------------------------------------------------------
    // Domain ↔ draft
    // -----------------------------------------------------------------------

    /**
     * The compound to write (§4.4.4). `numberOfContainers` is persisted as the **unopened** count:
     * the form's field is the total the user owns, so the opened container — the one in their hand —
     * comes back out of it. Total owned `3` with one opened stores `2`, and the two together still
     * describe three physical containers.
     */
    private fun CompoundFormDraft.toCompound(): CompoundSupply {
        val timestamp = now()
        return CompoundSupply(
            id = args.compoundId ?: 0L,
            name = name.trim(),
            category = category,
            form = form,
            containerType = containerType,
            primaryUnit = primaryUnit,
            amountPerContainer = requireNotNull(amountPerContainerOrNull()) { "validated before save" },
            concentration = concentrationOrNull(),
            numberOfContainers = totalContainers.trim().toInt() - openedContainerCount(),
            currentOpened = openedContainer,
            batchExpiryDate = batchExpiryDate,
            expiryAfterOpeningDays = expiryAfterOpeningDays.trim().toIntOrNull(),
            storageLocation = storageLocation,
            batchNumber = batchNumber.trim().ifBlank { null },
            supplier = supplier.trim().ifBlank { null },
            notes = notes.trim().ifBlank { null },
            deletedAt = null,
            // The repository stamps both on write (§5.8.5); these are the values it replaces.
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    /** The stored compound as form fields — the reverse of [toCompound], and the discard baseline. */
    private fun CompoundSupply.toDraft() = CompoundFormDraft(
        name = name,
        category = category,
        form = form,
        containerType = containerType,
        // §4.4.3: the field is the total owned, so the opened container is added back in.
        totalContainers = (numberOfContainers + if (currentOpened != null) 1 else 0).toString(),
        amountPerContainer = amountPerContainer.value.toPlainString(),
        primaryUnit = primaryUnit,
        concentrationAmount = concentration?.amount?.value?.toPlainString().orEmpty(),
        concentrationUnit = concentration?.amount?.unit ?: form.concentrationUnitOptions().first().amount,
        concentrationPerUnit = concentration?.per?.unit ?: form.concentrationUnitOptions().first().per,
        storageLocation = storageLocation,
        batchExpiryDate = batchExpiryDate,
        batchNumber = batchNumber.orEmpty(),
        supplier = supplier.orEmpty(),
        expiryAfterOpeningDays = expiryAfterOpeningDays?.toString().orEmpty(),
        notes = notes.orEmpty(),
    )

    /**
     * The §4.4.3 summary card for a container.
     *
     * [capacity] is passed rather than read off a compound because a container staged during the New
     * Compound flow (§4.5.5) has no stored compound to read it from — the form's own
     * `amountPerContainer` is all there is, and it may still be half-typed.
     */
    private fun OpenedContainer.toUi(capacity: Quantity?, containerType: ContainerType): OpenedContainerUi {
        val size = capacity ?: remainingAmount
        return OpenedContainerUi(
            containerType = containerType,
            remaining = remainingAmount.value.toPlainString(),
            capacity = size.value.toPlainString(),
            unit = size.unit.name.lowercase(),
            fillFraction = remainingAmount.fractionOf(size),
            openedDaysAgo = openedAt.toLocalDateTime(timeZone).date.daysUntil(today()),
        )
    }

    /**
     * The live stock preview of §6.4.2's right column. Null while the numbers it needs are missing or
     * half-typed — a preview that guessed at "1." would flicker nonsense as the user types.
     */
    private fun forecastOf(draft: CompoundFormDraft): StockForecastUi? {
        val total = draft.totalContainers.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val perContainer = draft.amountPerContainerOrNull()?.takeIf { it.value > ZERO } ?: return null
        val unopened = total - openedContainerCount()
        if (unopened < 0) return null

        val sealedStock = perContainer * Decimal.parse(unopened.toString())
        // The opened container counts too, but only for what is left in it — counting it as a full
        // container would overstate the total by however much has already been used.
        val stock = openedContainer?.remainingAmount?.convertedTo(perContainer.unit)
            ?.let { sealedStock + it }
            ?: sealedStock

        return StockForecastUi(
            totalStock = stock.toString(),
            containers = total,
            // "…once mixed" is reconstitution talk, so it is only asked when the concentration is
            // actually per volume. Per tablet or per gram, dividing by it answers a question nobody
            // asked — how many tablets the container's mass fills — and would answer it wrongly.
            volumePerContainer = draft.concentrationOrNull()
                ?.takeIf { it.per.unit.family == UnitFamily.VOLUME }
                ?.let { perContainer.dividedBy(it)?.toString() },
        )
    }

    private fun openedContainerCount(): Int = if (openedContainer != null) 1 else 0

    private companion object {
        const val DRAFT_KEY = "compound-form-draft"
    }
}

// ---------------------------------------------------------------------------
// Quantity helpers — every one of them a conversion that can legitimately fail
// ---------------------------------------------------------------------------

/** The failing code of a validator, or null when it passed. */
private fun EmptyResult<ValidationError>.failure(): ValidationError.Code? =
    (this as? Result.Error)?.error as? ValidationError.Code

/** Null rather than a throw when the units are of different families (a tub of grams has no millilitres). */
internal fun Quantity.convertedTo(target: UnitCode): Quantity? = try {
    Quantity(unit.convertTo(target, value), target)
} catch (_: IllegalArgumentException) {
    null
}

private fun Quantity.dividedBy(concentration: Concentration): Quantity? = try {
    this / concentration
} catch (_: IllegalArgumentException) {
    null
}

/**
 * How full the container is, for the progress track of the opened-container card — the form's
 * summary (§4.4.3) and Compound Detail's segmented bar (§4.3.3) both read it. Display geometry,
 * not dose math, which is why it is one of the few `Float`s in the feature (§3.0.1).
 */
internal fun Quantity.fractionOf(capacity: Quantity): Float {
    if (capacity.value <= ZERO) return 0f
    val remaining = convertedTo(capacity.unit) ?: return 0f
    return (remaining.value / capacity.value).raw.toFloat().coerceIn(0f, 1f)
}
