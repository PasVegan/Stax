package com.stax.feature.protocols.presentation.form

import androidx.compose.runtime.Immutable
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.Decimal
import com.stax.core.domain.Quantity
import com.stax.core.domain.ReminderBucket
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.serializers.DayOfWeekSerializer
import kotlinx.serialization.Serializable

/**
 * kotlinx-datetime ships a `DayOfWeek` serializer but does not mark the enum `@Serializable`, so the
 * draft names the pairing once rather than annotating the type argument in place.
 */
typealias SerializableDayOfWeek =
    @Serializable(DayOfWeekSerializer::class)
    DayOfWeek

/**
 * The fields of the Create / Edit Protocol form the user can edit (§4.9.3), and nothing else.
 *
 * Every numeric field is the user's **raw text** for the same reason the compound form's are: a
 * field mid-edit ("1.", "", "0.0") has no `Decimal` to be, and parsing on each keystroke would
 * either throw or rewrite what is being typed. Parsing happens once, on Save.
 *
 * The four schedule counts are all kept, not one shared field: flipping between "Every 3 days" and
 * "2×/day" and back should give the 3 back, not whatever the other chip was last set to.
 *
 * `@Serializable` because this is what the ViewModel mirrors into its `SavedStateHandle` — the same
 * auto-saved-draft rule the compound form follows (§4.4.5), so backgrounding and the process death
 * that can follow it both resume the form as it was left.
 */
