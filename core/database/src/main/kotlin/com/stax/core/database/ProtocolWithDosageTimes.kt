package com.stax.core.database

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room join result: a protocol row plus its dosage-time rows.
 *
 * Used by the repository layer to assemble complete [com.stax.core.domain.Protocol] domain
 * objects (which carry `dosageTimes: List<LocalTime>`) without extra round-trips.
 */
data class ProtocolWithDosageTimes(
    @Embedded val protocol: ProtocolEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "protocolId",
    )
    val dosageTimeEntities: List<ProtocolDosageTimeEntity>,
)
