plugins {
    id("com.stax.android.feature")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))

    // Only the test double for CompoundRepository needs it: its opened-container writes take
    // LocalDate, and :core:domain does not re-export kotlinx-datetime.
    testImplementation(libs.kotlinx.datetime)
}
