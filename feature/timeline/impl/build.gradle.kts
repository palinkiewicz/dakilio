plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.timeline.impl"
}

dependencies {
    api(projects.feature.timeline.api)
    implementation(projects.feature.entry.api)
    implementation(projects.feature.search.api)
}
