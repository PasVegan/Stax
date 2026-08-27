package com.stax.feature.reconstitution.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxTheme
import com.stax.core.design.system.paneInsets
import com.stax.core.domain.Concentration
import com.stax.core.domain.UnitCode
import com.stax.core.presentation.ObserveAsEvents
import com.stax.core.presentation.asString
import kotlinx.coroutines.launch
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
    onSaved: (Concentration) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReconstitutionViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events, key1 = onBack, key2 = onSaved) { event ->
        when (event) {
            ReconstitutionEvent.NavigateBack -> onBack()
            is ReconstitutionEvent.Saved -> onSaved(event.concentration)
            is ReconstitutionEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(context.asString(event.message))
            }
        }
    }

    ReconstitutionScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The Reconstitution Helper (§4.6): the dose to draw over the mix that produces it, with the result
 * of that mix under both and the save dock at the bottom.
 *
 * §6.4.2 arranges those sections into one, two or three columns, and the count is read off the
 * **pane's** width rather than the window's — the navigation rail takes its side of a Medium window
 * before this screen sees any of it, so a breakpoint measured on the window would promise room the
 * pane does not have. [TWO_COLUMN_MIN_WIDTH] and [THREE_COLUMN_MIN_WIDTH] are the two thresholds.
 *
 * §4.6's progressive disclosure belongs to the single column alone: folding Mix behind "Show
 * calculation" is what a Compact scroll needs, and a column layout has nothing to fold away since the
 * sections no longer queue behind one another.
 *
 * §4.6.3's chips ride in the hero, under the syringe they restate; §4.6.5's ladder follows the field
 * it types into as far as the room allows — beside Mix in the single column, in the syringe's column
 * at two, and back with the Result it is read against at three.
 */
@Suppress("FunctionName")
@Composable
fun ReconstitutionScreen(
    state: ReconstitutionState,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
                when {
                    maxWidth < TWO_COLUMN_MIN_WIDTH ->
                        SingleColumn(state = state, onAction = onAction, paneWidth = maxWidth)
                    maxWidth < THREE_COLUMN_MIN_WIDTH ->
                        TwoColumn(state = state, onAction = onAction, paneWidth = maxWidth)
                    else -> ThreeColumn(state = state, onAction = onAction, paneWidth = maxWidth)
                }
            }
            SaveDock(state = state, onAction = onAction)
        }
        // Above the dock rather than under it: a snackbar the save button covers says nothing.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = SNACKBAR_DOCK_GAP),
        )
    }
}

/**
 * §6.4.2 Compact: one scroll, with §4.6's disclosure holding Mix and the ladder back until asked.
 *
 * The hero and the Result stay out of the fold — they are the answer the screen exists to give, and a
 * user who only wants to read it should not have to open the arithmetic behind it first.
 */
@Suppress("FunctionName")
@Composable
private fun SingleColumn(
    state: ReconstitutionState,
    onAction: (ReconstitutionAction) -> Unit,
    paneWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        DrawToHero(state = state, onAction = onAction)
        ShowCalculationRow(isExpanded = state.isCalculationExpanded, onAction = onAction)
        if (state.isCalculationExpanded) {
            MixSection(state = state, width = paneWidth - SCREEN_PADDING * 2, onAction = onAction)
            // §4.6.5 sits inside the same disclosure as Mix: it edits the same desired dose, and an
            // empty ladder is a dose that has not been typed yet.
            if (state.ladder.isNotEmpty()) {
                DoseLadderSection(state = state, onAction = onAction)
            }
        }
        ResultSection(state = state)
    }
}

/**
 * §6.4.2 Medium: the dose on the left, the arithmetic that produced it on the right.
 *
 * The ladder rides with the syringe rather than with the field it types into, because at this width
 * the two are the same glance: a rung is picked by looking at what it draws to, and the barrel is the
 * thing that shows it. The columns scroll apart, which is what §6.4.2's "sticky" asks for — the
 * syringe holds still while the user works down Mix — without stranding a hero taller than a landscape
 * window has room for.
 */
@Suppress("FunctionName")
@Composable
private fun TwoColumn(
    state: ReconstitutionState,
    onAction: (ReconstitutionAction) -> Unit,
    paneWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val columnWidth = (paneWidth - SCREEN_PADDING * 2 - SECTION_GAP) / 2
    ColumnRow(modifier = modifier) {
        ScrollingColumn(modifier = Modifier.weight(1f)) {
            DrawToHero(state = state, onAction = onAction)
            if (state.ladder.isNotEmpty()) {
                DoseLadderSection(state = state, onAction = onAction)
            }
        }
        ScrollingColumn(modifier = Modifier.weight(1f)) {
            MixSection(state = state, width = columnWidth, onAction = onAction)
            ResultSection(state = state)
        }
    }
}

