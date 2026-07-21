package com.stax.feature.onboarding.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxShapes
import com.stax.core.design.system.StaxTheme
import com.stax.feature.onboarding.presentation.R

/**
 * The onboarding progress pills (§4.14 step 1): the pill for [currentStep] is a wide `primary` fill,
 * the remaining ones are small `outline-variant` dots.
 *
 * [currentStep] is 1-based. The pills are decorative individually, so the row announces itself as a
 * single "Step 1 of 3" instead of three unlabeled boxes.
 */
@Suppress("FunctionName")
@Composable
fun OnboardingStepIndicator(currentStep: Int, stepCount: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step_indicator, currentStep, stepCount)

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(PILL_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(stepCount) { index ->
            val active = index + 1 == currentStep
            Box(
                modifier = Modifier
                    .width(if (active) ACTIVE_PILL_WIDTH else PILL_HEIGHT)
                    .height(PILL_HEIGHT)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = StaxShapes.Pill,
                    ),
            )
        }
    }
}

private val PILL_HEIGHT = 8.dp
private val ACTIVE_PILL_WIDTH = 28.dp
private val PILL_SPACING = 6.dp

@Preview(showBackground = true, widthDp = 200, heightDp = 48)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun OnboardingStepIndicatorPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            OnboardingStepIndicator(currentStep = 1, stepCount = 3)
        }
    }
}
