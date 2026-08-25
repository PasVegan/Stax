package com.stax.feature.reconstitution.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxMotion
import com.stax.core.design.system.StaxShapes
import com.stax.core.design.system.StaxTheme

/**
 * §4.6.2's syringe: the barrel drawn to the dose, on the graduation the chosen syringe carries.
 *
 * One [Canvas] rather than a stack of composables because the whole thing is a single coordinate
 * system — the fill, every graduation and the bubble over the fill all key off the same barrel span,
 * and laying that out with boxes means recomputing it in three places.
 *
 * [fill] is a fraction of the syringe's capacity and is the ViewModel's arithmetic, not this
 * function's: the volume divided by the capacity is dose math and belongs to `Decimal` (§3.0.1).
 * What arrives here is already a ratio, and it animates on §5.9's syringe spring — damping `0.8`,
 * stiffness `380` — which is the whole of §4.6.8.
 *
 * [drawTo] is the figure the bubble carries, in whichever unit §4.6.4's Display tile is on — which
 * is what [display] names for the screen reader. Null is a mix that has not produced a volume yet:
 * an empty barrel with no bubble over it.
 */
@Suppress("FunctionName")
@Composable
internal fun SyringeVisualization(
    syringeSize: SyringeSize,
    fill: Float,
    drawTo: String?,
    display: DoseDisplay,
    modifier: Modifier = Modifier,
) {
    val animatedFill by animateFloatAsState(
        targetValue = fill,
        animationSpec = StaxMotion.syringeFillSpec(),
        label = "syringeFill",
    )
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val graduationStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val bubbleStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onPrimary)
    val description = stringResource(
        R.string.reconstitution_syringe_description,
        syringeSizeLabel(syringeSize),
        drawTo ?: stringResource(R.string.reconstitution_no_value),
        drawToUnitLabel(display),
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(SYRINGE_HEIGHT)
            // The drawing has no text nodes of its own, so the whole barrel reads as one label.
            .semantics { contentDescription = description },
    ) {
        val barrelLeft = size.width * BARREL_START
        val barrelRight = size.width * BARREL_END
        val barrelWidth = barrelRight - barrelLeft
        val centreY = (BUBBLE_HEIGHT + BUBBLE_GAP).toPx() + BAND_HEIGHT.toPx() / 2
        // §4.6.2 "visual change if insulin or regular": an insulin barrel is the narrow one, which is
        // what tells the two apart at a glance once the graduation numbers are too small to read.
        val barrelHalf = (if (syringeSize.isInsulin) INSULIN_BARREL_HALF else REGULAR_BARREL_HALF).toPx()

        drawPlunger(barrelLeft, centreY, colors.outline)
        drawBarrel(barrelLeft, barrelRight, centreY, barrelHalf, colors.surface, colors.outline)
        drawFill(barrelLeft, barrelWidth, centreY, barrelHalf, animatedFill, colors.primary)
        drawGraduations(syringeSize, barrelLeft, barrelWidth, centreY, barrelHalf, colors.outlineVariant)
        drawNeedle(barrelRight, centreY, colors.outline, colors.onSurfaceVariant)
        drawGraduationLabels(syringeSize, barrelLeft, barrelWidth, centreY, barrelHalf, textMeasurer, graduationStyle)
        if (drawTo != null) {
            drawFillBubble(barrelLeft + barrelWidth * animatedFill, drawTo, textMeasurer, bubbleStyle, colors.primary)
        }
    }
}

/**
 * §4.6.2's size badge: `secondary-container`, leading `straighten`, and a tap that walks
 * [SyringeSize.next] — U-30 → U-50 → U-100 → 2 mL → 3 mL → 5 mL and round again.
 */
