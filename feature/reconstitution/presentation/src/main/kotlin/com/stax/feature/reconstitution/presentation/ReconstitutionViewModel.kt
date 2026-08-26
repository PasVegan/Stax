package com.stax.feature.reconstitution.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.Decimal
import com.stax.core.domain.Quantity
import com.stax.core.domain.UnitCode
import com.stax.core.domain.UnitFamily
import com.stax.core.domain.repository.CompoundRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode

/**
 * What the route tells the helper: which compound it was opened on (§4.6).
 *
 * Null is §4.4.3's standalone calculator — the Create form's Helper button has no compound to
 * pre-select yet, so the container amount is typed rather than read.
 */
data class ReconstitutionArgs(val compoundId: Long?)

/**
 * MVI ViewModel for the Reconstitution Helper (§4.6, §10.1).
 *
 * The whole screen is one derivation: container amount, diluent and desired dose in, and the mix's
 * concentration, the volume to draw and the doses one container yields out (§4.6.2, §4.6.6). It runs
 * on every keystroke rather than behind a "Calculate" button, which is what makes the numbers live —
 * and it is pure, so [recalculated] is the whole of what there is to test.
 *
 * The arithmetic is the domain's: [Quantity] divided by [Concentration] is the volume, and nothing
 * here touches a `Double` (§3.0.1). Only the last step — turning a [Decimal] into the digits on a
 * tile — happens in this class, because how many of them to show is a UI question.
 */
class ReconstitutionViewModel(compoundRepository: CompoundRepository, args: ReconstitutionArgs) : ViewModel() {

    private val _state = MutableStateFlow(ReconstitutionState())
    val state = _state.asStateFlow()

    private val _events = Channel<ReconstitutionEvent>()
    val events = _events.receiveAsFlow()

    /**
     * Whether the compound has been read once. The row is *observed*, so it re-emits on every write
     * to it; the fields the user types into are seeded from the first emission only, or a save
     * elsewhere would type over them mid-edit.
     */
    private var isSeeded = false

    init {
        args.compoundId?.let { id ->
            compoundRepository.observeById(id)
                .onEach(::onCompound)
                .launchIn(viewModelScope)
        }
    }

    fun onAction(action: ReconstitutionAction) {
        when (action) {
            ReconstitutionAction.OnCloseClick -> send(ReconstitutionEvent.NavigateBack)

            ReconstitutionAction.OnToggleCalculation ->
                _state.update { it.copy(isCalculationExpanded = !it.isCalculationExpanded) }

            is ReconstitutionAction.OnContainerAmountChange ->
                _state.update { it.copy(containerAmount = action.value).recalculated() }

            is ReconstitutionAction.OnContainerUnitSelected -> onContainerUnitSelected(action.unit)

            is ReconstitutionAction.OnDiluentChange ->
                _state.update { it.copy(diluent = action.value).recalculated() }

            is ReconstitutionAction.OnDesiredDoseChange ->
                _state.update { it.copy(desiredDose = action.value).recalculated() }

            is ReconstitutionAction.OnDoseUnitSelected ->
                _state.update { it.copy(doseUnit = action.unit, openPicker = null).recalculated() }

            ReconstitutionAction.OnCycleSyringeSize ->
                _state.update { it.copy(syringeSize = it.syringeSize.next()).recalculated() }

            is ReconstitutionAction.OnDisplaySelected ->
                _state.update { it.copy(display = action.display, openPicker = null).recalculated() }

            is ReconstitutionAction.OnPickerClick -> _state.update { it.copy(openPicker = action.picker) }

            ReconstitutionAction.OnPickerDismiss -> _state.update { it.copy(openPicker = null) }

            // §4.6.7 writes the concentration back and returns to the caller — M8-04.
            ReconstitutionAction.OnSaveClick -> Unit
        }
    }

