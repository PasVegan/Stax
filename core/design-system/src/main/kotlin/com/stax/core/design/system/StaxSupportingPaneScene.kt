package com.stax.core.design.system

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/**
 * Reusable Nav3 **supporting-pane Scene** wrapper (§6.4.2 Dashboard) — used by the Dashboard Medium
 * layout.
 *
 * Wraps the Material 3 adaptive [SupportingPaneSceneStrategy] with Stax's standard pane sizing: the
 * supporting pane is `360dp` (≈40% width, ≥320dp per §6.4.2) and the main pane fills the remainder
 * (≈60%, ≥400dp). This is a Nav3 **Scene strategy**, not `SupportingPaneScaffold` (banned by §6.4).
 * Tag the main entry with [mainPane] and the supporting entry with [supportingPane]; the strategy
 * then shows the main pane alone below 600dp and the main + supporting panes side-by-side at 600dp+.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
object StaxSupportingPaneScene {

    /** Supporting pane width — ≈40% of a Medium/Expanded window, at least the §6.4.2 320dp minimum. */
    private val SupportingPaneWidth = 360.dp

    /** Outline-variant 1dp separation between the main and supporting panes (§6.4.2). */
    private val PaneDivider = 1.dp

    /**
     * Remembers a [SupportingPaneSceneStrategy] whose supporting pane is `360dp` (main fills the
     * remainder). Pass it to `NavDisplay`'s `sceneStrategies`.
     */
    @Composable
    fun <T : Any> rememberSceneStrategy(): SupportingPaneSceneStrategy<T> {
        val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
        val foldingFeature = LocalFoldingFeature.current
        val directive = remember(windowAdaptiveInfo, foldingFeature) {
            val base = calculatePaneScaffoldDirective(windowAdaptiveInfo)
            base.copy(
                defaultPanePreferredWidth = SupportingPaneWidth,
                horizontalPartitionSpacerSize = PaneDivider,
                // Snap the divider to a vertical hinge when folded (§6.4.3).
                excludedBounds = foldingFeature.verticalHingeBounds() ?: base.excludedBounds,
            )
        }
        return rememberSupportingPaneSceneStrategy(directive = directive)
    }

    /** Metadata marking an entry as the **main** pane. */
    fun mainPane(): Map<String, Any> = SupportingPaneSceneStrategy.mainPane()

    /** Metadata marking an entry as the **supporting** pane. */
    fun supportingPane(): Map<String, Any> = SupportingPaneSceneStrategy.supportingPane()
}
