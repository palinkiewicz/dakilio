plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.settings.impl"
}

dependencies {
    api(projects.feature.settings.api)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
