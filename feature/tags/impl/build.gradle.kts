plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.tags.impl"
}

dependencies {
    api(projects.feature.tags.api)
    implementation(libs.androidx.activity.compose)
}
