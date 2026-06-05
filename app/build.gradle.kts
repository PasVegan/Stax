plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.stax.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stax.app"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}
