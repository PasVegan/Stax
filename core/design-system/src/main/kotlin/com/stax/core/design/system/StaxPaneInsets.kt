package com.stax.core.design.system

import androidx.compose.foundation.layout.fitInside
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers

/**
 * Fits a pane's content inside **its own slice** of the window's safe-drawing insets — status bar,
 * navigation bar, caption bar, display cutout, tappable element and the IME (§2.3.6, `edge-to-edge`
 * skill).
 *
 * This is the **one and only** inset method a pane may use (spec §2.3.6: inset-padding *or*
 * ruler-alignment, never both) — enforced by the `stax:NoWindowInsetsOutsideDesignSystem` detekt rule, which bans
 * the `WindowInsets` padding modifiers outside `:core:design-system`. Apply it once, at the root of
 * every `NavDisplay` entry, because a Nav3 entry *is* a Scene pane: the adaptive Scene strategies
 * lay panes out but propagate no insets of their own, so each pane must claim its own.
 *
 * Ruler alignment is used rather than `Modifier.windowInsetsPadding` because rulers are resolved in
 * the pane's coordinate space rather than by upstream consumption. Two consequences matter here:
 *
 * - **Correct slice per pane.** In a two-pane Scene, only the pane that actually touches a system
 *   bar is inset by it — the nav bar on the bottom-most pane of a tabletop (vertical) split, a
 *   landscape cutout or three-button bar on the outer pane of a side-by-side split. Inset padding
 *   would give the full window inset to both panes and open a gap down the middle.
 * - **Double padding is impossible.** The rulers of a node already sitting inside the safe area
 *   resolve to its own edges, so a nested `paneInsets` — or one composed with the padding
 *   `NavigationSuiteScaffold` already applies for its chrome — adds nothing.
 *
 * Requires bounded constraints (e.g. a preceding `Modifier.fillMaxSize()`); the pane content is
 * fitted to the safe area rather than drawn behind the bars.
 */
fun Modifier.paneInsets(): Modifier = fitInside(WindowInsetsRulers.SafeDrawing.current)
