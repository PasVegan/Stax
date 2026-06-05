package com.stax.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.Preferences
import com.stax.core.data.preferences.ThemePreferences
import com.stax.core.data.repository.RoomSettingsRepository
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.repository.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val coreDataModule = module {
    // DAOs — resolved from the StaxDatabase singleton bound by RoomDatabaseInitializer.
    single { get<StaxDatabase>().compoundSupplyDao() }
    single { get<StaxDatabase>().openedContainerDao() }
    single { get<StaxDatabase>().protocolDao() }
    single { get<StaxDatabase>().protocolDosageTimeDao() }
    single { get<StaxDatabase>().scheduledDoseDao() }
    single { get<StaxDatabase>().administrationEventDao() }
    single { get<StaxDatabase>().doseComponentDao() }
    single { get<StaxDatabase>().injectionSiteDao() }
    single { get<StaxDatabase>().inventoryTransactionDao() }
    single { get<StaxDatabase>().settingsDao() }

    // DataStore — theme mirror (§2.3.4, §3.8).
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            produceFile = { androidContext().preferencesDataStoreFile(ThemePreferences.FILE_NAME) },
        )
    }

    // Repositories.
    single { RoomSettingsRepository(get(), get()) } bind SettingsRepository::class
}
