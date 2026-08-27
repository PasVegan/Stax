plugins {
    id("com.stax.android.feature")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))
    // Schedules, dosage times and duration dates are LocalDate/LocalTime/DayOfWeek;
    // :core:domain does not re-export kotlinx-datetime.
    implementation(libs.kotlinx.datetime)

    // The form confirms a dirty discard before it closes (§4.4.5's rule, which §4.9 inherits), so the
    // back gesture has to be intercepted before NavDisplay pops the entry: BackHandler.
    implementation(libs.androidx.activity.compose)
}
