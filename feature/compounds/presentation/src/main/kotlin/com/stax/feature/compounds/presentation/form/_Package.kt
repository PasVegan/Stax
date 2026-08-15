/**
 * The Create / Edit Compound form (§4.4) — one screen serving both modes, plus onboarding step 2.
 *
 * MVI per §10.1: `CompoundFormState` / `CompoundFormAction` / `CompoundFormEvent` +
 * `CompoundFormViewModel`, rendered by the `CompoundFormRoot` / `CompoundFormScreen` pair over the
 * field primitives in `CompoundFormFields.kt` and the sections in `CompoundFormSections.kt`.
 *
 * The editable half of the state is `CompoundFormDraft`, a `@Serializable` value the ViewModel keeps
 * in its `SavedStateHandle` — that is what "auto-save draft on backgrounding" means here (§4.4.5):
 * the draft is written on every edit, so it survives process death, not merely a rotation.
 *
 * Boundaries: no Room — the compound is read and written through `CompoundRepository`, and no domain
 * model reaches the screen. Every numeric field is held as the user's raw text and only becomes a
 * `Decimal` / `Quantity` at validation time, so a half-typed "1." is never parsed (§3.0.1).
 *
 * Entry points: `CompoundFormRoot`, `CompoundFormViewModel`, `CompoundFormState`.
 */
package com.stax.feature.compounds.presentation.form
