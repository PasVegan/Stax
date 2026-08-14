package com.stax.feature.settings.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.presentation.ObserveAsEvents
import com.stax.feature.settings.presentation.R
import org.koin.androidx.compose.koinViewModel

/**
 * Root of the Settings screen (§10.1): holds the [SettingsViewModel] and owns the one framework
 * interaction it can trigger — the jump to the system "Alarms & reminders" screen (§5.1).
 */
@Suppress("FunctionName")
@Composable
fun SettingsRoot(modifier: Modifier = Modifier, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.OpenExactAlarmSettings -> context.openExactAlarmSettings()
        }
    }

    SettingsScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * Settings (§4.13).
 *
 * The section list-detail structure lands with M14-01; today the screen carries the exact-alarm
 * warning row, which §5.1 requires as soon as reminders can be degraded — ahead of the section it
 * will eventually sit in (Reminders, §4.13.3).
 */
@Suppress("FunctionName")
@Composable
fun SettingsScreen(state: SettingsState, onAction: (SettingsAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .paneInsets()
            .verticalScroll(rememberScrollState())
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        if (state.exactAlarmDegraded) {
            ExactAlarmWarningRow(
                onEnableClick = { onAction(SettingsAction.OnEnableExactRemindersClick) },
            )
        }
    }
}

/**
 * Degraded-reminders warning (§5.1): `error-container` fill + leading `warning` icon + two-line
 * content, the same treatment every warning row in the app gets (§4.1, §4.5).
 *
 * It is not a chevron navigation row — the action leaves the app for a system screen — so the CTA is
 * an explicit button rather than a whole-row tap target.
 */
@Suppress("FunctionName")
@Composable
private fun ExactAlarmWarningRow(onEnableClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
        ) {
            Icon(
                painter = StaxIcons.Warning,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
            )
            Column(verticalArrangement = Arrangement.spacedBy(COPY_GAP)) {
                Text(
                    text = stringResource(R.string.settings_exact_alarm_warning_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_exact_alarm_warning_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onEnableClick,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(R.string.settings_exact_alarm_enable))
                }
            }
        }
    }
}

/**
 * Opens the system "Alarms & reminders" screen for Stax (§5.1). The `package:` data scopes it to this
 * app rather than the device-wide list of apps holding the special access.
 */
private fun Context.openExactAlarmSettings() {
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private val SCREEN_PADDING = 24.dp
private val SECTION_GAP = 16.dp
private val ROW_PADDING = 16.dp
private val ICON_GAP = 16.dp
private val COPY_GAP = 4.dp
private val ICON_SIZE = 24.dp

@Preview(name = "Compact", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Expanded", showBackground = true, widthDp = 1000, heightDp = 800)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SettingsScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            SettingsScreen(state = SettingsState(exactAlarmDegraded = true), onAction = {})
        }
    }
}
