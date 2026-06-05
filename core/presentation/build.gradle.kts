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
}
