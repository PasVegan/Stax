/**
 * The exact-alarm permission layer for dose reminders (§5.1): reading "Alarms & reminders" special
 * access and mirroring it into `Settings.exactAlarmDegraded`.
 *
 * Boundaries: no Compose, no Room — persistence goes through `SettingsRepository`. Scheduling
 * itself (`setExactAndAllowWhileIdle`, bucket aggregation, cancellation) lands here at M13-02.
 *
 * Entry points: `ExactAlarmPermission` / `AndroidExactAlarmPermission`, `ExactAlarmPermissionMonitor`.
 */
package com.stax.notification.alarm
