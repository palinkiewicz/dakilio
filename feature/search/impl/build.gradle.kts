plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.search.impl"
}

dependencies {
    api(projects.feature.search.api)
    implementation(projects.feature.entry.api)
}
