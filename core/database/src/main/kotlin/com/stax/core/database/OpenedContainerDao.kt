package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenedContainerDao {

    @Insert
    suspend fun insert(entity: OpenedContainerEntity): Long

    @Query("SELECT * FROM opened_container WHERE compoundSupplyId = :compoundSupplyId LIMIT 1")
    fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<OpenedContainerEntity?>
}
