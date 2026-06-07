package com.stax.core.design.system

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Stax Material 3 Expressive theme with dynamic color and animated color-scheme changes.
 *
 * The theme cross-fade uses [StaxMotion.defaultEffectsSpec] — all motion specs are centralized
 * in [StaxMotion] (spec §5.9); inline `tween(...)` is forbidden here.
 */
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
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = StaxTypography.material,
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

private val STAX_LIGHT_COLOR_SCHEME = lightColorScheme(
    primary = Color(0xFF006A64),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF72F7EC),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A5F7D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E4FF),
    onSecondaryContainer = Color(0xFF041C36),
    tertiary = Color(0xFF745B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF7A),
    onTertiaryContainer = Color(0xFF241A00),
)

private val STAX_DARK_COLOR_SCHEME = darkColorScheme(
    primary = Color(0xFF50DBD0),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFF72F7EC),
    secondary = Color(0xFFB2C8E8),
    onSecondary = Color(0xFF1C314B),
    secondaryContainer = Color(0xFF334763),
    onSecondaryContainer = Color(0xFFD2E4FF),
    tertiary = Color(0xFFE5C34D),
    onTertiary = Color(0xFF3D2F00),
    tertiaryContainer = Color(0xFF574500),
    onTertiaryContainer = Color(0xFFFFDF7A),
)
