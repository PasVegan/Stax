package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface InjectionSiteDao {

    @Insert
    suspend fun insert(entity: InjectionSiteEntity): Long

    @Insert
    suspend fun insertAll(entities: List<InjectionSiteEntity>)

    @Update
    suspend fun update(entity: InjectionSiteEntity): Int

    @Query("DELETE FROM injection_site WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM injection_site WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<InjectionSiteEntity?>

    @Query("SELECT * FROM injection_site WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InjectionSiteEntity?

    @Query(
        """
        SELECT * FROM injection_site
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeAll(): Flow<List<InjectionSiteEntity>>

    @Query(
        """
        SELECT * FROM injection_site
        WHERE isAvailable = 1
            AND (avoidUntil IS NULL OR avoidUntil <= :now)
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeReadySites(now: Instant): Flow<List<InjectionSiteEntity>>

    @Query(
        """
        SELECT * FROM injection_site
        WHERE avoidUntil > :now
        ORDER BY avoidUntil ASC
        """,
    )
    fun observeCoolingSites(now: Instant): Flow<List<InjectionSiteEntity>>
}
