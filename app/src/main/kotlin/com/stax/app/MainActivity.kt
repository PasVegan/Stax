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
        // NavigationSuiteScaffold owns the system-bar insets for its chrome (edge-to-edge, §2.3.6);
        // per-screen content applies its own insets via app bars in later milestones.
        MainScaffold(modifier = Modifier.fillMaxSize())
    }
}

private fun Settings?.darkTheme(systemDarkTheme: Boolean): Boolean = when (this?.theme) {
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
    AppTheme.SYSTEM, null -> systemDarkTheme
}
