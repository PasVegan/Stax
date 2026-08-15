package com.stax.feature.compounds.presentation.form

import androidx.compose.runtime.Immutable
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.Concentration
import com.stax.core.domain.ContainerType
import com.stax.core.domain.Decimal
import com.stax.core.domain.Quantity
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The fields of the form the user can edit (§4.4.3), and nothing else.
 *
 * Every numeric field is the user's **raw text**, not a parsed number: a field mid-edit ("1.", "",
 * "0.0") has no `Decimal` to be, and parsing on each keystroke would either throw or silently
 * rewrite what the user is typing. Parsing happens once, at validation (§4.4.4).
 *
 * `@Serializable` because this is what the ViewModel stores in its `SavedStateHandle` — the draft is
 * auto-saved on every edit so backgrounding *and* the process death that can follow it both resume
 * the form as it was left (§4.4.5).
 *
 * `@Immutable` because [touched] is a read-only `Set` the Compose compiler cannot infer stability
 * for; nothing here is ever mutated in place.
 */
@Immutable
@Serializable
data class CompoundFormDraft(
    val name: String = "",
    val category: CompoundCategory = CompoundCategory.PEPTIDE,
    val form: CompoundForm = CompoundForm.INJECTABLE,
    val containerType: ContainerType = ContainerType.VIAL,
    /**
     * Containers the user owns **in total**, the opened one included (§4.4.3). Persistence stores the
     * unopened count, so Save subtracts the opened container back out (§4.4.4 worked example).
     */
    val totalContainers: String = "1",
    val amountPerContainer: String = "",
    val primaryUnit: UnitCode = UnitCode.MG,
    val concentrationAmount: String = "",
    /** Numerator unit of the "{amount} per 1 mL" concentration the form edits (§4.4.3). */
    val concentrationUnit: UnitCode = UnitCode.MG,
    val storageLocation: StorageLocation = StorageLocation.FRIDGE,
    val batchExpiryDate: LocalDate? = null,
    val batchNumber: String = "",
    val supplier: String = "",
    val expiryAfterOpeningDays: String = "",
    val notes: String = "",
    /**
     * The fields the user has set by hand. Smart defaults (§4.4.3) fill only what is still untouched,
     * so picking a Form after typing an amount never overwrites the amount.
     */
    val touched: Set<CompoundFormField> = emptySet(),
)

/**
 * The fields smart defaults can fill (§4.4.3) and validation can reject (§4.4.4), in the order they
 * appear on the form — which is the order Save walks to find the first error to scroll to.
 */
enum class CompoundFormField { NAME, CONTAINER_TYPE, TOTAL_CONTAINERS, AMOUNT_PER_CONTAINER, CONCENTRATION }

/** Why a field was rejected on Save (§4.4.4). The screen owns the wording; the ViewModel owns the rule. */
enum class CompoundFormError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    CONTAINERS_INVALID,
    CONTAINERS_BELOW_OPENED,
    AMOUNT_NOT_POSITIVE,
    CONCENTRATION_REQUIRED,
    CONCENTRATION_NOT_POSITIVE,
}

/** The dropdowns of the form. At most one is open, so the open one is a nullable enum, not a flag each. */
enum class CompoundFormPicker { CATEGORY, FORM, CONTAINER_TYPE, STORAGE_LOCATION, PRIMARY_UNIT, CONCENTRATION_UNIT }

// ---------------------------------------------------------------------------
// Smart defaults (§4.4.3)
// ---------------------------------------------------------------------------

/** The three [CompoundFormDraft] fields a Form selection fills in (§4.4.3). */
internal data class SmartDefaults(
    val containerType: ContainerType,
    val primaryUnit: UnitCode,
    val amountPerContainer: String,
)

/**
 * Smart defaults per Form selection (§4.4.3).
 *
 * The route each row also names (subcutaneous / oral / topical) belongs to a Protocol, not to a
 * `CompoundSupply` — this form has no route field, so only the three fields it does own are filled.
 * `POWDER` is the non-injectable powder row: an injectable powder keeps the Injectable defaults,
 * because Form is what the user picked.
 */
