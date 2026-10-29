plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.dakilio.hilt)
}

android {
    namespace = "pl.dakil.dakilio.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
}
