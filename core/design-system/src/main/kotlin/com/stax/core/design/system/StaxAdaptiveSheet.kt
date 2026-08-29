package com.stax.core.design.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.core.layout.WindowSizeClass

/**
 * The app's one modal sheet (§6.3), in the three shapes §6.4.2 asks of it:
 *
 * - **Compact**: full-width modal bottom sheet.
 * - **Medium**: the same sheet, clamped to `560dp` and centred — still bottom-anchored, so it is
 *   still reachable from a thumb on a tablet held in portrait.
 * - **Expanded**: a **side sheet** on the end edge, full window height, [sideSheetWidth] wide. In
 *   landscape the bottom of the window is the far edge from the eye and the hand, and a sheet that
 *   grows upward from it either covers the screen or leaves its actions at the very bottom.
 *
 * Material has no side-sheet component, so the Expanded branch is a `Dialog` that fills the window:
 * that is what puts it above everything with its own back handling, which is what makes it modal.
 * The two branches take the same [content] — a column that scrolls if it has to — because §6.4.2's
 * reflow for these sheets is vertical either way.
 *
 * Callers pass a plain `content`; the sheet owns the drag handle, the scrim, the insets and the
 * motion (§5.9 `defaultSpatialSpec`).
 *
 * It opens fully rather than half-expanded. Every sheet in the app ends in its actions — Save, Delete,
 * Mark unavailable — so a first frame that shows half the content is a first frame with nothing to
 * press on it, and the drag that fixes that is one the user has no reason to know about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
fun StaxAdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sideSheetWidth: Dp = SIDE_SHEET_WIDTH,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isExpandedWidth()) {
        StaxSideSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            width = sideSheetWidth,
            content = content,
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // Unspecified rather than the window's width: `widthIn` reads it as "no maximum", which
            // is what "full-width" has to mean when the Compact range runs up to 599dp.
            sheetMaxWidth = if (isMediumWidth()) MEDIUM_SHEET_MAX_WIDTH else Dp.Unspecified,
            content = content,
        )
    }
}

/**
 * §6.4.2 Expanded: the sheet as an end-edge side sheet.
 *
 * The dialog is dismissed only once the exit animation has run, which is why [onDismissRequest] is
 * not what the scrim and the back gesture call — they start the exit, and the transition's own idle
 * state ends it. Without that the sheet vanishes on the frame it is dismissed while the bottom-sheet
 * branch slides away, and the same action would look like two different things at two widths.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
private fun StaxSideSheet(
    onDismissRequest: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Composed already on its way in, so the first frame is not the idle "hidden" state the effect
    // below reads as "the exit has finished".
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visibility.isIdle) {
        if (visibility.isIdle && !visibility.currentState) onDismissRequest()
    }

    val hide = { visibility.targetState = false }

    Dialog(
        onDismissRequest = hide,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(animationSpec = StaxMotion.defaultEffectsSpec()),
                exit = fadeOut(animationSpec = StaxMotion.defaultEffectsSpec()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BottomSheetDefaults.ScrimColor)
                        // No indication: a ripple spreading across a full-window scrim reads as the
                        // page reacting, not as the sheet closing.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = hide,
                        ),
                )
            }
            AnimatedVisibility(
                visibleState = visibility,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(animationSpec = StaxMotion.defaultSpatialSpec()) { it },
                exit = slideOutHorizontally(animationSpec = StaxMotion.defaultSpatialSpec()) { it },
            ) {
                Surface(
                    modifier = modifier
                        .width(width)
                        .fillMaxHeight(),
                    shape = StaxShapes.SideSheet,
                    color = BottomSheetDefaults.ContainerColor,
                ) {
                    // The dialog draws behind the system bars (`decorFitsSystemWindows = false`), so
                    // the sheet is the surface that has to hold its content clear of them (§2.3.6).
                    Column(modifier = Modifier.safeDrawingPadding(), content = content)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isMediumWidth(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isExpandedWidth(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** §6.4.2: a bottom sheet at Medium is clamped to this and centred. */
private val MEDIUM_SHEET_MAX_WIDTH = 560.dp

/** §6.4.2: the Expanded side sheet's width. Picker sheets (§4.0.2) pass `360dp` for narrower content. */
private val SIDE_SHEET_WIDTH = 420.dp
