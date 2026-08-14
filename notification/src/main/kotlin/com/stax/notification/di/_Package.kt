/**
 * Koin module for the `:notification` module's out-of-app reminder machinery.
 *
 * Boundaries: bindings only — no logic, no Compose. Assembled into the graph by `:app`'s
 * `KoinInitializer` (§2.3.4).
 *
 * Entry points: `notificationModule`.
 */
package com.stax.notification.di
