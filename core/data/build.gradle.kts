plugins {
    id("com.stax.android.library")
    id("com.stax.koin")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
}
