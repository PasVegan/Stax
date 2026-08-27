package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.Instant

/** How many random escalations the property tests cover; each seed is one JUnit case. */
private const val SEEDS = 200

/** Counters probed per escalation — wide enough that even an `EveryXWeeks` rule steps several times. */
private const val MAX_COUNTER = 200

private val startDate = LocalDate(2026, 1, 1)

class EscalationEngineTest {

    // -----------------------------------------------------------------------
    // Worked examples (§3.2)
    // -----------------------------------------------------------------------

    @Test
    fun `EveryXDays steps once per increaseEveryValue days`() {
        val esc = escalation(
            startDose = q("0.25", UnitCode.MG),
            increaseAmount = q("0.25", UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.EVERY_X_DAYS,
            increaseEveryValue = 7,
        )
        assertThat(esc.doseAt(daysSinceStart = 6).value.toPlainString()).isEqualTo("0.25")
        assertThat(esc.doseAt(daysSinceStart = 7).value.toPlainString()).isEqualTo("0.5")
        assertThat(esc.doseAt(daysSinceStart = 20).value.toPlainString()).isEqualTo("0.75")
    }

    @Test
    fun `EveryXWeeks steps once per increaseEveryValue weeks`() {
        val esc = escalation(
            startDose = q("2", UnitCode.MG),
            increaseAmount = q("1", UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.EVERY_X_WEEKS,
            increaseEveryValue = 2,
        )
        assertThat(esc.doseAt(daysSinceStart = 13).value.toPlainString()).isEqualTo("2")
        assertThat(esc.doseAt(daysSinceStart = 14).value.toPlainString()).isEqualTo("3")
    }

    @Test
    fun `AfterXDoses steps on the dose count, not on elapsed days`() {
        val esc = escalation(
            startDose = q("1", UnitCode.MG),
            increaseAmount = q("1", UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.AFTER_X_DOSES,
            increaseEveryValue = 4,
        )
        assertThat(esc.doseAt(daysSinceStart = 999, dosesBefore = 3).value.toPlainString()).isEqualTo("1")
        assertThat(esc.doseAt(daysSinceStart = 0, dosesBefore = 8).value.toPlainString()).isEqualTo("3")
    }

    @Test
    fun `a date before startDate reads as day zero`() {
        val esc = escalation(startDose = q("1", UnitCode.MG), increaseAmount = q("1", UnitCode.MG))
        assertThat(esc.doseAt(daysSinceStart = -30).value.toPlainString()).isEqualTo("1")
    }

    @Test
    fun `maxDose in another unit of the family still clamps`() {
        val esc = escalation(
            startDose = q("100", UnitCode.MCG),
            increaseAmount = q("100", UnitCode.MCG),
            targetDose = q("2", UnitCode.MG),
            maxDose = q("0.35", UnitCode.MG),
            increaseEveryValue = 1,
        )
        // Day 10 wants 1100 mcg; maxDose is 350 mcg once converted.
        val dose = esc.doseAt(daysSinceStart = 10)
        assertThat(dose.value.toPlainString()).isEqualTo("350")
        assertThat(dose.unit).isEqualTo(UnitCode.MCG)
    }

    @Test
    fun `stopAtTarget clamps to targetDose expressed in the startDose unit`() {
        val esc = escalation(
            startDose = q("500", UnitCode.MCG),
            increaseAmount = q("500", UnitCode.MCG),
            targetDose = q("1.5", UnitCode.MG),
            stopAtTarget = true,
            increaseEveryValue = 1,
        )
        val dose = esc.doseAt(daysSinceStart = 99)
        assertThat(dose.value.toPlainString()).isEqualTo("1500")
        assertThat(dose.unit).isEqualTo(UnitCode.MCG)
    }

    @Test
    fun `plannedDoseAt falls back to the flat dose without an escalation`() {
        val protocol = protocol(escalation = null)
        assertThat(protocol.plannedDoseAt(startDate.plusDays(90), dosesBefore = 40))
            .isEqualTo(protocol.plannedDose)
    }

    @Test
    fun `plannedDoseAt reads the escalation against startDate`() {
        val esc = escalation(
            startDose = q("1", UnitCode.MG),
            increaseAmount = q("1", UnitCode.MG),
            increaseEveryValue = 5,
        )
        val protocol = protocol(escalation = esc)
        assertThat(protocol.plannedDoseAt(startDate.plusDays(11)).value.toPlainString()).isEqualTo("3")
    }

    // -----------------------------------------------------------------------
    // Properties — one case per random escalation (§3.2 acceptance)
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `dose never decreases as the counter grows`(seed: Int) {
        val esc = randomEscalation(Random(seed))
        var previous = esc.doseAt(0, 0)
        for (counter in 1..MAX_COUNTER) {
            val dose = esc.doseAt(counter, counter)
            assertThat(previous.value <= dose.value, name = "seed $seed, counter $counter: $previous -> $dose")
                .isTrue()
            previous = dose
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `dose never exceeds maxDose nor - under stopAtTarget - targetDose`(seed: Int) {
        val esc = randomEscalation(Random(seed))
        val ceiling = listOfNotNull(
            esc.maxDose?.valueIn(esc.startDose.unit),
            esc.targetDose.valueIn(esc.startDose.unit).takeIf { esc.stopAtTarget },
        ).minOrNull()

        for (counter in 0..MAX_COUNTER) {
            val dose = esc.doseAt(counter, counter)
            assertThat(dose.unit, name = "seed $seed: unit").isEqualTo(esc.startDose.unit)
            if (ceiling != null) {
                assertThat(dose.value, name = "seed $seed, counter $counter").isLessThanOrEqualTo(ceiling)
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `an unclamped dose is startDose plus increaseAmount added once per step`(seed: Int) {
        val esc = randomEscalation(Random(seed)).copy(maxDose = null, stopAtTarget = false)
        val step = esc.increaseAmount.valueIn(esc.startDose.unit)

        for (counter in 0..MAX_COUNTER) {
            // Repeated addition, deliberately not the engine's own multiplication.
            var expected = esc.startDose.value
            repeat(steps(esc, counter)) { expected += step }
            assertThat(esc.doseAt(counter, counter).value.toPlainString(), name = "seed $seed, counter $counter")
                .isEqualTo(expected.toPlainString())
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `dose only ever changes on a step boundary`(seed: Int) {
        val esc = randomEscalation(Random(seed))
        for (counter in 1..MAX_COUNTER) {
            if (steps(esc, counter) == steps(esc, counter - 1)) {
                assertThat(esc.doseAt(counter, counter), name = "seed $seed, counter $counter")
                    .isEqualTo(esc.doseAt(counter - 1, counter - 1))
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `a stopAtTarget escalation ends at exactly targetDose`(seed: Int) {
        val esc = randomEscalation(Random(seed)).copy(maxDose = null, stopAtTarget = true)
        val target = esc.targetDose.valueIn(esc.startDose.unit)
        // Enough steps to overshoot any target this generator builds.
        val far = esc.doseAt(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
        assertThat(far.value.toPlainString(), name = "seed $seed").isEqualTo(target.toPlainString())
    }

    companion object {
        @JvmStatic
        fun seeds(): Stream<Int> = IntStream.range(0, SEEDS).boxed()
    }
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

private fun q(value: String, unit: UnitCode) = Quantity(Decimal.parse(value), unit)

private fun LocalDate.plusDays(days: Int) = plus(days, DateTimeUnit.DAY)

/** The engine's own step count, restated so the property tests can talk about boundaries. */
private fun steps(esc: Escalation, counter: Int): Int = when (esc.increaseEvery) {
    EscalationIncreaseEvery.EVERY_X_DAYS -> counter / esc.increaseEveryValue
    EscalationIncreaseEvery.EVERY_X_WEEKS -> counter / (esc.increaseEveryValue * 7)
    EscalationIncreaseEvery.AFTER_X_DOSES -> counter / esc.increaseEveryValue
}

private fun escalation(
    startDose: Quantity = q("1", UnitCode.MG),
    targetDose: Quantity = q("100", UnitCode.MG),
    increaseAmount: Quantity = q("1", UnitCode.MG),
    increaseEvery: EscalationIncreaseEvery = EscalationIncreaseEvery.EVERY_X_DAYS,
    increaseEveryValue: Int = 7,
    maxDose: Quantity? = null,
    stopAtTarget: Boolean = false,
) = Escalation(startDose, targetDose, increaseAmount, increaseEvery, increaseEveryValue, maxDose, stopAtTarget)

/** Mass units convert into one another; the rest are the only member of their family. */
private val unitPools = listOf(
    listOf(UnitCode.MCG, UnitCode.MG, UnitCode.G),
    listOf(UnitCode.ML),
    listOf(UnitCode.IU),
    listOf(UnitCode.CAPSULE),
)

/**
 * A random escalation that would pass §3.2 validation: positive `increaseAmount`, `increaseEveryValue ≥ 1`,
 * `targetDose > startDose`, every quantity convertible into the `startDose` unit.
 */
private fun randomEscalation(random: Random): Escalation {
    val pool = unitPools.random(random)
    val startDose = q(random.nextInt(1, 200).toString(), pool.random(random))
    val startValue = startDose.valueIn(startDose.unit)
    return Escalation(
        startDose = startDose,
        targetDose = Quantity(startValue + Decimal.parse(random.nextInt(1, 500).toString()), startDose.unit),
        increaseAmount = q(random.nextInt(1, 50).toString(), pool.random(random)),
        increaseEvery = EscalationIncreaseEvery.entries.random(random),
        increaseEveryValue = random.nextInt(1, 15),
        maxDose = if (random.nextBoolean()) {
            Quantity(startValue + Decimal.parse(random.nextInt(0, 400).toString()), pool.random(random))
        } else {
            null
        },
        stopAtTarget = random.nextBoolean(),
    )
}

private fun protocol(escalation: Escalation?) = Protocol(
    id = 1,
    name = "Test",
    compoundSupplyId = 1,
    plannedDose = q("5", UnitCode.MG),
    route = Route.SUBCUTANEOUS,
    schedule = Schedule(ScheduleType.DAILY, null, null, null, null, null),
    dosageTimes = emptyList(),
    escalation = escalation,
    protocolBreak = null,
    startDate = startDate,
    endDate = null,
    reminderEnabled = false,
    reminderOffsetMinutes = 0,
    reminderBucket = null,
    injectionSiteRestriction = null,
    siteCooldownDays = null,
    notes = null,
    status = ProtocolStatus.ACTIVE,
    deletedAt = null,
    createdAt = Instant.fromEpochSeconds(0),
    updatedAt = Instant.fromEpochSeconds(0),
)
