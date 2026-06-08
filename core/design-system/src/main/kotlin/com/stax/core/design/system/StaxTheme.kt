package com.stax.core.design.system

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Material3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    Crossfade(
        targetState = colorScheme,
        animationSpec = StaxMotion.defaultEffectsSpec(),
        label = "StaxThemeColorScheme",
    ) { animatedColorScheme ->
        // motionScheme omitted → defaults to MotionScheme.expressive() (M3 Expressive motion app-wide).
        MaterialExpressiveTheme(
            colorScheme = animatedColorScheme,
            typography = StaxTypography.material,
            shapes = StaxShapes.material,
            content = content,
        )
    }
}

private fun staxColorScheme(context: Context, darkTheme: Boolean, dynamicColor: Boolean): ColorScheme = when {
    dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
    dynamicColor -> dynamicLightColorScheme(context)
    darkTheme -> STAX_DARK_COLOR_SCHEME
    else -> STAX_LIGHT_COLOR_SCHEME
}
