plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.tagcolors.impl"
}

dependencies {
    api(projects.feature.tagcolors.api)
    implementation(projects.feature.tags.api)
}
