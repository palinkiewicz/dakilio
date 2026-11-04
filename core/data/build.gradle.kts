plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.dakilio.hilt)
}

android {
    namespace = "pl.dakil.dakilio.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.daylio)
    api(projects.core.backup)
    implementation(projects.core.security)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.core.ktx)
}
