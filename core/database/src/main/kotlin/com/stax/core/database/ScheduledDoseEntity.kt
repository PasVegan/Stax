package com.stax.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

@Entity(
    tableName = "scheduled_dose",
    foreignKeys = [
        ForeignKey(
            entity = ProtocolEntity::class,
            parentColumns = ["id"],
            childColumns = ["protocolId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CompoundSupplyEntity::class,
            parentColumns = ["id"],
            childColumns = ["compoundSupplyId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["protocolId", "scheduledAt"], unique = true),
        Index(value = ["status", "scheduledAt"]),
        Index(value = ["compoundSupplyId", "status", "scheduledAt"]),
        Index(value = ["administrationEventId"]),
    ],
)
data class ScheduledDoseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val protocolId: Long,
    val compoundSupplyId: Long,
    val scheduledAt: Instant,
    val hasTimeOfDay: Boolean,
    val plannedDoseValue: Decimal,
    val plannedDoseUnit: UnitCode,
    val route: Route,
    val status: ScheduledDoseStatus,
    val administrationEventId: Long?,
    val originalLocalDate: LocalDate,
    val originalLocalTime: LocalTime?,
    val originalZone: String,
    val createdAt: Instant,
)

enum class ScheduledDoseStatus {
    PENDING,
    TAKEN,
    SKIPPED,
    MISSED,
    PARTIAL,
}
