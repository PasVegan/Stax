package com.stax.core.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.concurrent.Executors

/**
 * Stax Room database.
 *
 * Journal mode: WRITE_AHEAD_LOGGING — concurrent reads during background-worker writes (§2.3.5).
 * Foreign keys: ON — Room default, no explicit configuration needed.
 *
 * Entities and DAOs are added incrementally.
 */
@Database(
    entities = [
        CompoundSupplyEntity::class,
        OpenedContainerEntity::class,
        ProtocolEntity::class,
        ProtocolDosageTimeEntity::class,
        ScheduledDoseEntity::class,
        InjectionSiteEntity::class,
        AdministrationEventEntity::class,
        DoseComponentEntity::class,
        InventoryTransactionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class StaxDatabase : RoomDatabase() {

    abstract fun compoundSupplyDao(): CompoundSupplyDao
    abstract fun openedContainerDao(): OpenedContainerDao
    abstract fun protocolDao(): ProtocolDao
    abstract fun protocolDosageTimeDao(): ProtocolDosageTimeDao
    abstract fun scheduledDoseDao(): ScheduledDoseDao
    abstract fun administrationEventDao(): AdministrationEventDao
    abstract fun doseComponentDao(): DoseComponentDao
    abstract fun injectionSiteDao(): InjectionSiteDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao

    companion object {

        private const val DB_NAME = "stax.db"
        private const val TAG = "StaxRoom"

        /**
         * Build the database instance.
         *
         * @param context     Application context.
         * @param enableQueryLog  Pass `true` on debug builds to log every SQL statement.
         *                        Foreign keys are enabled automatically by Room (§2.3.5).
         */
        fun build(context: Context, enableQueryLog: Boolean): StaxDatabase = Room.databaseBuilder(
            context.applicationContext,
            StaxDatabase::class.java,
            DB_NAME,
        )
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .apply {
                if (enableQueryLog) {
                    setQueryCallback(
                        QueryCallback { sql, args ->
                            Log.d(TAG, "SQL: $sql | args: $args")
                        },
                        Executors.newSingleThreadExecutor(),
                    )
                }
            }
            .build()
    }
}
