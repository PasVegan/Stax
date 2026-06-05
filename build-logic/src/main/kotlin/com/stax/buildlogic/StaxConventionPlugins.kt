package com.stax.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

private const val CompileSdk = 36
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
