/**
 * The Settings screen (§4.13) — today the exact-alarm degraded warning row (§5.1); the
 * Appearance / Reminders / Data / About sections land with M14-01.
 *
 * MVI per §10.1 — `SettingsState` / `SettingsAction` / `SettingsEvent` / `SettingsViewModel`, with
 * `SettingsRoot` + `SettingsScreen`. Boundaries: no Room — reminder precision is read from the
 * persisted `Settings` row via `SettingsRepository`, never by calling `AlarmManager` here.
 *
 * Entry points: `SettingsRoot`.
 */
package com.stax.feature.settings.presentation.settings
