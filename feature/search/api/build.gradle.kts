plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.dakil.dakilio.feature.search.api"
}

dependencies {
    api(projects.core.navigation)
}
