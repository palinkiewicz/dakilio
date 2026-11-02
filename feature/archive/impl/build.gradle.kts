plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.archive.impl"
}

dependencies {
    api(projects.feature.archive.api)
}
