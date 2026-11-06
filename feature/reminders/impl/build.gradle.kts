plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.reminders.impl"
}

dependencies {
    api(projects.feature.reminders.api)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
