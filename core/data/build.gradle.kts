plugins {
    id("com.stax.android.library")
    id("com.stax.koin")
    id("com.stax.testing")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.datetime)
    // Room runtime needed for StaxDatabase supertype resolution (not re-exported by :core:database).
    implementation(libs.androidx.room.runtime)
}