    /**
     * §4.6.1 + §4.6.4: the compound names the screen and fills the read-only container tile.
     *
     * A compound that already carries a concentration opens on the diluent that produced it, which is
     * §4.6's "most reconstitution events use the saved concentration" — the user lands on the mix they
     * are already using rather than on an empty field.
     */
    private fun onCompound(compound: CompoundSupply?) {
        // The row is gone — archived from somewhere else, or a route pointing at nothing. Leaving is
        // the only honest answer; the standalone calculator is a different screen, not a fallback.
        if (compound == null) {
            send(ReconstitutionEvent.NavigateBack)
            return
        }
        val unit = compound.amountPerContainer.unit
        _state.update { current ->
            current.copy(
                compoundName = compound.name,
                containerAmount = compound.amountPerContainer.value.toPlainString(),
                containerUnit = unit,
                isContainerEditable = false,
                doseUnitOptions = unit.family.doseUnits(),
                diluent = if (isSeeded) current.diluent else compound.savedDiluent(),
                doseUnit = if (isSeeded) current.doseUnit else unit,
            ).recalculated()
        }
        isSeeded = true
    }

    /**
     * §4.6.4's container picker, standalone only. A unit from another family takes the desired dose
     * with it: a dose in mg of a vial measured in IU is not a dose the mix can convert.
     */
    private fun onContainerUnitSelected(unit: UnitCode) {
        _state.update { current ->
            current.copy(
                containerUnit = unit,
                openPicker = null,
                doseUnitOptions = unit.family.doseUnits(),
                doseUnit = if (current.doseUnit.family == unit.family) current.doseUnit else unit,
            ).recalculated()
        }
    }

