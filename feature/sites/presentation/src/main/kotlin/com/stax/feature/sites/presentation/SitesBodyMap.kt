package com.stax.feature.sites.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.Sublocation
import kotlinx.collections.immutable.ImmutableList

/**
 * §4.12.4's body silhouette with one dot per site, drawn into whatever bounds it is given.
 *
 * The figure is a vector, not an asset: [torsoHalf] and [armHalf] trace one side of a canonical
 * eight-head standing figure in normalized coordinates and the other side is the same trace with `x`
 * flipped, so the silhouette is symmetric by construction and sharp at every size. The four pieces
 * are unioned rather than drawn on top of one another — an arm hanging over the hip overlaps the
 * torso, and only a union renders that as one body instead of two shapes with a seam.
 *
 * Everything below is a fraction of the bounds, dots included, so the map and its hit targets scale
 * together: [nearestSite] resolves a tap against the same fractions the [Canvas] just drew.
 *
 * [mode] still draws dots in [MapMode.HEAT] — M10-03 replaces them there with blurred ellipses.
 */
@Suppress("FunctionName", "UnusedParameter")
@Composable
internal fun BodyMap(
    view: BodyView,
    sites: ImmutableList<SiteUi>,
    mode: MapMode,
    onSiteClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bodyColor = scheme.surfaceContainerHighest
    val dotColors = remember(scheme) {
        mapOf(
            SiteStatus.SUGGESTED to scheme.primary,
            SiteStatus.COOLING to scheme.error,
            SiteStatus.RECENT to scheme.secondary,
            SiteStatus.READY to scheme.outline,
        )
    }
    val placed = remember(sites, view) { sites.map { site -> site to site.dotAt(view) } }

    BoxWithConstraints(modifier = modifier) {
        val dotSize = (maxWidth * DOT_DIAMETER_FRACTION).coerceIn(DOT_MIN_DIAMETER, DOT_MAX_DIAMETER)
        val hitRadius = with(LocalDensity.current) { maxOf(dotSize * HIT_RADIUS_SCALE, MIN_HIT_RADIUS).toPx() }

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(placed, hitRadius) {
                    detectTapGestures { tap ->
                        nearestSite(placed, tap, size.toSize(), hitRadius)?.let(onSiteClick)
                    }
                },
        ) {
            drawPath(path = silhouette(), color = bodyColor)
            placed.forEach { (site, at) -> drawSiteDot(at, dotColors.getValue(site.status), site.status) }
        }
        // The dots are pixels, so TalkBack is given a node per site over them (§5.10, M17-01). It
        // carries the label and the action but no pointer input of its own: taps fall through to the
        // canvas, which is the one place the geometry lives.
        placed.forEach { (site, at) ->
            val label = stringResource(
                R.string.sites_dot_description,
                site.name,
                stringResource(site.status.labelRes()),
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * at.x - dotSize / 2, y = maxHeight * at.y - dotSize / 2)
                    .size(dotSize)
                    .semantics {
                        contentDescription = label
                        onClick {
                            onSiteClick(site.id)
                            true
                        }
                    },
            )
        }
    }
}

/**
 * The site whose dot a tap at [tap] landed on, or null for a tap on bare body.
 *
 * Nearest wins rather than first-found: the four abdomen quadrants sit within a dot's width of each
 * other, so their [hitRadius] targets overlap and "whichever was listed first" would leave one of
 * them unreachable at every canvas size.
 */
private fun nearestSite(placed: List<Pair<SiteUi, Offset>>, tap: Offset, size: Size, hitRadius: Float): Long? = placed
    .map { (site, at) -> site to (Offset(at.x * size.width, at.y * size.height) - tap).getDistance() }
    .filter { (_, distance) -> distance <= hitRadius }
    .minByOrNull { (_, distance) -> distance }
    ?.first
    ?.id

/** §4.12.4's four dot states: the fill, and the `primary` ring only the suggested site wears. */
private fun DrawScope.drawSiteDot(at: Offset, color: Color, status: SiteStatus) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * DOT_DIAMETER_FRACTION / 2f
    if (status == SiteStatus.SUGGESTED) {
        drawCircle(
            color = color.copy(alpha = RING_ALPHA),
            radius = radius * RING_RADIUS_SCALE,
            center = center,
            style = Stroke(width = radius * RING_WIDTH_SCALE),
        )
    }
    drawCircle(color = color, radius = radius, center = center)
}

// ---------------------------------------------------------------------------
// The silhouette (§4.12.4)
// ---------------------------------------------------------------------------

