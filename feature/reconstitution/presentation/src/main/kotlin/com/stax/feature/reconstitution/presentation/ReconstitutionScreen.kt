package com.stax.feature.reconstitution.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.UnitCode
import com.stax.core.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Root of the Reconstitution Helper (§10.1): holds the [ReconstitutionViewModel] and turns its
 * navigation events into the callbacks `:app` wired into the entry (§10.3).
 */
@Suppress("FunctionName")
@Composable
fun ReconstitutionRoot(
    args: ReconstitutionArgs,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReconstitutionViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events, key1 = onBack) { event ->
        when (event) {
            ReconstitutionEvent.NavigateBack -> onBack()
        }
    }

    ReconstitutionScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * The Reconstitution Helper (§4.6): the dose to draw over the mix that produces it, with the result
 * of that mix under both and the save dock at the bottom.
 *
 * §4.6's progressive disclosure is decided from the **pane's** width rather than the window's, and it
 * exists only where the room is short: below [DISCLOSURE_MAX_WIDTH] the Mix grid folds behind "Show
 * calculation", and above it §6.4.2 keeps the same sections open because horizontal space is cheap.
 * The columns those wider panes arrange them into land with M8-05; here every width is one scroll.
 *
 * §4.6.2's syringe (M8-02) and §4.6.3 / §4.6.5's chips and dose ladder (M8-03) are not drawn yet.
 */
@Suppress("FunctionName")
@Composable
fun ReconstitutionScreen(
    state: ReconstitutionState,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // The pane opens with its own app bar, so the status bar is the bar's to claim and draw
            // its container behind (§2.3.6). The pane still takes the sides and the bottom.
            .paneInsets(claimTop = false),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReconstitutionTopBar(state = state, onAction = onAction)
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isDisclosed = maxWidth < DISCLOSURE_MAX_WIDTH
                val contentWidth = maxWidth - SCREEN_PADDING * 2
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(SCREEN_PADDING),
                    verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                ) {
                    DrawToHero(state = state)
                    if (isDisclosed) {
                        ShowCalculationRow(isExpanded = state.isCalculationExpanded, onAction = onAction)
                    }
                    if (!isDisclosed || state.isCalculationExpanded) {
                        MixSection(
                            state = state,
                            width = contentWidth,
                            onAction = onAction,
                        )
                    }
                    ResultSection(state = state)
                }
            }
            SaveDock(state = state, onAction = onAction)
        }
    }
}

/** §4.6.1: leading `close`, title "Reconstitute", the compound and its container underneath. */
@Suppress("FunctionName")
@Composable
private fun ReconstitutionTopBar(state: ReconstitutionState, onAction: (ReconstitutionAction) -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.reconstitution_title),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
                Text(
                    text = state.compoundName?.let { name ->
                        stringResource(
                            R.string.reconstitution_subtitle,
                            name,
                            state.containerAmount,
                            unitLabel(state.containerUnit),
                        )
                    } ?: stringResource(R.string.reconstitution_subtitle_standalone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { onAction(ReconstitutionAction.OnCloseClick) }) {
                Icon(
                    painter = StaxIcons.Close,
                    contentDescription = stringResource(R.string.reconstitution_close),
                )
            }
        },
    )
}

/**
 * §4.6.7: the sticky dock. Disabled until the mix actually produces a concentration — there is
 * nothing to set before that. The write itself lands with M8-04.
 */
@Suppress("FunctionName")
@Composable
private fun SaveDock(state: ReconstitutionState, onAction: (ReconstitutionAction) -> Unit) {
    Column {
        HorizontalDivider()
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Button(
                onClick = { onAction(ReconstitutionAction.OnSaveClick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SCREEN_PADDING),
                enabled = state.canSave,
            ) {
                Icon(painter = StaxIcons.Check, contentDescription = null)
                Text(
                    text = stringResource(R.string.reconstitution_save),
                    modifier = Modifier.padding(start = DOCK_ICON_GAP),
                )
            }
        }
    }
}

/**
 * The pane width above which §4.6's progressive disclosure stops earning its place: §6.4.2 starts
 * splitting this screen into columns at Medium, and a column layout has nothing to fold away.
 *
 * Measured against the **pane**, which is not the window: a `673dp` Medium window hands this screen
 * about `593dp` once the navigation rail has taken its side, so a `600dp` threshold left the "Show
 * calculation" row on screen at exactly the breakpoint §6.4.2 says should not have one.
 */
private val DISCLOSURE_MAX_WIDTH = 520.dp

private val DOCK_ICON_GAP = 8.dp

@Preview(name = "Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium", showBackground = true, widthDp = 700, heightDp = 900)
@Preview(name = "Expanded", showBackground = true, widthDp = 1000, heightDp = 900)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ReconstitutionScreenPreview() {
    StaxTheme(dynamicColor = false) {
        Surface { ReconstitutionScreen(state = previewState(), onAction = {}) }
    }
}

@Preview(name = "Compact · calculation open", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ReconstitutionExpandedPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ReconstitutionScreen(
                state = previewState().copy(isCalculationExpanded = true),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Standalone · nothing typed", showBackground = true, widthDp = 411, heightDp = 914)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun ReconstitutionStandalonePreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            ReconstitutionScreen(
                state = ReconstitutionState(isCalculationExpanded = true),
                onAction = {},
            )
        }
    }
}

private fun previewState() = ReconstitutionState(
    compoundName = "Semaglutide",
    containerAmount = "5",
    containerUnit = UnitCode.MG,
    isContainerEditable = false,
    diluent = "2.0",
    desiredDose = "0.25",
    doseUnit = UnitCode.MG,
).recalculated()
