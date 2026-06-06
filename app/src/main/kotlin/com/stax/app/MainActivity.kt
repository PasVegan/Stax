package com.stax.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            // NavHost wired in subsequent milestones
        }
    }
}

private fun Settings?.darkTheme(systemDarkTheme: Boolean): Boolean = when (this?.theme) {
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
    AppTheme.SYSTEM, null -> systemDarkTheme
}
