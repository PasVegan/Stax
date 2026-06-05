package com.stax.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.stax.core.data.mapper.toDomain
import com.stax.core.data.mapper.toEntity
import com.stax.core.data.preferences.ThemePreferences
import com.stax.core.database.SettingsDao
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Result
import com.stax.core.domain.Settings
import com.stax.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Room-backed [SettingsRepository].
 *
 * Write path (§3.8 storage rule):
 *  1. Persist full Settings row to Room.
 *  2. Mirror `theme` + `dynamicColor` to DataStore in the same coroutine.
 *
 * Both writes happen before `update` returns, so the DataStore mirror lags at most
 * one commit behind Room — the cold-start ThemeInitializer (§2.3.4) will always
 * see a value consistent with the previous Room commit.
 */
class RoomSettingsRepository(private val dao: SettingsDao, private val dataStore: DataStore<Preferences>) :
    SettingsRepository {

    override fun observe(): Flow<Settings> = dao.observe()
        .filterNotNull()
        .map { it.toDomain() }

    override suspend fun update(settings: Settings): EmptyResult<DataError.Local> {
        return try {
            val updatedRows = dao.update(settings.toEntity())
            if (updatedRows == 0) return Result.Error(DataError.Local.NOT_FOUND)

            // Write-through: keep DataStore mirror in sync with the Room commit (§3.8).
            dataStore.edit { prefs ->
                prefs[ThemePreferences.THEME] = settings.theme.name
                prefs[ThemePreferences.DYNAMIC_COLOR] = settings.dynamicColor
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
}
