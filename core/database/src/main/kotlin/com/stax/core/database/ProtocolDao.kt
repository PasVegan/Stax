package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtocolDao {

    @Insert
    suspend fun insert(entity: ProtocolEntity): Long

    @Query("SELECT * FROM protocol WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ProtocolEntity?>
}
