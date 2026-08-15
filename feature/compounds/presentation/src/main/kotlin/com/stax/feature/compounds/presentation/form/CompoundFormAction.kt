package com.stax.feature.compounds.presentation.form

import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.ContainerType
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate

/** Everything the user can do on the Create / Edit Compound form (§4.4). */
sealed interface CompoundFormAction {

    /** The text and numeric fields. Their values stay raw text until Save parses them (§4.4.4). */
    sealed interface Edit : CompoundFormAction {
        data class OnNameChange(val name: String) : Edit
        data class OnTotalContainersChange(val value: String) : Edit
        data class OnAmountPerContainerChange(val value: String) : Edit
        data class OnConcentrationChange(val value: String) : Edit
        data class OnBatchNumberChange(val value: String) : Edit
        data class OnSupplierChange(val value: String) : Edit
        data class OnExpiryAfterOpeningDaysChange(val value: String) : Edit
        data class OnNotesChange(val value: String) : Edit
    }

    /** The dropdowns. Picking a Form also fills the untouched smart-default fields (§4.4.3). */
    sealed interface Pick : CompoundFormAction {
        data class OnCategorySelected(val category: CompoundCategory) : Pick
        data class OnFormSelected(val form: CompoundForm) : Pick
        data class OnContainerTypeSelected(val containerType: ContainerType) : Pick
        data class OnStorageLocationSelected(val storageLocation: StorageLocation) : Pick
        data class OnPrimaryUnitSelected(val unit: UnitCode) : Pick

        /** Both halves at once: the picker offers whole concentrations ("mg/mL"), not two units. */
        data class OnConcentrationUnitSelected(val units: ConcentrationUnits) : Pick
    }

    /**
     * The transient surfaces of the form: the dropdowns, the date picker, the discard prompt and the
     * opened-container sheet. Grouped because they are all the same kind of statement — "this opened"
     * or "this closed" — and the ViewModel handles them as one branch.
     */
    sealed interface Overlay : CompoundFormAction {
        data class OnPickerOpen(val picker: CompoundFormPicker) : Overlay
        data object OnPickerDismiss : Overlay

        data object OnBatchExpiryClick : Overlay
        data class OnBatchExpirySelected(val date: LocalDate?) : Overlay
        data object OnBatchExpiryDismiss : Overlay

        /** §4.4.3 / §4.5: the opened-container CTA — "Add already opened" or the summary card's pencil. */
        data object OnOpenedContainerClick : Overlay

        data object OnDiscardDismiss : Overlay
    }

    /** §4.4.3: the trailing "Helper" button on the concentration row → the Reconstitution Helper (§4.6). */
    data object OnReconstitutionHelperClick : CompoundFormAction

    /** Consumed once the screen has focused the first failing field, scrolling it into view (§4.4.4). */
    data object OnErrorScrollHandled : CompoundFormAction

    data object OnSaveClick : CompoundFormAction

    /** The app bar's × and Cancel, plus the back gesture — all confirm first when dirty (§4.4.5). */
    data object OnCancelClick : CompoundFormAction
    data object OnDiscardConfirm : CompoundFormAction

    /** Onboarding step 2 only (§4.14): leaves the step without saving. */
    data object OnSkipClick : CompoundFormAction
}