/** Both sides of the figure, unioned into the one shape the canvas fills. */
private fun DrawScope.silhouette(): Path {
    val right = Path().apply { op(torsoHalf(mirrored = false), armHalf(mirrored = false), PathOperation.Union) }
    val left = Path().apply { op(torsoHalf(mirrored = true), armHalf(mirrored = true), PathOperation.Union) }
    return Path().apply { op(right, left, PathOperation.Union) }
}

/**
 * Head, neck, one shoulder, one side of the trunk and one leg, closed up the centre line.
 *
 * The landmarks are the canonical eight-head figure: chin at `0.13`, nipple at `0.25`, navel at
 * `0.375`, crotch at `0.51`, knee at `0.74`, sole at `1.0`; two heads across the shoulders and about
 * 1.6 across the hips. That is what keeps it a person rather than the snowman a blob of circles
 * draws.
 */
private fun DrawScope.torsoHalf(mirrored: Boolean): Path {
    val path = Path()
    val x = { v: Float -> (if (mirrored) 1f - v else v) * size.width }
    val y = { v: Float -> v * size.height }
    fun curve(c1x: Float, c1y: Float, c2x: Float, c2y: Float, ex: Float, ey: Float) =
        path.cubicTo(x(c1x), y(c1y), x(c2x), y(c2y), x(ex), y(ey))

    path.moveTo(x(0.500f), y(0.004f))
    curve(0.554f, 0.004f, 0.590f, 0.024f, 0.590f, 0.058f) // crown → temple
    curve(0.590f, 0.090f, 0.576f, 0.114f, 0.548f, 0.128f) // cheek → jaw
    curve(0.549f, 0.140f, 0.550f, 0.148f, 0.552f, 0.158f) // neck
    curve(0.602f, 0.168f, 0.650f, 0.181f, 0.674f, 0.213f) // trapezius → shoulder
    curve(0.666f, 0.253f, 0.647f, 0.301f, 0.637f, 0.349f) // ribs → waist
    curve(0.644f, 0.384f, 0.667f, 0.412f, 0.683f, 0.448f) // waist → hip
    curve(0.694f, 0.484f, 0.684f, 0.516f, 0.664f, 0.548f) // hip → thigh
    curve(0.650f, 0.600f, 0.634f, 0.670f, 0.622f, 0.744f) // thigh → knee
    curve(0.628f, 0.780f, 0.628f, 0.818f, 0.612f, 0.878f) // calf
    curve(0.600f, 0.918f, 0.592f, 0.940f, 0.586f, 0.956f) // ankle
    curve(0.594f, 0.978f, 0.606f, 0.990f, 0.608f, 0.998f) // instep → toe
    path.lineTo(x(0.514f), y(0.998f)) // sole
    curve(0.516f, 0.986f, 0.520f, 0.970f, 0.523f, 0.956f) // heel → inner ankle
    curve(0.516f, 0.910f, 0.512f, 0.850f, 0.528f, 0.744f) // inner calf → knee
    curve(0.532f, 0.664f, 0.516f, 0.582f, 0.502f, 0.516f) // inner thigh → crotch
    path.lineTo(x(0.500f), y(0.516f))
    path.close()
    return path
}

/**
 * One arm, hanging with the elbow just clear of the waist and the fingertips at mid-thigh.
 *
 * Its own closed shape because a hanging arm crosses the hip: traced as part of the trunk outline
 * that crossing is a self-intersection, and the wedge between forearm and waist fills in solid.
 */
private fun DrawScope.armHalf(mirrored: Boolean): Path {
    val path = Path()
    val x = { v: Float -> (if (mirrored) 1f - v else v) * size.width }
    val y = { v: Float -> v * size.height }
    fun curve(c1x: Float, c1y: Float, c2x: Float, c2y: Float, ex: Float, ey: Float) =
        path.cubicTo(x(c1x), y(c1y), x(c2x), y(c2y), x(ex), y(ey))

    path.moveTo(x(0.666f), y(0.196f))
    curve(0.720f, 0.192f, 0.772f, 0.224f, 0.774f, 0.268f) // deltoid cap
    curve(0.775f, 0.306f, 0.756f, 0.346f, 0.744f, 0.382f) // upper arm → elbow
    curve(0.735f, 0.426f, 0.724f, 0.482f, 0.716f, 0.528f) // forearm → wrist
    curve(0.727f, 0.554f, 0.724f, 0.592f, 0.701f, 0.611f) // back of hand → fingertips
    curve(0.685f, 0.622f, 0.664f, 0.607f, 0.662f, 0.578f) // fingertips → thumb web
    curve(0.660f, 0.556f, 0.662f, 0.541f, 0.664f, 0.528f) // → inner wrist
    curve(0.669f, 0.482f, 0.676f, 0.426f, 0.680f, 0.380f) // inner forearm → elbow
    curve(0.690f, 0.330f, 0.678f, 0.252f, 0.658f, 0.206f) // inner upper arm → armpit
    path.close()
    return path
}

