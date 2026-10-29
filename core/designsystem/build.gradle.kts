plugins {
    alias(libs.plugins.dakilio.android.library.compose)
}

android {
    namespace = "pl.dakil.dakilio.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation)
    api(libs.androidx.graphics.shapes)
    implementation(libs.androidx.core.ktx)
}
