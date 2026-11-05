plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.dakilio.hilt)
}

android {
    namespace = "pl.dakil.dakilio.core.notifications"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
}
