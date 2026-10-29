plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.diaries.impl"
}

dependencies {
    api(projects.feature.diaries.api)
}
