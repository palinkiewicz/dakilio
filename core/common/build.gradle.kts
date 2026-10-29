plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.dakilio.hilt)
}

android {
    namespace = "pl.dakil.dakilio.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