// ---------------------------------------------------------------------------
// Dot placement (§4.12.4, §5.8.6)
// ---------------------------------------------------------------------------

/**
 * Where this site's dot sits, as a fraction of the map's bounds.
 *
 * [BodyRegion] and [SiteUi.sublocation] fix how far down the body and how far off the centre line the
 * dot goes; [SiteUi.side] and [view] fix which way. The two disagree on purpose: on Front we are
 * facing the body, so its **left** is on the viewer's **right**, and on Back we are behind it and
 * the two agree. Getting that backwards mirrors the whole rotation.
 */
private fun SiteUi.dotAt(view: BodyView): Offset {
    val (offset, y) = bodyRegion.dotPlacement(sublocation)
    val onViewerRight = when (side) {
        InjectionSide.LEFT -> view == BodyView.FRONT
        InjectionSide.RIGHT -> view == BodyView.BACK
        // A midline site has no side to mirror — it sits on the centre line.
        InjectionSide.CENTER, InjectionSide.NOT_APPLICABLE -> return Offset(CENTRE_X, y)
    }
    return Offset(if (onViewerRight) CENTRE_X + offset else CENTRE_X - offset, y)
}

/**
 * The region's dot, as (distance from the centre line, distance down the body).
 *
 * §5.8.6 seeds fourteen of these; the rest of [BodyRegion] is placed too, because a site the seed
 * grows later (§5.8.6 names posterior deltoid and forearm for v1.1) would otherwise land on the
 * silhouette's navel with no warning.
 */
private fun BodyRegion.dotPlacement(sublocation: Sublocation?): Pair<Float, Float> = when (this) {
    BodyRegion.ABDOMEN -> when (sublocation) {
        Sublocation.UPPER -> 0.062f to 0.325f
        Sublocation.LOWER -> 0.068f to 0.415f
        else -> 0.065f to 0.372f
    }

    // "Lateral thigh" is the outer sublocation of the quadriceps (§5.8.6) — the same muscle, a
    // hand's width further out, which is the only reason the two are drawn apart.
    BodyRegion.QUADRICEPS -> if (sublocation == Sublocation.OUTER) 0.105f to 0.600f else 0.083f to 0.600f
    BodyRegion.THIGH -> 0.083f to 0.600f
    BodyRegion.GLUTE -> 0.105f to 0.478f
    BodyRegion.HAMSTRING -> 0.083f to 0.630f
    BodyRegion.LOWER_BACK -> 0.070f to 0.360f
    BodyRegion.DELT -> 0.222f to 0.248f
    BodyRegion.UPPER_ARM -> 0.226f to 0.320f
    BodyRegion.FOREARM -> 0.202f to 0.460f
}

internal fun SiteStatus.labelRes(): Int = when (this) {
    SiteStatus.SUGGESTED -> R.string.sites_legend_suggested
    SiteStatus.COOLING -> R.string.sites_legend_cooling
    SiteStatus.RECENT -> R.string.sites_legend_recent
    SiteStatus.READY -> R.string.sites_legend_ready
}

// ---------------------------------------------------------------------------
// Dot geometry
// ---------------------------------------------------------------------------

private const val CENTRE_X = 0.5f

/** A dot is about this much of the map's width — narrow enough to sit on a forearm and still fit. */
private const val DOT_DIAMETER_FRACTION = 0.078f
private val DOT_MIN_DIAMETER = 12.dp
private val DOT_MAX_DIAMETER = 18.dp

/** §4.12.4's `primary` 60%-opacity ring around the suggested dot. */
private const val RING_ALPHA = 0.6f
private const val RING_RADIUS_SCALE = 1.7f
private const val RING_WIDTH_SCALE = 0.3f

/**
 * How far past its own edge a dot answers a tap, and the floor that keeps that reachable.
 *
 * Both scale with the canvas — the dot does — but a map squeezed into a Medium left pane would put
 * the four abdomen dots inside a fingertip of each other, so the target never shrinks below
 * [MIN_HIT_RADIUS] and [nearestSite] decides the overlaps.
 */
private const val HIT_RADIUS_SCALE = 1.4f
private val MIN_HIT_RADIUS: Dp = 14.dp
