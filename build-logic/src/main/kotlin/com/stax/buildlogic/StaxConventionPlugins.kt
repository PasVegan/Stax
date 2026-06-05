package com.stax.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private const val CompileSdk = 37  // adaptive-navigation3 1.3.0-beta02 requires API 37
private const val MinSdk = 36
private const val TargetSdk = 36
private const val JavaToolchain = 21

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            namespace = defaultNamespace()
            compileSdk = CompileSdk

            defaultConfig {
                applicationId = defaultApplicationId()
                minSdk = MinSdk
                targetSdk = TargetSdk
                versionCode = 1
                versionName = "0.1.0"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }

        configureKotlin()
    }
}

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            namespace = defaultNamespace()
            compileSdk = CompileSdk

            defaultConfig {
                minSdk = MinSdk
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }

        configureKotlin()
    }
}

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.stax.android.library")
        pluginManager.apply("com.stax.compose")
        pluginManager.apply("com.stax.koin")

        addImplementation("androidx-lifecycle-runtime-compose")
        addImplementation("androidx-lifecycle-viewmodel-compose")
        addImplementation("androidx-navigation3-runtime")
        addImplementation("kotlinx-collections-immutable")
        addImplementation("kotlinx-coroutines-android")
    }
}

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlin()
    }
}

class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                buildFeatures.compose = true
            }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true
            }
        }

        addImplementationPlatform("androidx-compose-bom")
        addImplementation("androidx-compose-ui")
        addImplementation("androidx-compose-ui-tooling-preview")
        addImplementation("androidx-compose-material3")
        addImplementation("androidx-compose-material-icons-extended")
        addImplementation("androidx-compose-adaptive-layout")
        addImplementation("androidx-compose-adaptive-navigation3")
        addImplementation("androidx-window")
        addDebugImplementation("androidx-compose-ui-tooling")
    }
}

class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        addImplementation("koin-android")
        addImplementation("koin-compose")
    }
}

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        addImplementation("androidx-room-runtime")
        addImplementation("androidx-room-ktx")
        dependencies.add("ksp", libs.library("androidx-room-compiler"))
        Unit
    }
}

/**
 * Wires JUnit 5 (+ vintage engine for Robolectric/JUnit4 compat), AssertK, Turbine,
 * kotlinx-coroutines-test, and Robolectric into any module's test source set.
 * Also configures AndroidX instrumentation runner and Compose UI test deps for
 * Android modules.
 *
 * Apply via `id("com.stax.testing")` or `id("stax.testing")`.
 */
class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // JUnit Platform — Android library
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                testOptions.unitTests.all { it.useJUnitPlatform() }
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }
        }
        // JUnit Platform — Android application
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                testOptions.unitTests.all { it.useJUnitPlatform() }
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }
        }
        // JUnit Platform — pure Kotlin JVM module
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            tasks.withType<Test>().configureEach { useJUnitPlatform() }
        }

        // Core unit-test deps (all module types)
        addTestImplementationPlatform("junit-bom")       // pins all JUnit components to one version
        addTestImplementation("junit-jupiter")
        addTestRuntimeOnly("junit-platform-launcher")    // required by Gradle to start JUnit Platform
        addTestRuntimeOnly("junit-vintage-engine")       // runs JUnit4/Robolectric tests on JUnit Platform
        addTestImplementation("assertk")
        addTestImplementation("turbine")
        addTestImplementation("kotlinx-coroutines-test")

        // Robolectric — Android modules only
        pluginManager.withPlugin("com.android.library") {
            addTestImplementation("robolectric")
        }
        pluginManager.withPlugin("com.android.application") {
            addTestImplementation("robolectric")
        }

        // Instrumented-test runner + Compose UI test — Android modules
        pluginManager.withPlugin("com.android.library") {
            addAndroidTestImplementation("androidx-test-ext-junit")
            addAndroidTestImplementation("androidx-test-runner")
        }
        pluginManager.withPlugin("com.android.application") {
            addAndroidTestImplementation("androidx-test-ext-junit")
            addAndroidTestImplementation("androidx-test-runner")
        }
        // Compose UI test deps — only when compose plugin is also applied
        pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
            addAndroidTestImplementation("androidx-compose-ui-test-junit4")
            addDebugImplementation("androidx-compose-ui-test-manifest")
        }
    }
}

class KotlinxSerializationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        addImplementation("kotlinx-serialization-json")
    }
}

private fun Project.configureKotlin() {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<KotlinBaseExtension>("kotlin") {
            jvmToolchain(JavaToolchain)
        }
    }
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<KotlinBaseExtension>("kotlin") {
            jvmToolchain(JavaToolchain)
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(JavaToolchain)
        }
    }

    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}

private val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

private fun Project.addImplementation(alias: String) {
    dependencies.add("implementation", libs.library(alias))
}

private fun Project.addDebugImplementation(alias: String) {
    dependencies.add("debugImplementation", libs.library(alias))
}

private fun Project.addTestImplementation(alias: String) {
    dependencies.add("testImplementation", libs.library(alias))
}

private fun Project.addTestImplementationPlatform(alias: String) {
    dependencies.add("testImplementation", dependencies.platform(libs.library(alias)))
}

private fun Project.addTestRuntimeOnly(alias: String) {
    dependencies.add("testRuntimeOnly", libs.library(alias))
}

private fun Project.addAndroidTestImplementation(alias: String) {
    dependencies.add("androidTestImplementation", libs.library(alias))
}

private fun Project.addImplementationPlatform(alias: String) {
    dependencies.add("implementation", dependencies.platform(libs.library(alias)))
}

private fun Project.defaultApplicationId(): String =
    if (path == ":app") "com.stax.app" else defaultNamespace()

private fun Project.defaultNamespace(): String {
    val suffix = path
        .split(":")
        .filter(String::isNotBlank)
        .joinToString(".") { segment ->
            segment
                .replace("-", ".")
                .replace(Regex("[^A-Za-z0-9_.]"), "")
        }

    return if (suffix.isBlank()) "com.stax" else "com.stax.$suffix"
}