@Suppress("FunctionName")
@Composable
internal fun SyringeSizeBadge(
    syringeSize: SyringeSize,
    onAction: (ReconstitutionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onAction(ReconstitutionAction.OnCycleSyringeSize) },
        modifier = modifier,
        shape = StaxShapes.Pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BADGE_PADDING_H, vertical = BADGE_PADDING_V),
            horizontalArrangement = Arrangement.spacedBy(BADGE_ICON_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = StaxIcons.Straighten,
                contentDescription = null,
                modifier = Modifier.size(BADGE_ICON_SIZE),
            )
            Text(
                text = syringeSizeLabel(syringeSize),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/** "U-100 · 1 mL" for an insulin syringe, "3 mL" for a regular one (§4.6.2). */
@Composable
internal fun syringeSizeLabel(syringeSize: SyringeSize): String = if (syringeSize.isInsulin) {
    stringResource(R.string.reconstitution_syringe_insulin, syringeSize.scaleMax, syringeSize.capacityMl)
} else {
    stringResource(R.string.reconstitution_syringe_regular, syringeSize.capacityMl)
}

// ---------------------------------------------------------------------------
// The drawing
// ---------------------------------------------------------------------------

/** The rod and its thumb rest, left of the barrel. Static: the fill is what moves (§4.6.2). */
private fun DrawScope.drawPlunger(barrelLeft: Float, centreY: Float, color: Color) {
    val flangeWidth = FLANGE_WIDTH.toPx()
    val flangeHalf = FLANGE_HALF.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, centreY - flangeHalf),
        size = Size(flangeWidth, flangeHalf * 2),
        cornerRadius = CornerRadius(flangeWidth / 2),
    )
    val rodHalf = ROD_HALF.toPx()
    drawRect(
        color = color,
        topLeft = Offset(flangeWidth, centreY - rodHalf),
        size = Size((barrelLeft - flangeWidth).coerceAtLeast(0f), rodHalf * 2),
    )
}

private fun DrawScope.drawBarrel(
    barrelLeft: Float,
    barrelRight: Float,
    centreY: Float,
    barrelHalf: Float,
    fillColor: Color,
    outlineColor: Color,
) {
    val topLeft = Offset(barrelLeft, centreY - barrelHalf)
    val barrelSize = Size(barrelRight - barrelLeft, barrelHalf * 2)
    val corner = CornerRadius(BARREL_CORNER.toPx())
    drawRoundRect(color = fillColor, topLeft = topLeft, size = barrelSize, cornerRadius = corner)
    drawRoundRect(
        color = outlineColor,
        topLeft = topLeft,
        size = barrelSize,
        cornerRadius = corner,
        style = Stroke(width = BARREL_STROKE.toPx()),
    )
}

/** The drawn dose, measured from the `0` graduation — what "Draw to" means as a picture (§4.6.2). */
private fun DrawScope.drawFill(
    barrelLeft: Float,
    barrelWidth: Float,
    centreY: Float,
    barrelHalf: Float,
    fill: Float,
    color: Color,
) {
    val width = barrelWidth * fill.coerceIn(0f, 1f)
    if (width <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(barrelLeft, centreY - barrelHalf),
        size = Size(width, barrelHalf * 2),
        cornerRadius = CornerRadius(BARREL_CORNER.toPx()),
    )
}

/** The graduation the chosen syringe carries: minor ticks throughout, longer ones every major. */
private fun DrawScope.drawGraduations(
    syringeSize: SyringeSize,
    barrelLeft: Float,
    barrelWidth: Float,
    centreY: Float,
    barrelHalf: Float,
    color: Color,
) {
    val top = centreY - barrelHalf
    val stroke = TICK_STROKE.toPx()
    for (tick in 0..syringeSize.minorCount) {
        val x = barrelLeft + barrelWidth * tick / syringeSize.minorCount
        val length = barrelHalf * if (tick % syringeSize.majorEvery == 0) MAJOR_TICK_RATIO else MINOR_TICK_RATIO
        drawLine(color = color, start = Offset(x, top), end = Offset(x, top + length), strokeWidth = stroke)
    }
}

