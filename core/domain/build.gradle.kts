plugins {
    id("com.stax.kotlin.library")
    id("com.stax.testing")
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
}
