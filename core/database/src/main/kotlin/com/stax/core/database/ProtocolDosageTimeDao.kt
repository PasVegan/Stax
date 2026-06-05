package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtocolDosageTimeDao {

    @Insert
    suspend fun insert(entity: ProtocolDosageTimeEntity)

    @Insert
    suspend fun insertAll(entities: List<ProtocolDosageTimeEntity>)

    @Query("DELETE FROM protocol_dosage_time WHERE protocolId = :protocolId")
    suspend fun deleteByProtocolId(protocolId: Long): Int

    @Query(
        """
        SELECT * FROM protocol_dosage_time
        WHERE protocolId = :protocolId
        ORDER BY time ASC
        """,
    )
    fun observeByProtocolId(protocolId: Long): Flow<List<ProtocolDosageTimeEntity>>

    /** Non-Flow read for use inside transactions. */
    @Query(
        """
        SELECT * FROM protocol_dosage_time
        WHERE protocolId = :protocolId
        ORDER BY time ASC
        """,
    )
    suspend fun getByProtocolId(protocolId: Long): List<ProtocolDosageTimeEntity>
}
