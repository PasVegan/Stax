package com.stax.core.design.system

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Reusable Nav3 **list-detail Scene** wrapper (§6.4.2) — used by Compounds, Protocols and Settings.
 *
 * Wraps the Material 3 adaptive [ListDetailSceneStrategy] with Stax's standard list-pane sizing
 * (`360dp` at Medium, `400dp` at Expanded) and inter-pane separation. This is a Nav3 **Scene
 * strategy**, not `ListDetailPaneScaffold` (banned by §6.4). Tag the list entry with [listPane] and
 * the detail entry with [detailPane]; the strategy then renders one pane below 600dp (selecting an
 * item pushes the detail) and two panes at 600dp+ (selecting swaps the detail pane without a push,
 * showing the placeholder when nothing is selected).
 *
 * Every scene **must** carry its own `sceneKey`, which is why that parameter has no default here even
 * though the Material API defaults it to `Unit`. `NavDisplay` keys its `AnimatedContent` by
 * `(scene::class, scene.key)`, and both this strategy and [StaxSupportingPaneScene] build the same
 * `ThreePaneScaffoldScene` class — so on the default key Compounds, Protocols, Settings and the
 * Dashboard all collapse onto a single `AnimatedContent` slot. The scaffold state remembered for the
 * outgoing scene is then reused by the incoming scene's scaffold, whose new `Transition` finds the
 * same `SeekableTransitionState` already in use, and the app crashes with "An instance of
 * SeekableTransitionState has been used in different Transitions".
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
object StaxListDetailScene {

    private val ListPaneWidthMedium = 360.dp
    private val ListPaneWidthExpanded = 400.dp

    /** Outline-variant 1dp separation between the list and detail panes (§6.4.2). */
    private val PaneDivider = 1.dp

    /**
     * Remembers a list-detail [ListDetailSceneStrategy] whose list pane is `360dp` at Medium and
     * `400dp` at Expanded. Pass it to `NavDisplay`'s `sceneStrategy`.
     */
    @Composable
    fun <T : Any> rememberSceneStrategy(): ListDetailSceneStrategy<T> {
        val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
        val foldingFeature = LocalFoldingFeature.current
        val directive = remember(windowAdaptiveInfo, foldingFeature) {
            val expanded = windowAdaptiveInfo.windowSizeClass
                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
            val base = calculatePaneScaffoldDirective(windowAdaptiveInfo)
            base.copy(
                defaultPanePreferredWidth = if (expanded) ListPaneWidthExpanded else ListPaneWidthMedium,
                horizontalPartitionSpacerSize = PaneDivider,
                // Snap the divider to a vertical hinge when folded (§6.4.3).
                excludedBounds = foldingFeature.verticalHingeBounds() ?: base.excludedBounds,
            )
        }
        return rememberListDetailSceneStrategy(directive = directive)
    }

    /**
     * Metadata marking an entry as the **list** pane of the scene identified by [sceneKey].
     * [detailPlaceholder] fills the detail pane at Medium+ while no item is selected.
     */
    fun listPane(sceneKey: String, detailPlaceholder: @Composable () -> Unit): Map<String, Any> =
        ListDetailSceneStrategy.listPane(sceneKey = sceneKey, detailPlaceholder = { detailPlaceholder() })

    /** Metadata marking an entry as the **detail** pane of the scene identified by [sceneKey]. */
    fun detailPane(sceneKey: String): Map<String, Any> = ListDetailSceneStrategy.detailPane(sceneKey = sceneKey)
}
