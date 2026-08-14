plugins {
    id("com.stax.android.library")
    id("com.stax.koin")
    id("com.stax.testing")
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
}
