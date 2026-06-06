package com.stax.core.design.system

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.system.measureNanoTime

/**
 * Google Sans Flex typography and load-cost measurement for the Stax design system.
 */
object StaxTypography {

    val googleSansFlex: FontFamily = FontFamily(
        Font(R.font.google_sans_flex_light, FontWeight.Light),
        Font(R.font.google_sans_flex_regular, FontWeight.Normal),
        Font(R.font.google_sans_flex_medium, FontWeight.Medium),
        Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
        Font(R.font.google_sans_flex_bold, FontWeight.Bold),
    )

    val material: Typography = Typography(fontFamily = googleSansFlex)

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

    private val googleSansFlexFontResources = intArrayOf(
        R.font.google_sans_flex_light,
        R.font.google_sans_flex_regular,
        R.font.google_sans_flex_medium,
        R.font.google_sans_flex_semibold,
        R.font.google_sans_flex_bold,
    )
}

private const val NANOS_PER_MILLISECOND = 1_000_000L