    private fun send(event: ReconstitutionEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

/**
 * The one derivation the screen is (§4.6.2, §4.6.6), run on every edit.
 *
 * The concentration is normalized to **one millilitre** — "2.5 mg/mL", not "5 mg / 2 mL" — because
 * that is the form §4.6.6 shows, the form the dose volume divides by, and the form M8-04 saves.
 *
 * Any input that is blank, unparseable or non-positive simply leaves the results null: an empty
 * diluent field is a user still typing, not an error to report, and the tiles read "—" until it is a
 * number.
 */
internal fun ReconstitutionState.recalculated(): ReconstitutionState {
    val container = containerAmount.toDecimalOrNull()?.takeIf { it > ZERO }
    val diluentMl = diluent.toDecimalOrNull()?.takeIf { it > ZERO }
    val typedDose = desiredDose.toDecimalOrNull()?.takeIf { it > ZERO }
    // Guarded rather than assumed: `Quantity / Concentration` and `convertTo` both throw across unit
    // families, and a crash is a poor answer to a picker the user is still moving.
    val dose = typedDose?.takeIf { doseUnit.family == containerUnit.family }

    val perMl = if (container != null && diluentMl != null) container / diluentMl else null
    val mix = perMl?.let { Concentration(Quantity(it, containerUnit), Quantity(ONE, UnitCode.ML)) }
    val volume = if (mix != null && dose != null) (Quantity(dose, doseUnit) / mix).value else null
    val dosesPerContainer = if (container != null && dose != null) {
        container / doseUnit.convertTo(containerUnit, dose)
    } else {
        null
    }

    val equivalence = if (dose != null && volume != null) {
        DoseEquivalence(
            mass = dose.toPlainString(),
            volume = volume.asDisplayed(DoseDisplay.MILLILITRES),
            units = volume.asDisplayed(DoseDisplay.INSULIN_UNITS),
        )
    } else {
        null
    }

    return copy(
        equivalence = equivalence,
        // The ladder runs off the typed dose rather than the convertible one: a dose unit that does
        // not match the container still names five doses, it just cannot say what they draw to.
        ladder = typedDose.ladder(mix.takeIf { dose != null }, doseUnit, display),
        syringeFill = volume?.fractionOf(syringeSize) ?: 0f,
        concentration = perMl?.round(CONCENTRATION_SCALE),
        dosesPerContainer = dosesPerContainer?.floorToInt(),
    )
}

/**
 * §4.6.5's default rungs: `[0.1, dose/2, dose, dose×2, dose×3]`, ordered and de-duplicated.
 *
 * The duplicates are real — a dose of `0.2` puts `0.1` on the ladder twice, once as the floor and
 * once as its own half — and two identical pills side by side read as a bug. Sorting is what makes it
 * a ladder rather than the order the formula happens to list.
 *
 * Tapping a rung types its figure into Desired dose, so the ladder recomputes around the tapped
 * value: the rung stays selected and the next doubling is one tap further up.
 */
private fun Decimal?.ladder(mix: Concentration?, doseUnit: UnitCode, display: DoseDisplay): ImmutableList<DoseRung> {
    val dose = this ?: return persistentListOf()
    return listOf(LADDER_FLOOR, dose / TWO, dose, dose * TWO, dose * THREE)
        .sorted()
        .distinctBy(Decimal::toPlainString)
        .map { rung ->
            DoseRung(
                dose = rung.toPlainString(),
                equivalent = mix?.let { (Quantity(rung, doseUnit) / it).value.asDisplayed(display) },
                isSelected = rung.compareTo(dose) == 0,
            )
        }
        .toPersistentList()
}

/**
 * §4.6.2: how much of the syringe the dose fills.
 *
 * The division is the domain's — a volume over a capacity, both [Decimal] (§3.0.1) — and only the
 * ratio it comes to crosses into `Float`, because what the syringe does with it is geometry.
 *
 * A dose too big for the barrel pegs at full rather than overflowing it; the figure over the fill and
 * §4.6.3's chips still read the real one, and the size badge is one tap from a syringe that holds it.
 */
private fun Decimal.fractionOf(syringeSize: SyringeSize): Float =
    (this / Decimal.parse(syringeSize.capacityMl)).raw.toFloat().coerceIn(0f, 1f)

/**
 * §4.6.2's "Draw to". Millilitres keep two decimals — the graduation an insulin syringe is read to —
 * and insulin units keep one, so a half-unit dose is not silently rounded to a whole one.
 */
private fun Decimal.asDisplayed(display: DoseDisplay): String = when (display) {
    DoseDisplay.MILLILITRES -> raw.setScale(VOLUME_SCALE, RoundingMode.HALF_UP).toPlainString()
    DoseDisplay.INSULIN_UNITS -> (this * UNITS_PER_ML).round(UNITS_SCALE)
}

/** Half-up to [scale] decimals, with trailing zeros dropped — "2.50" is noise on a result tile. */
private fun Decimal.round(scale: Int): String =
    raw.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** §4.6.6: whole doses only — a container does not yield 20.8 injections. */
private fun Decimal.floorToInt(): Int = raw.setScale(0, RoundingMode.FLOOR).toInt()

/** The diluent that produces the compound's stored concentration, or blank when it has none (§4.6). */
private fun CompoundSupply.savedDiluent(): String {
    val stored = concentration ?: return ""
    if (stored.per.unit != UnitCode.ML || stored.amount.value <= ZERO) return ""
    if (stored.amount.unit.family != amountPerContainer.unit.family) return ""
    return (amountPerContainer / stored).value.round(VOLUME_SCALE)
}

private fun UnitFamily.doseUnits() = if (this == UnitFamily.IU) IU_UNITS else MASS_UNITS

private fun String.toDecimalOrNull(): Decimal? = try {
    trim().takeIf { it.isNotBlank() }?.let(Decimal::parse)
} catch (_: NumberFormatException) {
    null
}

private val ZERO: Decimal = Decimal.parse("0")
private val ONE: Decimal = Decimal.parse("1")
private val TWO: Decimal = Decimal.parse("2")
private val THREE: Decimal = Decimal.parse("3")

/** §4.6.5's fixed bottom rung — the smallest dose the ladder always offers, whatever was typed. */
private val LADDER_FLOOR: Decimal = Decimal.parse("0.1")

/** The U-100 standard every insulin syringe is graduated in, whatever its capacity (§4.6.2). */
private val UNITS_PER_ML: Decimal = Decimal.parse("100")

private const val VOLUME_SCALE = 2
private const val UNITS_SCALE = 1
private const val CONCENTRATION_SCALE = 3