internal fun CompoundForm.smartDefaults(): SmartDefaults = when (this) {
    CompoundForm.INJECTABLE -> SmartDefaults(ContainerType.VIAL, UnitCode.MG, "5")
    CompoundForm.CAPSULE -> SmartDefaults(ContainerType.BOTTLE, UnitCode.CAPSULE, "60")
    CompoundForm.TABLET -> SmartDefaults(ContainerType.BLISTER, UnitCode.TABLET, "30")
    CompoundForm.POWDER -> SmartDefaults(ContainerType.TUB, UnitCode.G, "100")
    CompoundForm.LIQUID -> SmartDefaults(ContainerType.BOTTLE, UnitCode.ML, "30")
    CompoundForm.TOPICAL -> SmartDefaults(ContainerType.TUB, UnitCode.G, "50")
}

/**
 * Applies the smart defaults of [form] over what the user has not set themselves (§4.4.3).
 *
 * The unit is the exception: the picker's options are per-form (a tablet has no millilitres), so a
 * unit the new form does not offer is replaced even when the user chose it — leaving it would show a
 * selection that is not in its own list.
 */
internal fun CompoundFormDraft.applySmartDefaults(form: CompoundForm): CompoundFormDraft {
    val defaults = form.smartDefaults()
    val keepUnit = primaryUnit in form.primaryUnitOptions() &&
        CompoundFormField.AMOUNT_PER_CONTAINER in touched
    return copy(
        form = form,
        containerType = if (CompoundFormField.CONTAINER_TYPE in touched) containerType else defaults.containerType,
        primaryUnit = if (keepUnit) primaryUnit else defaults.primaryUnit,
        amountPerContainer = if (CompoundFormField.AMOUNT_PER_CONTAINER in touched) {
            amountPerContainer
        } else {
            defaults.amountPerContainer
        },
    )
}

/**
 * The units offered for a compound of this form. Every unit the app knows would make the picker a
 * list of mostly-wrong answers — a tablet is never measured in millilitres — so each form offers the
 * units it is actually sold in, its smart default first (§4.4.3).
 */
internal fun CompoundForm.primaryUnitOptions(): List<UnitCode> = when (this) {
    CompoundForm.INJECTABLE -> listOf(UnitCode.MG, UnitCode.MCG, UnitCode.IU, UnitCode.ML)
    CompoundForm.CAPSULE -> listOf(UnitCode.CAPSULE)
    CompoundForm.TABLET -> listOf(UnitCode.TABLET)
    CompoundForm.POWDER -> listOf(UnitCode.G, UnitCode.MG, UnitCode.SCOOP)
    CompoundForm.LIQUID -> listOf(UnitCode.ML, UnitCode.DROP)
    CompoundForm.TOPICAL -> listOf(UnitCode.G, UnitCode.ML)
}

/** Numerator units of a concentration — what one millilitre holds once the vial is mixed (§4.4.3). */
internal val CONCENTRATION_UNIT_OPTIONS: List<UnitCode> =
    listOf(UnitCode.MG, UnitCode.MCG, UnitCode.IU, UnitCode.G)

// ---------------------------------------------------------------------------
// Reading the draft's numbers (§4.4.4)
// ---------------------------------------------------------------------------

/** Whether the form has changed since it was loaded (§4.4.5). Which fields were touched is not a change. */
internal fun CompoundFormDraft.differsFrom(other: CompoundFormDraft): Boolean =
    copy(touched = emptySet()) != other.copy(touched = emptySet())

internal fun CompoundFormDraft.amountPerContainerOrNull(): Quantity? = amountPerContainer.toQuantityOrNull(primaryUnit)

/** §4.4.3 edits "{amount} per 1 mL"; the `per` side of a concentration is not a field on this form. */
internal fun CompoundFormDraft.concentrationOrNull(): Concentration? =
    concentrationAmount.toQuantityOrNull(concentrationUnit)?.let {
        Concentration(amount = it, per = Quantity(ONE, UnitCode.ML))
    }

private fun String.toQuantityOrNull(unit: UnitCode): Quantity? = toDecimalOrNull()?.let { Quantity(it, unit) }

/** `Decimal.parse` throws on anything that is not a number, and a field mid-edit routinely is not. */
internal fun String.toDecimalOrNull(): Decimal? = try {
    trim().takeIf { it.isNotBlank() }?.let(Decimal::parse)
} catch (_: NumberFormatException) {
    null
}

internal val ZERO: Decimal = Decimal.parse("0")
internal val ONE: Decimal = Decimal.parse("1")
