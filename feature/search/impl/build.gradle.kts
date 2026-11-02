plugins {
    alias(libs.plugins.dakilio.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.dakil.dakilio.feature.search.impl"
}

dependencies {
    api(projects.feature.search.api)
    implementation(projects.feature.entry.api)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
}
