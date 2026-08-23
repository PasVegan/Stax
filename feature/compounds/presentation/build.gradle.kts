plugins {
    id("com.stax.android.feature")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))
    // Expiry dates are LocalDate; :core:domain does not re-export kotlinx-datetime.
    implementation(libs.kotlinx.datetime)

    // The search overlay (§4.0.1) is a mode of the list screen, not a nav destination, so the back
    // gesture has to close it before NavDisplay pops the entry: BackHandler.
    implementation(libs.androidx.activity.compose)

    // §4.3.8's history is paged: collectAsLazyPagingItems in the Root, LazyPagingItems in the list.
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.androidx.paging.testing)
}
