package com.stax.core.design.system

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized M3 Expressive motion specs (spec §5.9).
 *
 * All animation specs come from here, sourced from [MotionScheme.expressive]. Inline
 * `tween(...)` is forbidden everywhere else in the codebase — enforced by the
 * `checkForbiddenMotionApis` Gradle task (wired into `check`). Use a spec from this object
 * instead of hand-rolling an [androidx.compose.animation.core.AnimationSpec].
 *
 * The shape-morph corner radii here are the source/target values for the morphs described in
 * §5.9 (e.g. dose card → Take Dose sheet); the animation itself uses [fastSpatialSpec] /
 * [defaultSpatialSpec] to drive the corner [Dp] between them.
 */
@Suppress("MagicNumber") // this object is the single home for these motion constants (§5.9)
object StaxMotion {
    private val expressive: MotionScheme = MotionScheme.expressive()

    /** Screen-to-screen navigation + selected nav-indicator (§5.9). */
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = expressive.fastSpatialSpec()

    /** Bottom-sheet enter/exit + FAB → FAB-menu (§5.9). */
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = expressive.defaultSpatialSpec()

    /** Color / alpha effects — e.g. the theme cross-fade (§5.9). */
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = expressive.defaultEffectsSpec()

    /** Syringe fill-width change — spring, damping 0.8 / stiffness 380 (§5.9). */
    fun <T> syringeFillSpec(): SpringSpec<T> =
        spring(dampingRatio = SYRINGE_FILL_DAMPING, stiffness = SYRINGE_FILL_STIFFNESS)

    /** Day-chip color cross-fade — 200 ms (§5.9). */
    fun <T> dayChipCrossfadeSpec(): FiniteAnimationSpec<T> = tween(durationMillis = DAY_CHIP_CROSSFADE_MILLIS)

    /** Dose-card resting corner; morphs to [TakeSheetCorner] on "Take" tap (§5.9). */
    val DoseCardCorner: Dp = 24.dp

    /** Take Dose sheet corner — morph target from [DoseCardCorner] (§5.9). */
    val TakeSheetCorner: Dp = 28.dp

    /** Day-chip unselected corner (pill); morphs to [DayChipSelectedCorner] (§5.9). */
    val DayChipUnselectedCorner: Dp = 999.dp

    /** Day-chip selected corner (§5.9). */
    val DayChipSelectedCorner: Dp = 20.dp

    /** Selected bottom-nav indicator pill corner (§5.9). */
    val NavIndicatorPillCorner: Dp = 999.dp

    /** Theme-change cross-fade duration, paired with [defaultEffectsSpec] (§5.9). */
    const val THEME_CROSSFADE_MILLIS: Int = 300

    /** Day-chip cross-fade duration (§5.9). */
    const val DAY_CHIP_CROSSFADE_MILLIS: Int = 200

    private const val SYRINGE_FILL_DAMPING: Float = 0.8f
    private const val SYRINGE_FILL_STIFFNESS: Float = 380f
}
