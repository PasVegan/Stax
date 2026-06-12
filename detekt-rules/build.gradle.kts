plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Custom detekt ruleset (loaded onto every module's `detektPlugins` classpath by
// DetektConventionPlugin). This module deliberately does NOT apply `com.stax.detekt`
// itself — it cannot depend on its own output, and detekt config validation would
// otherwise reject the `stax` ruleset block when analysing this module.
dependencies {
    compileOnly(libs.detekt.api)
}

kotlin {
    jvmToolchain(21)
}
