package com.stax.core.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Executors

/**
 * Stax Room database.
 *
 * Journal mode: WRITE_AHEAD_LOGGING — concurrent reads during background-worker writes (§2.3.5).
 * Foreign keys: ON — Room default, no explicit configuration needed.
 *
 * Entities and DAOs are added incrementally starting from M1.
 * [PlaceholderEntity] exists only to satisfy Room KSP's non-empty entity requirement during
 * the scaffold phase; it is removed and [version] bumped when the first real entity lands.
 */
@Database(
    entities = [StaxDatabase.PlaceholderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class StaxDatabase : RoomDatabase() {

    /** Removed when real entities replace this scaffold (M1). */
    @Entity(tableName = "_placeholder")
    internal data class PlaceholderEntity(@PrimaryKey val id: Int = 1)

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
        fun build(context: Context, enableQueryLog: Boolean): StaxDatabase =
            Room.databaseBuilder(
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
