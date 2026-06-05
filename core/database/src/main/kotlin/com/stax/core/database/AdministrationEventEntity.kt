package com.stax.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(
    tableName = "administration_event",
    indices = [
        Index(value = ["loggedAt"]),
        Index(value = ["status", "loggedAt"]),
        Index(value = ["injectionSiteId", "loggedAt"]),
    ],
)
data class AdministrationEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loggedAt: Instant,
    val route: Route,
    val status: AdministrationEventStatus,
    val injectionSiteId: Long?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class AdministrationEventStatus {
    TAKEN,
    SKIPPED,
    PARTIAL,
}
