package com.stax.core.design.system

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * A primary FAB whose position adapts to the window width (§6.4.6):
 *
 * - **Compact** (`<600dp`): floating **bottom-end**, `16dp` inset above the bottom nav.
 * - **Medium / Expanded** (`600dp+`): anchored to the **top-start** rail FAB slot.
 *
 * When the breakpoint changes the FAB animates between the two corners with the M3 Expressive
 * spatial spec ([StaxMotion.defaultSpatialSpec]). Place it as the last child of a `fillMaxSize`
 * overlay (e.g. over a screen's content); its own `Box` fills the available space and aligns the FAB.
 *
 * The icon and behaviour are identical across breakpoints — only the position changes.
 *
 * Pass [label] for the **extended** form (§4.2.5): the FAB carries icon + label at Compact and drops
 * back to the icon alone once it moves into the rail slot, which has no room for a label.
 *
 * Applies no insets of its own: the overlay sits inside a pane that already claimed its slice via
 * [paneInsets], which is what keeps the Compact bottom-end FAB clear of the nav bar (§2.3.6).
 */
@Suppress("FunctionName")
@Composable
fun AdaptiveFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val railSlot = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val targetBias = if (railSlot) RAIL_SLOT_BIAS else BOTTOM_END_BIAS

    // Bottom-end = (+1, +1), top-start = (-1, -1); both axes animate together so the FAB slides
    // diagonally between the corners when the breakpoint changes.
    val horizontalBias by animateFloatAsState(
        targetValue = targetBias,
        animationSpec = StaxMotion.defaultSpatialSpec(),
        label = "AdaptiveFabHorizontalBias",
    )
    val verticalBias by animateFloatAsState(
        targetValue = targetBias,
        animationSpec = StaxMotion.defaultSpatialSpec(),
        label = "AdaptiveFabVerticalBias",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp), // §6.4.6 inset
    ) {
        val alignment = Modifier.align(BiasAlignment(horizontalBias, verticalBias))
        if (label == null || railSlot) {
            FloatingActionButton(
                onClick = onClick,
                modifier = alignment,
                content = content,
            )
        } else {
            ExtendedFloatingActionButton(onClick = onClick, modifier = alignment) {
                content()
                Spacer(modifier = Modifier.width(LABEL_GAP))
                label()
            }
        }
    }
}

/** Gap between the extended FAB's icon and its label. */
private val LABEL_GAP = 12.dp

/** Bottom-end corner bias (Compact). */
private const val BOTTOM_END_BIAS = 1f

/** Top-start corner bias (Medium+ rail FAB slot). */
private const val RAIL_SLOT_BIAS = -1f

@Preview(name = "Compact", showBackground = true, widthDp = 420, heightDp = 360)
@Preview(name = "Medium", showBackground = true, widthDp = 700, heightDp = 360)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun AdaptiveFabPreview() {
    StaxTheme(dynamicColor = false) {
        Surface(modifier = Modifier.size(width = 700.dp, height = 360.dp)) {
            AdaptiveFab(onClick = {}) {
                Icon(painter = StaxIcons.Add, contentDescription = null)
            }
        }
    }
}

@Preview(name = "Extended · Compact", showBackground = true, widthDp = 420, heightDp = 360)
@Preview(name = "Extended · Medium", showBackground = true, widthDp = 700, heightDp = 360)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun AdaptiveExtendedFabPreview() {
    StaxTheme(dynamicColor = false) {
        Surface(modifier = Modifier.size(width = 700.dp, height = 360.dp)) {
            AdaptiveFab(onClick = {}, label = { Text(text = "Add") }) {
                Icon(painter = StaxIcons.Add, contentDescription = null)
            }
        }
    }
}
