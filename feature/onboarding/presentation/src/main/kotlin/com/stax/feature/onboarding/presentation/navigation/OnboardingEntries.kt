package com.stax.feature.onboarding.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.core.design.system.paneInsets

/**
 * Contributes the Onboarding `NavEntry` to the app's `NavDisplay` `entryProvider`.
 *
 * Leaving onboarding hands control back to `:app`, which decides the next destination — expressed
 * as the [onOnboardingComplete] lambda (spec §10.3). This module references no other feature's
 * routes.
 */
fun EntryProviderScope<NavKey>.onboardingEntries(onOnboardingComplete: () -> Unit) {
    entry<OnboardingRoute> {
        PlaceholderScreen(title = "Welcome to Stax") {
            Button(onClick = onOnboardingComplete) { Text(text = "Get started") }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun PlaceholderScreen(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .paneInsets()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}
