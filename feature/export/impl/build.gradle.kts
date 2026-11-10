plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.export.impl"
}

dependencies {
    api(projects.feature.export.api)
    implementation(libs.androidx.activity.compose)
}
