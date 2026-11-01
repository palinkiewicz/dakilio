plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.moods.impl"
}

dependencies {
    api(projects.feature.moods.api)
}
