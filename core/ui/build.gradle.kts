plugins {
    alias(libs.plugins.dakilio.android.library.compose)
}

android {
    namespace = "pl.dakil.dakilio.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(libs.coil.compose)
    implementation(libs.androidx.navigation3.ui)
}
