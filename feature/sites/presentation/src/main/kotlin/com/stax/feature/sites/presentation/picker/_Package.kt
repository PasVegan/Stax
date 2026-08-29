/**
 * §4.12.7's full-screen site picker: the filter chips, the suggested row, the full list, and the
 * bottom dock that hands the chosen site back to whoever opened the picker.
 *
 * Boundaries: UI models and Compose only (§2.3.1) — no Room, no domain models past the ViewModel's
 * own mapping, and no other feature's routes. The caller is named by `:app` (§10.3).
 *
 * Entry points: `SitePickerRoot`, `SitePickerViewModel`, `SitePickerArgs`.
 */
package com.stax.feature.sites.presentation.picker
