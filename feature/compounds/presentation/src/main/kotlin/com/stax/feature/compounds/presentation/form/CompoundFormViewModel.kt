package com.stax.feature.compounds.presentation.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
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
import com.stax.core.presentation.toUiText
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
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
     * The compound's opened container, kept in domain form because Save needs it twice: to take one
     * off the total-owned count (§4.4.4) and to pass through untouched. Editing it is the bottom
     * sheet's job (§4.5), not this form's.
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

            // The section and its CTA are §4.4.3's; the sheet the CTA opens is §4.5's, which lands
            // with M7-06. Until then this records the intent and the screen renders nothing for it.
            CompoundFormAction.Overlay.OnOpenedContainerClick ->
                _state.update { it.copy(isOpenedContainerSheetOpen = true) }

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
                    opened = compound.currentOpened?.toUi(compound),
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

        val compound = current.draft.toCompound()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errors = persistentMapOf()) }
            val result: Result<*, DataError.Local> = if (args.compoundId == null) {
                compoundRepository.create(compound)
            } else {
                compoundRepository.update(compound)
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

    private fun OpenedContainer.toUi(compound: CompoundSupply) = OpenedContainerUi(
        containerType = compound.containerType,
        remaining = remainingAmount.value.toPlainString(),
        capacity = compound.amountPerContainer.value.toPlainString(),
        unit = compound.amountPerContainer.unit.name.lowercase(),
        fillFraction = remainingAmount.fractionOf(compound.amountPerContainer),
        openedDaysAgo = openedAt.toLocalDateTime(timeZone).date
            .daysUntil(now().toLocalDateTime(timeZone).date),
    )

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
private fun Quantity.convertedTo(target: UnitCode): Quantity? = try {
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
 * How full the container is, for the progress track of the opened-container card. Display geometry,
 * not dose math — which is why this is the one `Float` in the form (§3.0.1).
 */
private fun Quantity.fractionOf(capacity: Quantity): Float {
    if (capacity.value <= ZERO) return 0f
    val remaining = convertedTo(capacity.unit) ?: return 0f
    return (remaining.value / capacity.value).raw.toFloat().coerceIn(0f, 1f)
}
