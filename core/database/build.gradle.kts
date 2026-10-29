plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.dakilio.android.room)
    alias(libs.plugins.dakilio.hilt)
}

android {
    namespace = "pl.dakil.dakilio.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
}
