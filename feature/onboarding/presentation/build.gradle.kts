plugins {
    id("com.stax.android.feature")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))

    // The notification gate (§4.15) drives the POST_NOTIFICATIONS request flow from its Root
    // composable: rememberLauncherForActivityResult + LocalActivity for the rationale check.
    implementation(libs.androidx.activity.compose)
}
