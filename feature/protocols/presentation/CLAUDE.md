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
  `protocolsEntries` (Nav3 entryProvider extension).
- `list/` — **Protocols list** (§4.7): `ProtocolsListViewModel`, `ProtocolsListState/Action/Event`,
  `ProtocolsListRoot`/`ProtocolsListScreen`, `ProtocolCard`, `ProtocolsSearchOverlay`,
  `ProtocolsSelectionMode` (§4.7.4's contextual bar, dock and archive dialog).
  `ProtocolListItemUi` carries schedule *parts* (`scheduleType`/`scheduleValue`/`weekdays`/
  `dosageTimes`), not a formatted string — weekday names, plural forms and the 12h/24h clock all
  resolve at render time from the device.
- `detail/` — **Protocol Detail** (§4.8): `ProtocolDetailViewModel` (+ `ProtocolDetailArgs`),
  `ProtocolDetailState/Action/Event`, `ProtocolDetailRoot`/`ProtocolDetailScreen`, and the cards of
  `ProtocolDetailSections`. `ScheduleCardUi` carries schedule *parts* for the same reason
  `ProtocolListItemUi` does; `ForecastUi`, `LinkedCompoundUi` and `SiteRestrictionsUi` are the other
  three cards.
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
- **§4.7.2's Archived tab is `deletedAt != null`, not a `ProtocolStatus`.** The ViewModel reads it
  through `ProtocolRepository.observeArchived()` and keeps the two halves in separate fields, so
  Active/Paused/Completed filter only the live half and a soft-deleted protocol has no path into
  them. An archived protocol still shows whichever status pill it holds — there is no Archived pill.
- **The next-dose chip reads the generated `ScheduledDose` rows** (`observeNextPendingPerProtocol`),
  not a re-derivation of the schedule, so a snoozed dose moves it. One query for the whole list, not
  one flow per card — Compound Detail can afford that fan-out because a compound has a handful of
  protocols; this screen has all of them. Nothing pending (paused, completed, or a break longer than
  the 7-day horizon) renders as "No dose scheduled" / "In break" rather than an empty chip.
- **§4.7.4's dock narrows the selection, it does not refuse it.** Pause takes the Active and in-break
  cards, Resume the Paused ones, Complete whatever is not finished; each button is disabled only when
  its own part of the selection is empty, so a mixed selection still does the obvious thing. Archive
  is the one tab-driven rule — disabled on Archived, where the rows are soft-deleted already. The
  flags live on `ProtocolsListState` (`canPause`/`canResume`/…) rather than in the dock, because the
  ViewModel filters by the same predicate when it runs the batch and the two must not drift.
  `ProtocolsListAction.Selection.Batch` groups the five writing actions for that reason.
- **The contextual bar replaces the chip row, not just the app bar.** Select all, Invert and every
  dock action read `state.items` — the tab's visible result list — so a tab switch mid-selection
  would swap out the rows they are about to act on. `:app` hides the nav suite for the dock through
  `protocolsEntries(onSelectionModeChange = …)`, the same route Compounds uses (§4.2.4).
- The card's chips wrap (`FlowRow`) instead of scrolling: inside a `360dp`/`400dp` list pane two
  chips rarely fit one line, and a chip half off the edge of a card reads as a layout bug.
- List+Detail uses the Nav3 list-detail Scene at Medium+ (§6.4.2). **Known gap, shared with
  Compounds**: `StaxListDetailScene` splits into two panes only at Expanded — at Medium the detail
  replaces the list, where §6.4.2 asks for a `360dp` list pane beside it. Fixing it belongs in the
  Scene wrapper (`:core:design-system`), not in either feature.
- **Protocol Detail's run-out date is `InventoryRepository.observeRunOutDate` (M3-09)**, not a second
  walk of the schedule — §4.8.5 and the Dashboard's warnings must not disagree about the same
  protocol. Doses remaining and required-until-end are computed here, but against the *same*
  stock-per-dose rule that aggregation uses (divide the dose out of the concentration where the stock
  is measured in volume, convert into the stock's unit otherwise), so the three figures agree.
  §4.8.5's warning row is the one condition the spec names: a batch expiring before the run-out.
- **§4.8.6's rotation chip is the site cooldown, resolved through §5.3's source order** — the
  protocol's override, else the Settings default for its route. There is no L/R rotation field in the
  domain; the spec's old "Rotate L / R" example was reconciled to the rule the app enforces.
- **§4.8.7 has no status chips** (unlike §4.3.7), so its paged history is unfiltered — one query, and
  `AdministrationEventRepository.pagedHistoryForProtocol` has no status parameter.
- Detail's `Instant.formatDayAndTime` and `ProtocolPill.labelRes` are `list/`'s, reused rather than
  copied — same module, same sentences.
- Detail leaves through the protocol flow alone (`observeById` emitting null), not from `render`:
  the combine emits again for the compound that goes null with it, and two `NavigateBack`s would pop
  two entries off the back stack. Archive is the other exit — a soft delete keeps the row emitting,
  so that one is sent explicitly.
- Create form reused by Onboarding step 3 — keep it self-contained.
- See spec §4.7–§4.9, §6.4.2 Protocols; ISSUES M9-*.
