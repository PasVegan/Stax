package com.stax.core.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class Route { SUBCUTANEOUS, INTRAMUSCULAR, ORAL, TOPICAL }

enum class ScheduleType {
    DAILY,
    EVERY_X_DAYS,
    X_TIMES_PER_DAY,
    SPECIFIC_WEEKDAYS,
    X_TIMES_PER_WEEK,
    X_TIMES_PER_MONTH,
}

data class Schedule(
    val type: ScheduleType,
    val interval: Int?,
    val timesPerDay: Int?,
    val selectedWeekdays: Set<DayOfWeek>?,
    val timesPerWeek: Int?,
    val timesPerMonth: Int?,
)

enum class EscalationIncreaseEvery { EVERY_X_DAYS, EVERY_X_WEEKS, AFTER_X_DOSES }

data class Escalation(
    val startDose: Quantity,
    val targetDose: Quantity,
    val increaseAmount: Quantity,
    val increaseEvery: EscalationIncreaseEvery,
    val increaseEveryValue: Int,
    val maxDose: Quantity?,
    val stopAtTarget: Boolean,
)

data class ProtocolBreak(val daysOn: Int, val daysOff: Int)

enum class ReminderBucket { MORNING, AFTERNOON, EVENING }

enum class BodyRegion {
    ABDOMEN,
    QUADRICEPS,
    GLUTE,
    DELT,
    FOREARM,
    HAMSTRING,
    LOWER_BACK,
    THIGH,
    UPPER_ARM,
}

enum class ProtocolStatus { ACTIVE, PAUSED, COMPLETED }

data class Protocol(
    val id: Long,
    val name: String,
    val compoundSupplyId: Long,
    val plannedDose: Quantity,
    val route: Route,
    val schedule: Schedule,
    val dosageTimes: List<LocalTime>,
    val escalation: Escalation?,
    val protocolBreak: ProtocolBreak?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val reminderEnabled: Boolean,
    val reminderOffsetMinutes: Int,
    val reminderBucket: ReminderBucket?,
    val injectionSiteRestriction: BodyRegion?,
    val siteCooldownDays: Int?,
    val notes: String?,
    val status: ProtocolStatus,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
