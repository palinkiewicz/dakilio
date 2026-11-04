plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.lock.impl"
}

dependencies {
    api(projects.feature.lock.api)
    implementation(projects.core.security)
    implementation(libs.androidx.activity.compose)
}
