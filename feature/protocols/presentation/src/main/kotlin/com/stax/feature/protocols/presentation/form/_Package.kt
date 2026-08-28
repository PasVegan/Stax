/**
 * The Create / Edit Protocol form (§4.9) — one screen serving both modes, plus onboarding step 3.
 *
 * MVI per §10.1: `ProtocolFormState` / `ProtocolFormAction` / `ProtocolFormEvent` +
 * `ProtocolFormViewModel`, rendered by the `ProtocolFormRoot` / `ProtocolFormScreen` pair over the
 * field primitives in `ProtocolFormFields.kt`, the input sections in `ProtocolFormSections.kt`, the
 * right-hand sections in `ProtocolFormForecast.kt`, and the localized names in
 * `ProtocolFormLabels.kt`.
 *
 * The editable half of the state is `ProtocolFormDraft`, a `@Serializable` value the ViewModel keeps
 * in its `SavedStateHandle` — §4.4.5's auto-saved draft, which §4.9 inherits: written on every edit,
 * so it survives process death and not merely a rotation.
 *
 * **Save belongs to the repository** (§4.9.7). Create goes through `ProtocolRepository.create`, which
 * generates the 7-day Pending horizon; Edit goes through `update`, which runs §5.4's pending-regen
 * scope rule. Neither rule is reimplemented here — the ViewModel only picks which call to make.
 *
 * The live Forecast & warnings card and the 11b next-7-days strip read the **schedule rule** from
 * `:core:domain` (`Protocol.dosingTimesOn`, `dosesBetween`, §5.2) — the same rule
 * `ScheduledDoseGenerator` uses — so what the form previews is what Save writes. A feature module may
 * not import `:core:data`, which is why that rule lives in the domain.
 *
 * Boundaries: no Room; protocols, compounds and settings are reached through their repositories, and
 * no domain model reaches the screen. Every numeric field is held as the user's raw text and only
 * becomes a `Decimal` / `Quantity` at validation time, so a half-typed "1." is never parsed (§3.0.1).
 *
 * §4.9.3 has no control for `name`, `escalation`, `protocolBreak` or `siteCooldownDays`: a created
 * protocol takes its compound's name, and an edit carries all four through untouched.
 *
 * Entry points: `ProtocolFormRoot`, `ProtocolFormViewModel`, `ProtocolFormState`.
 */
package com.stax.feature.protocols.presentation.form
