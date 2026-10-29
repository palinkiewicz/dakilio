plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.dakil.dakilio.feature.insights.api"
}

dependencies {
    api(projects.core.navigation)
}
