package io.stax.health

import android.app.Application
import io.stax.health.core.di.appModule
import io.stax.health.core.di.dashboardModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class StaxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@StaxApp)
            modules(
                appModule,
                dashboardModule
            )
        }
    }
}
