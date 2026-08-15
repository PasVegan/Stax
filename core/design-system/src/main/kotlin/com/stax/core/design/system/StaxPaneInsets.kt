package com.stax.core.design.system

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pads a pane's content clear of the window's safe-drawing insets — status bar, navigation bar,
 * caption bar, display cutout, tappable element and the IME (§2.3.6, `edge-to-edge` skill).
 *
 * This is the **one and only** inset method a pane may use (spec §2.3.6: one method per surface,
 * never two) — enforced by the `stax:NoWindowInsetsOutsideDesignSystem` detekt rule, which bans the
 * `WindowInsets` APIs outside `:core:design-system`. Apply it once, at the root of every `NavDisplay`
 * entry, because a Nav3 entry *is* a Scene pane: the adaptive Scene strategies lay panes out but
 * propagate no insets of their own, so each pane must claim its own.
 *
 * Pass `claimTop = false` when the pane's **own top app bar** is the first thing in it — `TopAppBar`,
 * `SearchBar`, anything that takes a `windowInsets` parameter. The bar then claims the status bar
 * itself and draws its container colour behind it, which is what edge-to-edge is supposed to look
 * like; leaving the top to the pane instead stops the bar short and strands a strip of page
 * background under the status icons (visible wherever the bar's container differs from the page —
 * any dark or dynamic-colour scheme). The pane still claims the sides and the bottom, so the bar's
 * own horizontal insets resolve to zero underneath it.
 *
 * Either way padding is **consumed**, so nesting `paneInsets` — or composing it with the padding
 * `NavigationSuiteScaffold` already consumed for its chrome — adds nothing.
 *
 * Deliberately **not** the ruler alignment `fitInside(WindowInsetsRulers.SafeDrawing.current)`.
 * Rulers resolve by *position*: `Ruler.calculateCoordinate` maps the value through
 * `localPositionOf`, which includes every `graphicsLayer` transform between the window root and the
 * pane. `NavDisplay`'s entry transitions (§6.4.5) are `graphicsLayer` scale animations, so a pane
 * reads its rulers through a 0.92 scale and lands short; worse, a layer property settling back to
 * 1.0 triggers no relayout, so the wrong value is never re-read and the pane keeps a stale inset for
 * the rest of its life. Inset *values* are transform-independent and have no such failure mode.
 */
@Composable
fun Modifier.paneInsets(claimTop: Boolean = true): Modifier = if (claimTop) {
    safeDrawingPadding()
} else {
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
}
