package com.stax.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Days of Pending rows a protocol keeps ahead of today (§5.2). */
const val SCHEDULE_HORIZON_DAYS = 7

/** Cycle an `XTimesPerMonth` schedule spreads its doses over. */
private const val MONTH_CYCLE_DAYS = 30

private const val DAYS_PER_WEEK = 7
private const val MINUTES_PER_DAY = 24 * 60

/**
 * The schedule rule (§5.2): which days a protocol doses on and at what times.
 *
 * It lives here rather than in `ScheduledDoseGenerator` for the same reason the escalation rule does
 * (§3.2, M9-02): it is a rule over pure domain types, and a screen that has to answer "how many
 * doses will this place in the next week" (§4.9.3 Forecast & warnings) may not import `:core:data`.
 * The generator keeps what is genuinely its own — turning these days and times into `ScheduledDose`
 * rows, with time zones, escalation counters and idempotence.
 */
fun Protocol.dosingTimesOn(date: LocalDate): List<LocalTime?> {
    if (date < startDate) return emptyList()
    endDate?.let { if (date > it) return emptyList() }
    if (isInBreak(date)) return emptyList()
    if (!isDosingDay(date)) return emptyList()

    if (dosageTimes.isNotEmpty()) return dosageTimes

    val perDay = if (schedule.type == ScheduleType.X_TIMES_PER_DAY) {
        (schedule.timesPerDay ?: 1).coerceAtLeast(1)
    } else {
        1
    }
    // A single dose a day carries no time of day (§5.2); several need distinct `scheduledAt`
    // values, so spread them evenly over the day.
    if (perDay == 1) return listOf(null)
    return List(perDay) { i ->
        val minuteOfDay = i * MINUTES_PER_DAY / perDay
        LocalTime(minuteOfDay / 60, minuteOfDay % 60)
    }
}

/** How many doses this protocol places from [from] (inclusive) to [until] (exclusive). */
fun Protocol.dosesBetween(from: LocalDate, until: LocalDate): Int {
    var count = 0
    var date = maxOf(from, startDate)
    while (date < until) {
        count += dosingTimesOn(date).size
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return count
}

/** §3.2 in-break derivation: `cyclePos >= daysOn` of the `daysOn + daysOff` cycle anchored at [Protocol.startDate]. */
fun Protocol.isInBreak(date: LocalDate): Boolean {
    val cycle = protocolBreak ?: return false
    val daysSinceStart = startDate.daysUntil(date)
    if (daysSinceStart < 0) return false
    val cycleLength = cycle.daysOn + cycle.daysOff
    if (cycleLength <= 0) return false
    return daysSinceStart % cycleLength >= cycle.daysOn
}

/** Whether the schedule places any dose on [date], breaks and `endDate` aside. */
private fun Protocol.isDosingDay(date: LocalDate): Boolean {
    val daysSinceStart = startDate.daysUntil(date)

    return when (schedule.type) {
        ScheduleType.DAILY, ScheduleType.X_TIMES_PER_DAY -> true

        ScheduleType.EVERY_X_DAYS ->
            daysSinceStart % (schedule.interval ?: 1).coerceAtLeast(1) == 0

        ScheduleType.SPECIFIC_WEEKDAYS ->
            date.dayOfWeek in (schedule.selectedWeekdays ?: return false)

        ScheduleType.X_TIMES_PER_WEEK ->
            spreadsOver(daysSinceStart % DAYS_PER_WEEK, schedule.timesPerWeek ?: 0, DAYS_PER_WEEK)

        ScheduleType.X_TIMES_PER_MONTH ->
            spreadsOver(daysSinceStart % MONTH_CYCLE_DAYS, schedule.timesPerMonth ?: 0, MONTH_CYCLE_DAYS)
    }
}

/**
 * True on exactly [times] of the [cycle] days (as long as `times <= cycle`), spread as evenly as
 * whole days allow — 3×/week lands on days 0, 3 and 5 rather than clustering on 0, 1, 2.
 */
private fun spreadsOver(dayInCycle: Int, times: Int, cycle: Int): Boolean = (dayInCycle * times) % cycle < times
