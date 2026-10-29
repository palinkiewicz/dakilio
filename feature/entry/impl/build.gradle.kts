plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.entry.impl"
}

dependencies {
    api(projects.feature.entry.api)
    implementation(libs.androidx.activity.compose)
    implementation(projects.feature.tags.api)
}
