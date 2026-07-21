package com.stax.core.design.system

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens (§9). The **single legal home for raw `Color(0xFF…)` literals** — a raw color
 * anywhere else in the codebase fails the `stax:NoRawColorLiteral` detekt rule.
 *
 * Contains the fallback color-scheme seeds (used when dynamic color is unavailable) and
 * [StaxColors], the app's **semantic** color tokens. Standard M3 roles (`primary`,
 * `surfaceContainerLow`, `onSurfaceVariant`, …) are **not** redefined here — read them directly
 * from `MaterialTheme.colorScheme`.
 */

internal val STAX_LIGHT_COLOR_SCHEME = lightColorScheme(
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

internal val STAX_DARK_COLOR_SCHEME = darkColorScheme(
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

/**
 * Semantic / domain color tokens (§9). Each encodes an **app meaning** that M3 has no role for and
 * resolves to the `MaterialTheme.colorScheme` role the spec assigns it. The semantic *name* is the
 * value here — this is **not** re-wrapping standard roles (read those from `colorScheme` directly).
 *
 * All values track the active theme. Sources: dose status §4.1, site status / heat map §4.12, low
 * stock §4.2, syringe §4.6.
 */
@Suppress("TooManyFunctions", "MagicNumber")
object StaxColors {
    // --- Dose status (§4.1) ---
    val doseTakenContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondaryContainer
    val onDoseTakenContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSecondaryContainer
    val dosePartialContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiaryContainer
    val onDosePartialContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onTertiaryContainer
    val doseSkippedContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.errorContainer
    val onDoseSkippedContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onErrorContainer
    val doseMissed: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error
    val dosePending: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    // --- Injection-site status (§4.12) ---
    val siteSuggested: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
    val siteCooling: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error
    val siteReady: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondaryContainer

    // --- Inventory (§4.2) ---
    val lowStockContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.errorContainer
    val onLowStockContainer: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onErrorContainer

    // --- Syringe (§4.6) ---
    val syringeFill: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
    val onSyringeFill: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimary

    /**
     * Heat-map (§4.12.4): the `error` role drawn at an alpha between [HEAT_MIN_ALPHA] and
     * [HEAT_MAX_ALPHA], scaled by usage frequency. Apply alpha at the call site (`fill.copy(alpha=…)`).
     */
    val heatMapFill: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error
    const val HEAT_MIN_ALPHA: Float = 0.05f
    const val HEAT_MAX_ALPHA: Float = 0.7f
}
