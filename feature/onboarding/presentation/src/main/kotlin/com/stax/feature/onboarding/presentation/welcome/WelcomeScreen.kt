package com.stax.feature.onboarding.presentation.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.presentation.ObserveAsEvents
import com.stax.feature.onboarding.presentation.R
import com.stax.feature.onboarding.presentation.components.OnboardingHeroBlob
import com.stax.feature.onboarding.presentation.components.OnboardingStepIndicator
import org.koin.androidx.compose.koinViewModel

/**
 * Root of onboarding step 1 (§10.1): holds the [WelcomeViewModel], observes its one-time events and
 * turns them into the navigation callbacks `:app` supplied ([onContinue] / [onSkip], §10.3).
 */
@Suppress("FunctionName")
@Composable
fun WelcomeRoot(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events, onContinue, onSkip) { event ->
        when (event) {
            WelcomeEvent.NavigateToNextStep -> onContinue()
            WelcomeEvent.SkipOnboarding -> onSkip()
        }
    }

    WelcomeScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

/**
 * Onboarding step 1 — Welcome (§4.14 step 1).
 *
 * Hero blob illustration, two-line headline, value-prop subtitle, a full-width "Continue" CTA and a
 * "Skip" text action, with the step-indicator pills showing progress through the flow.
 *
 * Adaptive per §6.4.2: a single centered column at Compact, and a hero-left / content-right split at
 * Medium and Expanded — the same content, re-laid-out rather than re-written.
 */
@Suppress("FunctionName")
@Composable
fun WelcomeScreen(state: WelcomeState, onAction: (WelcomeAction) -> Unit, modifier: Modifier = Modifier) {
    val heroLeftLayout = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (heroLeftLayout) {
        WideWelcome(state = state, onAction = onAction, modifier = modifier)
    } else {
        CompactWelcome(state = state, onAction = onAction, modifier = modifier)
    }
}

/** Compact (<600dp): full-screen stepper — pills, hero, copy and actions stacked and centered. */
@Suppress("FunctionName")
@Composable
private fun CompactWelcome(state: WelcomeState, onAction: (WelcomeAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .paneInsets()
            .padding(SCREEN_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingStepIndicator(currentStep = state.currentStep, stepCount = state.stepCount)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HERO_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingHeroBlob(modifier = Modifier.fillMaxWidth(COMPACT_HERO_WIDTH_FRACTION))
            WelcomeCopy(textAlign = TextAlign.Center)
        }

        WelcomeActions(onAction = onAction)
    }
}

/**
 * Medium + Expanded (600dp+): hero illustration on the leading half, step content and CTA on the
 * trailing half with the step indicator at the top of that column (§6.4.2).
 */
@Suppress("FunctionName")
@Composable
private fun WideWelcome(state: WelcomeState, onAction: (WelcomeAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .paneInsets()
            .padding(SCREEN_PADDING),
        horizontalArrangement = Arrangement.spacedBy(PANE_GAP),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingHeroBlob(modifier = Modifier.fillMaxWidth(WIDE_HERO_WIDTH_FRACTION))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            OnboardingStepIndicator(currentStep = state.currentStep, stepCount = state.stepCount)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                WelcomeCopy(textAlign = TextAlign.Start)
            }

            WelcomeActions(onAction = onAction)
        }
    }
}

/** Two-line headline + value-prop subtitle (§4.14 step 1). */
@Suppress("FunctionName")
@Composable
private fun WelcomeCopy(textAlign: TextAlign, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(COPY_GAP),
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_headline),
            style = MaterialTheme.typography.displaySmall,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Full-width filled "Continue" with "Skip" below it (§4.14 step 1). */
@Suppress("FunctionName")
@Composable
private fun WelcomeActions(onAction: (WelcomeAction) -> Unit) {
    Button(
        onClick = { onAction(WelcomeAction.OnContinueClick) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.onboarding_continue))
    }
    TextButton(
        onClick = { onAction(WelcomeAction.OnSkipClick) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.onboarding_skip))
    }
}

private val SCREEN_PADDING = 24.dp
private val PANE_GAP = 32.dp
private val HERO_GAP = 32.dp
private val COPY_GAP = 12.dp

/** The hero never spans the full column — it breathes inside it. */
private const val COMPACT_HERO_WIDTH_FRACTION = 0.86f
private const val WIDE_HERO_WIDTH_FRACTION = 0.8f

@Preview(name = "Compact", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Medium", showBackground = true, widthDp = 700, heightDp = 840)
@Preview(name = "Expanded", showBackground = true, widthDp = 1000, heightDp = 800)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun WelcomeScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            WelcomeScreen(state = WelcomeState(), onAction = {})
        }
    }
}
