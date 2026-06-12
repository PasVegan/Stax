package com.stax.core.design.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * The current window [FoldingFeature], or `null` on non-foldables / when flat (§6.4.3). Provided by
 * [ProvideFoldingFeature] around the navigation roots; consumed by the adaptive Scene strategies
 * ([StaxListDetailScene], [StaxSupportingPaneScene]) to align the pane divider to a vertical hinge,
 * and available to screens for single-pane hinge padding and tabletop layouts.
 */
val LocalFoldingFeature: ProvidableCompositionLocal<FoldingFeature?> = compositionLocalOf { null }

/**
 * Collects `WindowInfoTracker.windowLayoutInfo` for the host Activity and exposes the active
 * [FoldingFeature] through [LocalFoldingFeature] (§6.4.3). Wrap the navigation roots with it.
 */
@Suppress("FunctionName")
@Composable
fun ProvideFoldingFeature(content: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val foldingFeature by produceState<FoldingFeature?>(initialValue = null, key1 = activity) {
        val current = activity ?: return@produceState
        WindowInfoTracker.getOrCreate(current)
            .windowLayoutInfo(current)
            .collect { layoutInfo ->
                value = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
    }
    CompositionLocalProvider(LocalFoldingFeature provides foldingFeature) {
        content()
    }
}

/**
 * The window-pixel bounds of a **vertical** hinge (book posture), for a `PaneScaffoldDirective`'s
 * `excludedBounds` so a two-pane divider snaps to the fold (§6.4.3). `null` for no fold or a
 * horizontal fold (which two-pane layouts ignore).
 */
internal fun FoldingFeature?.verticalHingeBounds(): List<Rect>? =
    this?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.let { listOf(it.bounds.toComposeRect()) }

private fun android.graphics.Rect.toComposeRect(): Rect =
    Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
