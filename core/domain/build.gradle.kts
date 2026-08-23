plugins {
    id("com.stax.kotlin.library")
    id("com.stax.testing")
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    // The paged history read model of §4.3.8 is a Flow<PagingData<…>> on the repository interface.
    implementation(libs.androidx.paging.common)
}
