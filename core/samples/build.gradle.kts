plugins {
    alias(libs.plugins.dakilio.android.library)
    alias(libs.plugins.dakilio.hilt)
}

// The sample entries, their strings and photos. Only debug and preview builds of :app depend on it.
android {
    namespace = "pl.dakil.dakilio.core.samples"
}

dependencies {
    implementation(projects.core.database)
}
