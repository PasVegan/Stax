plugins {
    id("com.stax.android.library")
    id("com.stax.room")
    id("com.stax.testing")
}

ksp {
    arg("room.schemaLocation", "${rootProject.projectDir}/app/schemas")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.datetime)
    // Room-generated PagingSource for the compound history query (§4.3.8).
    implementation(libs.androidx.room.paging)

    testImplementation(libs.androidx.paging.testing)
    androidTestImplementation(libs.androidx.room.testing)
}
