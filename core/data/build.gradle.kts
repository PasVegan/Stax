plugins {
    id("com.stax.android.library")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
}
