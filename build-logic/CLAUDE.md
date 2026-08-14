# `:build-logic` — Gradle convention plugins

## Purpose
Included build that defines all `com.stax.*` Gradle convention plugins. Centralizes Android/Kotlin
config so feature/core modules stay tiny — they just apply a plugin. SDK levels, JDK toolchain,
Compose, Koin, Room, KSP, lint, and test wiring all live here.

## Module coordinates
- Gradle: included build (`includeBuild("build-logic")` in root `settings.gradle.kts`).
- Package: `com.stax.buildlogic`.
- Applies: the standard Gradle Kotlin DSL + AGP/KSP plugin APIs.

## Allowed dependencies
Build-tooling only (AGP, Kotlin Gradle plugin, KSP, ktlint, detekt). No app modules depend on it
at the source level; modules consume it by applying its plugin ids.

## Key types
- `AndroidApplicationConventionPlugin` / `AndroidLibraryConventionPlugin` / `AndroidFeatureConventionPlugin`
- `KotlinLibraryConventionPlugin` (pure-Kotlin modules like `:core:domain`)
- `ComposeConventionPlugin`, `KoinConventionPlugin`, `RoomConventionPlugin`,
  `KotlinxSerializationConventionPlugin`, `TestingConventionPlugin`, `KtlintConventionPlugin`,
  `DetektConventionPlugin`
- SDK/JDK constants in `StaxConventionPlugins.kt`: `CompileSdk = 37`, `MinSdk = 36`,
  `TargetSdk = 36`, `JavaToolchain = 21`.

## Applicable skills
`android-module-structure`.

## Owned by
Shared (build infra).

## Notes
- **`compileSdk = 37`** is required by `adaptive-navigation3 1.3.0-beta02`; do not lower it.
  Runtime min/target stay 36 (Android 16). Change SDK levels here only.
- Plugin ids are registered in `build-logic/build.gradle.kts` (both `com.stax.*` and `stax*` aliases).
- Compose compiler metrics are emitted to `build/compose_metrics/` per module (§2.3.1).
- **Compose UI tests run on Robolectric**, in `src/test` (§10.5): `TestingConventionPlugin` puts
  `ui-test-junit4` on the unit-test source set as well as `androidTest`, and turns on
  `unitTests.isIncludeAndroidResources`. The breakpoint matrix of §6.4.8 is therefore part of
  `./gradlew test` and needs no device.
- See spec §10.4, ISSUES M0-01/M0-02.
