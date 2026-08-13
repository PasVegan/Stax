package com.stax.feature.onboarding.presentation.notificationgate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import org.koin.androidx.compose.koinViewModel

/**
 * Root of the notification-permission gate (§10.1): holds the [NotificationGateViewModel] and owns the
 * framework side of the flow — the `POST_NOTIFICATIONS` request launcher, the permanent-denial check,
 * and the jump to system settings. The screen it wraps stays a pure `state` + `onAction` composable.
 *
 * [onProceed] is `:app`'s call — it drops the onboarding flow and lands on Dashboard (§10.3, §4.15).
 */
@Suppress("FunctionName")
@Composable
fun NotificationGateRoot(
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationGateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // After a denial `shouldShowRequestPermissionRationale` is false only when the OS will no
        // longer show the dialog — i.e. a permanent denial, the one case §4.15 offers Settings for.
        val permanentlyDenied = activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.onAction(NotificationGateAction.OnPermissionResult(granted, permanentlyDenied))
    }

    ObserveAsEvents(viewModel.events, onProceed) { event ->
        when (event) {
            NotificationGateEvent.RequestPermission ->
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            NotificationGateEvent.OpenAppSettings -> context.openAppNotificationSettings()
            NotificationGateEvent.Proceed -> onProceed()
        }
    }

    NotificationGateScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Notification-permission gate (§4.15).
 *
 * Blob illustration, headline, value-prop subtitle and a full-width "Allow notifications" CTA, with a
 * "Continue" text action to proceed without the permission. The secondary "Open system settings"
 * action appears only once the permission is permanently denied ([NotificationGateState.showOpenSettings]).
 *
 * Adaptive per §6.4.2 (same treatment as Onboarding step 1): a single centered column at Compact, a
 * hero-left / content-right split at Medium and Expanded — the same content, re-laid-out.
 */
@Suppress("FunctionName")
@Composable
fun NotificationGateScreen(
    state: NotificationGateState,
    onAction: (NotificationGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroLeftLayout = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (heroLeftLayout) {
        WideGate(state = state, onAction = onAction, modifier = modifier)
    } else {
        CompactGate(state = state, onAction = onAction, modifier = modifier)
    }
}

/** Compact (<600dp): hero, copy and actions stacked and centered. */
@Suppress("FunctionName")
@Composable
private fun CompactGate(
    state: NotificationGateState,
    onAction: (NotificationGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .paneInsets()
            .padding(SCREEN_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HERO_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingHeroBlob(modifier = Modifier.fillMaxWidth(COMPACT_HERO_WIDTH_FRACTION))
            GateCopy(textAlign = TextAlign.Center)
        }

        GateActions(state = state, onAction = onAction)
    }
}

/**
 * Medium + Expanded (600dp+): hero illustration on the leading half, copy and actions on the trailing
 * half (§6.4.2).
 */
@Suppress("FunctionName")
@Composable
private fun WideGate(
    state: NotificationGateState,
    onAction: (NotificationGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                GateCopy(textAlign = TextAlign.Start)
            }

            GateActions(state = state, onAction = onAction)
        }
    }
}

/** Headline + value-prop subtitle (§4.15). */
@Suppress("FunctionName")
@Composable
private fun GateCopy(textAlign: TextAlign, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(COPY_GAP),
    ) {
        Text(
            text = stringResource(R.string.onboarding_gate_headline),
            style = MaterialTheme.typography.displaySmall,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.onboarding_gate_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Full-width "Allow notifications" CTA, then "Continue" below it. "Open system settings" sits between
 * them only when the permission is permanently denied (§4.15).
 */
@Suppress("FunctionName")
@Composable
private fun GateActions(state: NotificationGateState, onAction: (NotificationGateAction) -> Unit) {
    Button(
        onClick = { onAction(NotificationGateAction.OnAllowClick) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.onboarding_gate_allow))
    }
    if (state.showOpenSettings) {
        TextButton(
            onClick = { onAction(NotificationGateAction.OnOpenSettingsClick) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.onboarding_gate_open_settings))
        }
    }
    TextButton(
        onClick = { onAction(NotificationGateAction.OnContinueClick) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.onboarding_continue))
    }
}

/** Sends the user to this app's system notification settings (permanent-denial path, §4.15). */
private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
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
private fun NotificationGateScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            NotificationGateScreen(state = NotificationGateState(showOpenSettings = true), onAction = {})
        }
    }
}
