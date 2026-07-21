plugins {
    id("com.stax.android.feature")
    id("com.stax.testing")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))
}
