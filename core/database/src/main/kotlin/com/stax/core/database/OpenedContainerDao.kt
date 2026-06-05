package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenedContainerDao {

    @Insert
    suspend fun insert(entity: OpenedContainerEntity): Long

    @Update
    suspend fun update(entity: OpenedContainerEntity): Int

    @Query("SELECT * FROM opened_container WHERE compoundSupplyId = :compoundSupplyId LIMIT 1")
    fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<OpenedContainerEntity?>

    /** Non-Flow point read for use inside transactions. */
    @Query("SELECT * FROM opened_container WHERE compoundSupplyId = :compoundSupplyId LIMIT 1")
    suspend fun getByCompoundSupplyId(compoundSupplyId: Long): OpenedContainerEntity?

    @Query("DELETE FROM opened_container WHERE compoundSupplyId = :compoundSupplyId")
    suspend fun deleteByCompoundSupplyId(compoundSupplyId: Long): Int
}
