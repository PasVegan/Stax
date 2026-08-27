package com.stax.feature.protocols.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Protocols list — top-level destination. */
@Serializable
data object ProtocolsRoute : NavKey

/** Protocol detail for the protocol identified by [protocolId]. */
@Serializable
data class ProtocolDetailRoute(val protocolId: Long) : NavKey

/**
 * Create-protocol flow (§4.9).
 *
 * [onboarding] marks the instance that onboarding step 3 reuses (§4.14 step 3): the same form, with
 * the app bar titled "Create your first protocol · 3 of 3" and a Skip action in its trailing slot.
 * Onboarding cannot reach this route itself — `:app` builds it (§10.3).
 */
@Serializable
data class CreateProtocolRoute(val onboarding: Boolean = false) : NavKey

/** Edit the protocol identified by [protocolId] (§4.9, Edit mode). */
@Serializable
data class EditProtocolRoute(val protocolId: Long) : NavKey