@Immutable
@Serializable
data class ProtocolFormDraft(
    val compoundSupplyId: Long? = null,
    val route: Route = Route.SUBCUTANEOUS,
    val doseAmount: String = "",
    val doseUnit: UnitCode = UnitCode.MG,
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val everyXDays: String = "2",
    val timesPerDay: String = "2",
    val weekdays: Set<SerializableDayOfWeek> = emptySet(),
    val timesPerWeek: String = "3",
    val timesPerMonth: String = "2",
    /** Empty = "no specific time": the dose shows as "Today" on the dashboard (§4.9.3). */
    val dosageTimes: List<LocalTime> = emptyList(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val reminderEnabled: Boolean = true,
    /** §4.9.3: `0` = at the scheduled time. Not editable here — Settings owns the notification style (§4.13.3). */
    val reminderOffsetMinutes: Int = 0,
    /** Only used when [dosageTimes] is empty (§3.2); the bucket chips default to Morning (§4.9.3). */
    val reminderBucket: ReminderBucket = ReminderBucket.MORNING,
    val siteRestriction: BodyRegion? = null,
    val notes: String = "",
    /** The fields the user has set by hand, so picking a compound never overwrites a dose they typed. */
    val touched: Set<ProtocolFormField> = emptySet(),
)

/**
 * The fields the form tracks, in the order they appear on it — which is the order Save walks to find
 * the first error to scroll to. §4.9.3 marks Compound, Planned dose, Schedule and Start required;
 * [ROUTE] is here only so a route the user picked survives the next compound's default, and never
 * appears in `errors`.
 */
enum class ProtocolFormField { COMPOUND, ROUTE, DOSE, SCHEDULE_COUNT, WEEKDAYS, START_DATE, END_DATE }

/** Why a field was rejected on Save. The screen owns the wording; the ViewModel owns the rule. */
enum class ProtocolFormError {
    COMPOUND_REQUIRED,
    DOSE_NOT_POSITIVE,
    SCHEDULE_COUNT_INVALID,
    WEEKDAYS_REQUIRED,
    START_DATE_REQUIRED,
    END_DATE_NOT_AFTER_START,
}

/** The overlays the form opens to choose something. At most one is open, so it is a nullable enum. */
enum class ProtocolFormPicker { COMPOUND, BODY_REGION, DOSE_UNIT }

/** Which end of the Duration row (§4.9.3) the date picker was opened for. */
enum class ProtocolDateField { START, END }

/** The schedule chips of §4.9.3, in the order the row lists them. */
val SCHEDULE_CHIPS: List<ScheduleType> = listOf(
    ScheduleType.DAILY,
    ScheduleType.EVERY_X_DAYS,
    ScheduleType.SPECIFIC_WEEKDAYS,
    ScheduleType.X_TIMES_PER_WEEK,
    ScheduleType.X_TIMES_PER_DAY,
    ScheduleType.X_TIMES_PER_MONTH,
)

/**
 * The route a compound of this form is normally taken by (§4.4.3's smart-default table, whose route
 * column belongs to a Protocol rather than to the compound). §4.9.3 defaults the Route segments to it.
 */
internal fun CompoundForm.defaultRoute(): Route = when (this) {
    CompoundForm.INJECTABLE -> Route.SUBCUTANEOUS
    CompoundForm.TOPICAL -> Route.TOPICAL
    CompoundForm.CAPSULE, CompoundForm.TABLET, CompoundForm.POWDER, CompoundForm.LIQUID -> Route.ORAL
}

/** Which numeric count the selected chip reads, or null for the chips that need no number. */
internal fun ProtocolFormDraft.scheduleCount(): String? = when (scheduleType) {
    ScheduleType.EVERY_X_DAYS -> everyXDays
    ScheduleType.X_TIMES_PER_DAY -> timesPerDay
    ScheduleType.X_TIMES_PER_WEEK -> timesPerWeek
    ScheduleType.X_TIMES_PER_MONTH -> timesPerMonth
    ScheduleType.DAILY, ScheduleType.SPECIFIC_WEEKDAYS -> null
}

/** [scheduleCount] written back into whichever field the selected chip owns. */
internal fun ProtocolFormDraft.withScheduleCount(value: String): ProtocolFormDraft = when (scheduleType) {
    ScheduleType.EVERY_X_DAYS -> copy(everyXDays = value)
    ScheduleType.X_TIMES_PER_DAY -> copy(timesPerDay = value)
    ScheduleType.X_TIMES_PER_WEEK -> copy(timesPerWeek = value)
    ScheduleType.X_TIMES_PER_MONTH -> copy(timesPerMonth = value)
    ScheduleType.DAILY, ScheduleType.SPECIFIC_WEEKDAYS -> this
}

/** The domain [Schedule] the chips describe — only the field the selected type reads is filled. */
internal fun ProtocolFormDraft.toSchedule(): Schedule = Schedule(
    type = scheduleType,
    interval = everyXDays.toIntOrNull().takeIf { scheduleType == ScheduleType.EVERY_X_DAYS },
    timesPerDay = timesPerDay.toIntOrNull().takeIf { scheduleType == ScheduleType.X_TIMES_PER_DAY },
    selectedWeekdays = weekdays.takeIf { scheduleType == ScheduleType.SPECIFIC_WEEKDAYS },
    timesPerWeek = timesPerWeek.toIntOrNull().takeIf { scheduleType == ScheduleType.X_TIMES_PER_WEEK },
    timesPerMonth = timesPerMonth.toIntOrNull().takeIf { scheduleType == ScheduleType.X_TIMES_PER_MONTH },
)

/** The reverse of [toSchedule]: a stored schedule as chip + counts, leaving the other counts at their defaults. */
internal fun ProtocolFormDraft.withSchedule(schedule: Schedule): ProtocolFormDraft = copy(
    scheduleType = schedule.type,
    everyXDays = schedule.interval?.toString() ?: everyXDays,
    timesPerDay = schedule.timesPerDay?.toString() ?: timesPerDay,
    weekdays = schedule.selectedWeekdays.orEmpty(),
    timesPerWeek = schedule.timesPerWeek?.toString() ?: timesPerWeek,
    timesPerMonth = schedule.timesPerMonth?.toString() ?: timesPerMonth,
)

/** Whether the form differs from what it was loaded with (§4.4.5's rule). Which fields were touched is not a change. */
internal fun ProtocolFormDraft.differsFrom(other: ProtocolFormDraft): Boolean =
    copy(touched = emptySet()) != other.copy(touched = emptySet())

internal fun ProtocolFormDraft.plannedDoseOrNull(): Quantity? =
    doseAmount.toDecimalOrNull()?.let { Quantity(it, doseUnit) }

/** `Decimal.parse` throws on anything that is not a number, and a field mid-edit routinely is not. */
internal fun String.toDecimalOrNull(): Decimal? = try {
    trim().takeIf { it.isNotBlank() }?.let(Decimal::parse)
} catch (_: NumberFormatException) {
    null
}

internal val ZERO: Decimal = Decimal.parse("0")
