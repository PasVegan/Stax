package com.stax.app.initializer

import android.content.Context
import androidx.startup.Initializer
import com.stax.core.data.di.coreDataModule
import com.stax.feature.compounds.presentation.di.compoundsPresentationModule
import com.stax.feature.dashboard.presentation.di.dashboardPresentationModule
import com.stax.feature.logging.presentation.di.loggingPresentationModule
import com.stax.feature.onboarding.presentation.di.onboardingPresentationModule
import com.stax.feature.protocols.presentation.di.protocolsPresentationModule
import com.stax.feature.reconstitution.presentation.di.reconstitutionPresentationModule
import com.stax.feature.settings.presentation.di.settingsPresentationModule
import com.stax.feature.sites.presentation.di.sitesPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KoinInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        startKoin {
            androidContext(context)
            modules(
                // core
                coreDataModule,
                // features
                onboardingPresentationModule,
                compoundsPresentationModule,
                protocolsPresentationModule,
                sitesPresentationModule,
                dashboardPresentationModule,
                reconstitutionPresentationModule,
                loggingPresentationModule,
                settingsPresentationModule,
            )
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
