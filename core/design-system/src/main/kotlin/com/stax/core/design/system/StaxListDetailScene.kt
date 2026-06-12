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
        val directive = remember(windowAdaptiveInfo) {
            val expanded = windowAdaptiveInfo.windowSizeClass
                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
            calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(
                defaultPanePreferredWidth = if (expanded) ListPaneWidthExpanded else ListPaneWidthMedium,
                horizontalPartitionSpacerSize = PaneDivider,
            )
        }
        return rememberListDetailSceneStrategy(directive = directive)
    }

    /**
     * Metadata marking an entry as the **list** pane. [detailPlaceholder] fills the detail pane at
     * Medium+ while no item is selected.
     */
    fun listPane(detailPlaceholder: @Composable () -> Unit): Map<String, Any> =
        ListDetailSceneStrategy.listPane(detailPlaceholder = { detailPlaceholder() })

    /** Metadata marking an entry as the **detail** pane. */
    fun detailPane(): Map<String, Any> = ListDetailSceneStrategy.detailPane()
}
