plugins {
    id("com.stax.android.library")
    id("com.stax.room")
    id("com.stax.testing")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.datetime)
}
