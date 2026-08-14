package com.stax.feature.settings.presentation.settings

/**
 * UI state of the Settings screen (§4.13).
 *
 * [exactAlarmDegraded] mirrors `Settings.exactAlarmDegraded` (§3.8) — the persisted answer to
 * "may we schedule exact alarms?". The screen never asks `AlarmManager` itself: `:notification`'s
 * `ExactAlarmPermissionMonitor` is the single writer and keeps the flag current on app start and on
 * every permission-state change, so reading the row is enough to stay in sync (§5.1).
 */
data class SettingsState(val exactAlarmDegraded: Boolean = false)
