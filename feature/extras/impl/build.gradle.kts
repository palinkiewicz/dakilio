plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.extras.impl"
}

dependencies {
    api(projects.feature.extras.api)
    // Every extra's NavKey, so the hub can open it.
    implementation(projects.feature.tagcolors.api)
}
