plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.importer.impl"
}

dependencies {
    api(projects.feature.importer.api)
    implementation(projects.feature.timeline.api)
    implementation(libs.androidx.activity.compose)
}