/**
 * §6.4.2 Expanded: syringe, then the mix, then what the mix comes to.
 *
 * Reading order is left to right and so is causality — the ladder leaves the syringe for the right
 * column here, where it sits under the Result it is checked against and beside the doses-per-container
 * figure a bigger rung eats into.
 *
 * The side columns are fixed and the centre takes what is left, so [THREE_COLUMN_MIN_WIDTH] is what
 * keeps that remainder worth having: the split is measured on the pane, and below the threshold the
 * pane keeps [TwoColumn]'s halves rather than a centre column too thin for §4.6.4's tiles.
 */
@Suppress("FunctionName")
@Composable
private fun ThreeColumn(
    state: ReconstitutionState,
    onAction: (ReconstitutionAction) -> Unit,
    paneWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val centerWidth =
        paneWidth - SCREEN_PADDING * 2 - SECTION_GAP * 2 - SYRINGE_COLUMN_WIDTH - RESULT_COLUMN_WIDTH
    ColumnRow(modifier = modifier) {
        ScrollingColumn(modifier = Modifier.width(SYRINGE_COLUMN_WIDTH)) {
            DrawToHero(state = state, onAction = onAction)
        }
        ScrollingColumn(modifier = Modifier.weight(1f)) {
            MixSection(state = state, width = centerWidth, onAction = onAction)
        }
        ScrollingColumn(modifier = Modifier.width(RESULT_COLUMN_WIDTH)) {
            ResultSection(state = state)
            if (state.ladder.isNotEmpty()) {
                DoseLadderSection(state = state, onAction = onAction)
            }
        }
    }
}

/** The row §6.4.2's columns sit in: screen padding outside, one section gap between each pair. */
@Suppress("FunctionName")
@Composable
private fun ColumnRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxSize().padding(SCREEN_PADDING),
        horizontalArrangement = Arrangement.spacedBy(SECTION_GAP),
        content = content,
    )
}

/** One §6.4.2 column. Each scrolls on its own, so a long one never drags the others past their end. */
@Suppress("FunctionName")
@Composable
private fun ScrollingColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        content = content,
    )
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
 * nothing to set before that — and again while the write is in flight, so one save is one write.
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
 * The pane width at which §6.4.2 splits this screen in two, and with it the width above which §4.6's
 * progressive disclosure stops earning its place — the disclosure exists to queue sections behind one
 * another, and columns are the alternative to queueing them.
 *
 * Measured against the **pane**, which is not the window: a `673dp` Medium window hands this screen
 * about `593dp` once the navigation rail has taken its side, so a `600dp` threshold left the "Show
 * calculation" row on screen at exactly the breakpoint §6.4.2 says should not have one.
 */
private val TWO_COLUMN_MIN_WIDTH = 520.dp

/**
 * The pane width at which the second column splits again (§6.4.2 Expanded).
 *
 * It is the sum of what the three columns need, not a breakpoint: [SYRINGE_COLUMN_WIDTH] and
 * [RESULT_COLUMN_WIDTH] are fixed, so the centre gets the rest, and `280dp` is the least §4.6.4's Mix
 * grid renders two full-width tiles per row in. Below this the pane is Expanded by the window's
 * reckoning and still too narrow for a third column — an `840dp` window less the rail is `744dp`,
 * which the two side columns alone would eat — so it keeps the halves of [TwoColumn].
 */
private val THREE_COLUMN_MIN_WIDTH = 1024.dp

/** §6.4.2 Expanded: the syringe column, fixed — the barrel scales to whatever it is given. */
private val SYRINGE_COLUMN_WIDTH = 360.dp

/** §6.4.2 Expanded: the result + ladder column, fixed. */
private val RESULT_COLUMN_WIDTH = 320.dp

private val DOCK_ICON_GAP = 8.dp

/** Roughly the dock's own height, so a snackbar clears the button it is reporting on. */
private val SNACKBAR_DOCK_GAP = 88.dp

@Preview(name = "Compact", showBackground = true, widthDp = 411, heightDp = 914)
@Preview(name = "Medium · two columns", showBackground = true, widthDp = 700, heightDp = 900)
@Preview(name = "Expanded · two columns", showBackground = true, widthDp = 1000, heightDp = 900)
@Preview(name = "Expanded · three columns", showBackground = true, widthDp = 1280, heightDp = 800)
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
