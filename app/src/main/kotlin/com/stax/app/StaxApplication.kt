package com.stax.app

import android.app.Application

/**
 * Stax application entry point.
 *
 * Stax divergence from android-di-koin: `startKoin` is NOT called here.
 * Koin is bootstrapped by [KoinInitializer] via androidx.startup, which
 * runs before the first Activity frame per §2.3.4. This keeps Application
 * lean and lets startup be measured / replaced independently.
 */
class StaxApplication : Application()
