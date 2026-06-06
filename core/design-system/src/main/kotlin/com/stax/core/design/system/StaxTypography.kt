package com.stax.core.design.system

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.system.measureNanoTime

/**
 * Google Sans Flex typography and load-cost measurement for the Stax design system.
 */
object StaxTypography {

    private val base = Typography()

    val googleSansFlex: FontFamily = FontFamily(
        Font(R.font.google_sans_flex_light, FontWeight.Light),
        Font(R.font.google_sans_flex_regular, FontWeight.Normal),
        Font(R.font.google_sans_flex_medium, FontWeight.Medium),
        Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
        Font(R.font.google_sans_flex_bold, FontWeight.Bold),
    )

    val material: Typography = Typography(
        displayLarge = base.displayLarge.googleSans(FontWeight.Light),
        displayMedium = base.displayMedium.googleSans(FontWeight.Light),
        displaySmall = base.displaySmall.googleSans(FontWeight.Normal),
        headlineLarge = base.headlineLarge.googleSans(FontWeight.Normal),
        headlineMedium = base.headlineMedium.googleSans(FontWeight.Normal),
        headlineSmall = base.headlineSmall.googleSans(FontWeight.Normal),
        titleLarge = base.titleLarge.googleSans(FontWeight.Medium),
        titleMedium = base.titleMedium.googleSans(FontWeight.Medium),
        titleSmall = base.titleSmall.googleSans(FontWeight.Medium),
        bodyLarge = base.bodyLarge.googleSans(FontWeight.Normal),
        bodyMedium = base.bodyMedium.googleSans(FontWeight.Normal),
        bodySmall = base.bodySmall.googleSans(FontWeight.Normal),
        labelLarge = base.labelLarge.googleSans(FontWeight.Medium),
        labelMedium = base.labelMedium.googleSans(FontWeight.Medium),
        labelSmall = base.labelSmall.googleSans(FontWeight.Medium),
    )

    val displayLargeEmphasized: TextStyle = material.displayLarge.emphasized()
    val displayMediumEmphasized: TextStyle = material.displayMedium.emphasized()
    val displaySmallEmphasized: TextStyle = material.displaySmall.emphasized()
    val headlineLargeEmphasized: TextStyle = material.headlineLarge.emphasized()
    val headlineMediumEmphasized: TextStyle = material.headlineMedium.emphasized()
    val headlineSmallEmphasized: TextStyle = material.headlineSmall.emphasized()
    val titleLargeEmphasized: TextStyle = material.titleLarge.emphasized()
    val titleMediumEmphasized: TextStyle = material.titleMedium.emphasized()
    val titleSmallEmphasized: TextStyle = material.titleSmall.emphasized()
    val bodyLargeEmphasized: TextStyle = material.bodyLarge.emphasized()
    val bodyMediumEmphasized: TextStyle = material.bodyMedium.emphasized()
    val bodySmallEmphasized: TextStyle = material.bodySmall.emphasized()
    val labelLargeEmphasized: TextStyle = material.labelLarge.emphasized()
    val labelMediumEmphasized: TextStyle = material.labelMedium.emphasized()
    val labelSmallEmphasized: TextStyle = material.labelSmall.emphasized()

    /**
     * Synchronously loads the bundled Google Sans Flex weights and returns elapsed milliseconds.
     */
    fun measureGoogleSansFlexLoadCostMillis(context: Context): Long {
        val elapsedNanos = measureNanoTime {
            googleSansFlexFontResources.forEach { fontRes ->
                context.resources.getFont(fontRes)
            }
        }
        return elapsedNanos / NANOS_PER_MILLISECOND
    }

    private fun TextStyle.googleSans(fontWeight: FontWeight): TextStyle = copy(
        fontFamily = googleSansFlex,
        fontWeight = fontWeight,
    )

    private fun TextStyle.emphasized(): TextStyle = copy(fontWeight = FontWeight.SemiBold)

    private val googleSansFlexFontResources = intArrayOf(
        R.font.google_sans_flex_light,
        R.font.google_sans_flex_regular,
        R.font.google_sans_flex_medium,
        R.font.google_sans_flex_semibold,
        R.font.google_sans_flex_bold,
    )
}

private const val NANOS_PER_MILLISECOND = 1_000_000L
