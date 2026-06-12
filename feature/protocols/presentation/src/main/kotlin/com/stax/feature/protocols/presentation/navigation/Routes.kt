package com.stax.feature.protocols.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Protocols list — top-level destination. */
@Serializable
data object ProtocolsRoute : NavKey

/** Protocol detail for the protocol identified by [protocolId]. */
@Serializable
data class ProtocolDetailRoute(val protocolId: Long) : NavKey

/** Create-protocol flow. */
@Serializable
data object CreateProtocolRoute : NavKey
