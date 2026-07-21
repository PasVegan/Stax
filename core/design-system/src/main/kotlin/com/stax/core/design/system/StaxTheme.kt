package com.stax.core.design.system

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Material3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Stax Material 3 Expressive theme with dynamic color and animated color-scheme changes.
 *
 * Uses [MaterialExpressiveTheme] so the expressive [androidx.compose.material3.MotionScheme] is
 * provided app-wide — every Material 3 component (and [StaxMotion]) animates expressively, not just
 * our hand-written animations.
 *
 * The theme cross-fade uses [StaxMotion.defaultEffectsSpec] — all motion specs are centralized
 * in [StaxMotion] (spec §5.9); inline `tween(...)` is forbidden here.
 */
@OptIn(Material3ExpressiveApi::class)
@Suppress("FunctionName")
@Composable
fun StaxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(context, darkTheme, dynamicColor) {
        staxColorScheme(
            context = context,
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
        )
    }

    // motionScheme omitted → defaults to MotionScheme.expressive() (M3 Expressive motion app-wide).
    MaterialExpressiveTheme(
        colorScheme = colorScheme.animated(),
        typography = StaxTypography.material,
        shapes = StaxShapes.material,
        content = content,
    )
}

/**
 * Cross-fades to this scheme by animating every role's **color value** (§5.9).
 *
 * The animation deliberately happens in values, not in structure. Wrapping [content] in a
 * `Crossfade`/`AnimatedContent` would place the whole app in a new composition group each time the
 * scheme changes — and `rememberSaveable` derives its registry key from the enclosing group, so every
 * auto-keyed piece of saved state (the nav back stacks among them) would become unreachable and reset
 * on each theme recompute, including the one that follows every configuration change.
 */
@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = primary.animated(),
    onPrimary = onPrimary.animated(),
    primaryContainer = primaryContainer.animated(),
    onPrimaryContainer = onPrimaryContainer.animated(),
    inversePrimary = inversePrimary.animated(),
    secondary = secondary.animated(),
    onSecondary = onSecondary.animated(),
    secondaryContainer = secondaryContainer.animated(),
    onSecondaryContainer = onSecondaryContainer.animated(),
    tertiary = tertiary.animated(),
    onTertiary = onTertiary.animated(),
    tertiaryContainer = tertiaryContainer.animated(),
    onTertiaryContainer = onTertiaryContainer.animated(),
    background = background.animated(),
    onBackground = onBackground.animated(),
    surface = surface.animated(),
    onSurface = onSurface.animated(),
    surfaceVariant = surfaceVariant.animated(),
    onSurfaceVariant = onSurfaceVariant.animated(),
    surfaceTint = surfaceTint.animated(),
    inverseSurface = inverseSurface.animated(),
    inverseOnSurface = inverseOnSurface.animated(),
    error = error.animated(),
    onError = onError.animated(),
    errorContainer = errorContainer.animated(),
    onErrorContainer = onErrorContainer.animated(),
    outline = outline.animated(),
    outlineVariant = outlineVariant.animated(),
    scrim = scrim.animated(),
    surfaceBright = surfaceBright.animated(),
    surfaceDim = surfaceDim.animated(),
    surfaceContainer = surfaceContainer.animated(),
    surfaceContainerHigh = surfaceContainerHigh.animated(),
    surfaceContainerHighest = surfaceContainerHighest.animated(),
    surfaceContainerLow = surfaceContainerLow.animated(),
    surfaceContainerLowest = surfaceContainerLowest.animated(),
)

/** One role's cross-fade, driven by the centralized effects spec (§5.9). */
@Composable
private fun Color.animated(): Color =
    animateColorAsState(targetValue = this, animationSpec = StaxMotion.defaultEffectsSpec()).value

private fun staxColorScheme(context: Context, darkTheme: Boolean, dynamicColor: Boolean): ColorScheme = when {
    dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
    dynamicColor -> dynamicLightColorScheme(context)
    darkTheme -> STAX_DARK_COLOR_SCHEME
    else -> STAX_LIGHT_COLOR_SCHEME
}
