plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.backup.impl"
}

dependencies {
    api(projects.feature.backup.api)
    implementation(libs.androidx.activity.compose)
}
