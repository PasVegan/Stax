package com.stax.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * RoomDatabase.Callback that seeds the database on first creation (§5.8.6):
 *  1. Settings singleton row with all defaults.
 *  2. 14 preset injection_site rows.
 *
 * Uses raw SQL — DAOs require coroutines and are unavailable during onCreate.
 * INSERT OR IGNORE guards against accidental double-execution.
 */
object DatabaseSeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        seedSettings(db)
        seedInjectionSites(db)
    }

    private fun seedSettings(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO settings (
                id, theme, dynamicColor, notificationStyle, timeZoneOverride,
                missedDoseWindowMinutes, onboardingCompleted, exactAlarmDegraded,
                defaultSiteCooldownDaysSC, defaultSiteCooldownDaysIM,
                createdAt, updatedAt
            ) VALUES (1, 'SYSTEM', 1, 'NORMAL', NULL, 60, 0, 0, 5, 7, ?, ?)
            """.trimIndent(),
            arrayOf(now, now),
        )
    }

    private fun seedInjectionSites(db: SupportSQLiteDatabase) {
        val presets = listOf(
            SitePreset("Abdomen Upper-Left", "ABDOMEN", "LEFT", "UPPER"),
            SitePreset("Abdomen Upper-Right", "ABDOMEN", "RIGHT", "UPPER"),
            SitePreset("Abdomen Lower-Left", "ABDOMEN", "LEFT", "LOWER"),
            SitePreset("Abdomen Lower-Right", "ABDOMEN", "RIGHT", "LOWER"),
            SitePreset("Anterior Deltoid Left", "DELT", "LEFT", null),
            SitePreset("Anterior Deltoid Right", "DELT", "RIGHT", null),
            SitePreset("Lateral Thigh Left", "QUADRICEPS", "LEFT", "OUTER"),
            SitePreset("Lateral Thigh Right", "QUADRICEPS", "RIGHT", "OUTER"),
            SitePreset("Glute Upper-Outer Left", "GLUTE", "LEFT", "UPPER"),
            SitePreset("Glute Upper-Outer Right", "GLUTE", "RIGHT", "UPPER"),
            SitePreset("Hamstring Left", "HAMSTRING", "LEFT", null),
            SitePreset("Hamstring Right", "HAMSTRING", "RIGHT", null),
            SitePreset("Lower Back Left", "LOWER_BACK", "LEFT", null),
            SitePreset("Lower Back Right", "LOWER_BACK", "RIGHT", null),
        )
        presets.forEach { site ->
            db.execSQL(
                """
                INSERT INTO injection_site (name, bodyRegion, side, sublocation, lastUsedAt, avoidUntil, notes, isAvailable)
                VALUES (?, ?, ?, ?, NULL, NULL, NULL, 1)
                """.trimIndent(),
                arrayOf(site.name, site.bodyRegion, site.side, site.sublocation),
            )
        }
    }

    private data class SitePreset(val name: String, val bodyRegion: String, val side: String, val sublocation: String?)
}
