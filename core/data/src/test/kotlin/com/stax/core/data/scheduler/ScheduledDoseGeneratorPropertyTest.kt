package com.stax.core.data.scheduler

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.stax.core.domain.Decimal
import com.stax.core.domain.Escalation
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolBreak
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.core.domain.valueIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.Instant

/** How many random protocols the properties cover; each seed is one JUnit case. */
private const val SEEDS = 300

/** Days of protocol simulated per seed — long enough for several escalation steps and break cycles. */
private const val RUN_DAYS = 120

/**
 * The escalation rule engine (§3.2) as the generator applies it, over random schedules: the
 * properties that must hold whatever schedule, break, dosage times or escalation a protocol carries.
 *
 * The worked examples live in [ScheduledDoseGeneratorTest]; this file only states invariants.
 */
class ScheduledDoseGeneratorPropertyTest {

    private val generator = ScheduledDoseGenerator()
    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `planned dose never decreases over the run`(seed: Int) {
        val protocol = randomProtocol(Random(seed))
        val doses = generator.generate(protocol, protocol.startDate, protocol.startDate.plusDays(RUN_DAYS), zone, now)

        doses.zipWithNext { previous, next ->
            assertThat(
                previous.plannedDoseValue <= next.plannedDoseValue,
                name = "seed $seed: ${previous.originalLocalDate} ${previous.plannedDoseValue.toPlainString()} " +
                    "-> ${next.originalLocalDate} ${next.plannedDoseValue.toPlainString()}",
            ).isTrue()
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `planned dose respects maxDose and stopAtTarget`(seed: Int) {
        val protocol = randomProtocol(Random(seed))
        val escalation = protocol.escalation
        val doses = generator.generate(protocol, protocol.startDate, protocol.startDate.plusDays(RUN_DAYS), zone, now)

        if (escalation == null) {
            doses.forEach { assertThat(it.plannedDoseValue).isEqualTo(protocol.plannedDose.value) }
            return
        }
        val unit = escalation.startDose.unit
        val ceiling = listOfNotNull(
            escalation.maxDose?.valueIn(unit),
            escalation.targetDose.valueIn(unit).takeIf { escalation.stopAtTarget },
        ).minOrNull()

        doses.forEach { dose ->
            assertThat(dose.plannedDoseUnit, name = "seed $seed: unit").isEqualTo(unit)
            if (ceiling != null) {
                assertThat(
                    dose.plannedDoseValue <= ceiling,
                    name = "seed $seed: ${dose.originalLocalDate} ${dose.plannedDoseValue.toPlainString()} " +
                        "> ceiling ${ceiling.toPlainString()}",
                ).isTrue()
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `a sub-range generates exactly the rows the whole run puts in it`(seed: Int) {
        val random = Random(seed)
        val protocol = randomProtocol(random)
        val from = protocol.startDate.plusDays(random.nextInt(0, RUN_DAYS))
        // Kept inside the whole run, so the two generations cover the same days.
        val until = minOf(from.plusDays(random.nextInt(1, 30)), protocol.startDate.plusDays(RUN_DAYS))

        val whole = generator.generate(protocol, protocol.startDate, protocol.startDate.plusDays(RUN_DAYS), zone, now)
        val slice = generator.generate(protocol, from, until, zone, now)

        assertThat(slice, name = "seed $seed: [$from, $until)")
            .isEqualTo(whole.filter { it.originalLocalDate >= from && it.originalLocalDate < until })
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    fun `no dose lands outside the protocol dates or on a break day`(seed: Int) {
        val protocol = randomProtocol(Random(seed))
        val doses = generator.generate(
            protocol,
            protocol.startDate.minusDays(10),
            protocol.startDate.plusDays(RUN_DAYS),
            zone,
            now,
        )

        doses.forEach { dose ->
            val date = dose.originalLocalDate
            assertThat(date >= protocol.startDate, name = "seed $seed: $date before start").isTrue()
            protocol.endDate?.let { assertThat(date <= it, name = "seed $seed: $date after end").isTrue() }
            assertThat(generator.isInBreak(protocol, date), name = "seed $seed: $date in break").isFalse()
        }
    }

    companion object {
        @JvmStatic
        fun seeds(): Stream<Int> = IntStream.range(0, SEEDS).boxed()
    }
}

// ---------------------------------------------------------------------------
// Random protocols
// ---------------------------------------------------------------------------

private val start = LocalDate(2026, 1, 1)

private fun LocalDate.plusDays(days: Int) = plus(days, DateTimeUnit.DAY)

private fun LocalDate.minusDays(days: Int) = plus(-days, DateTimeUnit.DAY)

/** Mass units convert into one another, so an escalation may mix them; the others stand alone. */
private val unitPools = listOf(
    listOf(UnitCode.MCG, UnitCode.MG, UnitCode.G),
    listOf(UnitCode.ML),
    listOf(UnitCode.IU),
    listOf(UnitCode.CAPSULE),
)

private fun randomSchedule(random: Random): Schedule {
    val type = ScheduleType.entries.random(random)
    return Schedule(
        type = type,
        interval = random.nextInt(1, 10).takeIf { type == ScheduleType.EVERY_X_DAYS },
        timesPerDay = random.nextInt(1, 5).takeIf { type == ScheduleType.X_TIMES_PER_DAY },
        selectedWeekdays = DayOfWeek.entries
            .shuffled(random)
            .take(random.nextInt(1, 8))
            .toSet()
            .takeIf { type == ScheduleType.SPECIFIC_WEEKDAYS },
        timesPerWeek = random.nextInt(1, 8).takeIf { type == ScheduleType.X_TIMES_PER_WEEK },
        timesPerMonth = random.nextInt(1, 31).takeIf { type == ScheduleType.X_TIMES_PER_MONTH },
    )
}

/** A random escalation that would pass §3.2 validation — `targetDose > startDose`, `increaseAmount > 0`. */
private fun randomEscalation(random: Random): Escalation {
    val pool = unitPools.random(random)
    val startDose = Quantity(Decimal.parse(random.nextInt(1, 200).toString()), pool.random(random))
    return Escalation(
        startDose = startDose,
        targetDose = Quantity(startDose.value + Decimal.parse(random.nextInt(1, 500).toString()), startDose.unit),
        increaseAmount = Quantity(Decimal.parse(random.nextInt(1, 50).toString()), pool.random(random)),
        increaseEvery = EscalationIncreaseEvery.entries.random(random),
        increaseEveryValue = random.nextInt(1, 15),
        maxDose = Quantity(startDose.value + Decimal.parse(random.nextInt(0, 400).toString()), pool.random(random))
            .takeIf { random.nextBoolean() },
        stopAtTarget = random.nextBoolean(),
    )
}

private fun randomProtocol(random: Random): Protocol = Protocol(
    id = 1L,
    name = "Random protocol",
    compoundSupplyId = 1L,
    plannedDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
    route = Route.entries.random(random),
    schedule = randomSchedule(random),
    dosageTimes = List(random.nextInt(0, 4)) { LocalTime(random.nextInt(0, 24), random.nextInt(0, 60)) }
        .distinct()
        .sorted(),
    escalation = randomEscalation(random).takeIf { random.nextInt(4) > 0 },
    protocolBreak = ProtocolBreak(daysOn = random.nextInt(1, 30), daysOff = random.nextInt(0, 15))
        .takeIf { random.nextBoolean() },
    startDate = start,
    endDate = start.plusDays(random.nextInt(1, RUN_DAYS * 2)).takeIf { random.nextBoolean() },
    reminderEnabled = false,
    reminderOffsetMinutes = 0,
    reminderBucket = null,
    injectionSiteRestriction = null,
    siteCooldownDays = null,
    notes = null,
    status = ProtocolStatus.ACTIVE,
    deletedAt = null,
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
)
