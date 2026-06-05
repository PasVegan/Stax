plugins {
    id("com.stax.android.application")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:presentation"))
    implementation(project(":core:design-system"))
    implementation(project(":feature:onboarding:presentation"))
    implementation(project(":feature:compounds:presentation"))
    implementation(project(":feature:protocols:presentation"))
    implementation(project(":feature:sites:presentation"))
    implementation(project(":feature:dashboard:presentation"))
    implementation(project(":feature:reconstitution:presentation"))
    implementation(project(":feature:logging:presentation"))
    implementation(project(":feature:settings:presentation"))
    implementation(project(":widget"))
    implementation(project(":shortcut"))
    implementation(project(":work"))
    implementation(project(":notification"))
}
