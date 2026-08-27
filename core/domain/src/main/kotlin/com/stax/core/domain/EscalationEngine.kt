package com.stax.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private const val DAYS_PER_WEEK = 7

/**
 * The escalation rule engine (§3.2): the dose an [Escalation] prescribes at an arbitrary point of
 * a protocol.
 *
 * The rule is a step function of a single counter — elapsed days for
 * [EscalationIncreaseEvery.EVERY_X_DAYS] / [EscalationIncreaseEvery.EVERY_X_WEEKS], cumulative
 * doses for [EscalationIncreaseEvery.AFTER_X_DOSES]:
 *
 * ```
 * steps = counter / increaseEveryValue          // integer division
 * dose  = min(startDose + increaseAmount × steps, ceiling)
 * ceiling = min(maxDose, targetDose if stopAtTarget)   // whichever are set
 * ```
 *
 * @param daysSinceStart days elapsed since `Protocol.startDate`; negative values read as 0.
 * @param dosesBefore doses the protocol places between `startDate` and this one, breaks and
 *   off-days excluded — only [EscalationIncreaseEvery.AFTER_X_DOSES] reads it.
 * @return the dose, always expressed in [Escalation.startDose]'s unit.
 * @throws IllegalArgumentException if another quantity's unit is not convertible to that unit —
 *   an escalation that passed `validateEscalation*` (§3.2) never is.
 */
fun Escalation.doseAt(daysSinceStart: Int, dosesBefore: Int = 0): Quantity {
    val unit = startDose.unit
    val every = increaseEveryValue.coerceAtLeast(1)

    val steps = when (increaseEvery) {
        EscalationIncreaseEvery.EVERY_X_DAYS -> daysSinceStart.coerceAtLeast(0) / every
        EscalationIncreaseEvery.EVERY_X_WEEKS -> daysSinceStart.coerceAtLeast(0) / (every * DAYS_PER_WEEK)
        EscalationIncreaseEvery.AFTER_X_DOSES -> dosesBefore.coerceAtLeast(0) / every
    }

    val value = startDose.value + increaseAmount.valueIn(unit) * Decimal.parse(steps.toString())
    val ceiling = listOfNotNull(
        maxDose?.valueIn(unit),
        targetDose.valueIn(unit).takeIf { stopAtTarget },
    ).minOrNull()

    return Quantity(if (ceiling != null && value > ceiling) ceiling else value, unit)
}

/**
 * The dose this protocol plans for [date] — [doseAt] when it escalates, the flat
 * [Protocol.plannedDose] when it does not.
 *
 * @param dosesBefore see [doseAt]; only an [EscalationIncreaseEvery.AFTER_X_DOSES] escalation
 *   reads it, and only the caller knows how many doses its schedule has placed by [date].
 */
fun Protocol.plannedDoseAt(date: LocalDate, dosesBefore: Int = 0): Quantity =
    escalation?.doseAt(startDate.daysUntil(date), dosesBefore) ?: plannedDose
