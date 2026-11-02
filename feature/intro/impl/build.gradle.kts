plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.intro.impl"
}

dependencies {
    api(projects.feature.intro.api)
    implementation(projects.feature.importer.api)
}
