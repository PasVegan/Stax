package com.stax.core.data.di

import com.stax.core.database.StaxDatabase
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

    // Repository bindings added as implementations land (M3+).
    // Use singleOf(::Impl) { bind<Interface>() } form exclusively.
}
