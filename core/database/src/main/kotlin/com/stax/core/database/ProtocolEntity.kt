package com.stax.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Entity(
    tableName = "protocol",
    foreignKeys = [
        ForeignKey(
            entity = CompoundSupplyEntity::class,
            parentColumns = ["id"],
            childColumns = ["compoundSupplyId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["compoundSupplyId", "status", "deletedAt"]),
        Index(value = ["status", "startDate", "endDate"]),
    ],
)
data class ProtocolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val compoundSupplyId: Long,
    val plannedDoseValue: Decimal,
    val plannedDoseUnit: UnitCode,
    val route: Route,
    @Embedded(prefix = "schedule")
    val schedule: ScheduleEmbed,
    val selectedWeekdaysBitmask: Int,
    @Embedded(prefix = "escalation")
    val escalation: EscalationEmbed?,
    @Embedded(prefix = "break")
    val protocolBreak: ProtocolBreakEmbed?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val reminderEnabled: Boolean,
    val reminderOffsetMinutes: Int,
    val reminderBucket: ReminderBucket?,
    val injectionSiteRestriction: BodyRegion?,
    val notes: String?,
    val status: ProtocolStatus,
    val siteCooldownDays: Int?,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ScheduleEmbed(
    @ColumnInfo(name = "Type")
    val type: ScheduleType,
    @ColumnInfo(name = "Interval")
    val interval: Int?,
    @ColumnInfo(name = "TimesPerDay")
    val timesPerDay: Int?,
    @ColumnInfo(name = "TimesPerWeek")
    val timesPerWeek: Int?,
    @ColumnInfo(name = "TimesPerMonth")
    val timesPerMonth: Int?,
)

data class EscalationEmbed(
    @ColumnInfo(name = "StartDoseValue")
    val startDoseValue: Decimal?,
    @ColumnInfo(name = "StartDoseUnit")
    val startDoseUnit: UnitCode?,
    @ColumnInfo(name = "TargetDoseValue")
    val targetDoseValue: Decimal?,
    @ColumnInfo(name = "TargetDoseUnit")
    val targetDoseUnit: UnitCode?,
    @ColumnInfo(name = "IncreaseAmountValue")
    val increaseAmountValue: Decimal?,
    @ColumnInfo(name = "IncreaseAmountUnit")
    val increaseAmountUnit: UnitCode?,
    @ColumnInfo(name = "IncreaseEvery")
    val increaseEvery: EscalationIncreaseEvery?,
    @ColumnInfo(name = "IncreaseEveryValue")
    val increaseEveryValue: Int?,
    @ColumnInfo(name = "MaxDoseValue")
    val maxDoseValue: Decimal?,
    @ColumnInfo(name = "MaxDoseUnit")
    val maxDoseUnit: UnitCode?,
    @ColumnInfo(name = "StopAtTarget")
    val stopAtTarget: Boolean?,
)

data class ProtocolBreakEmbed(
    @ColumnInfo(name = "DaysOn")
    val daysOn: Int?,
    @ColumnInfo(name = "DaysOff")
    val daysOff: Int?,
)

enum class Route {
    SUBCUTANEOUS,
    INTRAMUSCULAR,
    ORAL,
    TOPICAL,
}

enum class ScheduleType {
    DAILY,
    EVERY_X_DAYS,
    X_TIMES_PER_DAY,
    SPECIFIC_WEEKDAYS,
    X_TIMES_PER_WEEK,
    X_TIMES_PER_MONTH,
}

enum class EscalationIncreaseEvery {
    EVERY_X_DAYS,
    EVERY_X_WEEKS,
    AFTER_X_DOSES,
}

enum class ReminderBucket {
    MORNING,
    AFTERNOON,
    EVENING,
}

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

enum class ProtocolStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
}
