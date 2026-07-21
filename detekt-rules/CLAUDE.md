# `:detekt-rules` — custom detekt ruleset

## Purpose
Stax's custom detekt rules, packaged as a detekt plugin and loaded onto every product module's
`detektPlugins` classpath by `DetektConventionPlugin`. Holds the architectural guardrails that
detekt's built-in rules cannot express: the §10.3 cross-feature-import backstop, plus the four
forbidden-API bans (inline `tween`, inline `RoundedCornerShape`, raw `Color(0x…)`, `WindowInsets`).

## Module coordinates
- Gradle: `:detekt-rules` · plain Kotlin JVM (`org.jetbrains.kotlin.jvm`), JDK 21 toolchain.
- Package: `com.stax.detekt`.
- Ruleset id: `stax` (configured in root `detekt.yml`).
- **Does not** apply `com.stax.detekt` itself — a module cannot depend on its own output, and detekt
  config validation would reject the `stax` ruleset block when analysing this module.

## Allowed dependencies
`compileOnly(libs.detekt.api)` only (provided by the detekt runtime). No app/product modules.
Tests add `detekt-test` + JUnit5 + AssertK.

## Key types
- `StaxRuleSetProvider` — `RuleSetProvider` (ruleset id `stax`); registered via the
  `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider` service file.
- `NoCrossFeatureRouteImport` — flags any `com.stax.feature.<a>` source importing
  `com.stax.feature.<b>` (a ≠ b).
- `NoInlineTween` — inline `tween(...)`; motion lives in `StaxMotion` (§5.9).
- `NoInlineRoundedCornerShape` — inline `RoundedCornerShape(...)`; use shape tokens (§9).
- `NoRawColorLiteral` — `Color(0x…)`; use `colorScheme` roles / `StaxColors` (§9). Hex-literal
  arguments only — `Color(r, g, b)` is a computed value, not a hardcoded design decision.
- `NoWindowInsetsOutsideDesignSystem` — inset padding/size modifiers, `consumeWindowInsets`, and
  `WindowInsets.<type>` reads; a pane gets exactly one inset method, `Modifier.paneInsets()` (§2.3.6).
- `CalleeName.kt` — `KtCallExpression.calleeName()`, shared by the four API bans.

Per-file exemptions are `excludes` globs in root `detekt.yml` (`StaxMotion.kt`, `Tokens.kt`,
`**/core/design-system/**`), not path checks in Kotlin — that keeps the exception list in one place
and lets a one-off be waived at the call site with `@Suppress("<RuleId>")`.

## Applicable skills
`navigation-3` (the rule it enforces), `android-module-structure`.

## Owned by
Shared (build/tooling infra).

## Notes
- Wired in `DetektConventionPlugin` (`:build-logic`): every module except `:detekt-rules` gets
  `detektPlugins(project(":detekt-rules"))`.
- Complements the `checkForbiddenModuleDependencies` Gradle task (root `build.gradle.kts`), which
  already forbids feature → feature *module* dependencies; `NoCrossFeatureRouteImport` is the
  static-analysis backstop. That task stays in Gradle — it inspects the module graph, not source.
- The four API bans replaced the old `checkForbidden{Motion,Shape,Color,Inset}Apis` Gradle tasks,
  which regex-matched raw lines and so could not tell a call from the same name inside a string or a
  comment, offered no IDE feedback, and had no `@Suppress` escape hatch.
- Tests (`ForbiddenApiRulesTest`) use `detekt-test`'s `lint()` — each rule runs against a real
  compiled snippet, so a rule is verified without hand-breaking the build.
- See spec §10.3, §2.3.6, §5.9, §9; ISSUES M5-01, M4-04/05/06, M5-09.
