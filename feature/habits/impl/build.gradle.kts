plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.habits.impl"
}

dependencies {
    api(projects.feature.habits.api)
}
