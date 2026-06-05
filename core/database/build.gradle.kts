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

    androidTestImplementation(libs.androidx.room.testing)
}
