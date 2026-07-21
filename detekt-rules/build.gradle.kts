plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Custom detekt ruleset (loaded onto every module's `detektPlugins` classpath by
// DetektConventionPlugin). This module deliberately does NOT apply `com.stax.detekt`
// itself — it cannot depend on its own output, and detekt config validation would
// otherwise reject the `stax` ruleset block when analysing this module.
dependencies {
    compileOnly(libs.detekt.api)

    // detekt-test's `lint()` compiles a code snippet and runs one rule over it, so each rule is
    // verified against real Kotlin rather than by hand-breaking the build.
    testImplementation(libs.detekt.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
