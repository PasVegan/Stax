# `:feature:protocols:presentation` — Protocols

## Purpose
Dosing protocols: protocols list (+ multi-select), Protocol Detail, Create/Edit Protocol (with live
forecast & warnings), escalation rules, and the pause-with-unsaved-changes flow.

## Module coordinates
- Gradle: `:feature:protocols:presentation` · plugin `com.stax.android.feature`.
- Package: `com.stax.feature.protocols.presentation` (`.di`).
- Deps: `:core:domain`, `:core:presentation`, `:core:design-system`.

## Allowed dependencies
`:core:domain`, `:core:presentation`, `:core:design-system` only.

## Key types
- `ProtocolsPresentationModule` (Koin); `navigation/Routes.kt` (`@Serializable` `NavKey` routes:
  `ProtocolsRoute`, `ProtocolDetailRoute`, `CreateProtocolRoute(onboarding)`, `EditProtocolRoute`) +
  `protocolsEntries` (Nav3 entryProvider extension). Coming: list/detail ViewModels &
  State/Action/Event, Root/Screen composables.
- `form/` — **Create / Edit Protocol** (§4.9), one MVI screen for both modes:
  `ProtocolFormViewModel` (+ `ProtocolFormArgs(protocolId, isOnboarding)`), `ProtocolFormDraft`
  (`@Serializable`, auto-saved to `SavedStateHandle`), `ProtocolFormState/Action/Event`,
  `ProtocolFormRoot`/`ProtocolFormScreen`, and the section/field/label files that build it.
  `protocolId == null` = Create.
- `CreateProtocolRoute(onboarding)` — onboarding step 3 reuses this form (§4.14 step 3): same screen,
  app bar titled "Create your first protocol · 3 of 3" with Skip in the trailing slot, driven by the
  route flag. It is the last step, so Save and Skip both end the flow;
  `protocolsEntries(onFinishOnboarding = …)` carries that back to `:app`, which owns the flow — this
  module still knows nothing about the onboarding feature (§10.3).

## Applicable skills
`android-presentation-mvi`, `android-compose-ui`, `navigation-3`, `adaptive`, `android-di-koin`.

## Owned by
Protocols feature.

## Notes
- Escalation math + scheduled-dose generation live in `:core:domain`/`:core:data`
  (`ScheduledDoseGenerator`) — the UI only configures them; do not reimplement dose math here. The
  **schedule rule** the live preview and forecast read is `:core:domain`'s `ScheduleEngine`
  (`Protocol.dosingTimesOn` / `dosesBetween`, `SCHEDULE_HORIZON_DAYS`), which is the same rule the
  generator uses — so what the form previews is what Save writes.
- **Save is the repository's job** (§4.9.7): `create` generates the 7-day Pending horizon, `update`
  runs §5.4's pending-regen scope rule. The ViewModel only picks which of the two to call.
- **§4.9.6 Save + Pause is one write, not two.** `update(protocol.copy(status = Paused))` lands the
  edits and runs §5.4's regen against an already-paused protocol, which generates nothing (§5.2). An
  `update` followed by a `pause` would rebuild the horizon for a protocol about to stop dosing.
  "Pause without saving" is the plain `pause`, and Cancel writes nothing. An untouched form never
  sees the dialog.
- **§4.9.3 has no control for `name`, `escalation`, `protocolBreak` or `siteCooldownDays`.** A created
  protocol takes its compound's name; an edit carries all four through from the loaded protocol
  untouched. Dropping any of them would silently flatten a titration.
- The form's field shapes (`FormTextField`, `FormPickerField`, `FormSectionHeader`) mirror the
  compound form's rather than sharing them — features never depend on features (§10.4). Lift them
  into `:core:design-system` when a third form wants them (there is a `ponytail:` marker on them).
- List+Detail uses the Nav3 list-detail Scene at Medium+ (§6.4.2).
- Create form reused by Onboarding step 3 — keep it self-contained.
- See spec §4.7–§4.9, §6.4.2 Protocols; ISSUES M9-*.
