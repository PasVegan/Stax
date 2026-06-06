package com.stax.core.data.di

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.isInstanceOf
import com.stax.core.data.repository.RoomAdministrationEventRepository
import com.stax.core.data.repository.RoomCompoundRepository
import com.stax.core.data.repository.RoomInjectionSiteRepository
import com.stax.core.data.repository.RoomInventoryRepository
import com.stax.core.data.repository.RoomProtocolRepository
import com.stax.core.data.repository.RoomScheduledDoseRepository
import com.stax.core.data.repository.RoomSettingsRepository
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.repository.AdministrationEventRepository
import com.stax.core.domain.repository.CompoundRepository
import com.stax.core.domain.repository.InjectionSiteRepository
import com.stax.core.domain.repository.InventoryRepository
import com.stax.core.domain.repository.ProtocolRepository
import com.stax.core.domain.repository.ScheduledDoseRepository
import com.stax.core.domain.repository.SettingsRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreDataModuleTest {

    @Test
    fun `repository interfaces resolve from core data module`() {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        val koinApplication = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                module {
                    single<StaxDatabase> { database }
                },
                coreDataModule,
            )
        }

        try {
            val koin = koinApplication.koin

            assertThat(koin.get<SettingsRepository>()).isInstanceOf(RoomSettingsRepository::class)
            assertThat(koin.get<CompoundRepository>()).isInstanceOf(RoomCompoundRepository::class)
            assertThat(koin.get<ProtocolRepository>()).isInstanceOf(RoomProtocolRepository::class)
            assertThat(koin.get<ScheduledDoseRepository>()).isInstanceOf(RoomScheduledDoseRepository::class)
            assertThat(koin.get<AdministrationEventRepository>()).isInstanceOf(RoomAdministrationEventRepository::class)
            assertThat(koin.get<InjectionSiteRepository>()).isInstanceOf(RoomInjectionSiteRepository::class)
            assertThat(koin.get<InventoryRepository>()).isInstanceOf(RoomInventoryRepository::class)
        } finally {
            koinApplication.close()
            database.close()
        }
    }
}
