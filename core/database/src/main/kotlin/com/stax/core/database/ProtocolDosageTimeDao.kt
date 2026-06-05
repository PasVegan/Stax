package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtocolDosageTimeDao {

    @Insert
    suspend fun insert(entity: ProtocolDosageTimeEntity)

    @Query(
        """
        SELECT * FROM protocol_dosage_time
        WHERE protocolId = :protocolId
        ORDER BY time ASC
        """,
    )
    fun observeByProtocolId(protocolId: Long): Flow<List<ProtocolDosageTimeEntity>>
}
