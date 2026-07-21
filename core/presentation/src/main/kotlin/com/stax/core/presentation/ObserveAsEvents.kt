package com.stax.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Collects a ViewModel's one-time [events] flow from a composable (§10.1).
 *
 * Collection is bound to the lifecycle — it starts at `STARTED` and stops at `STOPPED` — so an event
 * fired while the screen is in the background is not delivered to a UI that cannot act on it; the
 * `Channel` the ViewModel sends on holds it until collection resumes.
 *
 * [onEvent] runs on `Dispatchers.Main.immediate` so an event that triggers navigation takes effect
 * in the same frame rather than one dispatch later, which would let the user act on a screen that is
 * already leaving.
 *
 * Pass [key1] / [key2] when [onEvent] captures values that change over the screen's life — the
 * collector is restarted so it never invokes a stale lambda.
 */
@Suppress("FunctionName")
@Composable
fun <T> ObserveAsEvents(events: Flow<T>, key1: Any? = null, key2: Any? = null, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(events, lifecycleOwner.lifecycle, key1, key2) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                events.collect(onEvent)
            }
        }
    }
}
