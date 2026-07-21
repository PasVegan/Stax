package com.stax.feature.onboarding.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.design.system.StaxTheme

/**
 * The onboarding hero illustration (§4.14 step 1, reused by the permission gate §4.15): three
 * overlapping container-colored blobs with the round `primary` logo badge on top.
 *
 * Drawn on a [Canvas] rather than composed from shapes so the whole illustration scales with the
 * space it is given — every radius, offset and corner below is a fraction of the square side, and
 * the composable claims a 1:1 [aspectRatio] (§6.4.2, "vector renderers scale to allocated bounds").
 * That also keeps it clear of the shape/color token rules: the colors are `colorScheme` roles and
 * the only shape token used is [StaxShapes.Pill] for the badge.
 *
 * Purely decorative — the semantics tree is cleared so screen readers announce the headline instead.
 */
@Suppress("FunctionName")
@Composable
fun OnboardingHeroBlob(modifier: Modifier = Modifier) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clearAndSetSemantics {},
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val extent = size.minDimension

            drawCircle(
                color = primaryContainer,
                radius = PRIMARY_BLOB_RADIUS * extent,
                center = Offset(PRIMARY_BLOB_CENTER_X * extent, PRIMARY_BLOB_CENTER_Y * extent),
            )
            drawRoundRect(
                color = secondaryContainer,
                topLeft = Offset(SECONDARY_BLOB_LEFT * extent, SECONDARY_BLOB_TOP * extent),
                size = Size(SECONDARY_BLOB_SIDE * extent, SECONDARY_BLOB_SIDE * extent),
                cornerRadius = CornerRadius(SECONDARY_BLOB_CORNER * extent),
            )
            drawCircle(
                color = tertiaryContainer,
                radius = TERTIARY_BLOB_RADIUS * extent,
                center = Offset(TERTIARY_BLOB_CENTER_X * extent, TERTIARY_BLOB_CENTER_Y * extent),
            )
        }

        Surface(
            modifier = Modifier
                .align(BiasAlignment(BADGE_HORIZONTAL_BIAS, BADGE_VERTICAL_BIAS))
                .fillMaxSize(BADGE_SIDE),
            shape = StaxShapes.Pill,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = StaxIcons.Vaccines,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(BADGE_ICON_SIDE),
                )
            }
        }
    }
}

// Blob geometry — fractions of the square's side, laid out per the §4.14 mock: a large
// `primary-container` circle behind, a `secondary-container` squircle at the bottom-end corner and
// a `tertiary-container` circle overlapping both at the bottom-start.
private const val PRIMARY_BLOB_CENTER_X = 0.51f
private const val PRIMARY_BLOB_CENTER_Y = 0.41f
private const val PRIMARY_BLOB_RADIUS = 0.37f

private const val SECONDARY_BLOB_LEFT = 0.52f
private const val SECONDARY_BLOB_TOP = 0.52f
private const val SECONDARY_BLOB_SIDE = 0.48f
private const val SECONDARY_BLOB_CORNER = 0.14f

private const val TERTIARY_BLOB_CENTER_X = 0.25f
private const val TERTIARY_BLOB_CENTER_Y = 0.65f
private const val TERTIARY_BLOB_RADIUS = 0.25f

/** Logo badge: centered horizontally, sitting on the primary blob's center (bias = 2 × 0.40 − 1). */
private const val BADGE_HORIZONTAL_BIAS = 0f
private const val BADGE_VERTICAL_BIAS = -0.2f
private const val BADGE_SIDE = 0.26f
private const val BADGE_ICON_SIDE = 0.5f

@Preview(showBackground = true, widthDp = 320, heightDp = 320)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun OnboardingHeroBlobPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            OnboardingHeroBlob(modifier = Modifier.fillMaxSize())
        }
    }
}
