# `:detekt-rules` — custom detekt ruleset

## Purpose
Stax's custom detekt rules, packaged as a detekt plugin and loaded onto every product module's
`detektPlugins` classpath by `DetektConventionPlugin`. Holds architectural guardrails that detekt's
built-in rules cannot express — currently `NoCrossFeatureRouteImport`, the §10.3 backstop that
forbids a feature presentation module from importing another feature's routes.

## Module coordinates
- Gradle: `:detekt-rules` · plain Kotlin JVM (`org.jetbrains.kotlin.jvm`), JDK 21 toolchain.
- Package: `com.stax.detekt`.
- Ruleset id: `stax` (configured in root `detekt.yml`).
- **Does not** apply `com.stax.detekt` itself — a module cannot depend on its own output, and detekt
  config validation would reject the `stax` ruleset block when analysing this module.

## Allowed dependencies
`compileOnly(libs.detekt.api)` only (provided by the detekt runtime). No app/product modules.

## Key types
- `StaxRuleSetProvider` — `RuleSetProvider` (ruleset id `stax`); registered via the
  `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider` service file.
- `NoCrossFeatureRouteImport` — flags any `com.stax.feature.<a>` source importing
  `com.stax.feature.<b>` (a ≠ b).

## Applicable skills
`navigation-3` (the rule it enforces), `android-module-structure`.

## Owned by
Shared (build/tooling infra).

## Notes
- Wired in `DetektConventionPlugin` (`:build-logic`): every module except `:detekt-rules` gets
  `detektPlugins(project(":detekt-rules"))`.
- Complements the `checkForbiddenModuleDependencies` Gradle task (root `build.gradle.kts`), which
  already forbids feature → feature *module* dependencies; this rule is the static-analysis backstop.
- See spec §10.3, ISSUES M5-01.