private fun DrawScope.drawGraduationLabels(
    syringeSize: SyringeSize,
    barrelLeft: Float,
    barrelWidth: Float,
    centreY: Float,
    barrelHalf: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val top = centreY + barrelHalf + LABEL_GAP.toPx()
    for (tick in 0..syringeSize.minorCount step syringeSize.labelEvery) {
        val measured = textMeasurer.measure(syringeSize.graduationLabel(tick), style)
        val x = barrelLeft + barrelWidth * tick / syringeSize.minorCount
        drawText(measured, topLeft = Offset(x - measured.size.width / 2f, top))
    }
}

private fun DrawScope.drawNeedle(barrelRight: Float, centreY: Float, hubColor: Color, needleColor: Color) {
    val hubHalf = HUB_HALF.toPx()
    val hubWidth = HUB_WIDTH.toPx()
    drawRect(
        color = hubColor,
        topLeft = Offset(barrelRight, centreY - hubHalf),
        size = Size(hubWidth, hubHalf * 2),
    )
    drawLine(
        color = needleColor,
        start = Offset(barrelRight + hubWidth, centreY),
        end = Offset(size.width, centreY),
        strokeWidth = NEEDLE_STROKE.toPx(),
    )
}

/**
 * The figure to draw to, parked over the graduation it lands on.
 *
 * Clamped to the canvas so a full syringe does not push it off the end — at that point it stops
 * tracking the fill exactly, which is the honest trade against a number nobody can read.
 */
private fun DrawScope.drawFillBubble(
    fillX: Float,
    text: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    color: Color,
) {
    val measured = textMeasurer.measure(text, style)
    val bubbleWidth = measured.size.width + BUBBLE_PADDING.toPx() * 2
    val bubbleHeight = BUBBLE_HEIGHT.toPx()
    val left = (fillX - bubbleWidth / 2f).coerceIn(0f, (size.width - bubbleWidth).coerceAtLeast(0f))
    drawRoundRect(
        color = color,
        topLeft = Offset(left, 0f),
        size = Size(bubbleWidth, bubbleHeight),
        cornerRadius = CornerRadius(bubbleHeight / 2f),
    )
    drawText(
        measured,
        topLeft = Offset(
            left + (bubbleWidth - measured.size.width) / 2f,
            (bubbleHeight - measured.size.height) / 2f,
        ),
    )
}

// ---------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------

/** Bubble row, then the syringe band, then the graduation numbers under it. */
private val BUBBLE_HEIGHT = 22.dp
private val BUBBLE_GAP = 6.dp
private val BAND_HEIGHT = 44.dp
private val LABEL_GAP = 4.dp
internal val SYRINGE_HEIGHT = 90.dp

/** Where the barrel sits across the drawing: plunger to its left, hub and needle to its right. */
private const val BARREL_START = 0.16f
private const val BARREL_END = 0.82f

private val FLANGE_WIDTH = 5.dp
private val FLANGE_HALF = 19.dp
private val ROD_HALF = 6.dp
private val INSULIN_BARREL_HALF = 12.dp
private val REGULAR_BARREL_HALF = 16.dp
private val BARREL_CORNER = 4.dp
private val BARREL_STROKE = 1.dp
private val TICK_STROKE = 1.dp
private const val MAJOR_TICK_RATIO = 1.1f
private const val MINOR_TICK_RATIO = 0.55f
private val HUB_WIDTH = 10.dp
private val HUB_HALF = 7.dp
private val NEEDLE_STROKE = 2.dp
private val BUBBLE_PADDING = 8.dp

private val BADGE_PADDING_H = 12.dp
private val BADGE_PADDING_V = 6.dp
private val BADGE_ICON_GAP = 6.dp
private val BADGE_ICON_SIZE = 18.dp

@Preview(name = "Every syringe size", showBackground = true, widthDp = 380)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun SyringeVisualizationPreview() {
    StaxTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(BADGE_PADDING_H)) {
                SyringeSize.entries.forEach { size ->
                    SyringeSizeBadge(syringeSize = size, onAction = {})
                    SyringeVisualization(
                        syringeSize = size,
                        fill = 0.35f,
                        drawTo = "10",
                        display = DoseDisplay.INSULIN_UNITS,
                    )
                }
            }
        }
    }
}
