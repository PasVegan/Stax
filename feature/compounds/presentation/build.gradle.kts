plugins {
    id("com.stax.android.feature")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))
    // Expiry dates are LocalDate; :core:domain does not re-export kotlinx-datetime.
    implementation(libs.kotlinx.datetime)
}
