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
 * by the `checkForbiddenShapeApis` Gradle task (wired into `check`).
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
}
