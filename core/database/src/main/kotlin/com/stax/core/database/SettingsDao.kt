package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SettingsDao {

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    abstract fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    abstract suspend fun get(): SettingsEntity?

    @Update
    abstract suspend fun update(entity: SettingsEntity): Int

    suspend fun insert(entity: SettingsEntity) {
        require(entity.id == 1L) { "Settings id must be 1, got ${entity.id}" }
        insertInternal(entity)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertInternal(entity: SettingsEntity)
}
