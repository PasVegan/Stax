package com.stax.core.design.system

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The app's primary FAB: floating **bottom-end** of its pane with a `16dp` inset, at every window
 * width (§6.4.6). Place it as the last child of a `fillMaxSize` overlay over a screen's content; its
 * own `Box` fills the available space and aligns the FAB.
 *
 * Pass [label] for the **extended** form (§4.2.5) — icon + label, kept at every width, because a
 * primary action that names itself on a phone has no reason to stop naming itself on a tablet.
 *
 * **Not the navigation rail's FAB slot.** That slot belongs to `NavigationSuiteScaffold`
 * (`primaryActionContent`) in `:app`, and a FAB placed there is chrome, not screen content: it
 * outlives the screen, cannot read the screen's state — Compounds hides its FAB in multi-select
 * (§4.2.4) — and would have to route its action around the screen's ViewModel. The FAB is the
 * screen's, so it lives in the screen's pane (§6.4.6, revised).
 *
 * Applies no insets of its own: the overlay sits inside a pane that already claimed its slice via
 * [paneInsets], which is what keeps the FAB clear of the nav bar (§2.3.6).
 */
@Suppress("FunctionName")
@Composable
fun AdaptiveFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(FAB_INSET),
    ) {
        val alignment = Modifier.align(Alignment.BottomEnd)
        if (label == null) {
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

/** §6.4.6 inset between the FAB and the pane's edges. */
private val FAB_INSET = 16.dp

/** Gap between the extended FAB's icon and its label. */
private val LABEL_GAP = 12.dp

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
