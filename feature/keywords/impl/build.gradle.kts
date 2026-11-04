plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.keywords.impl"
}

dependencies {
    api(projects.feature.keywords.api)
    implementation(projects.feature.entry.api)
}
