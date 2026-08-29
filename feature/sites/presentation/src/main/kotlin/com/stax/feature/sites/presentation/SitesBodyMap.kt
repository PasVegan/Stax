package com.stax.feature.sites.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.stax.core.domain.InjectionSide
import kotlinx.collections.immutable.ImmutableList

/**
 * §4.12.4's body map: an anatomical figure with one injection zone and dot per site.
 *
 * Four layers, drawn into whatever bounds the hero gives it:
 *
 * 1. the **silhouette** — [BodyArt.TORSO] and [BodyArt.ARM], each mirrored and unioned, so the two
 *    halves are identical by construction and meet without a seam;
 * 2. the **muscle groups** of this [view], a shade off the body and clipped to it — a deltoid the
 *    user can find is what separates a body map from a gingerbread outline;
 * 3. the **zone** each site injects into, washed in its §4.12.4 state colour, so the map answers
 *    "where on me" and not only "which of fourteen rows";
 * 4. the **dot** at the middle of that zone, in the same colour at full strength, plus the `primary`
 *    ring the suggested site wears.
 *
 * Every coordinate comes from [BodyArt]'s fixed viewport and is scaled to the bounds once, so a tap
 * resolves against the same geometry the canvas drew and the hit target scales with the map.
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
    val muscleColor = scheme.onSurfaceVariant.copy(alpha = MUSCLE_ALPHA)
    val dotColors = remember(scheme) {
        mapOf(
            SiteStatus.SUGGESTED to scheme.primary,
            SiteStatus.COOLING to scheme.error,
            SiteStatus.RECENT to scheme.secondary,
            SiteStatus.READY to scheme.outline,
        )
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val canvas = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val figure = remember(canvas, view) { bodyFigure(view, canvas) }
        val placed = remember(canvas, view, sites) { sites.map { it.placeOn(canvas) } }

        val dotSize = (maxWidth * DOT_DIAMETER_FRACTION).coerceIn(DOT_MIN_DIAMETER, DOT_MAX_DIAMETER)
        val dotRadius = with(density) { dotSize.toPx() } / 2f
        val hitRadius = with(density) { maxOf(dotSize * HIT_RADIUS_SCALE, MIN_HIT_RADIUS).toPx() }

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(placed, hitRadius) {
                    detectTapGestures { tap -> nearestSite(placed, tap, hitRadius)?.let(onSiteClick) }
                },
        ) {
            drawPath(path = figure.silhouette, color = bodyColor)
            clipPath(figure.silhouette) {
                drawPath(path = figure.muscles, color = muscleColor)
                placed.forEach {
                    drawPath(
                        path = it.zone,
                        color = dotColors.getValue(it.site.status),
                        alpha = it.site.status.zoneAlpha(),
                    )
                }
            }
            placed.forEach { drawDot(it, dotColors.getValue(it.site.status), dotRadius) }
        }
        placed.forEach { DotSemantics(placed = it, size = dotSize, onSiteClick = onSiteClick) }
    }
}

/**
 * A TalkBack node over one dot (§5.10, M17-01), labelled "{site name}, {status}".
 *
 * It carries the label and the action but no pointer input of its own, so a tap falls through to the
 * canvas underneath — which is the one place the map's geometry lives.
 */
@Suppress("FunctionName")
@Composable
private fun DotSemantics(placed: PlacedSite, size: Dp, onSiteClick: (Long) -> Unit) {
    val label = stringResource(
        R.string.sites_dot_description,
        placed.site.name,
        stringResource(placed.site.status.labelRes()),
    )
    val radius = with(LocalDensity.current) { size.toPx() } / 2f
    Box(
        modifier = Modifier
            .offset { (placed.center - Offset(radius, radius)).round() }
            .size(size)
            .semantics {
                contentDescription = label
                onClick {
                    onSiteClick(placed.site.id)
                    true
                }
            },
    )
}

/**
 * The site whose dot a tap at [tap] landed on, or null for a tap on bare body.
 *
 * Nearest wins rather than first-found: the four abdomen quadrants sit within a dot's width of each
 * other, so their [hitRadius] targets overlap and "whichever was listed first" would leave one of
 * them unreachable at every canvas size.
 */
private fun nearestSite(placed: List<PlacedSite>, tap: Offset, hitRadius: Float): Long? = placed
    .map { it to (it.center - tap).getDistance() }
    .filter { (_, distance) -> distance <= hitRadius }
    .minByOrNull { (_, distance) -> distance }
    ?.first
    ?.site
    ?.id

/** §4.12.4's four dot states: the fill, and the `primary` ring only the suggested site wears. */
private fun DrawScope.drawDot(placed: PlacedSite, color: Color, radius: Float) {
    if (placed.site.status == SiteStatus.SUGGESTED) {
        drawCircle(
            color = color.copy(alpha = RING_ALPHA),
            radius = radius * RING_RADIUS_SCALE,
            center = placed.center,
            style = Stroke(width = radius * RING_WIDTH_SCALE),
        )
    }
    drawCircle(color = color, radius = radius, center = placed.center)
}

// ---------------------------------------------------------------------------
// §4.12.4 geometry — BodyArt's viewport, scaled to the canvas
// ---------------------------------------------------------------------------

/** One site's zone and dot, already in canvas pixels. */
@Immutable
private data class PlacedSite(val site: SiteUi, val zone: Path, val center: Offset)

