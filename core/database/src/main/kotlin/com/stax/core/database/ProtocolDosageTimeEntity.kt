package com.stax.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.datetime.LocalTime

@Entity(
    tableName = "protocol_dosage_time",
    primaryKeys = ["protocolId", "time"],
    foreignKeys = [
        ForeignKey(
            entity = ProtocolEntity::class,
            parentColumns = ["id"],
            childColumns = ["protocolId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["protocolId", "time"], unique = true),
        Index(value = ["time"]),
    ],
)
data class ProtocolDosageTimeEntity(val protocolId: Long, val time: LocalTime)
