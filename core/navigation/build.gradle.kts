plugins {
    alias(libs.plugins.dakilio.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pl.dakil.dakilio.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.core)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