/** The two layers that are the same for every site: the body, and the muscles of this [view]. */
@Immutable
private data class BodyFigure(val silhouette: Path, val muscles: Path)

private fun bodyFigure(view: BodyView, canvas: Size): BodyFigure {
    val silhouette = Path()
    silhouette.op(
        path1 = Path().apply {
            op(scaled(BodyArt.TORSO, canvas, false), scaled(BodyArt.ARM, canvas, false), PathOperation.Union)
        },
        path2 = Path().apply {
            op(scaled(BodyArt.TORSO, canvas, true), scaled(BodyArt.ARM, canvas, true), PathOperation.Union)
        },
        operation = PathOperation.Union,
    )
    val outlines = if (view == BodyView.FRONT) BodyArt.FRONT_MUSCLES else BodyArt.BACK_MUSCLES
    val muscles = Path().apply {
        // Added rather than unioned: no two muscle outlines overlap, and every one of them stops
        // short of the centre line, so the mirrored halves never double the alpha where they meet.
        outlines.forEach { outline ->
            addPath(scaled(outline, canvas, mirrored = false))
            addPath(scaled(outline, canvas, mirrored = true))
        }
    }
    return BodyFigure(silhouette = silhouette, muscles = muscles)
}

/**
 * This site's zone and dot on [canvas].
 *
 * [SiteUi.side] and the body view decide which way the zone is mirrored, and the two disagree on
 * purpose: on Front we face the body, so its **left** is the viewer's **right**; on Back we are
 * behind it and the two agree. Invert that and the whole rotation is mirrored.
 */
private fun SiteUi.placeOn(canvas: Size): PlacedSite {
    val zone = BodyArt.zoneOf(bodyRegion, sublocation)
    val mirrored = when (side) {
        InjectionSide.LEFT -> bodyView == BodyView.BACK
        InjectionSide.RIGHT -> bodyView == BodyView.FRONT
        // A midline site has no side to mirror; the right-hand data already sits nearest the centre.
        InjectionSide.CENTER, InjectionSide.NOT_APPLICABLE -> false
    }
    return PlacedSite(
        site = this,
        zone = scaled(zone.outline, canvas, mirrored),
        center = zone.center.scaledTo(canvas, mirrored),
    )
}

private fun scaled(outline: String, canvas: Size, mirrored: Boolean): Path =
    PathParser().parsePathString(outline).toPath().apply { transform(matrixFor(canvas, mirrored)) }

private fun Offset.scaledTo(canvas: Size, mirrored: Boolean): Offset {
    val scaledX = x * canvas.width / BodyArt.VIEWPORT_WIDTH
    return Offset(if (mirrored) canvas.width - scaledX else scaledX, y * canvas.height / BodyArt.VIEWPORT_HEIGHT)
}

/** Viewport → canvas, flipped about the centre line for the left half of the body. */
private fun matrixFor(canvas: Size, mirrored: Boolean): Matrix = Matrix().apply {
    if (mirrored) {
        translate(x = canvas.width)
        scale(x = -1f)
    }
    scale(x = canvas.width / BodyArt.VIEWPORT_WIDTH, y = canvas.height / BodyArt.VIEWPORT_HEIGHT)
}

internal fun SiteStatus.labelRes(): Int = when (this) {
    SiteStatus.SUGGESTED -> R.string.sites_legend_suggested
    SiteStatus.COOLING -> R.string.sites_legend_cooling
    SiteStatus.RECENT -> R.string.sites_legend_recent
    SiteStatus.READY -> R.string.sites_legend_ready
}

// ---------------------------------------------------------------------------
// Ink
// ---------------------------------------------------------------------------

/** How far the muscle groups sit off the body they are drawn on. Enough to read, not to distract. */
private const val MUSCLE_ALPHA = 0.11f

/**
 * How strongly a site washes the zone it injects into.
 *
 * By state, not one value: with fourteen preset sites almost every zone is tinted at once, and a map
 * where all fourteen shout equally is a map that says nothing. A ready site is a faint wash that only
 * says "here"; the site the rotation picked, and the ones still cooling, are what the eye should find.
 */
private fun SiteStatus.zoneAlpha(): Float = when (this) {
    SiteStatus.SUGGESTED, SiteStatus.COOLING -> 0.32f
    SiteStatus.RECENT -> 0.22f
    SiteStatus.READY -> 0.11f
}

/** A dot is about this much of the map's width — narrow enough to sit on a forearm and still fit. */
private const val DOT_DIAMETER_FRACTION = 0.07f
private val DOT_MIN_DIAMETER = 11.dp
private val DOT_MAX_DIAMETER = 18.dp

/** §4.12.4's `primary` 60%-opacity ring around the suggested dot. */
private const val RING_ALPHA = 0.6f
private const val RING_RADIUS_SCALE = 1.9f
private const val RING_WIDTH_SCALE = 0.34f

/**
 * How far past its own edge a dot answers a tap, and the floor that keeps that reachable.
 *
 * Both scale with the canvas — the dot does — but a map squeezed into a Medium left pane would put
 * the four abdomen dots inside a fingertip of each other, so the target never shrinks below
 * [MIN_HIT_RADIUS] and [nearestSite] decides the overlaps.
 */
private const val HIT_RADIUS_SCALE = 1.4f
private val MIN_HIT_RADIUS: Dp = 14.dp
