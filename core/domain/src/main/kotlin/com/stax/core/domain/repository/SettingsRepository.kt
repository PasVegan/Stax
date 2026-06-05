package com.stax.core.domain.repository

import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Settings
import kotlinx.coroutines.flow.Flow

/**
 * Owns all reads and writes for the singleton Settings entity (§3.8).
 *
 * Room is authoritative. The two theme-critical fields (`theme`, `dynamicColor`) are
 * mirrored to a DataStore preferences file on every [update] call so the cold-start
 * [ThemeInitializer] can read them without opening Room (§2.3.4).
 *
 * Rule: never read or write theme from anywhere except via this repository.
 */
interface SettingsRepository {

    /**
     * Emits the current [Settings] and re-emits whenever the row changes.
     * Skips the initial null emission when the singleton row has not yet been seeded.
     */
    fun observe(): Flow<Settings>

    /**
     * Persists [settings] to the Room `settings` table, then writes `theme` and
     * `dynamicColor` through to DataStore.
     *
     * The DataStore write happens in the same coroutine as the Room write, guaranteeing
     * the mirror lags at most one commit behind Room (§3.8 storage rule).
     */
    suspend fun update(settings: Settings): EmptyResult<DataError.Local>
}
