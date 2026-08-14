# `:feature:settings:presentation` — Settings

## Purpose
Settings: appearance (theme picker, dynamic color), reminders config, data management (export/import
JSON with FK remap, reset all data, repair inventory), and About. Adaptive list-detail at Medium+.

## Module coordinates
- Gradle: `:feature:settings:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.settings.presentation` (`.settings`, `.di`, `.navigation`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `SettingsPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` route) +
  `settingsEntries` (Nav3 entryProvider extension).
- `SettingsViewModel` + `SettingsState`/`Action`/`Event`, with `SettingsRoot` + `SettingsScreen`
  (§10.1). Currently carries only the exact-alarm degraded warning row; M14-01 grows it into the
  Appearance / Reminders / Data / About section list-detail.
- Coming: theme-picker dialog, the rest of the reminders rows, export/import flows, reset/repair flows.

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Settings feature.

## Notes
- Theme changes write through `SettingsRepository`; the theme-critical fields are also cached in
  DataStore (`ThemePreferences`) for fast first-frame (§2.3.4) — the repo owns the write-through.
- Export/Import (§5.6) + reset (§5.11) are destructive/IO — run off-main via repository/workers;
  reset uses a typed-confirm dialog ("Type RESET").
- Section list-detail uses the Nav3 list-detail Scene at Medium+ (§6.4.2).
- **Exact-alarm warning row** (§5.1, M6-05): reads `Settings.exactAlarmDegraded` from the repository —
  it never calls `AlarmManager` itself. `:notification`'s `ExactAlarmPermissionMonitor` is the single
  writer of that flag, which is what makes the row appear and disappear live, including on the return
  trip from the system screen the CTA opens. Feature modules cannot depend on `:notification`, and this
  is the reason they do not need to. M14-03 moves the row under the Reminders section.
- See spec §4.13, §5.1, §5.6, §5.11, §6.4.2 Settings; ISSUES M6-05, M14-*.
