plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.insights.impl"
}

dependencies {
    api(projects.feature.insights.api)
}
