package com.stax.core.design.system

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Stax M3 Expressive shape scale (§9).
 *
 * Wired into the theme as `MaterialTheme.shapes` by [StaxTheme] (via `MaterialExpressiveTheme`).
 * Components read shapes from `MaterialTheme.shapes.<slot>` (`extraSmall` … `extraLarge`) or from
 * [Pill] — **never** inline `RoundedCornerShape(...)`, which is banned outside `:core:design-system`
 * by the `stax:NoInlineRoundedCornerShape` detekt rule.
 *
 * The three M3 Expressive "increased" slots (`largeIncreased`, `extraLargeIncreased`,
 * `extraExtraLarge`) keep their `ShapeDefaults` values via the [Shapes] constructor.
 */
@Suppress("MagicNumber") // this object is the single home for the shape-scale corner radii (§9)
object StaxShapes {
    val material: Shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

    /** Fully-rounded pill (≈999r) — chips, status badges, the selected nav indicator (§9). */
    val Pill: Shape = RoundedCornerShape(999.dp)

    /**
     * The Expanded side sheet of §6.4.2: a bottom sheet's `extraLarge` corners, but only on the two
     * edges that are not against the window — a full-height sheet flush with the end edge has no
     * outside corner there to round.
     */
    val SideSheet: Shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 0.dp, bottomEnd = 0.dp)
}
