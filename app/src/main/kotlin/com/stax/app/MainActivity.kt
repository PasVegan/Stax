package com.stax.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.ProvideFoldingFeature
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.AppTheme
import com.stax.core.domain.Settings
import com.stax.core.domain.repository.SettingsRepository
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StaxApp()
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun StaxApp(settingsRepository: SettingsRepository = koinInject()) {
    val settings by settingsRepository.observe().collectAsStateWithLifecycle(initialValue = null)
    val systemDarkTheme = isSystemInDarkTheme()

    StaxTheme(
        darkTheme = settings.darkTheme(systemDarkTheme),
        dynamicColor = settings?.dynamicColor ?: true,
    ) {
        // Whether onboarding has run (§4.14) decides the *initial* back stack, so nothing composes
        // until settings arrive — otherwise Dashboard renders for a frame and onboarding animates in
        // over it on first launch. The window theme paints the background meanwhile, and the row is
        // seeded on database creation so this resolves within a frame or two of a cold start.
        val onboardingCompleted = settings?.onboardingCompleted
        if (onboardingCompleted != null) {
            // NavigationSuiteScaffold owns the system-bar insets for its chrome (edge-to-edge, §2.3.6);
            // each NavDisplay entry is a Scene pane and claims its own slice via Modifier.paneInsets().
            // ProvideFoldingFeature wraps the nav root so the adaptive Scenes can snap pane dividers to
            // a vertical hinge (§6.4.3).
            ProvideFoldingFeature {
                MainScaffold(
                    onboardingCompleted = onboardingCompleted,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun Settings?.darkTheme(systemDarkTheme: Boolean): Boolean = when (this?.theme) {
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
    AppTheme.SYSTEM, null -> systemDarkTheme
}
