package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryTransactionDao {

    @Insert
    suspend fun insert(entity: InventoryTransactionEntity): Long

    @Query(
        """
        SELECT * FROM inventory_transaction
        WHERE compoundSupplyId = :compoundSupplyId
        ORDER BY at ASC
        """,
    )
    fun observeByCompound(compoundSupplyId: Long): Flow<List<InventoryTransactionEntity>>

    @Query(
        """
        SELECT SUM(CAST(deltaValue AS REAL))
        FROM inventory_transaction
        WHERE compoundSupplyId = :compoundSupplyId
        """,
    )
    fun sumDelta(compoundSupplyId: Long): Flow<Double?>
}
