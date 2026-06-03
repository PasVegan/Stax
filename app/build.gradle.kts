plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.stax.health"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "io.stax.health"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "de.mannodermaus.junit5.AndroidJUnit5Runner"

        vectorDrawables {
            useSupportLibrary = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Modern 2026 optimizations
            optimization {
                enable = true
            }

            signingConfig = signingConfigs.getByName("debug") // Placeholder for demo
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = false
        resValues = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/kotlin/**"
            excludes += "**/META-INF/*.md"
            excludes += "**/META-INF/*.kotlin_module"
            excludes += "**/META-INF/MANIFEST.MF"
            excludes += "**/META-INF/com.android.tools/**"
            excludes += "**/META-INF/LICENSE.md"
            excludes += "**/META-INF/NOTICE.md"
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    experimentalProperties["android.experimental.privatizeResources"] = true
}

// Separate block for Kotlin settings if kotlinOptions is finicky in android block
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xopt-in=kotlin.RequiresOptIn",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.core)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.tooling.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Modern Unit Testing (JUnit 5 + AssertK + Turbine)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Modern UI Testing (JUnit 5 + Compose)
    androidTestImplementation(platform(libs.junit.bom))
    androidTestImplementation(libs.junit.jupiter.api)
    androidTestImplementation(libs.junit.jupiter.params)

    androidTestImplementation(libs.androidx.compose.ui.test.core)
    androidTestImplementation(libs.junit5.android.test.core)
    androidTestImplementation(libs.junit5.android.test.compose)
    androidTestRuntimeOnly(libs.junit5.android.test.runner)

    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
