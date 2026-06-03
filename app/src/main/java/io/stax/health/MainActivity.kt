package io.stax.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.stax.health.core.presentation.Navigator
import io.stax.health.core.presentation.rememberNavigationState
import io.stax.health.core.presentation.toEntries
import io.stax.health.features.dashboard.presentation.DashboardRoot
import io.stax.health.features.dashboard.presentation.DashboardRoute
import io.stax.health.ui.theme.StaxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StaxTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DashboardRoute,
                        topLevelRoutes = setOf(DashboardRoute),
                    )
                val navigator = remember { Navigator(navigationState) }
                val entryProvider =
                    remember {
                        entryProvider<NavKey> {
                            entry<DashboardRoute> {
                                DashboardRoot()
                            }
                        }
                    }

                NavDisplay(
                    entries = navigationState.toEntries(entryProvider),
                    onBack = { navigator.goBack() },
                )
            }
        }
    }
}
