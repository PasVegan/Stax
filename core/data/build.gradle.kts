plugins {
    id("com.stax.android.library")
    id("com.stax.koin")
    id("com.stax.testing")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.datetime)
    // Room runtime + KTX needed for StaxDatabase and withTransaction (not re-exported by :core:database).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    // The Pager that wraps the DAO's PagingSource lives here — a feature never sees a Room type.
    implementation(libs.androidx.paging.common)

    testImplementation(libs.androidx.paging.testing)
}
