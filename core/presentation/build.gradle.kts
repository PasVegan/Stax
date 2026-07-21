plugins {
    id("com.stax.android.library")
    id("com.stax.compose")
    id("com.stax.testing")
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    // ObserveAsEvents: lifecycle-aware collection of a ViewModel's event flow (§10.1).
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
}
