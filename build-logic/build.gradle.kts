plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("comStaxAndroidApplication") {
            id = "com.stax.android.application"
            implementationClass = "com.stax.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("staxAndroidApplication") {
            id = "stax.android.application"
            implementationClass = "com.stax.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("comStaxAndroidLibrary") {
            id = "com.stax.android.library"
            implementationClass = "com.stax.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("staxAndroidLibrary") {
            id = "stax.android.library"
            implementationClass = "com.stax.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("comStaxAndroidFeature") {
            id = "com.stax.android.feature"
            implementationClass = "com.stax.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("staxAndroidFeature") {
            id = "stax.android.feature"
            implementationClass = "com.stax.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("comStaxKotlinLibrary") {
            id = "com.stax.kotlin.library"
            implementationClass = "com.stax.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("staxKotlinLibrary") {
            id = "stax.kotlin.library"
            implementationClass = "com.stax.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("comStaxCompose") {
            id = "com.stax.compose"
            implementationClass = "com.stax.buildlogic.ComposeConventionPlugin"
        }
        register("staxCompose") {
            id = "stax.compose"
            implementationClass = "com.stax.buildlogic.ComposeConventionPlugin"
        }
        register("comStaxKoin") {
            id = "com.stax.koin"
            implementationClass = "com.stax.buildlogic.KoinConventionPlugin"
        }
        register("staxKoin") {
            id = "stax.koin"
            implementationClass = "com.stax.buildlogic.KoinConventionPlugin"
        }
        register("comStaxRoom") {
            id = "com.stax.room"
            implementationClass = "com.stax.buildlogic.RoomConventionPlugin"
        }
        register("staxRoom") {
            id = "stax.room"
            implementationClass = "com.stax.buildlogic.RoomConventionPlugin"
        }
        register("comStaxKotlinxSerialization") {
            id = "com.stax.kotlinx.serialization"
            implementationClass = "com.stax.buildlogic.KotlinxSerializationConventionPlugin"
        }
        register("staxKotlinxSerialization") {
            id = "stax.kotlinx.serialization"
            implementationClass = "com.stax.buildlogic.KotlinxSerializationConventionPlugin"
        }
        register("comStaxTesting") {
            id = "com.stax.testing"
            implementationClass = "com.stax.buildlogic.TestingConventionPlugin"
        }
        register("staxTesting") {
            id = "stax.testing"
            implementationClass = "com.stax.buildlogic.TestingConventionPlugin"
        }
        register("comStaxKtlint") {
            id = "com.stax.ktlint"
            implementationClass = "com.stax.buildlogic.KtlintConventionPlugin"
        }
        register("staxKtlint") {
            id = "stax.ktlint"
            implementationClass = "com.stax.buildlogic.KtlintConventionPlugin"
        }
        register("comStaxDetekt") {
            id = "com.stax.detekt"
            implementationClass = "com.stax.buildlogic.DetektConventionPlugin"
        }
        register("staxDetekt") {
            id = "stax.detekt"
            implementationClass = "com.stax.buildlogic.DetektConventionPlugin"
        }
    }
}
