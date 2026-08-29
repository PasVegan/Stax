plugins {
    id("com.stax.android.feature")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))

    // Cooldowns and "14 days rested" are Instant/LocalDate arithmetic (§4.12.3, §4.12.5);
    // :core:domain does not re-export kotlinx-datetime.
    implementation(libs.kotlinx.datetime)
}
