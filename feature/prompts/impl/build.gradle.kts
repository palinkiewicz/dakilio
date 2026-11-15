plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.prompts.impl"
}

dependencies {
    api(projects.feature.prompts.api)
}
