/**
 * Navigation 3 typed routes (`ProtocolsRoute`, `ProtocolDetailRoute`, `CreateProtocolRoute`) and the
 * [com.stax.feature.protocols.presentation.navigation.protocolsEntries] entryProvider extension for
 * the Protocols feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`.
 *
 * Entry points: `protocolsEntries(...)`.
 */
package com.stax.feature.protocols.presentation.navigation
